package org.cn_grass_block.hello_new_generation_core.code.drivebywire;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import org.cn_grass_block.hello_new_generation_core.HelloNewGenerationCoreMod;

import java.io.DataOutputStream;
import java.io.OutputStream;

import edn.stratodonut.drivebywire.wire.WireNetworkManager;

public final class WireSyncGuard {
    private static final int MAX_SAFE_SYNC_BYTES = 1024 * 1024;

    public static boolean shouldSkipFullSync(final ServerPlayer player) {
        try {
            final CompoundTag tag = WireNetworkManager.get(player.serverLevel()).save(new CompoundTag());
            final int bytes = estimateNbtSize(tag);
            if (bytes > MAX_SAFE_SYNC_BYTES) {
                HelloNewGenerationCoreMod.LOGGER.warn("[drivebywire-fix] Skipping wire network full-sync to {}: {} bytes exceeds the {} byte safe limit. " + "The network likely still contains orphaned connections; break the involved blocks or restart to trigger the startup scan.", player.getGameProfile().getName(), bytes, MAX_SAFE_SYNC_BYTES);
                return true;
            }
        } catch (final Throwable t) {
            HelloNewGenerationCoreMod.LOGGER.error("[drivebywire-fix] Failed to measure wire network sync size", t);
        }
        return false;
    }

    private static int estimateNbtSize(final CompoundTag tag) throws java.io.IOException {
        final CountingOutputStream counter = new CountingOutputStream();
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
