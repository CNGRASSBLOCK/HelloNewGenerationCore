package org.cn_grass_block.hello_new_generation_core.mixin.cbc;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * cbcmoreshells 2.1.0 — {@code CbcmoreshellsNeoForge.onRegisterSounds} has
 * two lambdas at the same source line both calling
 * {@code CBCMSSoundEvents.register}, causing every sound event to be
 * submitted to the registry twice.  The second pass throws
 * {@code IllegalStateException: Adding duplicate key 'cbcmoreshells:sonar_pulse'}
 * (and every other sound), aborting mod loading entirely.
 *
 * <p>Fix: guard {@code CBCMSSoundEvents.register} with a static once-flag so
 * the second invocation is silently skipped.
 */
@Mixin(targets = "com.cainiao1053.cbcmoreshells.index.CBCMSSoundEvents", remap = false)
public abstract class MixinCBCMSSoundEvents {

    private static boolean hello_new_generation_core$soundsRegistered = false;

    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private static void hello_new_generation_core$preventDuplicateRegistration(
            Consumer<?> consumer, CallbackInfo ci) {
        if (hello_new_generation_core$soundsRegistered) {
            ci.cancel();
        } else {
            hello_new_generation_core$soundsRegistered = true;
        }
    }
}
