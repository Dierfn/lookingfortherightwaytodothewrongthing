package com.spotifyhud.screen;

import com.spotifyhud.Config;
import com.spotifyhud.Draw;
import com.spotifyhud.Themes;
import com.spotifyhud.Themes.Theme;
import com.spotifyhud.hud.ModernHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.DoubleConsumer;

/**
 * Picks the one color the Modern HUD is built from. Live preview on top, then presets, hue /
 * saturation / brightness strips, and a hex box. The panel tint, gradients and lyric highlight all follow.
 */
public class ModernColorScreen extends BaseScreen {
    private static final int DEFAULT = 0x1ED79A;
    private static final int[] PRESETS = {
            0x1ED79A, 0x1DB954, 0x00D1FF, 0x3B9BFF, 0x6C5CE7, 0xB06CFF,
            0xFF6EC7, 0xFF5C8A, 0xFF4D4D, 0xFF7A3C, 0xFFC53D, 0xCFD3DC
    };

    // HSV is kept here (not re-derived from the RGB every frame) so dragging brightness to 0 doesn't lose the hue
    private float h, s, v;
    private final TextField hex = new TextField("", 7);

    public ModernColorScreen(Screen parent) {
        super("Modern HUD color", parent);
        setRgb(Config.get().modernColor, false);
    }

    private void setRgb(int rgb, boolean save) {
        float[] hsv = Themes.rgbToHsv(0xFF000000 | rgb);
        h = hsv[0];
        s = hsv[1];
        v = hsv[2];
        Config.get().modernColor = rgb & 0xFFFFFF;
        if (save) Config.save();
    }

    private void applyHsv() {
        Config.get().modernColor = Themes.hsvToRgb(h, s, v) & 0xFFFFFF;
    }

    @Override
    protected void draw(GuiGraphicsExtractor g, float delta) {
        Theme t = th();
        Config cfg = Config.get();
        Draw.rect(g, 0, 0, width, height, Themes.mix(t.bg(), 0xFF000000, 0.5f));

        // the preview shows the lyrics strip too, unless the window is too short to fit it
        int chrome = 22 + 8 + 14 + 8 + 48 + 6 + 16 + 8;
        boolean lyr = cfg.lyricsEnabled && height >= chrome + ModernHud.H + ModernHud.LYR_H + 8;
        int previewH = ModernHud.H + (lyr ? ModernHud.LYR_H : 0);
        int pw = Math.min(310, width - 16), ph = chrome + previewH;
        int px = (width - pw) / 2, py = Math.max(4, (height - ph) / 2);
        Draw.roundOutline(g, px, py, pw, ph, t.accent(), t.bg());
        Draw.text(g, "Modern HUD color", px + 10, py + 8, t.accent());

        // live preview: the real HUD renderer, so what you see is what you get
        int y = py + 22;
        int hx = px + (pw - ModernHud.W) / 2;
        if (pw >= ModernHud.W + 8) ModernHud.render(g, hx, y, lyr);
        y += previewH + 8;

        // presets
        int n = PRESETS.length;
        int gap = 4, sw = Math.max(12, (pw - 20 - gap * (n - 1)) / n);
        int sx = px + (pw - (sw * n + gap * (n - 1))) / 2;
        for (int i = 0; i < n; i++) {
            int x = sx + i * (sw + gap);
            boolean on = (cfg.modernColor & 0xFFFFFF) == PRESETS[i];
            int c = 0xFF000000 | PRESETS[i];
            Draw.rrectOutline(g, x, y, sw, 14, 3, on ? 0xFFFFFFFF : hover(x, y, sw, 14) ? Themes.mix(c, 0xFFFFFFFF, 0.5f) : Themes.mix(t.bg(), 0xFFFFFFFF, 0.2f), c);
            final int rgb = PRESETS[i];
            hit(x, y, sw, 14, () -> setRgb(rgb, true));
        }
        y += 14 + 8;

        // strips
        int lx = px + 10, bx = px + 72, bw = pw - 72 - 12;
        strip(g, t, "Hue", lx, bx, y, bw, h, i -> Themes.hsvToRgb((float) i, Math.max(s, 0.55f), Math.max(v, 0.7f)), val -> {
            h = (float) Math.min(0.9999, val);
            applyHsv();
        });
        y += 16;
        strip(g, t, "Saturation", lx, bx, y, bw, s, i -> Themes.hsvToRgb(h, (float) i, Math.max(v, 0.6f)), val -> {
            s = (float) val;
            applyHsv();
        });
        y += 16;
        strip(g, t, "Brightness", lx, bx, y, bw, v, i -> Themes.hsvToRgb(h, s, (float) i), val -> {
            v = (float) val;
            applyHsv();
        });
        y += 16 + 6;

        // hex box + buttons
        if (focus != hex) {
            hex.text.setLength(0);
            hex.text.append(String.format("#%06X", cfg.modernColor & 0xFFFFFF));
        } else {
            Integer parsed = parseHex(hex.get());
            if (parsed != null && (parsed & 0xFFFFFF) != (cfg.modernColor & 0xFFFFFF)) setRgb(parsed, false);
        }
        field(g, hex, px + 10, y, 64, 16, "#RRGGBB");
        int bw2 = (pw - 10 - 64 - 10 - 8 - 10) / 2;
        button(g, px + 10 + 64 + 8, y, bw2, 16, "Reset", false, () -> setRgb(DEFAULT, true));
        button(g, px + pw - 10 - bw2, y, bw2, 16, "Done", true, this::onClose);
    }

    /** One labelled gradient strip with a draggable marker. {@code colorAt} maps 0..1 across the strip to a color. */
    private void strip(GuiGraphicsExtractor g, Theme t, String label, int lx, int x, int y, int w, float value,
                       java.util.function.DoubleFunction<Integer> colorAt, DoubleConsumer onChange) {
        Draw.text(g, label, lx, y + 3, t.sub());
        for (int i = 0; i < w; i++) Draw.rect(g, x + i, y, 1, 12, colorAt.apply(i / (double) Math.max(1, w - 1)));
        Draw.outline(g, x - 1, y - 1, w + 2, 14, Themes.mix(t.bg(), 0xFFFFFFFF, 0.25f));
        int k = x + Math.round(value * (w - 1));
        Draw.rect(g, k - 1, y - 2, 3, 16, 0xFF000000);
        Draw.rect(g, k, y - 2, 1, 16, 0xFFFFFFFF);
        sliderHit(x, w, y - 2, 16, onChange, Config::save);
    }

    private static Integer parseHex(String raw) {
        String s = raw.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return null;
        try {
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    protected void onEnter(TextField f) {
        Integer parsed = parseHex(f.get());
        if (parsed != null) setRgb(parsed, true);
        focus = null;
    }
}
