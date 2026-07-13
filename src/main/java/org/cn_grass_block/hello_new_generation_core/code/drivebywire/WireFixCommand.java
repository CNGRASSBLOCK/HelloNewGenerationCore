package org.cn_grass_block.hello_new_generation_core.code.drivebywire;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.cn_grass_block.hello_new_generation_core.HelloNewGenerationCoreMod;

/**
 * Admin command for the Drive By Wire wire-pollution fix.
 *
 * <ul>
 *   <li>{@code /dbwfix scan} — full sweep of the <em>current</em> level: force-loads every chunk that holds a
 *       referenced wire endpoint, drops connections whose endpoint block is air, and reports a summary.</li>
 *   <li>{@code /dbwfix scanall} — same, but across every loaded dimension.</li>
 * </ul>
 *
 * <p>Unlike the startup scan ({@link WireCleanupEvents#onServerStarted}), this deliberately force-loads
 * distant chunks so it can verify endpoints that the startup scan skipped — which is where the bulk of the
 * pollution lives. Requires permission level 2 (op).
 */
public final class WireFixCommand {

    private WireFixCommand() {
    }

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        final LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("dbwfix")
            .requires(source -> source.hasPermission(2));

        root.then(Commands.literal("scan").executes(ctx -> {
            final CommandSourceStack source = ctx.getSource();
            final ServerLevel level = source.getLevel();
            scanLevel(source, level);
            return 1;
        }));

        root.then(Commands.literal("scanall").executes(ctx -> {
            final CommandSourceStack source = ctx.getSource();
            int total = 0;
            for (final ServerLevel level : source.getServer().getAllLevels()) {
                total += scanLevel(source, level);
            }
            final int finalTotal = total;
            source.sendSuccess(() -> Component.literal(
                "[drivebywire-fix] scanall complete: removed " + finalTotal + " orphaned connection(s) across all dimensions."), true);
            return 1;
        }));

        dispatcher.register(root);
    }

    /** Runs a forced scan on one level, reports to the command source and the log, and returns connections removed. */
    private static int scanLevel(final CommandSourceStack source, final ServerLevel level) {
        final String dim = level.dimension().location().toString();
        source.sendSuccess(() -> Component.literal("[drivebywire-fix] Scanning " + dim + " (this force-loads chunks, may take a moment)..."), false);

        final WireConnectionCleanup.ScanResult result;
        try {
            result = WireConnectionCleanup.scanAndRepairForced(level);
        } catch (final Throwable t) {
            HelloNewGenerationCoreMod.LOGGER.error("[drivebywire-fix] Forced scan failed for {}", dim, t);
            source.sendFailure(Component.literal("[drivebywire-fix] Scan failed for " + dim + ": " + t));
            return 0;
        }

        final String summary = String.format(
            "[drivebywire-fix] %s: %d endpoint(s), %d chunk(s) loaded, %d dead endpoint(s), %d connection(s) removed.",
            dim, result.endpoints(), result.chunksLoaded(), result.deadEndpoints(), result.removedConnections());
        HelloNewGenerationCoreMod.LOGGER.info(summary);
        source.sendSuccess(() -> Component.literal(summary), true);
        return result.removedConnections();
    }
}
