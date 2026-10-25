package com.spotifyhud.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class Http {
    private Http() {}

    public static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public record Resp(int status, String body, int retryAfter) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }

        public JsonObject json() {
            try {
                if (body == null || body.isBlank()) return new JsonObject();
                var el = JsonParser.parseString(body);
                return el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
            } catch (Exception e) {
                return new JsonObject();
            }
        }
    }

    public static Resp send(HttpRequest req) {
        try {
            HttpResponse<String> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            int retry = 0;
            try {
                retry = Integer.parseInt(res.headers().firstValue("Retry-After").orElse("0"));
            } catch (NumberFormatException ignored) {
            }
            return new Resp(res.statusCode(), res.body(), retry);
        } catch (Exception e) {
            return new Resp(-1, String.valueOf(e), 0);
        }
    }

    public static Resp get(String url, String userAgent) {
        return send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", userAgent)
                .GET().build());
    }

    public static byte[] getBytes(String url) {
        try {
            HttpResponse<byte[]> res = CLIENT.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            return res.statusCode() == 200 ? res.body() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
