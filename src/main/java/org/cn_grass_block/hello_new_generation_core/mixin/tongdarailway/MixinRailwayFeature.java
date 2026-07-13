package org.cn_grass_block.hello_new_generation_core.mixin.tongdarailway;

import com.hxzhitang.tongdarailway.structure.StationTemplate;
import com.hxzhitang.tongdarailway.worldgen.RailwayFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.phys.Vec3;
import org.cn_grass_block.hello_new_generation_core.HelloNewGenerationCoreMod;
import org.cn_grass_block.hello_new_generation_core.code.station.StationRegistryData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TongDa Railway town-linkage hook (PoC milestone 1): capture every station's coordinate the moment TongDa
 * generates it.
 *
 * <p>{@code RailwayFeature.place()} iterates {@code RailwayMap.stations} and calls the private static
 * {@code placeStation(ChunkPos, Vec3, StationTemplate, WorldGenLevel)} for each station landing in the current
 * chunk. We inject at its TAIL — after the station blocks are written — and record the station's position, type
 * ordinal, id and exit count into a per-world {@link StationRegistryData}.
 *
 * <p>This is intentionally a PURE OBSERVER: it only reads the arguments already in hand and appends to an
 * in-memory list (flushed to SavedData by the normal save cycle). It performs NO block placement, NO chunk
 * loading and NO cross-chunk reads on the worldgen thread, so it cannot deadlock under C2ME. Actual town
 * placement is deferred to a main-thread chunk-load event in a later milestone.
 *
 * <p>Everything is wrapped in a catch-all: capturing a coordinate must never break TongDa's station generation.
 */
@Mixin(value = RailwayFeature.class, remap = false)
public class MixinRailwayFeature {

    @Inject(
        method = "placeStation",
        at = @At("TAIL"),
        remap = true
    )
    private static void hello_new_generation_core$captureStation(
            final ChunkPos chunkPos, final Vec3 center, final StationTemplate template,
            final WorldGenLevel level, final CallbackInfo ci) {
        try {
            if (template == null || center == null) {
                return;
            }
            // During worldgen the WorldGenLevel is a WorldGenRegion; getLevel() yields the owning ServerLevel.
            if (!(level instanceof final WorldGenRegion region)) {
                return;
            }
            final ServerLevel serverLevel = region.getLevel();
            final BlockPos pos = BlockPos.containing(center);
            final int typeOrdinal = template.getType() == null ? -1 : template.getType().ordinal();

            final boolean added = StationRegistryData.get(serverLevel)
                .addStation(pos, typeOrdinal, template.getId(), template.getExitCount());

            if (added) {
                HelloNewGenerationCoreMod.LOGGER.info(
                    "[hng-station] captured station id={} type={} exits={} at {}",
                    template.getId(), typeOrdinal, template.getExitCount(), pos);
            }
        } catch (final Throwable t) {
            HelloNewGenerationCoreMod.LOGGER.error("[hng-station] failed to capture station coordinate", t);
        }
    }
}
