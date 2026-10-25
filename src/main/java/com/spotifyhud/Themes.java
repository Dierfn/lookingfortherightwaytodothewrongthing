package com.spotifyhud;

public final class Themes {
    private Themes() {}

    public record Theme(String name, int accent, int bg, int panel, int text, int sub) {}

    private static Theme of(String name, int accent, int bg) {
        return new Theme(name, accent, bg, mix(bg, 0xFFFFFFFF, 0.08f), 0xFFFFFFFF, 0xFFA0A0A8);
    }

    public static final Theme[] ALL = {
            of("Spotify Green", 0xFF1DB954, 0xFF101418),
            of("Ocean Blue", 0xFF3B9BFF, 0xFF0D1520),
            of("Sunset", 0xFFFF7A3C, 0xFF1A1210),
            of("Violet", 0xFFB06CFF, 0xFF15101F),
            of("Rose", 0xFFFF5C8A, 0xFF1D0F15),
            of("Gold", 0xFFFFC53D, 0xFF181408),
            of("Mono", 0xFFFFFFFF, 0xFF0A0A0A),
    };

    public static Theme hud() {
        return ALL[Math.floorMod(Config.get().hudTheme, ALL.length)];
    }

    public static Theme menu() {
        return ALL[Math.floorMod(Config.get().menuTheme, ALL.length)];
    }

    /** Colors of the modern HUD, all derived from the one color the player picks. */
    public record Modern(int accent, int accent2, int bg, int bgTop, int panel, int line, int text, int sub) {}

    public static Modern modern() {
        int rgb = Config.get().modernColor & 0xFFFFFF;
        int accent = 0xFF000000 | rgb;
        float[] hsv = rgbToHsv(accent);
        // second gradient stop: hue nudged toward the next color over (like blue -> green in the reference)
        int accent2 = hsvToRgb((hsv[0] + 0.12f) % 1f, Math.min(1f, hsv[1] * 0.95f), Math.max(0.55f, hsv[2]));
        // dark panel tinted with the accent's hue
        int bg = hsvToRgb(hsv[0], Math.min(0.55f, 0.25f + hsv[1] * 0.3f), 0.13f);
        int bgTop = hsvToRgb(hsv[0], Math.min(0.55f, 0.25f + hsv[1] * 0.3f), 0.20f);
        int panel = mix(bg, 0xFFFFFFFF, 0.07f);
        int line = mix(bg, accent, 0.28f);
        return new Modern(accent, accent2, bg, bgTop, panel, line, 0xFFFFFFFF, mix(0xFFB4B8C4, accent, 0.12f));
    }

    /** Dark or white, whichever reads better on top of the (average of the) given colors. */
    public static int onColor(int a, int b) {
        int m = mix(a, b, 0.5f);
        float lum = (0.299f * ((m >> 16) & 0xFF) + 0.587f * ((m >> 8) & 0xFF) + 0.114f * (m & 0xFF)) / 255f;
        return lum > 0.55f ? 0xFF0A0F0D : 0xFFFFFFFF;
    }

    /** Accent used by the HUD and sky lyrics: the modern color in modern style, else the HUD theme's. */
    public static int hudAccent() {
        return Config.get().hudStyle == 1 ? modern().accent() : hud().accent();
    }

    public static float[] rgbToHsv(int c) {
        float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h /= 6f;
        if (h < 0) h += 1f;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    public static int hsvToRgb(float h, float s, float v) {
        h = ((h % 1f) + 1f) % 1f;
        float c = v * s, x = c * (1 - Math.abs((h * 6f) % 2f - 1)), m = v - c;
        float r, g, b;
        int sector = (int) (h * 6f);
        switch (sector) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
