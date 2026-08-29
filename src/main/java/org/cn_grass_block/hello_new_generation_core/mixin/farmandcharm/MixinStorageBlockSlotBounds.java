package org.cn_grass_block.hello_new_generation_core.mixin.farmandcharm;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * farm_and_charm 1.1.22/1.1.23 — {@code StorageBlock.useItemOn} reads
 * {@code storageEntity.getInventory().get(section)} with a {@code section}
 * computed from the clicked block face.  Some subclasses (e.g. the 2-slot
 * cabinets) return a section index that exceeds their inventory size, so a
 * Create Deployer right-clicking the block crashes the server with
 * {@code ArrayIndexOutOfBoundsException: Index 3 out of bounds for length 2}.
 *
 * <p>Fix: clamp the redirected {@code NonNullList.get} to return
 * {@code ItemStack.EMPTY} for any out-of-range index, which makes
 * {@code useItemOn} take the "empty slot" branch instead of crashing.  The
 * deployer then either inserts into a valid slot or does nothing — never
 * crashes the tick.
 */
@Mixin(
    targets = "net.satisfy.farm_and_charm.core.block.StorageBlock",
    remap = false
)
public abstract class MixinStorageBlockSlotBounds {

    @Redirect(
        method = "useItemOn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/NonNullList;get(I)Ljava/lang/Object;"
        )
    )
    private Object hello_new_generation_core$guardSlotIndex(
            NonNullList<ItemStack> inventory, int index) {
        if (index < 0 || index >= inventory.size()) {
            return ItemStack.EMPTY;
        }
        return inventory.get(index);
    }
}
