package com.spotifyhud.screen;

import com.spotifyhud.Draw;
import com.spotifyhud.LyricsView;
import com.spotifyhud.Themes;
import com.spotifyhud.Themes.Theme;
import com.spotifyhud.Ui;
import com.spotifyhud.media.AlbumArt;
import com.spotifyhud.media.Lyrics;
import com.spotifyhud.media.Player;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

/** The "Now Playing" menu: cover, track info, progress bar, transport controls and synced lyrics. */
public class SpotifyScreen extends BaseScreen {

    public SpotifyScreen(Screen parent) {
        super("Spotify", parent);
    }

    @Override
    protected void draw(GuiGraphicsExtractor g, float delta) {
        Theme t = th();
        Draw.rect(g, 0, 0, width, height, Themes.mix(t.bg(), 0xFF000000, 0.5f));

        Draw.text(g, "Now Playing", 8, 7, t.accent());
        button(g, width - 70, 3, 66, 16, "Settings", false, () -> Ui.open(new SettingsScreen(this)));
        button(g, width - 124, 3, 48, 16, "Close", false, this::onClose);

        int pw = Math.min(340, width - 16), ph = Math.min(208, height - 34);
        int px = (width - pw) / 2, py = Math.max(24, (height - ph) / 2);
        Draw.roundOutline(g, px, py, pw, ph, Themes.mix(t.accent(), t.bg(), 0.55f), t.bg());

        Player.Track tr = Player.track;
        if (tr == null) {
            String a = Player.supported() ? "Nothing playing" : "Windows only";
            Draw.text(g, a, px + (pw - Draw.width(a)) / 2, py + ph / 2 - 10, t.text());
            String m = Draw.fit(Player.message, pw - 20);
            Draw.text(g, m, px + (pw - Draw.width(m)) / 2, py + ph / 2 + 3, t.sub());
            return;
        }

        // cover + info
        int cs = 80;
        Identifier art = AlbumArt.get(tr.id());
        if (art != null) Draw.image(g, art, px + 10, py + 10, cs, cs);
        else Draw.rect(g, px + 10, py + 10, cs, cs, t.panel());

        int tx = px + cs + 20, tw = pw - cs - 30;
        Draw.text(g, Draw.fit(tr.name(), tw), tx, py + 14, t.text());
        Draw.text(g, Draw.fit(tr.artist(), tw), tx, py + 27, t.sub());
        if (!tr.album().isEmpty()) Draw.text(g, Draw.fit(tr.album(), tw), tx, py + 39, t.sub());
        String label = Player.playing ? "Now playing" : "Paused";
        int lw = Draw.width(label) + 14;
        Draw.roundOutline(g, tx, py + 54, lw, 12, t.accent(), Themes.mix(t.bg(), t.accent(), 0.12f));
        Draw.rect(g, tx + 4, py + 59, 2, 2, t.accent());
        Draw.text(g, label, tx + 9, py + 56, t.accent());

        // progress
        int barW = pw - 90, bx = px + 45, by = py + 104;
        long dur = tr.durationMs();
        long pos = Player.progress();
        String el = Ui.time(pos), tot = Ui.time(dur);
        Draw.text(g, el, bx - 6 - Draw.width(el), by - 2, t.text());
        Draw.text(g, tot, bx + barW + 6, by - 2, t.text());
        Draw.rect(g, bx, by, barW, 4, Themes.mix(t.bg(), 0xFFFFFFFF, 0.2f));
        int filled = dur <= 0 ? 0 : (int) (barW * Math.min(1.0, (double) pos / dur));
        Draw.rect(g, bx, by, filled, 4, hover(bx, by - 4, barW, 12) ? t.accent() : 0xFFFFFFFF);
        hit(bx, by - 4, barW, 12, () -> {
            if (dur <= 0) return;
            double ratio = Math.max(0, Math.min(1, (mx - bx) / (double) barW));
            Player.seek((long) (ratio * dur));
        });

        // controls
        int cx = px + pw / 2, cy = py + 124;
        Draw.icon(g, "shuffle", cx - 64, cy, 4, Player.shuffle ? t.accent() : t.sub());
        hit(cx - 74, cy - 8, 20, 16, Player::toggleShuffle);
        Draw.icon(g, "prev", cx - 32, cy, 4, t.text());
        hit(cx - 42, cy - 8, 20, 16, Player::previous);
        Draw.circle(g, cx, cy, 9, 0xFFFFFFFF);
        Draw.icon(g, Player.playing ? "pause" : "play", cx + (Player.playing ? 0 : 1), cy, 4, 0xFF000000);
        hit(cx - 10, cy - 10, 20, 20, Player::togglePlay);
        Draw.icon(g, "next", cx + 32, cy, 4, t.text());
        hit(cx + 22, cy - 8, 20, 16, Player::next);
        Draw.icon(g, "repeat", cx + 64, cy, 4, Player.repeat.equals("off") ? t.sub() : t.accent());
        if (Player.repeat.equals("track")) Draw.text(g, "1", cx + 62, cy - 4, t.accent());
        hit(cx + 54, cy - 8, 20, 16, Player::cycleRepeat);

        // volume: speaker icon (click = mute) + draggable slider, right end of the controls row
        int vx = Math.max(cx + 88, px + pw - 84);
        int vsx = vx + 14, vsw = px + pw - 12 - vsx;
        if (vsw >= 24) {
            float vol = Player.volume;
            int lv = vol < 0 ? 3 : Player.volumeLevel();
            Draw.speaker(g, vx + 5, cy, lv, hover(vx - 3, cy - 8, 16, 16) ? t.accent() : t.sub());
            hit(vx - 3, cy - 8, 16, 16, Player::toggleMute);
            slider(g, vsx, cy - 2, vsw, Math.max(0, vol), t.accent(), Themes.mix(t.bg(), 0xFFFFFFFF, 0.2f),
                    v -> Player.setVolume((float) v, false), () -> Player.setVolume(Player.volume, true));
            if (hover(vsx - 3, cy - 8, vsw + 6, 16) && vol >= 0) {
                String pct = Math.round(vol * 100) + "%";
                Draw.text(g, pct, vsx + vsw - Draw.width(pct), cy - 17, t.sub());
            }
        }

        // lyrics: previous / current (word-by-word highlight) / next
        int ly = py + 142, lh = ph - 142 - 8;
        if (lh >= 24) {
            Draw.round(g, px + 8, ly, pw - 16, lh, Themes.mix(t.bg(), 0xFF000000, 0.25f));
            Lyrics.Cur c = Lyrics.cur(pos);
            int lyricsWidth = pw - 32;
            if (lh >= 56) {
                String pv = Draw.fit(c.prev(), lyricsWidth), nx = Draw.fit(c.next(), lyricsWidth);
                Draw.text(g, pv, px + (pw - Draw.width(pv)) / 2, ly + 5, Themes.mix(t.sub(), t.bg(), 0.35f));
                LyricsView.drawCentered(g, c.text(), c.frac(), px + 16, ly + 16, lyricsWidth, 24, 2, 11,
                        t.accent(), t.sub(), t.text());
                Draw.text(g, nx, px + (pw - Draw.width(nx)) / 2, ly + lh - 13, Themes.mix(t.sub(), t.bg(), 0.35f));
            } else {
                LyricsView.drawCentered(g, c.text(), c.frac(), px + 16, ly, lyricsWidth, lh, lh >= 36 ? 2 : 1, 11,
                        t.accent(), t.sub(), t.text());
            }
        }

        if (!Player.message.isEmpty()) {
            String m = Draw.fit(Player.message, pw - 20);
            Draw.text(g, m, px + 10, py + ph + 4, t.accent());
        }
    }
}
