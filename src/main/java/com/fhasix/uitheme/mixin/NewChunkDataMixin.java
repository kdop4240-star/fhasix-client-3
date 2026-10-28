package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewPerf;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Performance > Lazy Chunk Loading: far chunk packets are applied later (see NewPerf.holdChunk). */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class NewChunkDataMixin {

    @Inject(method = "onChunkData", at = @At("HEAD"), cancellable = true, require = 0)
    private void fhasix$lazy(ChunkDataS2CPacket packet, CallbackInfo ci) {
        if (NewPerf.holdChunk((ClientPlayNetworkHandler) (Object) this, packet)) {
            ci.cancel();
        }
    }

    @Inject(method = "onUnloadChunk", at = @At("HEAD"), require = 0)
    private void fhasix$unload(UnloadChunkS2CPacket packet, CallbackInfo ci) {
        NewPerf.dropHeld(packet.pos().x, packet.pos().z);
    }
}
