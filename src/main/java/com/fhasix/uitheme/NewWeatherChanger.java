package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/** Weather Changer: shows clear sky, rain or a thunderstorm whatever the server says (client side only). */
final class NewWeatherChanger {

    static final String ID = "weather_changer";

    private NewWeatherChanger() {
    }

    static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("weather_changer", c -> {
            if (c.world == null || !NewModSettings.isEnabled(c, ID)) {
                return;
            }
            int mode = NewModSettings.opt(c, ID, "weather");
            c.world.setRainGradient(mode >= 1 ? 1f : 0f);
            c.world.setThunderGradient(mode >= 2 ? 1f : 0f);
        }));
    }
}
