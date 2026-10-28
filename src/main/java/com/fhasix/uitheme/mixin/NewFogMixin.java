package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewFog;
import net.minecraft.client.render.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Custom Fog: changes the fog colour and the fog start/end distances as they are written into the
 * fog uniform buffer.
 *
 * NOTE FOR WHOEVER COMPILES THIS: written without being able to compile. The target is the fog
 * renderer's private method that writes colour + environmentalStart/End + renderDistanceStart/End
 * (+ sky/cloud end) into the buffer - find the real name/descriptor in 1.21.11 and fix the "method"
 * strings. The float arguments are modified by ordinal (0 = environmental start, 1 = environmental
 * end, 2 = render distance start, 3 = render distance end, 4 = sky end). Sky end is only pushed out
 * (see NewFog.skyEnd) so the custom fog colour does not repaint the sky. require = 0, so a wrong
 * target just disables the mod instead of crashing.
 */
@Mixin(FogRenderer.class)
public abstract class NewFogMixin {

    private static final String WRITE =
            "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V";

    @ModifyVariable(method = WRITE, at = @At("HEAD"), argsOnly = true, require = 0)
    private Vector4f fhasix$color(Vector4f color) {
        return NewFog.color(color);
    }

    @ModifyVariable(method = WRITE, at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float fhasix$envStart(float v) {
        return NewFog.distance(v, 0);
    }

    @ModifyVariable(method = WRITE, at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private float fhasix$envEnd(float v) {
        return NewFog.distance(v, 1);
    }

    @ModifyVariable(method = WRITE, at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
    private float fhasix$renderStart(float v) {
        return NewFog.distance(v, 2);
    }

    @ModifyVariable(method = WRITE, at = @At("HEAD"), argsOnly = true, ordinal = 3, require = 0)
    private float fhasix$renderEnd(float v) {
        return NewFog.distance(v, 3);
    }

    @ModifyVariable(method = WRITE, at = @At("HEAD"), argsOnly = true, ordinal = 4, require = 0)
    private float fhasix$skyEnd(float v) {
        return NewFog.skyEnd(v);
    }
}
