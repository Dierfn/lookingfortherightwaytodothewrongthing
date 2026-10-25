package com.spotifyhud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Draws a lyric line with a karaoke-style wipe: words already sung are highlighted as they are reached. */
public final class LyricsView {
    private LyricsView() {}

    /**
     * Draws {@code text} centered inside the box (x, y, w, boxH).
     *
     * @param frac how much of the line has been sung (0..1), or negative for no highlighting
     */
    public static void drawCentered(GuiGraphicsExtractor g, String text, double frac, int x, int y, int w, int boxH,
                                    int maxLines, int lineH, int sungColor, int dimColor, int plainColor) {
        String[] words = text.trim().isEmpty() ? new String[0] : text.trim().split("\\s+");
        if (words.length == 0) return;

        // break into lines
        List<List<Integer>> lines = new ArrayList<>();
        List<Integer> cur = new ArrayList<>();
        int curW = 0, space = Draw.width(" ");
        for (int i = 0; i < words.length; i++) {
            int ww = Draw.width(words[i]);
            if (!cur.isEmpty() && curW + space + ww > w) {
                lines.add(cur);
                cur = new ArrayList<>();
                curW = 0;
            }
            curW += (cur.isEmpty() ? 0 : space) + ww;
            cur.add(i);
        }
        if (!cur.isEmpty()) lines.add(cur);
        if (lines.size() > maxLines) lines = new ArrayList<>(lines.subList(0, maxLines));

        // each word gets a share of the line proportional to its length
        double total = 0;
        double[] start = new double[words.length], end = new double[words.length];
        for (String word : words) total += Math.max(2, word.length());
        double acc = 0;
        for (int i = 0; i < words.length; i++) {
            start[i] = acc / total;
            acc += Math.max(2, words[i].length());
            end[i] = acc / total;
        }

        int sy = y + (boxH - lines.size() * lineH) / 2 + 1;
        for (int li = 0; li < lines.size(); li++) {
            List<Integer> ln = lines.get(li);
            int lw = 0;
            for (int k = 0; k < ln.size(); k++) lw += Draw.width(words[ln.get(k)]) + (k > 0 ? space : 0);
            if (lw > w) lw = w;
            int cx = x + (w - lw) / 2;
            int cy = sy + li * lineH;
            for (int idx : ln) {
                String word = words[idx];
                int ww = Draw.width(word);
                if (frac < 0) {
                    Draw.text(g, word, cx, cy, plainColor);
                } else {
                    Draw.text(g, word, cx, cy, dimColor);
                    double fw = Math.max(0, Math.min(1, (frac - start[idx]) / (end[idx] - start[idx])));
                    if (fw >= 1) {
                        Draw.text(g, word, cx, cy, sungColor);
                    } else if (fw > 0) {
                        Draw.clip(g, cx, cy - 1, Math.max(1, (int) Math.ceil(ww * fw)), lineH + 2);
                        Draw.text(g, word, cx, cy, sungColor);
                        Draw.unclip(g);
                    }
                }
                cx += ww + space;
            }
        }
    }
}
