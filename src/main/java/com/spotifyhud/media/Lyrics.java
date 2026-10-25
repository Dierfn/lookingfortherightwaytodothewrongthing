package com.spotifyhud.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Time-synced lyrics from the free LRCLIB service (lrclib.net). */
public final class Lyrics {
    private Lyrics() {}

    public record Line(long ms, String text) {}

    private static final String UA = "SpotifyHUD-MinecraftMod/1.0 (https://github.com/)";
    private static final Pattern TS = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");

    private static volatile String forId = "";
    private static volatile List<Line> lines = List.of();
    private static volatile String status = "";

    public static void update(Player.Track t) {
        if (t == null || t.id().equals(forId)) return;
        forId = t.id();
        lines = List.of();
        status = "Loading lyrics...";
        Thread th = new Thread(() -> fetch(t), "spotifyhud-lyrics");
        th.setDaemon(true);
        th.start();
    }

    private static void fetch(Player.Track t) {
        List<Line> found = List.of();
        try {
            String url = "https://lrclib.net/api/get?track_name=" + Http.enc(t.name())
                    + "&artist_name=" + Http.enc(t.firstArtist())
                    + (t.album().isEmpty() ? "" : "&album_name=" + Http.enc(t.album()))
                    + (t.durationMs() > 0 ? "&duration=" + (t.durationMs() / 1000) : "");
            Http.Resp r = Http.get(url, UA);
            if (r.ok()) found = parse(Player.str(r.json(), "syncedLyrics"));

            if (found.isEmpty()) {
                String s = "https://lrclib.net/api/search?track_name=" + Http.enc(t.name())
                        + "&artist_name=" + Http.enc(t.firstArtist());
                Http.Resp sr = Http.get(s, UA);
                if (sr.ok()) {
                    var el = com.google.gson.JsonParser.parseString(sr.body());
                    if (el.isJsonArray()) {
                        JsonArray arr = el.getAsJsonArray();
                        for (JsonElement e : arr) {
                            if (!e.isJsonObject()) continue;
                            found = parse(Player.str(e.getAsJsonObject(), "syncedLyrics"));
                            if (!found.isEmpty()) break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (!t.id().equals(forId)) return; // track changed while loading
        lines = found;
        status = found.isEmpty() ? "No synced lyrics found" : "";
    }

    private static List<Line> parse(String lrc) {
        List<Line> out = new ArrayList<>();
        if (lrc == null || lrc.isBlank()) return out;
        for (String raw : lrc.split("\\R")) {
            Matcher m = TS.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find()) {
                long min = Long.parseLong(m.group(1));
                long sec = Long.parseLong(m.group(2));
                String frac = m.group(3);
                long ms = 0;
                if (frac != null) {
                    ms = Long.parseLong(frac);
                    if (frac.length() == 1) ms *= 100;
                    else if (frac.length() == 2) ms *= 10;
                }
                times.add(min * 60_000 + sec * 1000 + ms);
                end = m.end();
            }
            if (times.isEmpty()) continue;
            String text = raw.substring(end).trim();
            if (text.isEmpty()) text = "...";
            for (long t : times) out.add(new Line(t, text));
        }
        out.sort((a, b) -> Long.compare(a.ms(), b.ms()));
        return out;
    }

    private static int index(List<Line> l, long progressMs) {
        long t = progressMs + 150; // small look-ahead so the line appears just as it is sung
        int lo = 0, hi = l.size() - 1, idx = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (l.get(mid).ms() <= t) {
                idx = mid;
                lo = mid + 1;
            } else hi = mid - 1;
        }
        return idx;
    }

    /** True when synced lyrics for the current track are loaded. */
    public static boolean available() {
        return !lines.isEmpty();
    }

    /** The line to show right now, or a status message. */
    public static String current(long progressMs) {
        List<Line> l = lines;
        if (l.isEmpty()) return status;
        int idx = index(l, progressMs);
        return idx < 0 ? "..." : l.get(idx).text();
    }

    /**
     * previous / current / next line, plus how far through the current line we are (0..1) for the
     * word-by-word highlight. frac is -1 when there is nothing to highlight (no lyrics / status text).
     * LRCLIB only has per-line timestamps, so the position inside a line is estimated from word length.
     */
    public record Cur(String prev, String text, String next, double frac) {}

    public static Cur cur(long progressMs) {
        List<Line> l = lines;
        if (l.isEmpty()) return new Cur("", status, "", -1);
        int idx = index(l, progressMs);
        if (idx < 0) return new Cur("", "...", l.get(0).text(), -1);
        Line c = l.get(idx);
        String text = c.text();
        String prev = idx - 1 >= 0 ? l.get(idx - 1).text() : "";
        String next = idx + 1 < l.size() ? l.get(idx + 1).text() : "";
        if (text.equals("...")) return new Cur(prev, text, next, -1);

        long t = progressMs + 150;
        long start = c.ms();
        long gap = (idx + 1 < l.size() ? l.get(idx + 1).ms() : start + 6000) - start;
        long singing = Math.min(gap, Math.max(1200L, text.length() * 75L)); // time spent actually singing it
        double frac = Math.max(0, Math.min(1, (t - start) / (double) Math.max(1, singing)));
        return new Cur(prev, text, next, frac);
    }
}
