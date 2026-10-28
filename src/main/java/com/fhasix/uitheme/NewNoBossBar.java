package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;

/** No Boss Bar: while this mod is enabled the vanilla boss bar is not drawn; disabled = normal boss bar. */
final class NewNoBossBar {

    static final String ID = "no_boss_bar";

    private NewNoBossBar() {
    }

    static void init() {
        HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, original -> NewSafe.replace("no_boss_bar", original, (context, tickCounter) -> {
            if (!NewModSettings.isEnabled(MinecraftClient.getInstance(), ID)) {
                original.render(context, tickCounter);
            }
        }));
    }
}
