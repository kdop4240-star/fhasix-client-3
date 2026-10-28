package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewPerf;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Performance > Culling: far entities are not drawn. */
@Mixin(EntityRenderer.class)
public abstract class NewEntityCullMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void fhasix$cull(Entity entity, Frustum frustum, double x, double y, double z,
                             CallbackInfoReturnable<Boolean> cir) {
        if (NewPerf.cullEntity(entity)) {
            cir.setReturnValue(false);
        }
    }
}
