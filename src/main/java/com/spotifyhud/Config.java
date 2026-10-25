package com.spotifyhud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Saved to .minecraft/config/spotifyhud.json */
public final class Config {
    /** true: only read Spotify. false: follow whatever player Windows says is current. */
    public boolean spotifyOnly = true;
    public boolean hudEnabled = true;
    public boolean lyricsEnabled = false;
    public int hudX = 8;
    public int hudY = 8;
    /** HUD size multiplier, 0.5 - 2.0 */
    public float hudScale = 1.0f;
    public int hudTheme = 0;
    /** 0 = classic card, 1 = modern card (lyrics fused into the same panel). */
    public int hudStyle = 0;
    /** Accent color of the modern HUD, 0xRRGGBB. The panel tint and gradients are derived from it. */
    public int modernColor = 0x1ED79A;
    /** Lyrics floating in the sky (look up to see them). Independent of the HUD. */
    public boolean skyLyrics = false;
    /** How many degrees above the horizon the lyrics hang, 15 - 75. */
    public int skyHeight = 40;
    /** Text size multiplier, 1.0 - 5.0. */
    public float skyScale = 2.5f;
    /** 0 = locked to your view direction, 1 = smooth follow, 2 = lazy follow. */
    public int skyFollow = 1;
    public int menuTheme = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config inst;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("spotifyhud.json");
    }

    public static synchronized Config get() {
        if (inst == null) load();
        return inst;
    }

    public static synchronized void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                inst = GSON.fromJson(Files.readString(f), Config.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (inst == null) inst = new Config();
    }

    public static synchronized void save() {
        try {
            Files.writeString(file(), GSON.toJson(get()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
