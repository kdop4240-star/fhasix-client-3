package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewCamera;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Camera mod: freelook rotation + third-person distance / shoulder / height offsets.
 * Each wrap changes one call inside Camera.update(...). require = 0 so a renamed call in a future
 * version just disables that part instead of crashing the game.
 */
@Mixin(Camera.class)
public abstract class NewCameraMixin {

    /** Freelook: while active the camera uses its own yaw/pitch instead of the player's. */
    @WrapOperation(method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"),
            require = 0)
    private void fhasix$rotation(Camera self, float yaw, float pitch, Operation<Void> original) {
        try {
            if (NewCamera.freelookActive()) {
                yaw = NewCamera.yaw();
                pitch = NewCamera.pitch();
            }
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("camera_mixin", t);
        }
        original.call(self, yaw, pitch);
    }

    /** Third-person distance. */
    @WrapOperation(method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"),
            require = 0)
    private float fhasix$distance(Camera self, float distance, Operation<Float> original) {
        float scale = 1f;
        try {
            scale = NewCamera.distanceScale();
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("camera_mixin", t);
        }
        return original.call(self, distance * scale);
    }

    /** Shoulder + height offset (only for the third-person "move backwards" call, whose first argument is negative). */
    @WrapOperation(method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;moveBy(FFF)V"),
            require = 0)
    private void fhasix$offset(Camera self, float x, float y, float z, Operation<Void> original) {
        try {
            if (x < 0f) {
                y += NewCamera.height();
                z -= NewCamera.shoulder(); // third argument moves toward the camera's LEFT, so subtract for "right"
            }
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("camera_mixin", t);
        }
        original.call(self, x, y, z);
    }
}
