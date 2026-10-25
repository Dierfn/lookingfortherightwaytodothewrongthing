package com.spotifyhud;

import com.spotifyhud.Themes.Theme;
import com.spotifyhud.media.Lyrics;
import com.spotifyhud.media.Player;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * Lyrics that hang in the sky. The text sits at a fixed height above the horizon and trails the
 * direction you are facing, so it is only on screen when you look up. It is drawn as a 2D overlay
 * placed with a perspective projection of the sky direction, which keeps it independent of the
 * world-rendering pipeline.
 */
public final class SkyLyrics {
    private SkyLyrics() {}

    /** Follow speeds for skyFollow 0/1/2 (0 = snap to the view direction). */
    private static final float[] RATES = {0f, 5f, 1.6f};

    private static boolean init;
    private static float anchorYaw;
    private static long lastNanos;
    private static final float[] pos = new float[2];

    /** Width of the text column in unscaled GUI pixels. */
    private static final int COL_W = 220;

    public static void render(GuiGraphicsExtractor g, float partialTick) {
        Config cfg = Config.get();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!cfg.skyLyrics || p == null || mc.level == null || Ui.hudHidden(mc)) {
            init = false;
            return;
        }

        float camYaw = p.getViewYRot(partialTick);
        float camPitch = p.getViewXRot(partialTick);

        long now = System.nanoTime();
        float dt = init ? (now - lastNanos) / 1_000_000_000f : 0f;
        lastNanos = now;
        if (!init) {
            anchorYaw = camYaw;
            init = true;
        } else {
            float rate = RATES[Math.floorMod(cfg.skyFollow, RATES.length)];
            anchorYaw = SkyMath.follow(anchorYaw, camYaw, rate, Math.min(dt, 0.25f));
        }

        Player.Track t = Player.track;
        if (t == null || !Lyrics.available()) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float fov = mc.options.fov().get();
        float skyPitch = -Math.max(15, Math.min(75, cfg.skyHeight));
        if (!SkyMath.project(camYaw, camPitch, anchorYaw, skyPitch, fov, sw, sh, pos)) return;

        float scale = Math.max(1f, Math.min(5f, cfg.skyScale));
        // cull when well outside the screen (text is roughly COL_W*scale wide, 5 lines tall)
        float halfW = COL_W * scale / 2f, halfH = 30 * scale;
        if (pos[0] + halfW < 0 || pos[0] - halfW > sw || pos[1] + halfH < 0 || pos[1] - halfH > sh) return;

        Lyrics.Cur c = Lyrics.cur(Player.progress());
        Theme th = Themes.hud();
        int cx = Math.round(pos[0]), cy = Math.round(pos[1]);

        Draw.pushScale(g, cx, cy, scale);
        int left = cx - COL_W / 2;

        // previous / next lines, smaller and dim
        side(g, c.prev(), left, cy - 22, th);
        side(g, c.next(), left, cy + 14, th);

        // current line: dark outline first so it stays readable against the sky, then the karaoke text
        int black = 0xFF000000;
        int[][] offs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] o : offs)
            LyricsView.drawCentered(g, c.text(), c.frac(), left + o[0], cy - 10 + o[1], COL_W, 20, 2, 10,
                    black, black, black);
        LyricsView.drawCentered(g, c.text(), c.frac(), left, cy - 10, COL_W, 20, 2, 10,
                Themes.hudAccent(), 0xFFE6E6EA, th.text());
        Draw.popScale(g);
    }

    private static void side(GuiGraphicsExtractor g, String s, int left, int y, Theme th) {
        if (s == null || s.isEmpty() || s.equals("...")) return;
        String fit = Draw.fit(s, COL_W);
        int x = left + (COL_W - Draw.width(fit)) / 2;
        Draw.text(g, fit, x + 1, y + 1, 0xCC000000);
        Draw.text(g, fit, x, y, 0xB0D8D8DE);
    }
}
