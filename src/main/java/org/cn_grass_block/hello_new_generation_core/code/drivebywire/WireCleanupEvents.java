package org.cn_grass_block.hello_new_generation_core.code.drivebywire;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.cn_grass_block.hello_new_generation_core.HelloNewGenerationCoreMod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edn.stratodonut.drivebywire.wire.WireNetworkManager;
import edn.stratodonut.drivebywire.wire.graph.WireNetworkNode.WireNetworkSink;

/**
 * Game-bus event handlers that keep the Drive By Wire network clean.
 *
 * <ul>
 *   <li><b>Fix A</b> — {@link #onBlockBreak}: when a block is broken, drop every connection that used it as a
 *       source or sink so the network never leaks. Sable assembly moves are handled separately by Drive By
 *       Wire (via its {@code SubLevelAssemblyHelper} mixin / {@code handleAssemblyMove}) and do <em>not</em>
 *       fire {@link BlockEvent.BreakEvent}, so relocating an assembly will not wipe its connections here.</li>
 *   <li><b>Fix C</b> — {@link #onServerStarted}: a one-shot startup scan that removes any connection whose
 *       source or sink position no longer holds a block (air), repairing already-polluted saves.</li>
 * </ul>
 */
public final class WireCleanupEvents {

    private WireCleanupEvents() {
    }

    @SubscribeEvent
    public static void onBlockBreak(final BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof final ServerLevel level)) {
            return;
        }

        try {
            WireConnectionCleanup.removeAllAt(level, event.getPos());
        } catch (final Throwable t) {
            // Never let a cleanup failure cancel a block break.
            HelloNewGenerationCoreMod.LOGGER.error("[drivebywire-fix] Failed to clean wire connections on block break at {}", event.getPos(), t);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        WireFixCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStarted(final ServerStartedEvent event) {
        final MinecraftServer server = event.getServer();
        for (final ServerLevel level : server.getAllLevels()) {
            try {
                scanAndRepair(level);
            } catch (final Throwable t) {
                HelloNewGenerationCoreMod.LOGGER.error("[drivebywire-fix] Failed to scan/repair wire network for {}", level.dimension().location(), t);
            }
        }
    }

    /**
     * Drops connections whose source or sink position is loaded and empty (air). Positions in unloaded chunks
     * are left untouched to avoid forcing chunk loads and to avoid deleting connections we cannot verify.
     */
    private static void scanAndRepair(final ServerLevel level) {
        final WireNetworkManager manager = WireNetworkManager.get(level);
        if (manager == null) {
            return;
        }

        final Map<Long, Map<String, Set<WireNetworkSink>>> network = manager.getNetwork();
        if (network.isEmpty()) {
            return;
        }

        // Collect every distinct endpoint position referenced by the network.
        final List<BlockPos> deadPositions = new ArrayList<>();
        final java.util.HashSet<Long> seen = new java.util.HashSet<>();

        for (final Map.Entry<Long, Map<String, Set<WireNetworkSink>>> sourceEntry : network.entrySet()) {
            considerEndpoint(level, sourceEntry.getKey(), seen, deadPositions);
            for (final Set<WireNetworkSink> sinks : sourceEntry.getValue().values()) {
                for (final WireNetworkSink sink : sinks) {
                    considerEndpoint(level, sink.position(), seen, deadPositions);
                }
            }
        }

        int removed = 0;
        for (final BlockPos dead : deadPositions) {
            removed += WireConnectionCleanup.removeAllAt(level, dead);
        }

        if (removed > 0) {
            HelloNewGenerationCoreMod.LOGGER.info(
                "[drivebywire-fix] Repaired {} orphaned wire connection(s) at {} dead position(s) in {}",
                removed, deadPositions.size(), level.dimension().location()
            );
        }
    }

    private static void considerEndpoint(final Level level, final long key, final java.util.Set<Long> seen, final List<BlockPos> deadPositions) {
        if (!seen.add(key)) {
            return;
        }

        final BlockPos pos = BlockPos.of(key);
        // Only judge loaded chunks; unloaded positions might still hold a valid block.
        if (!level.isLoaded(pos)) {
            return;
        }

        final BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            deadPositions.add(pos);
        }
    }
}
