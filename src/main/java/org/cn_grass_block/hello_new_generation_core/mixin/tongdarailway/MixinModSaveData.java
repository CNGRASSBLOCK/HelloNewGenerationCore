package org.cn_grass_block.hello_new_generation_core.mixin.tongdarailway;

import com.hxzhitang.tongdarailway.util.ModSaveData;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * TongDa Railway C2ME thread-safety fix.
 *
 * <p>{@code RailwayBuilder.generateRailway} (invoked from TongDa's {@code NoiseBasedChunkGenerator.surfaceStart}
 * world-gen hook) calls {@code ModSaveData.get(level)} on whatever thread is generating the chunk. Under C2ME's
 * rewritten chunk system that is a pool of {@code c2me-worker} threads — up to ~14 of them in parallel.
 *
 * <p>{@code ModSaveData.get} funnels straight into vanilla {@code DimensionDataStorage.computeIfAbsent}, which is
 * <em>main-thread-only</em>: its backing {@link java.util.HashMap} has no synchronization. When many workers hit a
 * cold cache at once (the first surface-gen burst of a session), every one of them sees a miss and runs
 * {@code ModSaveData::load} concurrently. That does two bad things:
 * <ul>
 *   <li>each worker re-parses the <em>entire</em> railway network NBT and rebuilds every Bézier {@code CurveRoute}
 *       (sample-point generation + recursive KD-tree build) — massive redundant work; and</li>
 *   <li>concurrent {@code HashMap.put} during a resize corrupts the map's internal links, producing a permanent
 *       {@code RUNNABLE} spin. In the captured crash all ten {@code c2me-worker} threads were stuck this way,
 *       while the server thread was parked in a Waystone-triggered synchronous {@code getChunk} waiting for one of
 *       those never-completing chunks — the watchdog then reports a 60-second tick and kills the server.</li>
 * </ul>
 *
 * <p>TongDa Railway is a closed-source third-party mod we cannot edit and it ships no config toggle, so we wrap
 * {@code ModSaveData.get} in a single global lock. The first worker into the lock performs the load and populates
 * the {@code DimensionDataStorage} cache; every subsequent caller enters the lock, finds the entry already cached,
 * and returns it immediately. This removes the worker-vs-worker write stampede — the actual cause of the hang —
 * while leaving C2ME's parallelism fully intact for all other chunk work (the lock is only ever contended for the
 * one-time load; steady-state calls are cache hits that return in microseconds).
 *
 * <p>Mirrors the C2ME force-load deadlock fixes in {@code struts.MixinGirderStrutShapeRegistry} and
 * {@code supplementaries.MixinBellowsBlockTile}.
 */
@Mixin(value = ModSaveData.class, remap = false)
public abstract class MixinModSaveData {

    @Unique
    private static final Object hello_new_generation_core$GET_LOCK = new Object();

    @WrapMethod(method = "get")
    private static ModSaveData hello_new_generation_core$serializeGet(final Level worldIn, final Operation<ModSaveData> original) {
        // Serialize access to the non-thread-safe DimensionDataStorage.computeIfAbsent reached inside get().
        // First caller loads + caches; the rest hit the populated cache and return immediately.
        synchronized (hello_new_generation_core$GET_LOCK) {
            return original.call(worldIn);
        }
    }
}
