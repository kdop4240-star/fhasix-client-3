package com.fhasix.uitheme;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;

/**
 * Animations (old 1.7 visuals). Currently: block-hitting - with a sword in your main hand and the
 * use key (right click) held, the sword is shown in the old "blocking" pose while it still swings.
 * The pose itself is applied by mixin/NewHandMixin.
 */
public final class NewAnimations {

    static final String ID = "animations";

    private NewAnimations() {
    }

    public static boolean blocking(ItemStack item) {
        MinecraftClient c = MinecraftClient.getInstance();
        return c != null && c.currentScreen == null && NewModSettings.isEnabled(c, ID)
                && NewModSettings.optBool(c, ID, "block")
                && c.options.useKey.isPressed()
                && item.isIn(ItemTags.SWORDS);
    }
}
