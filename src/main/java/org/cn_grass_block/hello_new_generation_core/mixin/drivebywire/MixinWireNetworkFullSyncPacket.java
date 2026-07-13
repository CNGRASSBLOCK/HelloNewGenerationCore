package org.cn_grass_block.hello_new_generation_core.mixin.drivebywire;

import edn.stratodonut.drivebywire.network.WireNetworkFullSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import org.cn_grass_block.hello_new_generation_core.code.drivebywire.WireSyncGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fix B: guard the per-second full network sync so a bloated network can never disconnect players.
 *
 * <p>While a player holds a Wire or Wire Cutter, the client requests a full network sync every 20 ticks. The
 * server answers by serializing the entire network into one {@code CompoundTag} carried by a
 * {@code ByteBufCodecs.COMPOUND_TAG} payload, which enforces a 2 MiB {@code NbtAccounter} ceiling. Once an
 * (orphaned) network exceeds that limit the packet fails to encode/decode and the player is kicked.
 *
 * <p>The actual measurement lives in {@link WireSyncGuard} (a regular package): a mixin-package class cannot be
 * referenced directly at runtime. This mixin only wires the guard into {@code sendTo}.
 */
@Mixin(value = WireNetworkFullSyncPacket.class, remap = false)
public class MixinWireNetworkFullSyncPacket {

    @Inject(method = "sendTo", at = @At("HEAD"), cancellable = true)
    private static void hello_new_generation_core$guardSyncSize(final ServerPlayer player, final CallbackInfo ci) {
        if (WireSyncGuard.shouldSkipFullSync(player)) {
            ci.cancel();
        }
    }
}
