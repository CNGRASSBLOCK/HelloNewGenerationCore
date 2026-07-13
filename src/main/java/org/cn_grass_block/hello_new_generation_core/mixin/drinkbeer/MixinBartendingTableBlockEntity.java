package org.cn_grass_block.hello_new_generation_core.mixin.drinkbeer;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DrinkBeer client-desync fix: when a beer is taken from the bartending table, the server clears its container
 * and broadcasts a block-entity update. {@code BartendingTableBlockEntity.getUpdateTag} serialises the inventory
 * with {@code ContainerHelper.saveAllItems(tag, items, true)} — the {@code true} skips EMPTY stacks, so a now-empty
 * table writes NO "Items" entry. The client's {@code handleUpdateTag} then calls
 * {@code ContainerHelper.loadAllItems}, which only OVERWRITES slots present in the tag and never clears slots the
 * tag omits. Result: the client keeps the stale beer stack, and the renderer (which reads the container every
 * frame via {@code takeBeer(true)}) keeps drawing the already-taken beer mug on top of the table forever.
 *
 * <p>DrinkBeer is a third-party mod we cannot edit, so we inject at the HEAD of {@code handleUpdateTag} and clear
 * the container before the vanilla load runs. When the update carries items (e.g. initial chunk sync of a filled
 * table) they are loaded right after and the table renders correctly; when the update is empty (after a take) the
 * cleared container stays empty and the stale model disappears. Safe in both cases.
 */
@Mixin(value = lekavar.lma.drinkbeer.blockentities.BartendingTableBlockEntity.class, remap = false)
public abstract class MixinBartendingTableBlockEntity {

    @Shadow
    @Final
    private SimpleContainer inv;

    @Inject(
        method = "handleUpdateTag",
        at = @At("HEAD"),
        remap = true
    )
    private void hello_new_generation_core$clearBeforeClientSync(
            final CompoundTag tag, final HolderLookup.Provider provider, final CallbackInfo ci) {
        // Clear first so an empty update packet (beer taken) actually empties the client-side container;
        // loadAllItems immediately afterwards repopulates it when the packet does carry items.
        this.inv.clearContent();
    }
}
