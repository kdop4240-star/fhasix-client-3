package com.fhasix.uitheme;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.MinecraftClient;

/**
 * UHC Overlay: dropped golden apples / apples, gold ingots and player heads are drawn bigger on
 * the ground so they are easy to spot in a fight (used by mixin/NewItemEntityMixin). The ore
 * outlines are drawn by NewOreHighlight.
 */
public final class NewUhc {

    static final String ID = "uhc_overlay";

    private NewUhc() {
    }

    /** Scale for a DROPPED item entity; 1.0 = normal. */
    public static float droppedScale(ItemStack s) {
        if (s.isEmpty()) {
            return 1f;
        }
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || !NewModSettings.isEnabled(c, ID)) {
            return 1f;
        }
        boolean hit = (NewModSettings.optBool(c, ID, "apples")
                && (s.isOf(Items.APPLE) || s.isOf(Items.GOLDEN_APPLE) || s.isOf(Items.ENCHANTED_GOLDEN_APPLE)))
                || (NewModSettings.optBool(c, ID, "gold") && s.isOf(Items.GOLD_INGOT))
                || (NewModSettings.optBool(c, ID, "heads") && s.isOf(Items.PLAYER_HEAD));
        return hit ? NewModSettings.opt(c, ID, "scale") / 100f : 1f;
    }
}
