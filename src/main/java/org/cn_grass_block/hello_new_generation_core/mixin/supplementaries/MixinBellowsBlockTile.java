package org.cn_grass_block.hello_new_generation_core.mixin.supplementaries;

import net.mehvahdjukaar.supplementaries.common.block.tiles.BellowsBlockTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Supplementaries fix: {@code BellowsBlockTile.inLineOfSight} walks outward cell by cell calling
 * {@link Block#canSupportCenter}, which reads the block state and therefore force-loads the target chunk via
 * {@code ServerChunkCache.getChunk}. Under C2ME's asynchronous chunk system a synchronous force-load from the
 * server tick deadlocks the main thread — a single tick blocks forever (ModernFix watchdog reports a multi-
 * hundred-second tick). This is hit hard once a bellows is physicalized by Sable and ends up pointing at an
 * unloaded / out-of-world position, making the world effectively unplayable (interactions freeze, saving hangs).
 *
 * <p>Supplementaries is a third-party mod we cannot edit, so we redirect the {@code canSupportCenter} call: if
 * the target position is not already loaded, we return {@code false} (treat the line of sight as blocked) instead
 * of forcing the chunk to load. A bellows can simply not push through unloaded space — vastly preferable to a
 * hard server lockup. Loaded positions behave exactly as before.
 */
@Mixin(value = BellowsBlockTile.class, remap = false)
public class MixinBellowsBlockTile {

    @Redirect(
        method = "inLineOfSight",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;canSupportCenter(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
            remap = true
        )
    )
    private boolean hello_new_generation_core$skipUnloadedLineOfSight(final LevelReader level, final BlockPos pos, final Direction direction) {
        // hasChunkAt() does NOT force-load — it only reports whether the chunk is already present.
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        return Block.canSupportCenter(level, pos, direction);
    }
}
