package org.cn_grass_block.hello_new_generation_core.code.station;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.cn_grass_block.hello_new_generation_core.HelloNewGenerationCoreMod;
import org.cn_grass_block.hello_new_generation_core.code.station.StationRegistryData.StationRecord;

import java.util.List;

/**
 * Admin/debug command for the TongDa Railway station linkage PoC.
 *
 * <ul>
 *   <li>{@code /hngstation list} — list every station captured in the current dimension.</li>
 *   <li>{@code /hngstation nearest} — report the captured station closest to the command source.</li>
 *   <li>{@code /hngstation tp} — teleport the command source to the nearest captured station.</li>
 *   <li>{@code /hngstation village [structureId]} — generate a village beside the nearest station
 *       (default {@code minecraft:village_plains}); milestone-2 Jigsaw test.</li>
 *   <li>{@code /hngstation villagehere [structureId]} — generate a village at the command source position
 *       (isolates "does placement work" from station coordinates).</li>
 * </ul>
 *
 * <p>Requires permission 2. The village commands manually drive {@code Structure.generate} +
 * {@code StructureStart.placeInChunk} on the main thread (the same recipe vanilla {@code /place structure} uses),
 * so the target chunks are already loaded and there is no worldgen-thread deadlock risk.
 */
public final class StationDebugCommand {

    /** Horizontal offset (X) from a station where {@code village} drops the test village. PoC: ignores facing. */
    private static final int VILLAGE_OFFSET = 40;
    private static final ResourceLocation DEFAULT_VILLAGE =
        ResourceLocation.fromNamespaceAndPath("minecraft", "village_plains");

    /**
     * Village structures tried by {@code villageauto}, in order. Each is overridden by Lukis Grand Capitals to a
     * grand-capital pool. We try them in turn and keep the first whose structure validates at the target site —
     * the structure's own biome restriction does the biome matching for us, so no hardcoded biome map is needed.
     */
    private static final ResourceLocation[] AUTO_VILLAGES = {
        ResourceLocation.fromNamespaceAndPath("minecraft", "village_plains"),
        ResourceLocation.fromNamespaceAndPath("minecraft", "village_desert"),
        ResourceLocation.fromNamespaceAndPath("minecraft", "village_savanna"),
        ResourceLocation.fromNamespaceAndPath("minecraft", "village_snowy"),
        ResourceLocation.fromNamespaceAndPath("minecraft", "village_taiga"),
    };

    /**
     * Lukis Grand Capitals start pools tried by {@code villageauto}, in order. We use the supported
     * {@link net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement#generateJigsaw} path (same as
     * {@code /place jigsaw}) which force-places a pool at an exact position — unlike {@code Structure.generate},
     * it does not run the structure's spread/placement validation, so it actually works at arbitrary coordinates.
     * The pool's own templates only fit certain biomes visually, but placement itself is unconditional; we try
     * plains first (most common) then the others.
     */
    private static final ResourceLocation[] AUTO_START_POOLS = {
        ResourceLocation.fromNamespaceAndPath("revampedvillages", "start"),
        ResourceLocation.fromNamespaceAndPath("revampedvillages", "desert/start"),
        ResourceLocation.fromNamespaceAndPath("revampedvillages", "savanna/start"),
        ResourceLocation.fromNamespaceAndPath("revampedvillages", "snowy/start"),
        ResourceLocation.fromNamespaceAndPath("revampedvillages", "taiga/start"),
    };

    /** Jigsaw block name marking the downward start connector in Lukis/vanilla village centre templates. */
    private static final ResourceLocation START_JIGSAW = ResourceLocation.fromNamespaceAndPath("minecraft", "bottom");

    /** Jigsaw recursion depth (matches Lukis village_plains size=5). */
    private static final int VILLAGE_MAX_DEPTH = 6;

    /** Town centre offset from a station so the station sits on the town edge (Lukis radius ~80). */
    private static final int TOWN_OFFSET = 95;

