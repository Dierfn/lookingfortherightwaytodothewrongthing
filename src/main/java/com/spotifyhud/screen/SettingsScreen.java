package com.spotifyhud.screen;

import com.spotifyhud.Config;
import com.spotifyhud.Draw;
import com.spotifyhud.SpotifyHudMod;
import com.spotifyhud.Themes;
import com.spotifyhud.Themes.Theme;
import com.spotifyhud.Ui;
import com.spotifyhud.media.Bridge;
import com.spotifyhud.media.Player;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

/** The "Settings" tab: HUD on/off, lyrics, move HUD, themes, keybinds, account. */
public class SettingsScreen extends BaseScreen {
    private KeyMapping listening;

    public SettingsScreen(Screen parent) {
        super("Spotify Settings", parent);
    }

    @Override
    protected void draw(GuiGraphicsExtractor g, float delta) {
        Theme t = th();
        Config cfg = Config.get();
        Draw.rect(g, 0, 0, width, height, Themes.mix(t.bg(), 0xFF000000, 0.5f));

        int pw = Math.min(310, width - 16), ph = 266;
        int px = (width - pw) / 2, py = Math.max(4, (height - ph) / 2);
        Draw.roundOutline(g, px, py, pw, ph, t.accent(), t.bg());

        Draw.text(g, "Spotify Settings", px + 10, py + 8, t.accent());
        int bx = px + pw - 112, bw = 102;
        int y = py + 24;

        row(g, t, px, y, "HUD");
        button(g, bx, y - 3, bw, 16, cfg.hudEnabled ? "Enabled" : "Disabled", cfg.hudEnabled, () -> {
            cfg.hudEnabled = !cfg.hudEnabled;
            Config.save();
        });
        y += 18;

        row(g, t, px, y, "Lyrics under HUD");
        button(g, bx, y - 3, bw, 16, cfg.lyricsEnabled ? "Enabled" : "Disabled", cfg.lyricsEnabled, () -> {
            cfg.lyricsEnabled = !cfg.lyricsEnabled;
            Config.save();
        });
        y += 18;

        row(g, t, px, y, "HUD position");
        button(g, bx, y - 3, bw, 16, "Move HUD...", false, () -> Ui.open(new HudMoveScreen(this)));
        y += 18;

        row(g, t, px, y, "HUD size");
        scalePicker(g, bx, y - 3, bw, cfg);
        y += 18;

        boolean modern = cfg.hudStyle == 1;
        row(g, t, px, y, "HUD style");
        button(g, bx, y - 3, bw, 16, modern ? "Modern" : "Classic", modern, () -> {
            cfg.hudStyle = modern ? 0 : 1;
            Config.save();
        });
        y += 18;

        if (modern) {
            // the modern HUD is built from one color; the classic theme list doesn't apply to it
            row(g, t, px, y, "Modern color");
            int mc = 0xFF000000 | (cfg.modernColor & 0xFFFFFF);
            boolean hv = hover(bx, y - 3, bw, 16);
            Draw.roundOutline(g, bx, y - 3, bw, 16, mc, Themes.mix(t.bg(), mc, hv ? 0.3f : 0.18f));
            Draw.rect(g, bx + 4, y + 2, 6, 6, mc);
            Draw.text(g, String.format("#%06X", cfg.modernColor & 0xFFFFFF), bx + 14, y + 1, 0xFFFFFFFF);
            hit(bx, y - 3, bw, 16, () -> Ui.open(new ModernColorScreen(this)));
        } else {
            row(g, t, px, y, "HUD theme");
            themePicker(g, bx, y - 3, bw, Themes.hud(), d -> {
                cfg.hudTheme = Math.floorMod(cfg.hudTheme + d, Themes.ALL.length);
                Config.save();
            });
        }
        y += 18;

        row(g, t, px, y, "Menu color");
        themePicker(g, bx, y - 3, bw, Themes.menu(), d -> {
            cfg.menuTheme = Math.floorMod(cfg.menuTheme + d, Themes.ALL.length);
            Config.save();
        });
        y += 18;

        row(g, t, px, y, "Media source");
        button(g, bx, y - 3, bw, 16, cfg.spotifyOnly ? "Spotify only" : "Any player", cfg.spotifyOnly, () -> {
            cfg.spotifyOnly = !cfg.spotifyOnly;
            Config.save();
            Player.applyFilter();
        });
        y += 24;

        Draw.text(g, "Keybinds (click, then press a key)", px + 10, y, t.sub());
        y += 13;
        int kw = (pw - 30) / 2;
        keyButton(g, px + 10, y, kw, "Open menu", SpotifyHudMod.OPEN);
        keyButton(g, px + 20 + kw, y, kw, "Play/Pause", SpotifyHudMod.PLAY_PAUSE);
        y += 20;
        keyButton(g, px + 10, y, kw, "Next", SpotifyHudMod.NEXT);
        keyButton(g, px + 20 + kw, y, kw, "Previous", SpotifyHudMod.PREV);
        y += 22;

        String st = Player.supported() ? "Windows media helper: " + Bridge.status : "Windows only";
        Draw.text(g, Draw.fit(st, pw - 20), px + 10, y, t.sub());
        if (Player.supported() && !Bridge.artStatus.isEmpty())
            Draw.text(g, Draw.fit("Cover art: " + Bridge.artStatus, pw - 20), px + 10, y + 10, t.sub());

        int half = (pw - 30) / 2;
        button(g, px + 10, py + ph - 22, half, 16, "Sky lyrics...", false, () -> Ui.open(new SkyLyricsScreen(this)));
        button(g, px + 20 + half, py + ph - 22, half, 16, "Back", true, this::onClose);
    }

