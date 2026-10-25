package com.spotifyhud.hud;

import com.spotifyhud.Config;
import com.spotifyhud.Draw;
import com.spotifyhud.LyricsView;
import com.spotifyhud.Themes;
import com.spotifyhud.Themes.Modern;
import com.spotifyhud.Ui;
import com.spotifyhud.media.AlbumArt;
import com.spotifyhud.media.Lyrics;
import com.spotifyhud.media.Player;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * The "Modern" HUD style: one dark, tinted panel with rounded corners. Cover + title on top, a
 * gradient progress line, then a control row (time, transport, volume). When lyrics are on they are
 * part of the same panel (no gap, no second box), under a thin gradient divider.
 *
 * <p>Everything is drawn in unscaled GUI pixels; {@link SpotifyHud} applies the size multiplier.
 * Colors all come from the one accent the player picks (Settings -> Modern color).
 */
public final class ModernHud {
    private ModernHud() {}

    public static final int W = 240;
    public static final int H = 74;
    public static final int LYR_H = 36;
    private static final int R = 6;

    // control row geometry (relative to the panel's left edge)
    private static final int CX = W / 2;
    private static final int ROW = 64;           // y of the control row's center line
    private static final int VOL_X = W - 8 - 56; // volume track start
    private static final int VOL_W = 56;

    public static void render(GuiGraphicsExtractor g, int x, int y, boolean lyrics) {
        Modern m = Themes.modern();
        Player.Track t = Player.track;
        int totalH = H + (lyrics ? LYR_H : 0);

        // ---- panel: border, vertical gradient body, lyrics strip fused onto the bottom ---------
        Draw.rrect(g, x, y, W, totalH, R, m.line());
        Draw.rrectVGrad(g, x + 1, y + 1, W - 2, totalH - 2, R - 1, m.bgTop(), m.bg());
        if (lyrics) {
            Draw.rrectBottom(g, x + 1, y + H, W - 2, LYR_H - 1, R - 1, 0x40000000);
            Draw.gradBar(g, x + 10, y + H, W - 20, 1,
                    Themes.mix(m.bg(), m.accent(), 0.45f), Themes.mix(m.bg(), m.accent2(), 0.45f));
        }

        // ---- cover ----------------------------------------------------------------------------
        Draw.rrect(g, x + 7, y + 7, 42, 42, 2, m.line());
        Identifier art = t == null ? null : AlbumArt.get(t.id());
        if (art != null) Draw.image(g, art, x + 8, y + 8, 40, 40);
        else Draw.rect(g, x + 8, y + 8, 40, 40, m.panel());

        int tx = x + 56;
        int tw = W - 56 - 28;

        if (t == null) {
            Draw.text(g, "Nothing playing", tx, y + 12, m.text());
            String msg = Player.message.isEmpty() ? "Open the Spotify app" : Player.message;
            Draw.text(g, Draw.fit(msg, W - 64), tx, y + 24, m.sub());
        } else {
            String title = Draw.fit(t.name(), tw);
            Draw.text(g, title, tx, y + 9, m.text());
            Draw.text(g, title, tx + 1, y + 9, m.text()); // fake bold
            Draw.text(g, Draw.fit(t.artist(), tw), tx, y + 21, m.sub());

            // status pill (gradient while playing, muted while paused)
            String label = Player.playing ? "Now playing" : "Paused";
            int pw = Draw.width(label) + 14;
            if (Player.playing) {
                Draw.gradPill(g, tx, y + 33, pw, 11, m.accent(), m.accent2());
                Draw.text(g, label, tx + 7, y + 35, Themes.onColor(m.accent(), m.accent2()));
            } else {
                Draw.gradPill(g, tx, y + 33, pw, 11, m.panel(), m.panel());
                Draw.text(g, label, tx + 7, y + 35, m.sub());
            }
        }

        // ---- little equalizer, top right: moves only while playing --------------------------------
        equalizer(g, x + W - 8 - 15, y + 18, m);

        // ---- progress line ------------------------------------------------------------------------
        long dur = t == null ? 0 : t.durationMs();
        long pos = Player.progress();
        int bx = x + 8, by = y + 53, bw = W - 16;
        Draw.rrect(g, bx, by, bw, 2, 1, Themes.mix(m.bg(), 0xFFFFFFFF, 0.16f));
        int filled = dur <= 0 ? 0 : (int) (bw * Math.min(1.0, (double) pos / dur));
        if (filled > 1) Draw.gradBar(g, bx, by, filled, 2, m.accent(), m.accent2());
        Draw.circle(g, bx + filled, by, 2, 0xFFFFFFFF);

        // ---- control row ---------------------------------------------------------------------------
        int cy = y + ROW;
        Draw.text(g, Ui.time(pos) + " / " + Ui.time(dur), x + 8, cy - 4, m.sub());

        Draw.icon(g, "prev", x + CX - 24, cy, 3, m.text());
        int fg = Themes.onColor(m.accent(), m.accent2());
        Draw.gradPill(g, x + CX - 13, cy - 7, 26, 14, m.accent(), m.accent2());
        Draw.icon(g, Player.playing ? "pause" : "play", x + CX + (Player.playing ? 0 : 1), cy, 4, fg);
        Draw.icon(g, "next", x + CX + 24, cy, 3, m.text());

        // volume
        float vol = Player.volume;
        Draw.speaker(g, x + VOL_X - 11, cy, vol < 0 ? 3 : Player.volumeLevel(), m.sub());
        int vf = (int) Math.round(VOL_W * Math.max(0, Math.min(1, vol)));
        Draw.rrect(g, x + VOL_X, cy - 1, VOL_W, 2, 1, Themes.mix(m.bg(), 0xFFFFFFFF, 0.16f));
        if (vf > 1) Draw.gradBar(g, x + VOL_X, cy - 1, vf, 2, m.accent(), m.accent2());
        if (vol >= 0) Draw.circle(g, x + VOL_X + vf, cy, 2, 0xFFFFFFFF);

        // ---- lyrics (same panel) -----------------------------------------------------------------------
        if (lyrics) {
            int ly = y + H + 1;
            if (t != null) {
                Lyrics.Cur c = Lyrics.cur(pos);
                LyricsView.drawCentered(g, c.text(), c.frac(), x + 10, ly, W - 20, LYR_H - 2, 2, 10,
                        Themes.mix(m.accent(), 0xFFFFFFFF, 0.18f), m.sub(), m.sub());
            }
        }
    }

