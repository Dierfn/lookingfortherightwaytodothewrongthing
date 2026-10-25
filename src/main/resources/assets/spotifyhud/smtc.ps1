# Spotify HUD media bridge.
# Reads the Windows "System Media Transport Controls" (the same data that shows in the Windows
# volume / media flyout) and prints one JSON object per line. Accepts commands on stdin.
# Windows PowerShell 5.1 only (powershell.exe).

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)

$script:Filter = 'spotify'
$script:lastKey = ''
$script:stable = 0
$script:artSent = $false
$script:artTries = 0

function Emit($obj) {
    [Console]::Out.WriteLine(($obj | ConvertTo-Json -Compress -Depth 4))
    [Console]::Out.Flush()
}

Add-Type -AssemblyName System.Runtime.WindowsRuntime
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
[void][Windows.Media.MediaPlaybackAutoRepeatMode, Windows.Media, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]

$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Await($op, [Type]$type) {
    $task = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
    [void]$task.Wait(5000)
    if ($task.IsCompleted) { return $task.Result }
    return $null
}

$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
if ($null -eq $mgr) {
    Emit @{ type = 'error'; msg = 'Could not open the Windows media session manager (needs Windows 10 1809 or newer).' }
    exit 1
}

function Pick-Session {
    if ($script:Filter -ne '') {
        foreach ($x in $mgr.GetSessions()) {
            if ($x.SourceAppUserModelId -like ('*' + $script:Filter + '*')) { return $x }
        }
        return $null
    }
    return $mgr.GetCurrentSession()
}

