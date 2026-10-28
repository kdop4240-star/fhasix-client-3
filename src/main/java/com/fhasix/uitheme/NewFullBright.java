package com.fhasix.uitheme;

import com.fhasix.uitheme.mixin.SimpleOptionAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;

/**
 * Full Bright: pushes the game's brightness (gamma) option past its normal maximum so caves and the
 * night are fully lit. "Light level" sets how far (1 = vanilla Bright, 15 = everything fully lit).
 * The player's own brightness is remembered and put back when the mod is turned off or the game closes.
 */
final class NewFullBright {

    static final String ID = "full_bright";

    private static Double original;

    private NewFullBright() {
    }

    static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("full_bright", NewFullBright::tick));
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> NewSafe.run("full_bright", () -> restore(c)));
    }

    private static void set(SimpleOption<Double> opt, double v) {
        ((SimpleOptionAccessor<Double>) (Object) opt).fhasix$setValue(v);
    }

    private static void tick(MinecraftClient c) {
        if (c.options == null) {
            return;
        }
        if (!NewModSettings.isEnabled(c, ID)) {
            restore(c);
            return;
        }
        SimpleOption<Double> gamma = c.options.getGamma();
        if (original == null) {
            original = gamma.getValue();
        }
        double target = NewModSettings.opt(c, ID, "level");
        if (Math.abs(gamma.getValue() - target) > 1.0e-6) {
            set(gamma, target);
        }
    }

    private static void restore(MinecraftClient c) {
        if (original != null && c.options != null) {
            set(c.options.getGamma(), original);
            original = null;
        }
    }
}
