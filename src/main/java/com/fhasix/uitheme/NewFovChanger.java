package com.fhasix.uitheme;

import com.fhasix.uitheme.mixin.SimpleOptionAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;

/**
 * FOV Changer: sets the field of view past the vanilla limit (110) - up to 180. The player's own FOV
 * is remembered and put back when the mod is turned off or the game closes.
 */
final class NewFovChanger {

    static final String ID = "fov_changer";

    private static Integer original;

    private NewFovChanger() {
    }

    static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("fov_changer", NewFovChanger::tick));
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> NewSafe.run("fov_changer", () -> restore(c)));
    }

    private static void set(SimpleOption<Integer> opt, int v) {
        ((SimpleOptionAccessor<Integer>) (Object) opt).fhasix$setValue(v);
    }

    private static void tick(MinecraftClient c) {
        if (c.options == null) {
            return;
        }
        if (!NewModSettings.isEnabled(c, ID)) {
            restore(c);
            return;
        }
        SimpleOption<Integer> fov = c.options.getFov();
        if (original == null) {
            original = fov.getValue();
        }
        int target = NewModSettings.opt(c, ID, "fov");
        if (fov.getValue() != target) {
            set(fov, target);
        }
    }

    private static void restore(MinecraftClient c) {
        if (original != null && c.options != null) {
            set(c.options.getFov(), original);
            original = null;
        }
    }
}
