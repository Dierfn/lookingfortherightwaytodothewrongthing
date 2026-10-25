package com.spotifyhud.media;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Takes the cover image Windows hands us, converts it to a 128x128 GUI texture, and caches it. */
public final class AlbumArt {
    private AlbumArt() {}

    private static final ConcurrentHashMap<String, Identifier> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<String> ORDER = new ConcurrentLinkedQueue<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final int MAX_CACHED = 120;

    /** Returns the texture if it is ready (else null). Art arrives via {@link #submit}. */
    public static Identifier get(String key) {
        if (key == null || key.isEmpty()) return null;
        return CACHE.get(key);
    }

    /** Called when the bridge delivers cover bytes for a track. */
    public static void submit(String key, byte[] bytes) {
        if (key == null || bytes == null || CACHE.containsKey(key) || !LOADING.add(key)) return;
        Thread t = new Thread(() -> load(key, bytes), "spotifyhud-art");
        t.setDaemon(true);
        t.start();
    }

    private static final Set<String> FALLBACK = ConcurrentHashMap.newKeySet();

    /**
     * If Windows has not handed us a cover a few seconds after the track starts, look one up on the
     * public iTunes search API (no key needed). Only used when the Windows cover never arrives.
     */
    public static void fallback(Player.Track t) {
        if (!FALLBACK.add(t.id())) return;
        Thread th = new Thread(() -> {
            try {
                Thread.sleep(4000);
                if (CACHE.containsKey(t.id())) return;
                String term = t.firstArtist() + " " + t.name();
                Http.Resp r = Http.get("https://itunes.apple.com/search?media=music&entity=song&limit=3&term="
                        + Http.enc(term), "SpotifyHUD-MinecraftMod/1.0");
                if (!r.ok()) return;
                var results = r.json().getAsJsonArray("results");
                if (results == null || results.isEmpty()) return;
                String art = Player.str(results.get(0).getAsJsonObject(), "artworkUrl100");
                if (art.isEmpty()) return;
                byte[] bytes = Http.getBytes(art.replace("100x100", "512x512"));
                if (bytes != null && !CACHE.containsKey(t.id())) {
                    Bridge.artStatus = "ok (online fallback)";
                    submit(t.id(), bytes);
                }
            } catch (Exception ignored) {
            }
        }, "spotifyhud-art-fallback");
        th.setDaemon(true);
        th.start();
    }

    private static void load(String url, byte[] bytes) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
            if (src == null) return;
            int s = Math.min(src.getWidth(), src.getHeight());
            BufferedImage sq = src.getSubimage((src.getWidth() - s) / 2, (src.getHeight() - s) / 2, s, s);
            BufferedImage out = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(sq, 0, 0, 128, 128, null);
            g.dispose();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bo);
            byte[] png = bo.toByteArray();

            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                try {
                    int n = COUNTER.incrementAndGet();
                    NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
                    DynamicTexture tex = new DynamicTexture(() -> "spotifyhud_art_" + n, img);
                    Identifier id = Identifier.fromNamespaceAndPath("spotifyhud", "art/" + n);
                    mc.getTextureManager().register(id, tex);
                    CACHE.put(url, id);
                    ORDER.add(url);
                    while (ORDER.size() > MAX_CACHED) {
                        String old = ORDER.poll();
                        Identifier oldId = old == null ? null : CACHE.remove(old);
                        if (oldId != null) mc.getTextureManager().release(oldId);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    LOADING.remove(url);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            LOADING.remove(url);
        }
    }
}
