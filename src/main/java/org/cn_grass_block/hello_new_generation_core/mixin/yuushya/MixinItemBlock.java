package org.cn_grass_block.hello_new_generation_core.mixin.yuushya;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * yuushya_modelling 2.4.1 — {@code ItemBlock.getShape} calls
 * {@code level.getBlockState(pos).is(...)} on line 25 (source ~40) with no
 * null-guard.  In normal gameplay {@code BlockGetter.getBlockState} never
 * returns null, but Sable's {@code RapierVoxelColliderBakery} uses an
 * internal BlockGetter during physics baking that returns null for positions
 * outside the baked section.  When a sublevel contains a yuushya ItemBlock,
 * loading or reloading the sublevel calls {@code buildPhysicsDataForBlock} →
 * {@code getCollisionShape} → {@code getShape} → NPE → server tick crash.
 *
 * <p>Fix: if {@code getBlockState} would return null, short-circuit and return
 * {@link Shapes#empty()} so the physics bakery treats the block as having no
 * collision, which is safe and avoids the crash.
 */
@Mixin(targets = "com.yuushya.modelling.blockentity.itemblock.ItemBlock", remap = false)
public abstract class MixinItemBlock {

    @Inject(
        method = "getShape",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hello_new_generation_core$nullSafeGetShape(
            BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (level.getBlockState(pos) == null) {
            cir.setReturnValue(Shapes.empty());
        }
    }
}