    private StationDebugCommand() {
    }

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        final LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("hngstation")
            // DEV: permission 0 so /hngstation works without enabling cheats on the save. Raise to 2 before release.
            .requires(source -> source.hasPermission(0));

        root.then(Commands.literal("list").executes(ctx -> {
            final CommandSourceStack source = ctx.getSource();
            final ServerLevel level = source.getLevel();
            final List<StationRecord> stations = StationRegistryData.get(level).getStations();
            if (stations.isEmpty()) {
                source.sendSuccess(() -> Component.literal(
                    "[hng-station] no stations captured yet in " + level.dimension().location()), false);
                return 0;
            }
            source.sendSuccess(() -> Component.literal(
                "[hng-station] " + stations.size() + " station(s) captured in " + level.dimension().location() + ":"), false);
            for (final StationRecord s : stations) {
                source.sendSuccess(() -> Component.literal(
                    "  id=" + s.stationId() + " type=" + s.typeOrdinal() + " exits=" + s.exitCount()
                        + " @ " + s.pos().getX() + "," + s.pos().getY() + "," + s.pos().getZ()), false);
            }
            return stations.size();
        }));

        root.then(Commands.literal("nearest").executes(ctx -> {
            final CommandSourceStack source = ctx.getSource();
            final StationRecord nearest = findNearest(source.getLevel(), source.getPosition());
            if (nearest == null) {
                source.sendSuccess(() -> Component.literal("[hng-station] no stations captured yet"), false);
                return 0;
            }
            final double dist = Math.sqrt(source.getPosition().distanceToSqr(Vec3.atCenterOf(nearest.pos())));
            source.sendSuccess(() -> Component.literal(String.format(
                "[hng-station] nearest: id=%d type=%d exits=%d @ %d,%d,%d (%.1f blocks away)",
                nearest.stationId(), nearest.typeOrdinal(), nearest.exitCount(),
                nearest.pos().getX(), nearest.pos().getY(), nearest.pos().getZ(), dist)), false);
            return 1;
        }));

        root.then(Commands.literal("tp").executes(ctx -> {
            final CommandSourceStack source = ctx.getSource();
            final ServerLevel level = source.getLevel();
            final StationRecord nearest = findNearest(level, source.getPosition());
            if (nearest == null) {
                source.sendSuccess(() -> Component.literal("[hng-station] no stations captured yet"), false);
                return 0;
            }
            final Entity entity = source.getEntity();
            if (entity == null) {
                source.sendSuccess(() -> Component.literal("[hng-station] tp requires an entity command source"), false);
                return 0;
            }
            final BlockPos p = nearest.pos();
            entity.teleportTo(level, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, java.util.Set.of(), entity.getYRot(), entity.getXRot());
            source.sendSuccess(() -> Component.literal(String.format(
                "[hng-station] teleported to station id=%d @ %d,%d,%d",
                nearest.stationId(), p.getX(), p.getY(), p.getZ())), false);
            return 1;
        }));

        root.then(Commands.literal("village")
            .executes(ctx -> runVillageAtStation(ctx.getSource(), DEFAULT_VILLAGE))
            .then(Commands.argument("structureId", StringArgumentType.string())
                .executes(ctx -> runVillageAtStation(
                    ctx.getSource(), ResourceLocation.parse(StringArgumentType.getString(ctx, "structureId"))))));

        root.then(Commands.literal("villagehere")
            .executes(ctx -> runVillageHere(ctx.getSource(), DEFAULT_VILLAGE))
            .then(Commands.argument("structureId", StringArgumentType.string())
                .executes(ctx -> runVillageHere(
                    ctx.getSource(), ResourceLocation.parse(StringArgumentType.getString(ctx, "structureId"))))));

        root.then(Commands.literal("villageauto").executes(ctx -> runVillageAuto(ctx.getSource())));

