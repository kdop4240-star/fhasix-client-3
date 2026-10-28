package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewDarkMode;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

/**
 * Hides the bundled Dark Mode pack from the Resource Packs screen. Only the lists the screen draws are
 * filtered - the pack itself stays loaded and keeps following the Dark Mode switch.
 */
@Mixin(ResourcePackOrganizer.class)
public abstract class NewPackListMixin {

    @Inject(method = "getEnabledPacks", at = @At("RETURN"), cancellable = true, require = 0)
    private void fhasix$hideEnabled(CallbackInfoReturnable<Stream<ResourcePackOrganizer.Pack>> cir) {
        try {
            cir.setReturnValue(cir.getReturnValue().filter(p -> !NewDarkMode.isHiddenPack(p.getDisplayName().getString())));
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("pack_list", t);
        }
    }

    @Inject(method = "getDisabledPacks", at = @At("RETURN"), cancellable = true, require = 0)
    private void fhasix$hideDisabled(CallbackInfoReturnable<Stream<ResourcePackOrganizer.Pack>> cir) {
        try {
            cir.setReturnValue(cir.getReturnValue().filter(p -> !NewDarkMode.isHiddenPack(p.getDisplayName().getString())));
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("pack_list", t);
        }
    }
}
