package com.spotifyhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class Ui {
    private Ui() {}

    /** True while one of the mod's screens is open. */
    public static volatile boolean menuOpen = false;

    /** 26.2 renamed Minecraft#setScreen to setScreenAndShow. */
    public static void open(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreenAndShow(screen));
    }

    public static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    // ---- "is the HUD hidden (F1)?" ----------------------------------------------------------
    // 26.2 moved this from Options.hideGui to Gui.hud.isHidden(). Looked up by reflection so the
    // mod compiles and runs regardless of which of the two exists.

    private static boolean probed = false;
    private static Field guiField, hudField, optionsField, hideGuiField;
    private static Method isHidden;

    private static Field field(Class<?> c, String name) {
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    private static void probe() {
        probed = true;
        guiField = field(Minecraft.class, "gui");
        if (guiField != null) {
            hudField = field(guiField.getType(), "hud");
            if (hudField != null) {
                try {
                    isHidden = hudField.getType().getMethod("isHidden");
                } catch (Exception ignored) {
                }
            }
        }
        optionsField = field(Minecraft.class, "options");
        if (optionsField != null) hideGuiField = field(optionsField.getType(), "hideGui");
    }

    public static boolean hudHidden(Minecraft mc) {
        try {
            if (!probed) probe();
            if (isHidden != null) {
                Object gui = guiField.get(mc);
                Object hud = gui == null ? null : hudField.get(gui);
                if (hud != null) return (boolean) isHidden.invoke(hud);
            }
            if (hideGuiField != null) return hideGuiField.getBoolean(optionsField.get(mc));
        } catch (Exception ignored) {
        }
        return false;
    }
}
