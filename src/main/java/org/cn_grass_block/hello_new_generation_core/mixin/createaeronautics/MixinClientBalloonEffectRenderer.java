package org.cn_grass_block.hello_new_generation_core.mixin.createaeronautics;

import net.irisshaders.iris.api.v0.IrisApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the hot-air balloon "heated air" effect while an Iris shader pack is active.
 *
 * <p>Create Aeronautics renders the shimmering hot air inside a balloon via Veil's own render pipeline:
 * {@code ClientBalloonEffectRenderer.onRenderLevelStage} draws the heat overlay into a Veil {@code AdvancedFbo}
 * with the {@code hot_air_overlay} shader at {@code AFTER_SOLID_BLOCKS}, then composites it back onto the screen
 * through Veil's {@code soft_light} post-processing pipeline. That composite runs independently of Iris's deferred
 * pipeline, so when a shader pack owns the main gbuffer the overlay's raw colour is re-interpreted by Iris as an
 * emissive/bloom source — producing bright light-spot artifacts over the balloon.
 *
 * <p>Fix: cancel the whole effect pass at HEAD when {@code IrisApi.isShaderPackInUse()} is true. With no shader
 * pack the method runs unchanged. Same approach as {@link MixinAbstractLaserRenderer} (Simulated lasers).
 *
 * <p>Targeted by string ({@code remap = false}) because the method signature references Veil's
 * {@code VeilRenderLevelStageEvent$Stage}, which is not on our compile classpath; the HEAD injector needs no
 * method parameters, so only {@link CallbackInfo} is declared.
 */
@Mixin(targets = "dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.effect.ClientBalloonEffectRenderer", remap = false)
public abstract class MixinClientBalloonEffectRenderer {

    @Inject(
        method = "onRenderLevelStage(Lfoundry/veil/api/event/VeilRenderLevelStageEvent$Stage;Lorg/joml/Matrix4fc;Lorg/joml/Matrix4fc;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void hello_new_generation_core$skipUnderShaders(final CallbackInfo ci) {
        if (IrisApi.getInstance().isShaderPackInUse()) {
            ci.cancel();
        }
    }
}
