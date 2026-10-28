package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/**
 * Time Changer: locks the time of day you SEE (client side only - the server clock is untouched).
 * 0 = sunrise, 6000 = noon, 12000 = sunset, 18000 = midnight.
 */
final class NewTimeChanger {

    static final String ID = "time_changer";

    private NewTimeChanger() {
    }

    static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("time_changer", c -> {
            if (c.world != null && NewModSettings.isEnabled(c, ID)) {
                c.world.getLevelProperties().setTimeOfDay(NewModSettings.opt(c, ID, "time"));
            }
        }));
    }
}