    private static void equalizer(GuiGraphicsExtractor g, int x, int baseY, Modern m) {
        long now = System.currentTimeMillis();
        boolean on = Player.playing && Player.track != null;
        for (int i = 0; i < 4; i++) {
            int h = on ? 3 + (int) Math.round(8 * (0.5 + 0.5 * Math.sin(now * 0.0065 * (1 + i * 0.31) + i * 1.9))) : 2;
            int col = Themes.mix(m.accent(), m.accent2(), i / 3f);
            Draw.rect(g, x + i * 4, baseY - h + 1, 3, h, on ? col : Themes.mix(m.bg(), 0xFFFFFFFF, 0.2f));
        }
    }

    // ---- clicks (coordinates are in the HUD's unscaled space, origin at the panel's top-left) -----------

    private static boolean in(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
    }

    /** Handles a click on the panel: transport buttons, mute, volume track or the progress line. */
    public static void click(double mx, double my) {
        Player.Track t = Player.track;
        int top = ROW - 8;
        if (in(mx, my, CX - 24 - 9, top, 18, 16)) {
            Player.previous();
        } else if (in(mx, my, CX - 13, top, 26, 16)) {
            Player.togglePlay();
        } else if (in(mx, my, CX + 24 - 9, top, 18, 16)) {
            Player.next();
        } else if (in(mx, my, VOL_X - 18, top, 14, 16)) {
            Player.toggleMute();
        } else if (in(mx, my, VOL_X - 3, top, VOL_W + 6, 16)) {
            double r = Math.max(0, Math.min(1, (mx - VOL_X) / VOL_W));
            Player.setVolume((float) r, true);
        } else if (t != null && in(mx, my, 8, 46, W - 16, 10)) {
            double r = Math.max(0, Math.min(1, (mx - 8) / (W - 16)));
            Player.seek((long) (r * t.durationMs()));
        }
    }
}
