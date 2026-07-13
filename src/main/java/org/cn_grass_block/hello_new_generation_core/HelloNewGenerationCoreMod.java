package org.cn_grass_block.hello_new_generation_core;

import com.mojang.logging.LogUtils;
import lombok.extern.slf4j.Slf4j;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import org.cn_grass_block.hello_new_generation_core.code.drivebywire.WireCleanupEvents;
import org.cn_grass_block.hello_new_generation_core.code.station.StationEvents;
import org.cn_grass_block.hello_new_generation_core.data.HelloNewGenerationCoreModDataManger;
import org.cn_grass_block.hello_new_generation_core.item.HelloNewGenerationCoreModItems;
import org.slf4j.Logger;

@Mod("hello_new_generation_core")
@Slf4j(topic = "HelloNewGenerationCore")
public class HelloNewGenerationCoreMod {

    public static final String MODID = "hello_new_generation_core";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HelloNewGenerationCoreMod(IEventBus modEventBus, ModContainer modContainer) {
        HelloNewGenerationCoreModDataManger.readJson();

        HelloNewGenerationCoreModItems.register(modEventBus);

        // Drive By Wire wire-pollution fix: reclaim orphaned connections on block break and on startup.
        NeoForge.EVENT_BUS.register(WireCleanupEvents.class);

        // TongDa Railway town-linkage (PoC): TEMPORARILY DISABLED while iterating on villageauto.
        // The MixinRailwayFeature registration is also removed from mixins.json for this build.
        // NeoForge.EVENT_BUS.register(StationEvents.class);
    }
}
