package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.FhasixCosmetics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents the vanilla cape from stacking over the equipped FHASIX cape.
 *
 * PORTING NOTE: vanilla's CapeFeatureRenderer#render now takes a
 * PlayerEntityRenderState instead of the live entity, and an
 * OrderedRenderCommandQueue instead of a VertexConsumerProvider (see
 * FhasixCapeFeatureRenderer for the full explanation). The local-player
 * identity check is done via state.id, the render state's captured
 * entity id, since the entity itself is no longer a parameter here.
 */
@Mixin(CapeFeatureRenderer.class)
public abstract class cape {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void fhasix$hideVanillaCape(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            PlayerEntityRenderState state,
            float limbAngle,
            float limbDistance,
            CallbackInfo ci) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if ((FhasixCosmetics.isFirstOptixEquipped() || FhasixCosmetics.isSecondCapeEquipped())
                    && client.player != null && state.id == client.player.getId()) {
                ci.cancel();
            }
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("cape_mixin", t);
        }
    }
}
