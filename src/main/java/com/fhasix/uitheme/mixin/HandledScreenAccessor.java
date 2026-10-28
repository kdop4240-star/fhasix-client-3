package com.fhasix.uitheme.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Gives Shulker Tooltips the slot under the mouse in inventory / container screens. */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {

    @Accessor("focusedSlot")
    Slot fhasix$getFocusedSlot();
}
