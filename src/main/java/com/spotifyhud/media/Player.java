package com.spotifyhud.media;

import com.google.gson.JsonObject;
import com.spotifyhud.Config;

import java.util.Base64;
import java.util.Locale;

/**
 * Live playback state, fed by the Windows media bridge (no Spotify account, API key or login).
 * Everything here is what Spotify already tells Windows: title, artist, album, cover, position,
 * play state, shuffle/repeat, plus the transport commands Windows exposes.
 */
public final class Player {
    private Player() {}

    public record Track(String id, String name, String artist, String album, int durationMs) {
        public String firstArtist() {
            String[] parts = artist.split("[,;]");
            return parts.length == 0 ? artist : parts[0].strip();
        }
    }

    // ---- live state (read by HUD/menu on the render thread) ---------------------------------
    public static volatile Track track;
    public static volatile boolean playing;
    public static volatile boolean shuffle;
    public static volatile String repeat = "off"; // off | context | track  (same names the old UI used)
    public static volatile String message = "";
    private static volatile long progressMs;
    private static volatile long stamp;
    private static volatile long lastCmd;
    /** 0..1 volume of the Spotify app (falls back to the PC's master volume); -1 until the helper reports it. */
    public static volatile float volume = -1f;
    private static volatile long lastVolCmd;
    private static volatile long lastVolSend;

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static void start() {
        if (!supported()) {
            message = "Spotify HUD only works on Windows.";
            return;
        }
        Bridge.start();
    }

    public static void clearTrack() {
        track = null;
        playing = false;
    }

    public static long progress() {
        Track t = track;
        if (t == null) return 0;
        long p = progressMs;
        if (playing) p += System.currentTimeMillis() - stamp;
        return Math.max(0, t.durationMs() > 0 ? Math.min(p, t.durationMs()) : p);
    }

    public static String trackId(String title, String artist, String album) {
        return title + "\u0001" + artist + "\u0001" + album;
    }

    // ---- incoming ---------------------------------------------------------------------------

    static void onState(JsonObject j) {
        if (!bool(j, "active")) {
            track = null;
            playing = false;
            message = Config.get().spotifyOnly
                    ? "Open the Spotify desktop app and play something."
                    : "No media is playing on this PC.";
            return;
        }
        message = "";
        String title = str(j, "title"), artist = str(j, "artist"), album = str(j, "album");
        int dur = (int) Math.min(Integer.MAX_VALUE, lng(j, "dur"));
        String id = trackId(title, artist, album);
        Track old = track;
        if (old == null || !old.id().equals(id) || (old.durationMs() == 0 && dur > 0)) {
            Track t = new Track(id, title, artist, album, dur);
            track = t;
            Lyrics.update(t);
            AlbumArt.fallback(t);
        }
        double vol = dbl(j, "vol", -1);
        if (vol >= 0 && System.currentTimeMillis() - lastVolCmd > 1500) volume = (float) Math.min(1.0, vol);
        if (System.currentTimeMillis() - lastCmd > 1500) {
            playing = bool(j, "playing");
            shuffle = bool(j, "shuffle");
            repeat = switch (str(j, "repeat")) {
                case "List" -> "context";
                case "Track" -> "track";
                default -> "off";
            };
            progressMs = lng(j, "pos");
            stamp = System.currentTimeMillis();
        }
    }

    static void onArt(JsonObject j) {
        try {
            String id = trackId(str(j, "title"), str(j, "artist"), str(j, "album"));
            AlbumArt.submit(id, Base64.getDecoder().decode(str(j, "data")));
            Bridge.artStatus = "ok (from Windows)";
        } catch (Exception ignored) {
        }
    }

    // ---- commands ---------------------------------------------------------------------------

    public static void applyFilter() {
        Bridge.send("filter " + (Config.get().spotifyOnly ? "spotify" : ""));
    }

    private static void freezeProgress() {
        progressMs = progress();
        stamp = System.currentTimeMillis();
        lastCmd = stamp;
    }

    public static void togglePlay() {
        freezeProgress();
        playing = !playing;
        Bridge.send("toggle");
    }

    public static void next() {
        lastCmd = System.currentTimeMillis();
        Bridge.send("next");
    }

    public static void previous() {
        lastCmd = System.currentTimeMillis();
        Bridge.send("prev");
    }

    public static void seek(long ms) {
        progressMs = ms;
        stamp = System.currentTimeMillis();
        lastCmd = stamp;
        Bridge.send("seek " + ms);
    }

    /**
     * Sets the volume (0..1). While a slider is being dragged this is called many times a second, so
     * sends are limited to one per 60 ms unless {@code force} is set (final value on release).
     */
    public static void setVolume(float v, boolean force) {
        v = Math.max(0f, Math.min(1f, v));
        volume = v;
        long now = System.currentTimeMillis();
        lastVolCmd = now;
        if (force || now - lastVolSend >= 60) {
            lastVolSend = now;
            Bridge.send("volume " + String.format(Locale.ROOT, "%.3f", v));
        }
    }

    private static float premute = 0.5f;

    /** Click on the speaker icon: mute, or go back to the volume from before. */
    public static void toggleMute() {
        if (volume > 0.001f) {
            premute = volume;
            setVolume(0f, true);
        } else {
            setVolume(premute > 0.01f ? premute : 0.5f, true);
        }
    }

    /** Speaker icon level for the current volume. */
    public static int volumeLevel() {
        float v = volume;
        if (v <= 0.001f) return 0;
        return v < 0.34f ? 1 : (v < 0.67f ? 2 : 3);
    }

    public static void toggleShuffle() {
        shuffle = !shuffle;
        lastCmd = System.currentTimeMillis();
        Bridge.send("shuffle " + shuffle);
    }

    public static void cycleRepeat() {
        repeat = switch (repeat) {
            case "off" -> "context";
            case "context" -> "track";
            default -> "off";
        };
        lastCmd = System.currentTimeMillis();
        Bridge.send("repeat " + switch (repeat) {
            case "context" -> "List";
            case "track" -> "Track";
            default -> "None";
        });
    }

    // ---- json helpers -----------------------------------------------------------------------

    static String str(JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull() && o.get(k).isJsonPrimitive()
                ? o.get(k).getAsString() : "";
    }

    static boolean bool(JsonObject o, String k) {
        try {
            return o != null && o.has(k) && !o.get(k).isJsonNull() && o.get(k).getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    static double dbl(JsonObject o, String k, double def) {
        try {
            return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : def;
        } catch (Exception e) {
            return def;
        }
    }

    static long lng(JsonObject o, String k) {
        try {
            return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
