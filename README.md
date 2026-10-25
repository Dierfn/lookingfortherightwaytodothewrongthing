# Spotify HUD (Fabric, Minecraft 26.2, Windows)

In-game "now playing" HUD + menu + word-by-word synced lyrics for the **Spotify desktop app**.

**No Spotify account, API key, Client ID or login.** The mod reads the same media info Spotify
already shares with Windows (the card you see when you press the volume keys).

## Controls
- `O` opens the menu. `\` play/pause, `]` next, `[` previous (rebindable).
- With chat or the pause menu open, click the HUD buttons / progress bar.
- Settings: HUD on/off, lyrics, Move HUD, **HUD size (50%-200%)**, **HUD style**, themes, keybinds, media source.

## Volume
A volume slider sits in the Now Playing menu (`O`) and on the Modern HUD. It controls the volume of the
Spotify app only (its slider in the Windows volume mixer), so Minecraft's own sound is untouched. Click the
speaker icon to mute / unmute. If Spotify has no audio session yet (nothing has played), or the media
source is set to "Any player", it falls back to the PC's master volume. On the HUD the slider is click-to-set
(it works while chat / the pause menu is open); in the menu you can drag it.

## HUD style
Settings -> **HUD style** switches between:
- **Classic**: the original card, with the lyrics in a separate box under it (picks from the theme list).
- **Modern**: a rounded, tinted panel with a cover, gradient progress line, play pill, volume slider and a
  small equalizer. Lyrics are part of the same panel (no gap, no second box) under a thin divider.
  **Modern color** opens a picker (presets, hue / saturation / brightness, hex). The panel tint, gradients,
  lyric highlight and sky lyrics all follow that one color.

## Lyrics
Lyrics come from lrclib.net, which only has per-line timing. The word highlight is an estimate:
each line's time is shared between its words by length, so words light up left-to-right as the
line is sung.

## Sky lyrics
Settings -> **Sky lyrics...** turns on lyrics that hang in the sky: look up and they are there, and they
trail the direction you face. Height, size and follow style (Locked / Smooth / Lazy) are adjustable.
It works on its own, separate from the HUD and the lyrics box under it. It needs a song with synced lyrics.

## Cover art
Taken from Windows. If Windows doesn't provide one within a few seconds, the mod looks it up on the
public iTunes search API instead. Settings shows which source was used (or why it failed).

## Build
`./gradlew build` -> `build/libs/spotify-hud-1.1.0.jar`.
Releases: `git tag v1.0.1 && git push origin v1.0.1`.
