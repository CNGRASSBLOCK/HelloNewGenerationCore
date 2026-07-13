package org.cn_grass_block.hello_new_generation_core.mixin.struts;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Strut Your Stuff fix: {@code GirderStrutStructureShapes$ShapeRegistry.unregisterConnection} iterates over
 * every {@link BlockPos} of a strut's geometry and calls {@code level.getBlockState(pos)} (then possibly
 * {@code level.removeBlock}) to tear down the auto-placed structure blocks. A strut spans multiple chunks, so
 * some of those positions live in neighbouring chunks.
 *
 * <p>{@code unregisterConnection} is called from {@code StrutBlockEntity.setRemoved()}, which runs during
 * {@code LevelChunk.clearAllBlockEntities()} while a chunk is being <em>unloaded</em>. When the accessed position
 * is in a not-yet-loaded neighbouring chunk, {@code getBlockState} force-loads it via
 * {@code ServerChunkCache.getChunk -> managedBlock -> waitForTasks}. Under C2ME's asynchronous chunk system this
 * synchronous force-load happens on the server thread from inside the unload callback: the load task can only be
 * completed by the server thread, which is now blocked waiting for it — the thread waits on itself and the tick
 * never ends. ModernFix's watchdog reports a multi-hundred-second tick and the game freezes.
 *
 * <p>Strut Your Stuff is a closed-source third-party library (a required dependency of Create: Bits 'n' Bobs) that
 * we cannot edit and that ships no config toggle, so we wrap the {@code getBlockState} call: if the target chunk
 * is not already loaded we return the air {@link BlockState} instead of force-loading. Since air's block is never
 * {@code GIRDER_STRUT_STRUCTURE}, the caller's identity check fails and it simply skips {@code removeBlock} for
 * that position — which is harmless, because the shape registry entry ({@code shapesByPosition}) has already been
 * removed above the call, and any leftover structure block is cleaned up when its own chunk is next processed.
 * Loaded positions behave exactly as before.
 *
 * <p>Reported upstream (see modpack bug report). Mirrors the existing
 * {@code supplementaries.MixinBellowsBlockTile} fix for the same C2ME force-load deadlock class.
 */
@Mixin(targets = "com.cake.struts.content.structure.GirderStrutStructureShapes$ShapeRegistry", remap = false)
public class MixinGirderStrutShapeRegistry {

    @WrapOperation(
        method = "unregisterConnection",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            remap = true
        )
    )
    private BlockState hello_new_generation_core$skipUnloadedDuringUnregister(final Level level, final BlockPos pos, final Operation<BlockState> original) {
        // hasChunkAt() does NOT force-load — it only reports whether the chunk is already present.
        if (!level.hasChunkAt(pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return original.call(level, pos);
    }
}
