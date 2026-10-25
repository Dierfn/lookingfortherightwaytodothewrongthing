package com.spotifyhud.hud;

import com.spotifyhud.Config;
import com.spotifyhud.Draw;
import com.spotifyhud.LyricsView;
import com.spotifyhud.Themes;
import com.spotifyhud.Themes.Theme;
import com.spotifyhud.Ui;
import com.spotifyhud.media.AlbumArt;
import com.spotifyhud.media.Lyrics;
import com.spotifyhud.media.Player;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.List;

/** The on-screen "now playing" card plus the optional lyrics box underneath it. */
public final class SpotifyHud {
    private SpotifyHud() {}

    public static final int W = 190;
    public static final int H = 64;
    public static final int LYR_GAP = 3;
    public static final int LYR_H = 26;

    // ---- style ------------------------------------------------------------------------------

    public static boolean modern() {
        return Config.get().hudStyle == 1;
    }

    /** Unscaled gap between the card and the lyrics box (0 in modern: the lyrics are part of the card). */
    public static int lyricsGap() {
        return modern() ? 0 : LYR_GAP;
    }

    /** Unscaled height of the lyrics box / strip. */
    public static int lyricsHeight() {
        return modern() ? ModernHud.LYR_H : LYR_H;
    }

    /** Unscaled width of the HUD in the current style. */
    public static int baseWidth() {
        return modern() ? ModernHud.W : W;
    }

    /** Unscaled height of the main card in the current style. */
    public static int baseHeight() {
        return modern() ? ModernHud.H : H;
    }

    // ---- size / position --------------------------------------------------------------------

    public static float scale() {
        float v = Config.get().hudScale;
        if (!(v >= 0.5f)) v = 1.0f;
        return Math.min(v, 2.0f);
    }

    /** On-screen width after scaling. */
    public static int width() {
        return Math.round(baseWidth() * scale());
    }

    /** On-screen height of the main card after scaling. */
    public static int cardHeight() {
        return Math.round(baseHeight() * scale());
    }

    /** On-screen height of card + lyrics box after scaling. */
    public static int totalHeight() {
        return Math.round((baseHeight() + (Config.get().lyricsEnabled ? lyricsGap() + lyricsHeight() : 0)) * scale());
    }

    public static int x() {
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        return Math.max(0, Math.min(Config.get().hudX, sw - width()));
    }

    public static int y() {
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return Math.max(0, Math.min(Config.get().hudY, sh - totalHeight()));
    }

    // ---- rendering --------------------------------------------------------------------------

