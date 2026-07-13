package org.cn_grass_block.hello_new_generation_core.code.station;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Game-bus event handlers for the TongDa Railway station-linkage feature.
 *
 * <p>Milestone 1 only wires up the debug command ({@link StationDebugCommand}). Later milestones will add the
 * main-thread chunk-load handler that places towns around captured stations.
 */
public final class StationEvents {

    private StationEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        StationDebugCommand.register(event.getDispatcher());
    }
}
