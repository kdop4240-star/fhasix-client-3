package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewScaleHolder;
import com.fhasix.uitheme.NewUhc;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * UHC Overlay: scales DROPPED item entities (golden apples, gold ingots, heads...).
 * 1) after the render state is filled, remember the scale for that entity's stack,
 * 2) around the renderer's render(...) scale the matrix stack by it.
 * Parameters are taken with @Local so the handlers don't depend on the exact method signatures.
 *
 * NOTE FOR WHOEVER COMPILES THIS: written without compiling; if the method names differ in
 * 1.21.11 (updateRenderState / render) adapt the @Inject targets only.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class NewItemEntityMixin {

    @Inject(method = "updateRenderState", at = @At("TAIL"), require = 0)
    private void fhasix$capture(CallbackInfo ci,
                                @Local(argsOnly = true) ItemEntity entity,
                                @Local(argsOnly = true) ItemEntityRenderState state) {
        try {
            ((NewScaleHolder) state).fhasix$setScale(NewUhc.droppedScale(entity.getStack()));
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("item_entity_mixin", t);
        }
    }

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void fhasix$push(CallbackInfo ci,
                             @Local(argsOnly = true) ItemEntityRenderState state,
                             @Local(argsOnly = true) MatrixStack matrices) {
        try {
            float s = ((NewScaleHolder) state).fhasix$getScale();
            if (s > 1.001f) {
                matrices.push();
                matrices.scale(s, s, s);
            }
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("item_entity_mixin", t);
        }
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void fhasix$pop(CallbackInfo ci,
                            @Local(argsOnly = true) ItemEntityRenderState state,
                            @Local(argsOnly = true) MatrixStack matrices) {
        try {
            if (((NewScaleHolder) state).fhasix$getScale() > 1.001f) {
                matrices.pop();
            }
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("item_entity_mixin", t);
        }
    }
}
