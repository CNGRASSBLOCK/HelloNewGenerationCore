package org.cn_grass_block.hello_new_generation_core.code.drivebywire;

import edn.stratodonut.drivebywire.wire.WireNetworkManager;
import edn.stratodonut.drivebywire.wire.graph.WireNetworkNode.WireNetworkSink;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WireConnectionCleanup {
    public static int removeAllAt(final Level level, final BlockPos pos) {
        if (level == null || level.isClientSide()) return 0;

        final WireNetworkManager manager = WireNetworkManager.get(level);
        if (manager == null) return 0;

        final long targetKey = pos.asLong();
        final Map<Long, Map<String, Set<WireNetworkSink>>> network = manager.getNetwork();
        final List<Connection> toRemove = new ArrayList<>();

        for (final Map.Entry<Long, Map<String, Set<WireNetworkSink>>> sourceEntry : network.entrySet()) {
            final long sourceKey = sourceEntry.getKey();
            final boolean sourceIsTarget = sourceKey == targetKey;

            for (final Map.Entry<String, Set<WireNetworkSink>> channelEntry : sourceEntry.getValue().entrySet()) {
                final String channel = channelEntry.getKey();
                for (final WireNetworkSink sink : channelEntry.getValue())
                    if (sourceIsTarget || sink.position() == targetKey)
                        toRemove.add(new Connection(BlockPos.of(sourceKey), BlockPos.of(sink.position()), Direction.from3DDataValue(sink.direction()), channel));
            }
        }

        int removed = 0;
        for (final Connection connection : toRemove)
            if (WireNetworkManager.removeConnection(level, connection.source, connection.sink, connection.direction, connection.channel))
                removed++;
        return removed;
    }

    /** Outcome of a full scan-and-repair pass over a single level. */
    public record ScanResult(int endpoints, int deadEndpoints, int removedConnections, int chunksLoaded) { }

    /**
     * Performs a full sweep of the network in {@code level}, force-loading every chunk that holds a referenced
     * endpoint so we can actually read the block state (the startup scan skipped unloaded chunks and so never
     * touched the bulk of the pollution, which sits in distant, unloaded chunks). Any endpoint whose block is
     * air is treated as dead and every connection touching it is removed.
     *
     * <p>Chunks are loaded one at a time via {@link Level#getChunk(int, int)} and grouped so each chunk is
     * touched at most once. This is intentionally synchronous and blocking: it is an admin-triggered repair,
     * not a hot path. On a heavily polluted save it may briefly load a large number of chunks.
     */
    public static ScanResult scanAndRepairForced(final Level level) {
        if (level == null || level.isClientSide()) return new ScanResult(0, 0, 0, 0);

        final WireNetworkManager manager = WireNetworkManager.get(level);
        if (manager == null) return new ScanResult(0, 0, 0, 0);

        final Map<Long, Map<String, Set<WireNetworkSink>>> network = manager.getNetwork();
        final Set<Long> endpointKeys = new LinkedHashSet<>();
        for (final Map.Entry<Long, Map<String, Set<WireNetworkSink>>> sourceEntry : network.entrySet()) {
            endpointKeys.add(sourceEntry.getKey());
            for (final Set<WireNetworkSink> sinks : sourceEntry.getValue().values())
                for (final WireNetworkSink sink : sinks)
                    endpointKeys.add(sink.position());
        }

        // Group endpoints by chunk so each chunk is force-loaded at most once.
        final Map<Long, List<BlockPos>> byChunk = new java.util.LinkedHashMap<>();
        for (final long key : endpointKeys) {
            final BlockPos pos = BlockPos.of(key);
            final long chunkKey = ChunkPos.asLong(pos);
            byChunk.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(pos);
        }

        final List<BlockPos> deadPositions = new ArrayList<>();
        int chunksLoaded = 0;
        for (final Map.Entry<Long, List<BlockPos>> chunkEntry : byChunk.entrySet()) {
            final ChunkPos chunkPos = new ChunkPos(chunkEntry.getKey());
            // getChunk(x, z) force-loads (and if necessary generates) the chunk synchronously.
            level.getChunk(chunkPos.x, chunkPos.z);
            chunksLoaded++;
            for (final BlockPos pos : chunkEntry.getValue()) {
                final BlockState state = level.getBlockState(pos);
                if (state.isAir()) deadPositions.add(pos);
            }
        }

        int removed = 0;
        for (final BlockPos dead : deadPositions) removed += removeAllAt(level, dead);

        return new ScanResult(endpointKeys.size(), deadPositions.size(), removed, chunksLoaded);
    }

    private record Connection(BlockPos source, BlockPos sink, Direction direction, String channel) { }
}
