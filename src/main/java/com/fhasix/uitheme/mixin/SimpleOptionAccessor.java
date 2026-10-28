package com.fhasix.uitheme.mixin;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Full Bright / FOV Changer set an option past the range its vanilla slider allows. */
@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor<T> {

    @Accessor("value")
    void fhasix$setValue(T value);
}
