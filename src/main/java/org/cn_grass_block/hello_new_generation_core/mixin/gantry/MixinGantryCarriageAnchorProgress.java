package org.cn_grass_block.hello_new_generation_core.mixin.gantry;

import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * createaerophysicsgantry 1.0.3 — gantry carriage misaligns with the shaft unless
 * it is exactly centred on the anchored shaft block.
 *
 * <p>{@code PhysicsGantryCarriageBlockEntity.setAssembledAttachmentState} hardcodes
 * {@code attachedShaftProgress = 0.0}, assuming the carriage rides the anchored shaft
 * block's centre. When the carriage sits elsewhere along the shaft, the physical
 * constraint pulls it back to progress 0, offset by the carriage's real distance from
 * that block (left of centre shifts left, right shifts right, centre is fine).
 *
 * <p>Fix: on the plot-scoped attachment call ({@code shaftSubLevelId != null}), compute
 * the carriage's actual progress along the shaft axis from its block position relative
 * to the anchored shaft block and write it back, so the first constraint creation
 * anchors the carriage where it actually is instead of at progress 0.
 *
 * <p>The projection is only applied to the carriage instance that lives inside the
 * assembled sub-level (checked via {@code Sable.HELPER.getContaining}), because only
 * that instance's block position shares the plot space of the shaft position. The
 * origin carriage still in the overworld is left untouched.
 *
 * <p>No compile-time dependency on the gantry mod: the mixin targets the class by name
 * and only touches the {@code attachedShaftProgress} field via {@link Shadow}.
 */
@Mixin(
    targets = "com.blorbee.createaerophysicsgantry.content.physics_gantry_carriage.PhysicsGantryCarriageBlockEntity",
    remap = false
)
public abstract class MixinGantryCarriageAnchorProgress {

    @Shadow(remap = false)
    private double attachedShaftProgress;

    @Inject(
        method = "setAssembledAttachmentState",
        at = @At("RETURN"),
        remap = false
    )
    private void hello_new_generation_core$recomputeProgressOnAssemble(
            BlockPos shaftPos, Direction shaftDirection, Direction carriageFacing,
            UUID subLevelId, UUID shaftSubLevelId, CallbackInfo ci) {
        if (shaftSubLevelId == null || shaftPos == null || shaftDirection == null) {
            return; // first, world-space call: shaft position is not yet plot space
        }
        BlockEntity self = (BlockEntity) (Object) this;
        if (self == null || self.getLevel() == null || self.getLevel().isClientSide()) {
            return;
        }
        // Only the carriage instance inside the assembled sub-level has a plot-space
        // block position matching the plot-space shaft position.
        if (Sable.HELPER.getContaining(self) == null) {
            return;
        }
        BlockPos here = self.getBlockPos();
        long dx = (long) here.getX() - shaftPos.getX();
        long dy = (long) here.getY() - shaftPos.getY();
        long dz = (long) here.getZ() - shaftPos.getZ();
        double progress = switch (shaftDirection.getAxis()) {
            case X -> dx;
            case Y -> dy;
            case Z -> dz;
        };
        this.attachedShaftProgress = progress;
    }
}
