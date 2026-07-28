package org.cn_grass_block.hello_new_generation_core.mixin.aeroengineering;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * AeroEngine 1.2.4 deadlock fix: prevent forced chunk load during engine BE invalidation.
 *
 * <p>When a chunk containing a {@code VectorBearingBlockEntity} is unloaded by C2ME's async chunk
 * system, the unload flow calls {@code LevelChunk.clearAllBlockEntities} →
 * {@code SmartBlockEntity.setRemoved} → {@code VectorBearingBlockEntity.invalidate} →
 * {@code unregisterAfterburnerLink} → Create's {@code RedstoneLinkNetworkHandler.removeFromNetwork}.
 * create_connected's wildcard handler then notifies peer redstone links, triggering
 * {@code AfterburnerLink.setReceivedStrength} → {@code receiveAfterburnerSignal} →
 * {@code isEngineStartModeActive()} → {@code EngineStructure.isStartModeActiveFromTail} →
 * {@code findIntakeFromTail}.
 *
 * <p>{@code findIntakeFromTail} walks neighboring blocks to locate the engine's intake fan via
 * {@code level.getBlockState(intakePos)} at EngineStructure.java:387 <strong>with no
 * {@code isLoaded} guard</strong> (unlike markAfterburnerCandidate:136 and markCandidate:167 which
 * correctly check {@code level.isLoaded(candidate)} first). If {@code intakePos} is in an unloaded
 * neighbor chunk, {@code getBlockState} forces a synchronous chunk load. Under C2ME's rewritten
 * chunk system, a blocking {@code ServerChunkCache.getChunk} call from inside a chunk-status-
 * transition task (the unload itself) re-entrantly calls {@code managedBlock}, which parks the
 * main server thread forever waiting for the chunk worker pool — but the pool can't make progress
 * because the main thread holds the chunk-status lock. The ServerHangWatchdog then kills the
 * server after 60 seconds with "Watching Server" / "managedBlock" in the stack.
 *
 * <p>This deadlock triggers whenever an AeroEngine jet (VectorBearing + linked afterburner) spans
 * a chunk boundary and the tail chunk unloads while the engine is running (common for flying
 * contraptions crossing chunk borders).
 *
 * <p>We {@code @WrapOperation} the {@code getBlockState} call inside {@code findIntakeFromTail}
 * and return {@code Blocks.AIR.defaultBlockState()} when the target chunk is not loaded. Since
 * AIR is never an intake block (fan/propeller), the intake-search loop safely returns null and
 * {@code isStartModeActiveFromTail} returns false — the correct behavior when the engine structure
 * is incomplete/unavailable (mirrors the intent of the existing {@code isLoaded} guards). No
 * forced chunk load occurs, so the unload completes without deadlock.
 *
 * <p>Preserves correct behavior: if the intake <em>is</em> loaded, the normal block state is
 * returned and the engine logic proceeds unchanged. Only prevents the pathological re-entrant
 * chunk-load case.
 */
@Mixin(targets = "com.cxw.aeroengineering.content.engine.EngineStructure", remap = false)
public abstract class MixinEngineStructure {

    @WrapOperation(
        method = "findIntakeFromTail",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelReader;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private static BlockState hello_new_generation_core$guardChunkLoad(
        final LevelReader level,
        final BlockPos pos,
        final Operation<BlockState> original
    ) {
        // LevelReader.hasChunkAt checks if the chunk is loaded without forcing a load.
        // If not loaded, return AIR (never matches intake blocks) so the search safely returns null.
        if (!level.hasChunkAt(pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return original.call(level, pos);
    }
}
