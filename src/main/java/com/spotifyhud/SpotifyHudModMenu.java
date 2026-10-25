package com.spotifyhud;

import com.spotifyhud.screen.SpotifyScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Clicking the mod's entry in Mod Menu opens the Spotify main menu. */
public final class SpotifyHudModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SpotifyScreen::new;
    }
}