# ---- volume (Windows Core Audio) ---------------------------------------------------------------
# Controls the volume of the Spotify app only (its entry in the Windows volume mixer). If Spotify has no
# audio session yet, or the media source is "any player", it falls back to the PC's master volume.
# Written in C# 5 syntax because Windows PowerShell 5.1 compiles Add-Type with the old compiler.
$script:VolOk = $false
try {
    Add-Type -TypeDefinition @"
using System;
using System.Diagnostics;
using System.Runtime.InteropServices;

namespace SpotifyHudAudio {
    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    class MMDeviceEnumeratorCom { }

    [ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDeviceEnumerator {
        int EnumAudioEndpoints(int dataFlow, int stateMask, out IntPtr devices);
        int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice device);
    }

    [ComImport, Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDevice {
        int Activate(ref Guid iid, int clsCtx, IntPtr activationParams, [MarshalAs(UnmanagedType.IUnknown)] out object iface);
    }

    [ComImport, Guid("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IAudioSessionManager2 {
        int GetAudioSessionControl(IntPtr audioSessionGuid, int streamFlags, out IntPtr sessionControl);
        int GetSimpleAudioVolume(IntPtr audioSessionGuid, int streamFlags, out IntPtr audioVolume);
        int GetSessionEnumerator(out IAudioSessionEnumerator sessionEnum);
    }

    [ComImport, Guid("E2F5BB11-0570-40CA-ACDD-3AA01277DEE8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IAudioSessionEnumerator {
        int GetCount(out int count);
        int GetSession(int index, out IAudioSessionControl2 session);
    }

    [ComImport, Guid("BFB7FF88-7239-4FC9-8FA2-07C950BE9C6D"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IAudioSessionControl2 {
        int GetState(out int state);
        int GetDisplayName([MarshalAs(UnmanagedType.LPWStr)] out string name);
        int SetDisplayName([MarshalAs(UnmanagedType.LPWStr)] string name, ref Guid ctx);
        int GetIconPath([MarshalAs(UnmanagedType.LPWStr)] out string path);
        int SetIconPath([MarshalAs(UnmanagedType.LPWStr)] string path, ref Guid ctx);
        int GetGroupingParam(out Guid param);
        int SetGroupingParam(ref Guid param, ref Guid ctx);
        int RegisterAudioSessionNotification(IntPtr client);
        int UnregisterAudioSessionNotification(IntPtr client);
        int GetSessionIdentifier([MarshalAs(UnmanagedType.LPWStr)] out string id);
        int GetSessionInstanceIdentifier([MarshalAs(UnmanagedType.LPWStr)] out string id);
        int GetProcessId(out uint pid);
        int IsSystemSoundsSession();
        int SetDuckingPreference(bool optOut);
    }

    [ComImport, Guid("87CE5498-68D6-44E5-9215-6DA47EF883D8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface ISimpleAudioVolume {
        int SetMasterVolume(float level, ref Guid ctx);
        int GetMasterVolume(out float level);
        int SetMute(bool mute, ref Guid ctx);
        int GetMute(out bool mute);
    }

    [ComImport, Guid("5CDF2C82-841E-4546-9722-0CF74078229A"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IAudioEndpointVolume {
        int RegisterControlChangeNotify(IntPtr notify);
        int UnregisterControlChangeNotify(IntPtr notify);
        int GetChannelCount(out uint count);
        int SetMasterVolumeLevel(float db, ref Guid ctx);
        int SetMasterVolumeLevelScalar(float level, ref Guid ctx);
        int GetMasterVolumeLevel(out float db);
        int GetMasterVolumeLevelScalar(out float level);
    }

    public static class Volume {
        static Guid NoCtx = Guid.Empty;

        static IMMDevice Device() {
            IMMDeviceEnumerator en = (IMMDeviceEnumerator)(new MMDeviceEnumeratorCom());
            IMMDevice dev;
            Marshal.ThrowExceptionForHR(en.GetDefaultAudioEndpoint(0, 1, out dev)); // eRender, eMultimedia
            return dev;
        }

        static object Activate(IMMDevice dev, Guid iid) {
            object o;
            Marshal.ThrowExceptionForHR(dev.Activate(ref iid, 23, IntPtr.Zero, out o)); // CLSCTX_ALL
            return o;
        }

        // Every audio session whose process name contains `filter` (Spotify runs several processes).
        static System.Collections.Generic.List<ISimpleAudioVolume> Sessions(string filter) {
            System.Collections.Generic.List<ISimpleAudioVolume> found = new System.Collections.Generic.List<ISimpleAudioVolume>();
            if (string.IsNullOrEmpty(filter)) return found;
            IAudioSessionManager2 mgr = (IAudioSessionManager2)Activate(Device(), typeof(IAudioSessionManager2).GUID);
            IAudioSessionEnumerator list;
            Marshal.ThrowExceptionForHR(mgr.GetSessionEnumerator(out list));
            int n;
            list.GetCount(out n);
            for (int i = 0; i < n; i++) {
                try {
                    IAudioSessionControl2 ctl;
                    if (list.GetSession(i, out ctl) != 0 || ctl == null) continue;
                    uint pid;
                    ctl.GetProcessId(out pid);
                    if (pid == 0) continue;
                    string name = Process.GetProcessById((int)pid).ProcessName;
                    if (name.IndexOf(filter, StringComparison.OrdinalIgnoreCase) < 0) continue;
                    ISimpleAudioVolume v = ctl as ISimpleAudioVolume;
                    if (v != null) found.Add(v);
                } catch (Exception) { }
            }
            return found;
        }

        static IAudioEndpointVolume Master() {
            return (IAudioEndpointVolume)Activate(Device(), typeof(IAudioEndpointVolume).GUID);
        }

        public static float Get(string filter) {
            try {
                System.Collections.Generic.List<ISimpleAudioVolume> s = Sessions(filter);
                if (s.Count > 0) {
                    float lv;
                    s[0].GetMasterVolume(out lv);
                    return lv;
                }
                float m;
                Master().GetMasterVolumeLevelScalar(out m);
                return m;
            } catch (Exception) { return -1f; }
        }

        public static void Set(string filter, float level) {
            if (level < 0f) level = 0f;
            if (level > 1f) level = 1f;
            try {
                System.Collections.Generic.List<ISimpleAudioVolume> s = Sessions(filter);
                if (s.Count > 0) {
                    foreach (ISimpleAudioVolume v in s) v.SetMasterVolume(level, ref NoCtx);
                    return;
                }
                Master().SetMasterVolumeLevelScalar(level, ref NoCtx);
            } catch (Exception) { }
        }
    }
}
"@
    $script:VolOk = $true
} catch {
    $script:VolOk = $false
}

function Get-Vol {
    if (-not $script:VolOk) { return -1.0 }
    try { return [math]::Round([double][SpotifyHudAudio.Volume]::Get($script:Filter), 3) } catch { return -1.0 }
}

function Art-Status($msg) {
    Emit ([ordered]@{ type = 'artstatus'; msg = $msg })
}

function Get-Art($ref) {
    $stream = $null
    try {
        $stream = Await ($ref.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
    } catch {
        Art-Status ('could not open thumbnail: ' + $_.Exception.Message)
        return $null
    }
    if ($null -eq $stream) { Art-Status 'thumbnail did not open'; return $null }
    $size = [uint32]$stream.Size
    if ($size -lt 100) { Art-Status ('thumbnail empty (' + $size + ' bytes)'); return $null }

    # Method 1: DataReader (plain WinRT, most reliable)
    try {
        $reader = New-Object Windows.Storage.Streams.DataReader($stream)
        [void](Await ($reader.LoadAsync($size)) ([uint32]))
        $bytes = New-Object byte[] $size
        $reader.ReadBytes($bytes)
        return [Convert]::ToBase64String($bytes)
    } catch {
        $e1 = $_.Exception.Message
    }
    # Method 2: convert to a .NET stream
    try {
        $net = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($stream)
        $ms = New-Object System.IO.MemoryStream
        $net.CopyTo($ms)
        $net.Dispose()
        return [Convert]::ToBase64String($ms.ToArray())
    } catch {
        Art-Status ('could not read thumbnail: ' + $e1 + ' / ' + $_.Exception.Message)
        return $null
    }
}

function Send-State {
    $s = Pick-Session
    if ($null -eq $s) { Emit ([ordered]@{ type = 'state'; active = $false }); return }
    $props = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
    if ($null -eq $props) { Emit ([ordered]@{ type = 'state'; active = $false }); return }

    $pi = $s.GetPlaybackInfo()
    $tl = $s.GetTimelineProperties()
    $playing = ([string]$pi.PlaybackStatus -eq 'Playing')

    $dur = ($tl.EndTime - $tl.StartTime).TotalMilliseconds
    $pos = ($tl.Position - $tl.StartTime).TotalMilliseconds
    if ($playing) { $pos += ([DateTimeOffset]::Now - $tl.LastUpdatedTime).TotalMilliseconds }
    if ($dur -lt 0) { $dur = 0 }
    if ($pos -lt 0) { $pos = 0 }
    if ($dur -gt 0 -and $pos -gt $dur) { $pos = $dur }

    $shuffle = $false
    if ($null -ne $pi.IsShuffleActive) { $shuffle = [bool]$pi.IsShuffleActive }
    $repeat = ''
    if ($null -ne $pi.AutoRepeatMode) { $repeat = [string]$pi.AutoRepeatMode }

    Emit ([ordered]@{
        type    = 'state'
        active  = $true
        app     = [string]$s.SourceAppUserModelId
        title   = [string]$props.Title
        artist  = [string]$props.Artist
        album   = [string]$props.AlbumTitle
        playing = $playing
        shuffle = $shuffle
        repeat  = $repeat
        pos     = [long]$pos
        dur     = [long]$dur
        vol     = (Get-Vol)
    })

    # Cover art: only fetched once per track, after the track has been stable for a poll
    # (Spotify sometimes still shows the previous cover for a moment).
    $key = [string]$props.Title + '|' + [string]$props.Artist + '|' + [string]$props.AlbumTitle
    if ($key -ne $script:lastKey) {
        $script:lastKey = $key
        $script:stable = 0
        $script:artSent = $false
        $script:artTries = 0
    } else {
        $script:stable++
    }
    if (-not $script:artSent -and $script:stable -ge 1 -and $null -eq $props.Thumbnail -and $script:artTries -eq 0) {
        Art-Status 'Spotify has not provided a cover yet'
    }
    if (-not $script:artSent -and $script:stable -ge 1 -and $script:artTries -lt 40 -and $null -ne $props.Thumbnail) {
        $script:artTries++
        $b64 = Get-Art $props.Thumbnail
        if ($b64) {
            $script:artSent = $true
            Emit ([ordered]@{
                type   = 'art'
                title  = [string]$props.Title
                artist = [string]$props.Artist
                album  = [string]$props.AlbumTitle
                data   = $b64
            })
        }
    }
}

function Handle-Command($line) {
    $p = $line.Trim().Split(' ', 2)
    $cmd = $p[0]
    $arg = ''
    if ($p.Count -gt 1) { $arg = $p[1] }
    if ($cmd -eq 'filter') { $script:Filter = $arg.Trim(); return }
    if ($cmd -eq 'volume') {
        if ($script:VolOk) {
            $v = 0.0
            if ([double]::TryParse($arg.Trim(), [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref]$v)) {
                [SpotifyHudAudio.Volume]::Set($script:Filter, [single]$v)
            }
        }
        return
    }
    $s = Pick-Session
    if ($null -eq $s) { return }
    switch ($cmd) {
        'toggle'  { [void]$s.TryTogglePlayPauseAsync() }
        'play'    { [void]$s.TryPlayAsync() }
        'pause'   { [void]$s.TryPauseAsync() }
        'next'    { [void]$s.TrySkipNextAsync() }
        'prev'    { [void]$s.TrySkipPreviousAsync() }
        'seek'    { [void]$s.TryChangePlaybackPositionAsync([long]([double]$arg * 10000)) }
        'shuffle' { [void]$s.TryChangeShuffleActiveAsync(($arg.Trim() -eq 'true')) }
        'repeat'  { [void]$s.TryChangeAutoRepeatModeAsync([Windows.Media.MediaPlaybackAutoRepeatMode]($arg.Trim())) }
    }
}

# Non-blocking stdin: a StreamReader's ReadLineAsync really is asynchronous (Console.In's is not).
$stdin = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), (New-Object System.Text.UTF8Encoding($false)))
$pending = $stdin.ReadLineAsync()

Emit @{ type = 'ready' }

$lastState = [Diagnostics.Stopwatch]::StartNew()
$first = $true
while ($true) {
    while ($pending.IsCompleted) {
        $line = $null
        try { $line = $pending.Result } catch { exit 0 }
        if ($null -eq $line) { exit 0 }   # Minecraft closed the pipe -> quit
        try { Handle-Command $line } catch { Emit @{ type = 'error'; msg = $_.Exception.Message } }
        $pending = $stdin.ReadLineAsync()
    }
    if ($first -or $lastState.ElapsedMilliseconds -ge 400) {
        $first = $false
        $lastState.Restart()
        try { Send-State } catch { Emit @{ type = 'error'; msg = $_.Exception.Message } }
    }
    # short naps so commands (e.g. dragging the volume slider) are handled within ~40 ms
    Start-Sleep -Milliseconds 40
}
