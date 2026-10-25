package com.spotifyhud;

/**
 * Pure math for placing the sky lyrics (no Minecraft classes, so it can be unit-tested).
 * Angles are degrees in Minecraft's convention: yaw 0 looks south and grows clockwise,
 * pitch 0 is the horizon, negative pitch looks up.
 */
public final class SkyMath {
    private SkyMath() {}

    /** Wraps an angle difference into [-180, 180). */
    public static float wrap(float deg) {
        deg %= 360f;
        if (deg >= 180f) deg -= 360f;
        if (deg < -180f) deg += 360f;
        return deg;
    }

    /**
     * Projects a point that sits far away in direction (targetYaw, targetPitch) onto the screen of a
     * camera looking along (camYaw, camPitch).
     *
     * @param vFovDeg vertical field of view (Minecraft's FOV setting is the vertical one)
     * @param out     receives {screenX, screenY} in the same units as screenW / screenH
     * @return false when the direction is behind the camera (nothing to draw)
     */
    public static boolean project(float camYaw, float camPitch, float targetYaw, float targetPitch,
                                  float vFovDeg, int screenW, int screenH, float[] out) {
        double cy = Math.toRadians(camYaw), cp = Math.toRadians(camPitch);
        double ty = Math.toRadians(targetYaw), tp = Math.toRadians(targetPitch);

        // camera basis (forward, right, up)
        double fx = -Math.sin(cy) * Math.cos(cp), fy = -Math.sin(cp), fz = Math.cos(cy) * Math.cos(cp);
        double rx = -Math.cos(cy), ry = 0, rz = -Math.sin(cy);
        double ux = ry * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - ry * fx; // right x forward

        // target direction
        double dx = -Math.sin(ty) * Math.cos(tp), dy = -Math.sin(tp), dz = Math.cos(ty) * Math.cos(tp);

        double depth = dx * fx + dy * fy + dz * fz;
        if (depth < 0.05) return false;
        double right = dx * rx + dy * ry + dz * rz;
        double up = dx * ux + dy * uy + dz * uz;

        double focal = (screenH / 2.0) / Math.tan(Math.toRadians(vFovDeg) / 2.0);
        out[0] = (float) (screenW / 2.0 + right / depth * focal);
        out[1] = (float) (screenH / 2.0 - up / depth * focal);
        return true;
    }

    /** Moves {@code current} toward {@code target} (shortest way round); rate <= 0 means "snap". */
    public static float follow(float current, float target, float rate, float dtSeconds) {
        if (rate <= 0f) return target;
        float k = 1f - (float) Math.exp(-rate * Math.max(0f, dtSeconds));
        return current + wrap(target - current) * k;
    }
}
