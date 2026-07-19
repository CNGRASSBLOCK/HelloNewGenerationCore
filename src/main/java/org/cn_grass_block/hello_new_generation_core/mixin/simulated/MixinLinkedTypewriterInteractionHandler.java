package org.cn_grass_block.hello_new_generation_core.mixin.simulated;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * simulated 1.3.0 — {@code LinkedTypewriterInteractionHandler.onKeyPress}
 * guards its body with {@code minecraft.player != null} but not
 * {@code minecraft.level != null}.  When the server crashes while a player
 * is using a linked typewriter, the client begins disconnecting
 * ({@code Minecraft.disconnect()}), but {@code player} remains transiently
 * non-null.  A key event (e.g. ESC) fires during the same tick, the method
 * body runs, and {@code minecraft.setScreen(null)} throws
 * {@code IllegalStateException: Trying to return to in-game GUI during
 * disconnection} because {@code level} is already null.
 *
 * <p>Fix: cancel early if {@code minecraft.level == null}, which is the
 * authoritative signal that the client is no longer in a live game session.
 */
@Mixin(
    targets = "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterInteractionHandler",
    remap = false
)
public abstract class MixinLinkedTypewriterInteractionHandler {

    @Inject(method = "onKeyPress", at = @At("HEAD"), cancellable = true)
    private static void hello_new_generation_core$guardDisconnecting(
            int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (Minecraft.getInstance().level == null) {
            ci.cancel();
        }
    }
}
