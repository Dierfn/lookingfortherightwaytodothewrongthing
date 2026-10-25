package com.spotifyhud.screen;

import com.spotifyhud.Config;
import com.spotifyhud.Draw;
import com.spotifyhud.Themes;
import com.spotifyhud.Themes.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Settings for the lyrics that float in the sky. */
public class SkyLyricsScreen extends BaseScreen {
    private static final String[] FOLLOW = {"Locked", "Smooth", "Lazy"};

    public SkyLyricsScreen(Screen parent) {
        super("Sky Lyrics", parent);
    }

    @Override
    protected void draw(GuiGraphicsExtractor g, float delta) {
        Theme t = th();
        Config cfg = Config.get();
        Draw.rect(g, 0, 0, width, height, Themes.mix(t.bg(), 0xFF000000, 0.5f));

        int pw = Math.min(310, width - 16), ph = 152;
        int px = (width - pw) / 2, py = Math.max(4, (height - ph) / 2);
        Draw.roundOutline(g, px, py, pw, ph, t.accent(), t.bg());
        Draw.text(g, "Sky Lyrics", px + 10, py + 8, t.accent());

        int bx = px + pw - 112, bw = 102;
        int y = py + 24;

        Draw.text(g, "Lyrics in the sky", px + 10, y + 1, t.text());
        button(g, bx, y - 3, bw, 16, cfg.skyLyrics ? "Enabled" : "Disabled", cfg.skyLyrics, () -> {
            cfg.skyLyrics = !cfg.skyLyrics;
            Config.save();
        });
        y += 20;

        Draw.text(g, "Height", px + 10, y + 1, t.text());
        stepper(g, bx, y - 3, bw, cfg.skyHeight + " deg", () -> {
            cfg.skyHeight = Math.max(15, cfg.skyHeight - 5);
            Config.save();
        }, () -> {
            cfg.skyHeight = Math.min(75, cfg.skyHeight + 5);
            Config.save();
        });
        y += 20;

        Draw.text(g, "Size", px + 10, y + 1, t.text());
        stepper(g, bx, y - 3, bw, Math.round(cfg.skyScale * 100) + "%", () -> {
            cfg.skyScale = Math.max(1f, Math.round((cfg.skyScale - 0.5f) * 10f) / 10f);
            Config.save();
        }, () -> {
            cfg.skyScale = Math.min(5f, Math.round((cfg.skyScale + 0.5f) * 10f) / 10f);
            Config.save();
        });
        y += 20;

        Draw.text(g, "Follow", px + 10, y + 1, t.text());
        int f = Math.floorMod(cfg.skyFollow, FOLLOW.length);
        stepper(g, bx, y - 3, bw, FOLLOW[f], () -> {
            cfg.skyFollow = Math.floorMod(cfg.skyFollow - 1, FOLLOW.length);
            Config.save();
        }, () -> {
            cfg.skyFollow = Math.floorMod(cfg.skyFollow + 1, FOLLOW.length);
            Config.save();
        });
        y += 22;

        Draw.text(g, "Look up to see them. They trail the direction you face.", px + 10, y, t.sub());
        Draw.text(g, "Needs a song with synced lyrics playing.", px + 10, y + 10, t.sub());

        button(g, px + pw / 2 - 40, py + ph - 22, 80, 16, "Back", true, this::onClose);
    }

    private void stepper(GuiGraphicsExtractor g, int x, int y, int w, String label, Runnable minus, Runnable plus) {
        Theme t = th();
        button(g, x, y, 16, 16, "-", false, minus);
        int nx = x + 18, nw = w - 36;
        Draw.roundOutline(g, nx, y, nw, 16, Themes.mix(t.bg(), 0xFFFFFFFF, 0.25f), t.bg());
        Draw.text(g, label, nx + (nw - Draw.width(label)) / 2, y + 4, t.text());
        button(g, x + w - 16, y, 16, 16, "+", false, plus);
    }
}
