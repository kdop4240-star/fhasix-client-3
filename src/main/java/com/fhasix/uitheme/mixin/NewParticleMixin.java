package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewPerf;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Performance > Particles: drops particles before they are created (Small / Medium by type, High = all). */
@Mixin(ParticleManager.class)
public abstract class NewParticleMixin {

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void fhasix$skip(ParticleEffect effect, double x, double y, double z, double vx, double vy, double vz,
                             CallbackInfoReturnable<Particle> cir) {
        if (NewPerf.blockParticle(effect)) {
            cir.setReturnValue(null);
        }
    }

    /**
     * Block break / block crack particles (and emitters) are built directly and handed to this method,
     * so Particles High has to stop them here as well.
     */
    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void fhasix$skipDirect(Particle particle, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (NewPerf.blockAllParticles()) {
            ci.cancel();
        }
    }
}
