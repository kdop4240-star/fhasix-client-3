package com.fhasix.uitheme.mixin;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Accesses the 1.21.11 line render layer factory, which is package-private in vanilla. */
@Mixin(RenderLayers.class)
public interface RenderLayersAccessor {
    @Invoker("lines")
    static RenderLayer fhasix$lines() {
        throw new AssertionError("Mixin invoker was not transformed");
    }

    @Invoker("debugFilledBox")
    static RenderLayer fhasix$debugFilledBox() {
        throw new AssertionError("Mixin invoker was not transformed");
    }
}