        dispatcher.register(root);
    }

    /** Nearest captured station to {@code from}, or null if none captured. */
    private static StationRecord findNearest(final ServerLevel level, final Vec3 from) {
        final List<StationRecord> stations = StationRegistryData.get(level).getStations();
        StationRecord best = null;
        double bestSq = Double.MAX_VALUE;
        for (final StationRecord s : stations) {
            final double dSq = from.distanceToSqr(Vec3.atCenterOf(s.pos()));
            if (dSq < bestSq) {
                bestSq = dSq;
                best = s;
            }
        }
        return best;
    }

    private static int runVillageAtStation(final CommandSourceStack source, final ResourceLocation structureId) {
        final ServerLevel level = source.getLevel();
        final StationRecord nearest = findNearest(level, source.getPosition());
        if (nearest == null) {
            source.sendSuccess(() -> Component.literal("[hng-station] no stations captured yet"), false);
            return 0;
        }
        final BlockPos target = nearest.pos().offset(VILLAGE_OFFSET, 0, 0);
        return placeStructure(source, level, target, structureId);
    }

    private static int runVillageHere(final CommandSourceStack source, final ResourceLocation structureId) {
        return placeStructure(source, source.getLevel(), BlockPos.containing(source.getPosition()), structureId);
    }

    /**
     * Milestone-3 core: at the nearest station, place a town on its edge, auto-selecting the village type that
     * fits the local biome. Tries each candidate in {@link #AUTO_VILLAGES}; the first whose structure validates
     * at the target site wins (the structure's own biome restriction performs the matching). Reports which one.
     */
    private static int runVillageAuto(final CommandSourceStack source) {
        final ServerLevel level = source.getLevel();
        final StationRecord nearest = findNearest(level, source.getPosition());
        if (nearest == null) {
            source.sendSuccess(() -> Component.literal("[hng-station] no stations captured yet"), false);
            return 0;
        }
        final BlockPos base = nearest.pos().offset(TOWN_OFFSET, 0, 0);
        final int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, base.getX(), base.getZ());
        final BlockPos target = new BlockPos(base.getX(), surfaceY, base.getZ());
        final var biomeHolder = level.getBiome(target);
        final String biomeName = biomeHolder.unwrapKey().map(k -> k.location().toString()).orElse("?");
        HelloNewGenerationCoreMod.LOGGER.info("[hng-station] villageauto target {},{} surfaceY={} biome={}",
            target.getX(), target.getZ(), surfaceY, biomeName);
        for (final ResourceLocation poolId : AUTO_START_POOLS) {
            if (tryPlaceVillagePool(source, level, target, poolId)) {
                return 1;
            }
        }
        source.sendSuccess(() -> Component.literal(
            "[hng-station] no village pool could be placed at " + target.getX() + "," + target.getZ()), false);
        return 0;
    }

    /**
     * Force-place a Lukis village start pool at {@code pos} using {@code JigsawPlacement.generateJigsaw} — the
     * same routine {@code /place jigsaw} uses. Returns false if the pool is unknown or placement failed, so the
     * caller can try the next candidate. On success the village is written to the world immediately.
     */
    private static boolean tryPlaceVillagePool(final CommandSourceStack source, final ServerLevel level,
                                               final BlockPos pos, final ResourceLocation poolId) {
        try {
            final ResourceKey<net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool> key =
                ResourceKey.create(Registries.TEMPLATE_POOL, poolId);
            final Holder<net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool> pool =
                level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL).getHolder(key).orElse(null);
            if (pool == null) {
                HelloNewGenerationCoreMod.LOGGER.info("[hng-station]   pool {} not found", poolId);
                return false;
            }
            final boolean ok = net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement.generateJigsaw(
                level, pool, START_JIGSAW, VILLAGE_MAX_DEPTH, pos, false);
            if (!ok) {
                HelloNewGenerationCoreMod.LOGGER.info("[hng-station]   pool {} -> generateJigsaw failed", poolId);
                return false;
            }
            source.sendSuccess(() -> Component.literal(
                "[hng-station] placed village pool " + poolId + " on town edge @ "
                    + pos.getX() + "," + pos.getY() + "," + pos.getZ()), true);
            return true;
        } catch (final Throwable t) {
            HelloNewGenerationCoreMod.LOGGER.error("[hng-station] error placing pool {} at {}", poolId, pos, t);
            return false;
        }
    }

    /**
     * Attempt to generate {@code structureId} at {@code pos}. Returns false (no message) if the structure is
     * unknown or fails biome/terrain validation, so callers can fall through to the next candidate. On success
     * it places the structure and reports it. Never throws.
     */
    private static boolean tryPlaceStructure(final CommandSourceStack source, final ServerLevel level,
                                             final BlockPos pos, final ResourceLocation structureId) {
        try {
            final ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, structureId);
            final Holder<Structure> holder = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE).getHolder(key).orElse(null);
            if (holder == null) {
                return false;
            }
            final Structure structure = holder.value();
            final ChunkGenerator gen = level.getChunkSource().getGenerator();
            final StructureStart start = structure.generate(
                level.registryAccess(), gen, gen.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(),
                level.getSeed(), new ChunkPos(pos), 0, level, b -> true);
            if (!start.isValid()) {
                HelloNewGenerationCoreMod.LOGGER.info("[hng-station]   {} -> INVALID_START (no generation point)", structureId);
                return false;
            }
            placeStart(level, gen, start);
            final BoundingBox bb = start.getBoundingBox();
            source.sendSuccess(() -> Component.literal(String.format(
                "[hng-station] auto-placed %s on town edge, bbox %d,%d,%d -> %d,%d,%d",
                structureId, bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ())), true);
            return true;
        } catch (final Throwable t) {
            HelloNewGenerationCoreMod.LOGGER.error("[hng-station] error trying {} at {}", structureId, pos, t);
            return false;
        }
    }

    /** Write a validated StructureStart into every chunk its bounding box covers. */
    private static void placeStart(final ServerLevel level, final ChunkGenerator gen, final StructureStart start) {
        final BoundingBox bb = start.getBoundingBox();
        final ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(bb.minX()), SectionPos.blockToSectionCoord(bb.minZ()));
        final ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(bb.maxX()), SectionPos.blockToSectionCoord(bb.maxZ()));
        ChunkPos.rangeClosed(min, max).forEach(cp -> start.placeInChunk(
            level, level.structureManager(), gen, level.getRandom(),
            new BoundingBox(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ(),
                cp.getMaxBlockX(), level.getMaxBuildHeight(), cp.getMaxBlockZ()), cp));
    }

    /**
     * Manually generate a registered structure at {@code pos}, mirroring vanilla {@code /place structure}.
     * Returns 1 on success, 0 on failure (reported to the command source). Never throws to the command pipeline.
     */
    private static int placeStructure(final CommandSourceStack source, final ServerLevel level,
                                      final BlockPos pos, final ResourceLocation structureId) {
        try {
            final ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, structureId);
            final Holder<Structure> holder = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE).getHolder(key).orElse(null);
            if (holder == null) {
                source.sendSuccess(() -> Component.literal("[hng-station] unknown structure: " + structureId), false);
                return 0;
            }
            final Structure structure = holder.value();
            final ChunkGenerator gen = level.getChunkSource().getGenerator();
            final StructureStart start = structure.generate(
                level.registryAccess(), gen, gen.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(),
                level.getSeed(), new ChunkPos(pos), 0, level, b -> true);
            if (!start.isValid()) {
                source.sendSuccess(() -> Component.literal(
                    "[hng-station] structure failed to generate at " + pos.getX() + "," + pos.getZ()
                        + " (biome/terrain may not allow " + structureId + ")"), false);
                return 0;
            }
            final BoundingBox bb = start.getBoundingBox();
            placeStart(level, gen, start);
            source.sendSuccess(() -> Component.literal(String.format(
                "[hng-station] placed %s, bbox %d,%d,%d -> %d,%d,%d",
                structureId, bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ())), true);
            return 1;
        } catch (final Throwable t) {
            HelloNewGenerationCoreMod.LOGGER.error("[hng-station] failed to place structure {} at {}", structureId, pos, t);
            source.sendSuccess(() -> Component.literal("[hng-station] error placing structure (see log): " + t), false);
            return 0;
        }
    }
}
