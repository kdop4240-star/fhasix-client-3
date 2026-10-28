package com.fhasix.uitheme.mixin;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SplashOverlay.class)
public abstract class loading {
    private static final Identifier FHASIX_LOADING =
            Identifier.of("fhasixuitheme", "textures/gui/loading/custom_loading.png");

    private static final int TEXTURE_WIDTH = 1536;
    private static final int TEXTURE_HEIGHT = 691;

    @Inject(method = "render", at = @At("TAIL"))
    private void fhasix$renderCustomLoading(
            DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (com.fhasix.uitheme.NewSafe.disabled("loading_screen")) {
            return;
        }
        try {
            fhasix$drawLoading(context);
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("loading_screen", t); // the vanilla loading screen stays
        }
    }

    private void fhasix$drawLoading(DrawContext context) {

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        // Draw after vanilla SplashOverlay logic so Minecraft can still finish
        // the resource reload and remove the overlay normally.
        // Keep the supplied artwork's proportions. The artwork has a black
        // background, so any small letterbox area remains visually seamless.
        float scale = Math.min(
                (float) screenWidth / TEXTURE_WIDTH,
                (float) screenHeight / TEXTURE_HEIGHT
        );

        int drawWidth = Math.round(TEXTURE_WIDTH * scale);
        int drawHeight = Math.round(TEXTURE_HEIGHT * scale);
        int x = (screenWidth - drawWidth) / 2;
        int y = (screenHeight - drawHeight) / 2;

        context.fill(0, 0, screenWidth, screenHeight, 0xFF000000);
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                FHASIX_LOADING,
                x, y,
                0, 0,
                drawWidth, drawHeight,
                TEXTURE_WIDTH, TEXTURE_HEIGHT,
                TEXTURE_WIDTH, TEXTURE_HEIGHT
        );

    }
}
