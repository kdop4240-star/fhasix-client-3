package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewAnimations;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Animations (1.7 block-hitting): right after the game has positioned the held item (equip + swing
 * offsets) and right before it draws it, tilt a sword into the old blocking pose while the use key
 * is held. Because this is injected before the draw call, the swing animation keeps playing.
 *
 * NOTE FOR WHOEVER COMPILES THIS: written without being able to compile it. If the renderItem call
 * name/owner differs in 1.21.11, adapt the @At target. The rotation numbers are the classic 1.8
 * values; they may need a small tweak in game to look right.
 */
@Mixin(HeldItemRenderer.class)
public abstract class NewHandMixin {

    @Inject(method = "renderFirstPersonItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem"),
            require = 0)
    private void fhasix$blockPose(CallbackInfo ci,
                                  @Local(argsOnly = true) MatrixStack matrices,
                                  @Local(argsOnly = true) ItemStack item) {
        try {
            if (NewAnimations.blocking(item)) {
                matrices.translate(-0.1f, 0.15f, 0.0f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30f));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(60f));
                matrices.scale(0.9f, 0.9f, 0.9f);
            }
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("hand_mixin", t);
        }
    }
}
