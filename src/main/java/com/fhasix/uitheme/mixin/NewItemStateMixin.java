package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewScaleHolder;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives every dropped-item render state a scale field (read/written by NewItemEntityMixin). */
@Mixin(ItemEntityRenderState.class)
public abstract class NewItemStateMixin implements NewScaleHolder {

    @Unique
    private float fhasix$scale = 1f;

    @Override
    public float fhasix$getScale() {
        return fhasix$scale;
    }

    @Override
    public void fhasix$setScale(float scale) {
        fhasix$scale = scale;
    }
}