    public static void render(GuiGraphicsExtractor g) {
        Config cfg = Config.get();
        Minecraft mc = Minecraft.getInstance();
        if (!cfg.hudEnabled || !Player.supported() || Ui.hudHidden(mc)) return;

        int x = x(), y = y();
        if (modern()) {
            Draw.pushScale(g, x, y, scale());
            ModernHud.render(g, x, y, cfg.lyricsEnabled);
            Draw.popScale(g);
            return;
        }

        Theme th = Themes.hud();
        Draw.pushScale(g, x, y, scale());
        int border = Themes.mix(th.accent(), th.bg(), 0.55f);
        Draw.roundOutline(g, x, y, W, H, border, th.bg());

        Player.Track t = Player.track;
        // cover
        Identifier art = t == null ? null : AlbumArt.get(t.id());
        if (art != null) Draw.image(g, art, x + 8, y + 8, 36, 36);
        else Draw.rect(g, x + 8, y + 8, 36, 36, th.panel());

        int tx = x + 52;
        int tw = W - 60;
        if (t == null) {
            Draw.text(g, "Nothing playing", tx, y + 10, th.text());
            String msg = Player.message.isEmpty() ? "Open the Spotify app" : Player.message;
            Draw.text(g, Draw.fit(msg, tw), tx, y + 21, th.sub());
        } else {
            Draw.text(g, Draw.fit(t.name(), tw), tx, y + 9, th.text());
            Draw.text(g, Draw.fit(t.artist(), tw), tx, y + 20, th.sub());
            String label = Player.playing ? "Now playing" : "Paused";
            int pw = Draw.width(label) + 14;
            Draw.roundOutline(g, tx, y + 31, pw, 12, th.accent(), Themes.mix(th.bg(), th.accent(), 0.12f));
            Draw.rect(g, tx + 4, y + 36, 2, 2, th.accent());
            Draw.text(g, label, tx + 9, y + 33, th.accent());
        }

        // progress bar
        long dur = t == null ? 0 : t.durationMs();
        long pos = Player.progress();
        int bx = x + 8, by = y + 47, bw = W - 16;
        Draw.rect(g, bx, by, bw, 3, Themes.mix(th.bg(), 0xFFFFFFFF, 0.2f));
        int filled = dur <= 0 ? 0 : (int) (bw * Math.min(1.0, (double) pos / dur));
        Draw.rect(g, bx, by, filled, 3, th.accent());
        Draw.circle(g, bx + filled, by + 1, 2, 0xFFFFFFFF);

        // time + controls
        Draw.text(g, Ui.time(pos) + " / " + Ui.time(dur), x + 8, y + 54, th.sub());
        int cy = y + 57;
        Draw.icon(g, "prev", prevX() + 7, cy, 3, th.sub());
        Draw.icon(g, Player.playing ? "pause" : "play", playX() + 7, cy, 4, th.accent());
        Draw.icon(g, "next", nextX() + 7, cy, 3, th.sub());

        // lyrics (word-by-word highlight)
        if (cfg.lyricsEnabled) {
            int ly = y + H + LYR_GAP;
            Draw.roundOutline(g, x, ly, W, LYR_H, border, th.bg());
            if (t != null) {
                Lyrics.Cur c = Lyrics.cur(pos);
                LyricsView.drawCentered(g, c.text(), c.frac(), x + 8, ly, W - 16, LYR_H, 2, 10,
                        th.accent(), th.sub(), th.text());
            }
        }
        Draw.popScale(g);
    }

    // ---- button geometry (relative to the HUD) ----------------------------------------------

    private static int prevX() { return x() + W - 62; }
    private static int playX() { return x() + W - 42; }
    private static int nextX() { return x() + W - 22; }

    private static boolean in(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
    }

    /** Screen-space test against the (scaled) main card (the whole panel in modern style, lyrics included). */
    public static boolean contains(double mx, double my) {
        return in(mx, my, x(), y(), width(), modern() ? totalHeight() : cardHeight());
    }

    /** Called when a screen (pause menu, chat, ...) is open and the player clicks. Returns true if the click was used. */
    public static boolean mouseClicked(double sx, double sy) {
        Config cfg = Config.get();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !cfg.hudEnabled || !Player.supported() || !contains(sx, sy)) return false;

        // convert the click back into the HUD's unscaled coordinates
        float s = scale();
        double mx = x() + (sx - x()) / s;
        double my = y() + (sy - y()) / s;

        if (modern()) {
            ModernHud.click(mx - x(), my - y());
            return true;
        }

        int y = y();
        if (in(mx, my, prevX(), y + 50, 14, 13)) {
            Player.previous();
            return true;
        }
        if (in(mx, my, playX(), y + 50, 14, 13)) {
            Player.togglePlay();
            return true;
        }
        if (in(mx, my, nextX(), y + 50, 14, 13)) {
            Player.next();
            return true;
        }
        // click on the progress bar to seek
        Player.Track t = Player.track;
        if (t != null && in(mx, my, x() + 8, y + 44, W - 16, 8)) {
            double ratio = Math.max(0, Math.min(1, (mx - (x() + 8)) / (W - 16)));
            Player.seek((long) (ratio * t.durationMs()));
            return true;
        }
        return true; // swallow clicks on the card so they don't hit things behind it
    }
}
