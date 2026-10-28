package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;

/** No Scoreboard: while enabled the server's sidebar scoreboard is not drawn. No settings. */
final class NewNoScoreboard {

    static final String ID = "no_scoreboard";

    private NewNoScoreboard() {
    }

    static void init() {
        HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, original -> NewSafe.replace("no_scoreboard", original, (context, tickCounter) -> {
            if (!NewModSettings.isEnabled(MinecraftClient.getInstance(), ID)) {
                original.render(context, tickCounter);
            }
        }));
    }
}
