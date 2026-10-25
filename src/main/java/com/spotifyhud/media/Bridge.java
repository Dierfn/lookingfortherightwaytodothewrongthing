package com.spotifyhud.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Talks to a hidden powershell.exe running smtc.ps1, which reads the Windows System Media Transport
 * Controls (the "now playing" data Spotify publishes to Windows). One JSON line per update comes
 * back; plain-text lines are commands going the other way.
 */
public final class Bridge {
    private Bridge() {}

    /** Shown in the settings screen. */
    public static volatile String status = "Starting...";
    /** Why the cover art is (not) showing - shown in Settings. */
    public static volatile String artStatus = "";

    private static volatile Process proc;
    private static volatile BufferedWriter out;

    public static void start() {
        Thread t = new Thread(Bridge::loop, "spotifyhud-bridge");
        t.setDaemon(true);
        t.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Process p = proc;
            if (p != null) p.destroy();
        }));
    }

    public static synchronized void send(String line) {
        try {
            BufferedWriter w = out;
            if (w == null) return;
            w.write(line);
            w.write('\n');
            w.flush();
        } catch (Exception ignored) {
        }
    }

    private static void loop() {
        Path script;
        try {
            script = extractScript();
        } catch (Exception e) {
            status = "Could not write helper script: " + e.getMessage();
            Player.message = status;
            return;
        }
        int quickFails = 0;
        while (true) {
            long began = System.currentTimeMillis();
            String err = runOnce(script);
            if (System.currentTimeMillis() - began < 15_000) quickFails++;
            else quickFails = 0;
            status = err.isEmpty() ? "Helper stopped, restarting..." : err;
            if (!err.isEmpty()) Player.message = err;
            Player.clearTrack();
            try {
                Thread.sleep(quickFails >= 4 ? 30_000 : 2_000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** Runs the helper until it exits. Returns the last error text it printed (may be empty). */
    private static String runOnce(Path script) {
        String lastError = "";
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-File", script.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            proc = p;
            out = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8));
            Player.applyFilter();

            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.strip();
                    if (line.isEmpty()) continue;
                    if (!line.startsWith("{")) {
                        lastError = line.length() > 160 ? line.substring(0, 160) : line;
                        continue;
                    }
                    try {
                        JsonObject j = JsonParser.parseString(line).getAsJsonObject();
                        String type = Player.str(j, "type");
                        switch (type) {
                            case "state" -> {
                                status = "Running";
                                Player.onState(j);
                            }
                            case "art" -> Player.onArt(j);
                            case "artstatus" -> artStatus = Player.str(j, "msg");
                            case "ready" -> status = "Running";
                            case "error" -> {
                                lastError = Player.str(j, "msg");
                                status = lastError;
                            }
                            default -> {
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            p.waitFor();
        } catch (Exception e) {
            lastError = "Could not start PowerShell: " + e.getMessage();
        } finally {
            out = null;
            Process p = proc;
            if (p != null) p.destroy();
            proc = null;
        }
        return lastError;
    }

    private static Path extractScript() throws Exception {
        Path dir = FabricLoader.getInstance().getGameDir().resolve(".spotifyhud");
        Files.createDirectories(dir);
        Path file = dir.resolve("smtc.ps1");
        try (InputStream in = Bridge.class.getResourceAsStream("/assets/spotifyhud/smtc.ps1")) {
            if (in == null) throw new IllegalStateException("smtc.ps1 missing from jar");
            Files.write(file, in.readAllBytes());
        }
        return file;
    }
}
