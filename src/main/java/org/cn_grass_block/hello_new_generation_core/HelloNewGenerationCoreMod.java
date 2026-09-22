package org.cn_grass_block.hello_new_generation_core;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import org.cn_grass_block.hello_new_generation_core.code.drivebywire.WireCleanupEvents;
import org.slf4j.Logger;

/**
 * Bug fixes for the mods this pack runs.
 *
 * <p>This mod used to hold the blueprint placement feature too — the schematic folder scan, one blueprint
 * item per file, and the item that placed Create / Sable / toolgun blueprints, plus the two Create mixins
 * that made it work. That feature has moved to {@code hello_new_journautics_core}, which is the pack's
 * gameplay mod, so the split is now clean: <b>this mod patches other people's mods, that one implements
 * gameplay.</b>
 *
 * <p>What a pack needs to know after the move:
 * <ul>
 *   <li>blueprint items are {@code hello_new_journautics_core:<file name>} now, not
 *       {@code hello_new_generation_core:<file name>} — quests, shops and market configs that hand one out
 *       need that one rename;</li>
 *   <li>the blueprint folders are read from {@code config/hello_new_journautics_core/schematics/<kind>/};
 *       the old {@code config/hello_new_generation_core/schematics/} tree is still read as a fallback and
 *       logged when it is used, so an unmigrated pack keeps working;</li>
 *   <li>that mod also accepts the old owner marker on existing blueprint stacks, so a blueprint already in
 *       somebody's chest still loads its file.</li>
 * </ul>
 */
@Mod("hello_new_generation_core")
public class HelloNewGenerationCoreMod {

    public static final String MODID = "hello_new_generation_core";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HelloNewGenerationCoreMod(IEventBus modEventBus, ModContainer modContainer) {
        // 蓝图放置 moved to hello_new_journautics_core (ShipBlueprintCatalog + ShipBlueprintItems there).
        // Nothing of it is registered here any more: the items, the folder scan and the two Create mixins
        // all live on that side now. Keep it that way — two mods registering the same blueprint items is
        // two items with one name, in two namespaces, which is a support question nobody can answer.

        // Drive By Wire wire-pollution fix: reclaim orphaned connections on block break and on startup.
        NeoForge.EVENT_BUS.register(WireCleanupEvents.class);

        // TongDa Railway town-linkage (PoC): TEMPORARILY DISABLED while iterating on villageauto.
        // The MixinRailwayFeature registration is also removed from mixins.json for this build.
        // NeoForge.EVENT_BUS.register(StationEvents.class);
    }
}
