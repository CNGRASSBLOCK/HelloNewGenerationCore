package org.cn_grass_block.hello_new_generation_core.code.drivebywire;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import org.cn_grass_block.hello_new_generation_core.HelloNewGenerationCoreMod;

import java.io.DataOutputStream;
import java.io.OutputStream;

import edn.stratodonut.drivebywire.wire.WireNetworkManager;

/**
 * Helper for {@code MixinWireNetworkFullSyncPacket} (Fix B). Lives in a regular (non-mixin) package because
 * classes inside a declared mixin package cannot be referenced directly at runtime — doing so throws
 * {@code IllegalClassLoadError} when the target class is transformed.
 *
 * <p>Measures the serialized size of the wire network and decides whether the per-second full sync is safe to
 * send, so a bloated/orphaned network can never exceed the {@code COMPOUND_TAG} codec ceiling and kick players.
 */
public final class WireSyncGuard {

    // 1 MiB — comfortably under the 2 MiB COMPOUND_TAG codec ceiling.
    private static final int MAX_SAFE_SYNC_BYTES = 1024 * 1024;

    private WireSyncGuard() {
    }

    /**
     * @return {@code true} if the full-sync packet to this player should be skipped (network too large).
     */
    public static boolean shouldSkipFullSync(final ServerPlayer player) {
        try {
            final CompoundTag tag = WireNetworkManager.get(player.serverLevel()).save(new CompoundTag());
            final int bytes = estimateNbtSize(tag);
            if (bytes > MAX_SAFE_SYNC_BYTES) {
                HelloNewGenerationCoreMod.LOGGER.warn(
                    "[drivebywire-fix] Skipping wire network full-sync to {}: {} bytes exceeds the {} byte safe limit. "
                        + "The network likely still contains orphaned connections; break the involved blocks or restart to trigger the startup scan.",
                    player.getGameProfile().getName(), bytes, MAX_SAFE_SYNC_BYTES
                );
                return true;
            }
        } catch (final Throwable t) {
            // If measuring fails, fall through to vanilla behaviour rather than break syncing.
            HelloNewGenerationCoreMod.LOGGER.error("[drivebywire-fix] Failed to measure wire network sync size", t);
        }
        return false;
    }

    private static int estimateNbtSize(final CompoundTag tag) throws java.io.IOException {
        final CountingOutputStream counter = new CountingOutputStream();
        // The small constant overhead of the (empty) tag name is irrelevant at a 1 MiB threshold.
        NbtIo.write(tag, new DataOutputStream(counter));
        return counter.count;
    }

    private static final class CountingOutputStream extends OutputStream {
        private int count;

        @Override
        public void write(final int b) {
            count++;
        }

        @Override
        public void write(final byte[] b, final int off, final int len) {
            count += len;
        }
    }
}
