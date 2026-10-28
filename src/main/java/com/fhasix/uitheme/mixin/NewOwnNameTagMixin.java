package com.fhasix.uitheme.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.EntityAttachmentType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The REAL Minecraft name tag, for you only: vanilla never draws the name tag of the player you are
 * playing, so hasLabel() is forced to true for the local player (third person then shows the normal
 * name tag, with the vanilla background, text and see-through look). The Fhasix F logo - a glyph of
 * the default font, see assets/minecraft/font/default.json - is put in front of the name. Default
 * feature: no setting. Other players and mobs are not touched.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class NewOwnNameTagMixin {

    /** Private-use character that the bundled font maps to the Fhasix F logo. */
    private static final String LOGO = "\ue9a1";

    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void fhasix$ownLabel(LivingEntity entity, double squaredDistance, CallbackInfoReturnable<Boolean> cir) {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (entity == c.player && !c.options.hudHidden) {
                cir.setReturnValue(true);
            }
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("own_nametag", t);
        }
    }

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"), require = 0)
    private void fhasix$ownLogo(LivingEntity entity, LivingEntityRenderState state, float tickProgress, CallbackInfo ci) {
        try {
            if (entity != MinecraftClient.getInstance().player) {
                return;
            }
            if (state.nameLabelPos == null) {
                state.nameLabelPos = entity.getAttachments().getPointNullable(
                        EntityAttachmentType.NAME_TAG, 0, entity.getLerpedYaw(tickProgress));
            }
            if (state.nameLabelPos == null) {
                return; // no position for a tag: leave the state alone
            }
            Text name = state.displayName != null ? state.displayName : entity.getName();
            if (!name.getString().startsWith(LOGO)) {
                state.displayName = Text.literal(LOGO + " ").append(name);
            }
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("own_nametag", t);
        }
    }
}
