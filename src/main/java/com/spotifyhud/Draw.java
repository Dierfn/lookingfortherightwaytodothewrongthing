package com.spotifyhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Every call into Minecraft's GUI drawing API lives in this one class.
 * If a method name or signature differs on your exact 26.2 build, this is the only file to fix.
 */
public final class Draw {
    private Draw() {}

    public static final int ART_TEX = 128;

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static int width(String s) {
        return font().width(s);
    }

    // ---- primitives -------------------------------------------------------------------------

    public static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + h, c);
    }

    public static void pixel(GuiGraphicsExtractor g, int x, int y, int c) {
        g.fill(x, y, x + 1, y + 1, c);
    }

    public static void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(font(), s, x, y, color);
    }

    public static void image(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h) {
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, ART_TEX, ART_TEX, ART_TEX, ART_TEX);
    }

    public static void clip(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.enableScissor(x, y, x + w, y + h);
    }

    public static void unclip(GuiGraphicsExtractor g) {
        g.disableScissor();
    }

    /** Scales everything drawn until popScale(), growing out from the point (x, y). */
    public static void pushScale(GuiGraphicsExtractor g, int x, int y, float s) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s, s);
        g.pose().translate(-x, -y);
    }

    public static void popScale(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }

    // ---- shapes -----------------------------------------------------------------------------

    /** Rectangle with 2px notched corners. */
    public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x + 2, y, x + w - 2, y + 1, c);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, c);
        g.fill(x, y + 2, x + w, y + h - 2, c);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, c);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, c);
    }

    /** Smoothly rounded rectangle (circular corners of radius r). */
    public static void rrect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            g.fill(x, y, x + w, y + h, c);
            return;
        }
        for (int i = 0; i < r; i++) {
            int inset = cornerInset(r, i);
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, c);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, c);
        }
        g.fill(x, y + r, x + w, y + h - r, c);
    }

    /** Rectangle whose bottom two corners are rounded (top edge is square). */
    public static void rrectBottom(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c) {
        r = Math.max(0, Math.min(r, Math.min(w, h)));
        for (int i = 0; i < r; i++) {
            int inset = cornerInset(r, i);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, c);
        }
        g.fill(x, y, x + w, y + h - r, c);
    }

    /** Rounded rectangle with a 1px border. */
    public static void rrectOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int border, int fill) {
        rrect(g, x, y, w, h, r, border);
        rrect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    /** How far row i (0 = outermost) of a radius-r corner is pulled in from the edge. */
    private static int cornerInset(int r, int i) {
        double dy = r - i - 0.5;
        return (int) Math.round(r - Math.sqrt(Math.max(0, (double) r * r - dy * dy)));
    }

    /** Fully rounded pill filled with a left-to-right gradient. */
    public static void gradPill(GuiGraphicsExtractor g, int x, int y, int w, int h, int c1, int c2) {
        int r = h / 2;
        for (int i = 0; i < w; i++) {
            // horizontal distance from the center of the nearest end cap (0 in the straight middle part)
            double dx = i < r ? r - i - 0.5 : (i >= w - r ? i - (w - r) + 0.5 : 0);
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, (double) r * r - dx * dx)));
            g.fill(x + i, y + inset, x + i + 1, y + h - inset, Themes.mix(c1, c2, w <= 1 ? 0 : i / (float) (w - 1)));
        }
    }

    /** Plain left-to-right gradient bar. */
    public static void gradBar(GuiGraphicsExtractor g, int x, int y, int w, int h, int c1, int c2) {
        for (int i = 0; i < w; i++)
            g.fill(x + i, y, x + i + 1, y + h, Themes.mix(c1, c2, w <= 1 ? 0 : i / (float) (w - 1)));
    }

    /** Top-to-bottom gradient inside a rounded rectangle. */
    public static void rrectVGrad(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int top, int bottom) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int row = 0; row < h; row++) {
            int inset = 0;
            if (row < r) inset = cornerInset(r, row);
            else if (row >= h - r) inset = cornerInset(r, h - 1 - row);
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, Themes.mix(top, bottom, h <= 1 ? 0 : row / (float) (h - 1)));
        }
    }

    public static void roundOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int border, int fill) {
        round(g, x, y, w, h, border);
        round(g, x + 1, y + 1, w - 2, h - 2, fill);
    }

    public static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    public static void circle(GuiGraphicsExtractor g, int cx, int cy, int r, int c) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, c);
        }
    }

    // ---- icons ------------------------------------------------------------------------------

    /** Right-pointing triangle, s wide, (2s-1) tall, with its left edge at x. */
    private static void triRight(GuiGraphicsExtractor g, int x, int cy, int s, int c) {
        for (int i = 0; i < s; i++) {
            int half = s - 1 - i;
            g.fill(x + i, cy - half, x + i + 1, cy + half + 1, c);
        }
    }

    /** Left-pointing triangle with its right edge at x+s. */
    private static void triLeft(GuiGraphicsExtractor g, int x, int cy, int s, int c) {
        for (int i = 0; i < s; i++) {
            int half = i;
            g.fill(x + i, cy - half, x + i + 1, cy + half + 1, c);
        }
    }

    public static void icon(GuiGraphicsExtractor g, String kind, int cx, int cy, int s, int c) {
        switch (kind) {
            case "play" -> triRight(g, cx - s / 2, cy, s, c);
            case "pause" -> {
                int bw = Math.max(2, s * 2 / 5);
                g.fill(cx - bw - 1, cy - s + 1, cx - 1, cy + s, c);
                g.fill(cx + 1, cy - s + 1, cx + 1 + bw, cy + s, c);
            }
            case "next" -> {
                triRight(g, cx - s / 2 - 1, cy, s, c);
                g.fill(cx + s / 2, cy - s + 1, cx + s / 2 + 2, cy + s, c);
            }
            case "prev" -> {
                triLeft(g, cx - s / 2 + 1, cy, s, c);
                g.fill(cx - s / 2 - 2, cy - s + 1, cx - s / 2, cy + s, c);
            }
            case "shuffle" -> {
                for (int i = 0; i <= 8; i++) {
                    pixel(g, cx - 4 + i, cy - 3 + i * 6 / 8, c);
                    pixel(g, cx - 4 + i, cy + 3 - i * 6 / 8, c);
                }
                pixel(g, cx + 5, cy - 3, c);
                pixel(g, cx + 5, cy + 3, c);
            }
            case "repeat" -> {
                g.fill(cx - 5, cy - 3, cx + 5, cy - 2, c);
                g.fill(cx - 5, cy + 3, cx + 5, cy + 4, c);
                g.fill(cx - 5, cy - 3, cx - 4, cy + 4, c);
                g.fill(cx + 4, cy - 3, cx + 5, cy + 4, c);
            }
            case "home" -> {
                for (int i = 0; i < 5; i++) g.fill(cx - i, cy - 5 + i, cx + i + 1, cy - 4 + i, c);
                g.fill(cx - 4, cy, cx + 5, cy + 5, c);
            }
            default -> {
            }
        }
    }

    /** Speaker icon (about 11px wide). level 0 = muted (x), 1 = low, 2 = medium, 3 = loud. */
    public static void speaker(GuiGraphicsExtractor g, int cx, int cy, int level, int c) {
        g.fill(cx - 5, cy - 2, cx - 3, cy + 3, c);
        for (int i = 0; i < 4; i++) g.fill(cx - 3 + i, cy - 2 - i, cx - 2 + i, cy + 3 + i, c);
        if (level <= 0) {
            for (int i = 0; i < 4; i++) {
                pixel(g, cx + 2 + i, cy - 2 + i, c);
                pixel(g, cx + 2 + i, cy + 1 - i, c);
            }
            return;
        }
        g.fill(cx + 2, cy - 1, cx + 3, cy + 2, c);
        if (level >= 2) g.fill(cx + 4, cy - 2, cx + 5, cy + 3, c);
        if (level >= 3) g.fill(cx + 6, cy - 4, cx + 7, cy + 5, c);
    }

    /** Pixel heart used for the "Liked Songs" cover. */
    public static void heart(GuiGraphicsExtractor g, int cx, int cy, int scale, int c) {
        String[] rows = {"0110110", "1111111", "1111111", "0111110", "0011100", "0001000"};
        int x0 = cx - (7 * scale) / 2, y0 = cy - (6 * scale) / 2;
        for (int r = 0; r < rows.length; r++)
            for (int col = 0; col < 7; col++)
                if (rows[r].charAt(col) == '1')
                    g.fill(x0 + col * scale, y0 + r * scale, x0 + (col + 1) * scale, y0 + (r + 1) * scale, c);
    }

    // ---- text helpers -----------------------------------------------------------------------

    public static String fit(String s, int maxW) {
        if (s == null) return "";
        if (width(s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && width(t + "...") > maxW) t = t.substring(0, t.length() - 1);
        return t + "...";
    }

    public static List<String> wrap(String s, int maxW, int maxLines) {
        List<String> out = new ArrayList<>();
        String[] words = s.split("\\s+");
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            String test = cur.isEmpty() ? w : cur + " " + w;
            if (width(test) <= maxW) {
                cur = new StringBuilder(test);
            } else {
                if (!cur.isEmpty()) out.add(cur.toString());
                cur = new StringBuilder(w);
                if (out.size() == maxLines) break;
            }
        }
        if (out.size() < maxLines && !cur.isEmpty()) out.add(cur.toString());
        if (out.size() > maxLines) out = new ArrayList<>(out.subList(0, maxLines));
        for (int i = 0; i < out.size(); i++) out.set(i, fit(out.get(i), maxW));
        return out;
    }
}
