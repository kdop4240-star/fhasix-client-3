package com.fhasix.uitheme;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.EntityType;

/**
 * As of the 1.21.2+ entity-render-state rewrite, FeatureRenderer's first type
 * parameter is the render STATE (PlayerEntityRenderState), not the live
 * entity (AbstractClientPlayerEntity) the way it was on 1.20.1. The
 * raw-type double-cast below is unchanged in shape from the 1.20.1 source;
 * only the target generic types moved from <AbstractClientPlayerEntity, ...>
 * to <PlayerEntityRenderState, ...>.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class e5 implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ScreenshotManager.init();
        NewHud.init(); // v30: Right Shift launcher + FPS/CPS HUD
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
            if (entityType == EntityType.PLAYER && entityRenderer instanceof PlayerEntityRenderer) {
                registrationHelper.register(new FhasixCapeFeatureRenderer(
                        (FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel>) (FeatureRendererContext) entityRenderer
                ));
            }
        });
    }
}
