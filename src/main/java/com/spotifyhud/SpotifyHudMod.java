package com.spotifyhud;

import com.mojang.blaze3d.platform.InputConstants;
import com.spotifyhud.hud.SpotifyHud;
import com.spotifyhud.screen.BaseScreen;
import com.spotifyhud.screen.SpotifyScreen;
import com.spotifyhud.media.Player;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class SpotifyHudMod implements ClientModInitializer {
    public static final String ID = "spotifyhud";

    public static KeyMapping OPEN;
    public static KeyMapping PLAY_PAUSE;
    public static KeyMapping NEXT;
    public static KeyMapping PREV;

    @Override
    public void onInitializeClient() {
        Config.load();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"));
        OPEN = key("open", GLFW.GLFW_KEY_O, category);
        PLAY_PAUSE = key("play_pause", GLFW.GLFW_KEY_BACKSLASH, category);
        NEXT = key("next", GLFW.GLFW_KEY_RIGHT_BRACKET, category);
        PREV = key("prev", GLFW.GLFW_KEY_LEFT_BRACKET, category);

        // The HUD itself
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(ID, "hud"),
                (graphics, tickCounter) -> {
                    SkyLyrics.render(graphics, tickCounter.getGameTimeDeltaPartialTick(false));
                    SpotifyHud.render(graphics);
                });

        // Keybinds (these only fire when no screen is open)
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN.consumeClick()) Ui.open(new SpotifyScreen(null));
            while (PLAY_PAUSE.consumeClick()) Player.togglePlay();
            while (NEXT.consumeClick()) Player.next();
            while (PREV.consumeClick()) Player.previous();
        });

        // Click the HUD buttons while a screen with a free mouse is open (pause menu, chat, inventory...)
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof BaseScreen) return;
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) ->
                    !(event.button() == 0 && SpotifyHud.mouseClicked(event.x(), event.y())));
        });

        Player.start();
    }

    private static KeyMapping key(String name, int code, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key." + ID + "." + name, InputConstants.Type.KEYSYM, code, category));
    }

    /** Rebinds a key from the settings screen (same effect as changing it in Controls). */
    public static void rebind(KeyMapping mapping, int keyCode) {
        Minecraft mc = Minecraft.getInstance();
        mapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(keyCode));
        KeyMapping.resetMapping();
        mc.options.save();
    }
}