    private void scalePicker(GuiGraphicsExtractor g, int x, int y, int w, Config cfg) {
        Theme t = th();
        button(g, x, y, 16, 16, "-", false, () -> step(cfg, -0.1f));
        int nx = x + 18, nw = w - 36;
        Draw.roundOutline(g, nx, y, nw, 16, Themes.mix(t.bg(), 0xFFFFFFFF, 0.25f), t.bg());
        String s = Math.round(cfg.hudScale * 100) + "%";
        Draw.text(g, s, nx + (nw - Draw.width(s)) / 2, y + 4, t.text());
        button(g, x + w - 16, y, 16, 16, "+", false, () -> step(cfg, 0.1f));
    }

    private static void step(Config cfg, float d) {
        float v = Math.max(0.5f, Math.min(2.0f, cfg.hudScale + d));
        cfg.hudScale = Math.round(v * 10f) / 10f;
        Config.save();
    }

    private void row(GuiGraphicsExtractor g, Theme t, int px, int y, String label) {
        Draw.text(g, label, px + 10, y + 1, t.text());
    }

    private void themePicker(GuiGraphicsExtractor g, int x, int y, int w, Theme cur, java.util.function.IntConsumer change) {
        Theme t = th();
        button(g, x, y, 16, 16, "<", false, () -> change.accept(-1));
        int nx = x + 18, nw = w - 36;
        boolean hv = hover(nx, y, nw, 16);
        Draw.roundOutline(g, nx, y, nw, 16, cur.accent(), Themes.mix(t.bg(), cur.accent(), hv ? 0.3f : 0.18f));
        Draw.rect(g, nx + 4, y + 5, 6, 6, cur.accent());
        String s = Draw.fit(cur.name(), nw - 18);
        Draw.text(g, s, nx + 14, y + 4, 0xFFFFFFFF);
        hit(nx, y, nw, 16, () -> change.accept(1));
        button(g, x + w - 16, y, 16, 16, ">", false, () -> change.accept(1));
    }

    private void keyButton(GuiGraphicsExtractor g, int x, int y, int w, String label, KeyMapping m) {
        boolean listen = listening == m;
        String key = listen ? "..." : m.getTranslatedKeyMessage().getString();
        button(g, x, y, w, 16, label + ": " + key, listen, () -> listening = m);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (listening != null) {
            if (e.key() != 256) SpotifyHudMod.rebind(listening, e.key());
            listening = null;
            return true;
        }
        return super.keyPressed(e);
    }
}
