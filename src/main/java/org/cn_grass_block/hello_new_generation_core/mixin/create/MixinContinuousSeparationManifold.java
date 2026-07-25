package org.cn_grass_block.hello_new_generation_core.mixin.create;

import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create 6.0.10 ContinuousOBBCollider.ContinuousSeparationManifold.
 *
 * axis / normalAxis start null after reset() and are only assigned inside
 * separate() when the tested axis has distance != 0.0. When two colliders have
 * coincident centers (distance 0 on every axis) but still register as colliding,
 * ContinuousOBBCollider.collideMany dereferences the still-null mf.axis at
 * line 153 (collisionResponseX += mf.axis.x * sep) and crashes the server tick
 * with an NPE.
 *
 * Default both axes to Vec3.ZERO after reset() so the degenerate case yields a
 * zero collision response (a safe no-op push) instead of a crash.
 */
@Mixin(targets = "com.simibubi.create.foundation.collision.ContinuousOBBCollider$ContinuousSeparationManifold", remap = false)
public abstract class MixinContinuousSeparationManifold {

    @Shadow
    Vec3 axis;

    @Shadow
    Vec3 normalAxis;

    @Inject(method = "reset", at = @At("TAIL"), remap = false)
    private void hello_new_generation_core$avoidNullAxis(CallbackInfo ci) {
        this.axis = Vec3.ZERO;
        this.normalAxis = Vec3.ZERO;
    }
}
