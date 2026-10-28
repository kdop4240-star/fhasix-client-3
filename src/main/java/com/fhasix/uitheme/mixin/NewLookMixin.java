package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewCamera;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freelook: while the key is held, mouse movement turns the camera (NewCamera.look) instead of
 * the player, so the character keeps facing and moving the same way.
 */
@Mixin(Entity.class)
public abstract class NewLookMixin {

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true, require = 0)
    private void fhasix$freelook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        try {
            if (NewCamera.freelookActive() && (Object) this instanceof ClientPlayerEntity) {
                NewCamera.look(cursorDeltaX, cursorDeltaY);
                ci.cancel();
            }
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("look_mixin", t);
        }
    }
}
