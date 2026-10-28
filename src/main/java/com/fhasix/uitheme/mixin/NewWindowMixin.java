package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewGuiScale;
import com.fhasix.uitheme.NewSafe;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** GUI scale lock: while a Fhasix screen is open the calculated GUI scale is always 2 (if the window is big enough). */
@Mixin(Window.class)
public abstract class NewWindowMixin {

    @Shadow
    public abstract int getFramebufferWidth();

    @Shadow
    public abstract int getFramebufferHeight();

    @Inject(method = "calculateScaleFactor", at = @At("RETURN"), cancellable = true, require = 0)
    private void fhasix$lockScale(int guiScale, boolean forceUnicodeFont, CallbackInfoReturnable<Integer> cir) {
        try {
            if (NewGuiScale.lockWanted() && getFramebufferWidth() / NewGuiScale.LOCKED >= 320
                    && getFramebufferHeight() / NewGuiScale.LOCKED >= 240) {
                cir.setReturnValue(NewGuiScale.LOCKED);
            }
        } catch (Exception | LinkageError t) {
            NewSafe.failed("gui_scale_window", t);
        }
    }
}
