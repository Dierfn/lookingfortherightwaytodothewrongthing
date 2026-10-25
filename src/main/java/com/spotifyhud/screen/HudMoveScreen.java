package com.spotifyhud.screen;

import com.spotifyhud.Config;
import com.spotifyhud.Draw;
import com.spotifyhud.Themes;
import com.spotifyhud.Themes.Theme;
import com.spotifyhud.hud.SpotifyHud;
import com.spotifyhud.media.Player;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

/** Drag the HUD anywhere on screen. */
public class HudMoveScreen extends BaseScreen {
    private boolean dragging;
    private int offX, offY;

    public HudMoveScreen(Screen parent) {
        super("Move HUD", parent);
    }

    @Override
    protected void draw(GuiGraphicsExtractor g, float delta) {
        Theme t = th();
        Config cfg = Config.get();
        Draw.rect(g, 0, 0, width, height, 0x55000000);

        int hx = SpotifyHud.x(), hy = SpotifyHud.y();
        int totalH = SpotifyHud.totalHeight();
        int sw = SpotifyHud.width(), sh = SpotifyHud.cardHeight();
        float sc = SpotifyHud.scale();

        // The real HUD is drawn under this screen when you're in a world.
        boolean realHudVisible = Minecraft.getInstance().level != null && cfg.hudEnabled && Player.supported();
        if (!realHudVisible) {
            Theme ht = Themes.hud();
            if (SpotifyHud.modern()) {
                Themes.Modern mo = Themes.modern();
                int fullH = Math.round((SpotifyHud.baseHeight() + (cfg.lyricsEnabled ? SpotifyHud.lyricsHeight() : 0)) * sc);
                Draw.rrectOutline(g, hx, hy, sw, fullH, 5, mo.line(), mo.bg());
                Draw.text(g, "HUD preview", hx + 10, hy + 10, mo.text());
                Draw.gradBar(g, hx + 10, hy + 22, Math.max(8, sw / 3), 2, mo.accent(), mo.accent2());
                if (cfg.lyricsEnabled) Draw.text(g, "lyrics", hx + 10, hy + sh + 8, mo.sub());
            } else {
                Draw.roundOutline(g, hx, hy, sw, sh, ht.accent(), ht.bg());
                Draw.text(g, "HUD preview", hx + 10, hy + 10, ht.text());
                if (cfg.lyricsEnabled) {
                    int ly = hy + sh + Math.round(SpotifyHud.lyricsGap() * sc);
                    Draw.roundOutline(g, hx, ly, sw, Math.round(SpotifyHud.lyricsHeight() * sc), ht.accent(), ht.bg());
                    Draw.text(g, "lyrics", hx + 10, ly + 9, ht.sub());
                }
            }
        }
        Draw.outline(g, hx - 2, hy - 2, sw + 4, totalH + 4, dragging ? 0xFFFFFFFF : t.accent());

        String hint = "Drag the HUD to move it";
        Draw.text(g, hint, (width - Draw.width(hint)) / 2, 10, 0xFFFFFFFF);
        button(g, width / 2 - 78, height - 28, 74, 16, "Reset", false, () -> {
            cfg.hudX = 8;
            cfg.hudY = 8;
            Config.save();
        });
        button(g, width / 2 + 4, height - 28, 74, 16, "Done", true, this::onClose);
    }

    @Override
    protected void onMiss(double x, double y) {
        if (x >= SpotifyHud.x() && x < SpotifyHud.x() + SpotifyHud.width()
                && y >= SpotifyHud.y() && y < SpotifyHud.y() + SpotifyHud.totalHeight()) {
            dragging = true;
            offX = (int) (x - SpotifyHud.x());
            offY = (int) (y - SpotifyHud.y());
        } else {
            super.onMiss(x, y);
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dragX, double dragY) {
        if (dragging) {
            Config cfg = Config.get();
            cfg.hudX = Math.max(0, Math.min((int) (e.x() - offX), width - SpotifyHud.width()));
            cfg.hudY = Math.max(0, Math.min((int) (e.y() - offY), height - SpotifyHud.totalHeight()));
            return true;
        }
        return super.mouseDragged(e, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragging) {
            dragging = false;
            Config.save();
            return true;
        }
        return super.mouseReleased(e);
    }
}
