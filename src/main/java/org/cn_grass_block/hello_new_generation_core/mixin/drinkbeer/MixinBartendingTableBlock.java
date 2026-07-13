package org.cn_grass_block.hello_new_generation_core.mixin.drinkbeer;

import lekavar.lma.drinkbeer.blocks.BartendingTableBlock;
import lekavar.lma.drinkbeer.items.BeerMugItem;
import lekavar.lma.drinkbeer.items.MixedBeerBlockItem;
import lekavar.lma.drinkbeer.items.SpiceBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DrinkBeer Refill fix: the bartending table's {@code useItemOn} returns {@code CONSUME} (server) /
 * {@code sidedSuccess} (client) for the fall-through case where the held item is NOT a beer mug / mixed beer /
 * spice — i.e. an empty hand or any unrelated item. In 1.21 the interaction dispatcher only calls
 * {@code useWithoutItem} when {@code useItemOn} returns {@code PASS_TO_DEFAULT_BLOCK_INTERACTION}; returning
 * CONSUME swallows the interaction. All the bartending table's empty-hand behaviour — taking the finished beer
 * ({@code takeBeer}) and toggling the drawer (the {@code OPENED} state) — lives in {@code useWithoutItem}, so it
 * NEVER runs on the server. Result: the drawer is stuck open (default {@code OPENED=true} can't be toggled),
 * the beer can't be taken out, and breaking the table doesn't recover it.
 *
 * <p>DrinkBeer is a third-party mod we cannot edit. We inject at HEAD: when the held item is none of the three
 * handled item types, we return {@code PASS_TO_DEFAULT_BLOCK_INTERACTION} so the dispatcher proceeds to
 * {@code useWithoutItem} (take beer / toggle drawer). When the player IS holding a mug/mixed beer/spice we do
 * nothing and let the original method run its place/spice logic unchanged.
 */
@Mixin(value = BartendingTableBlock.class, remap = false)
public class MixinBartendingTableBlock {

    @Inject(
        method = "useItemOn",
        at = @At("HEAD"),
        cancellable = true,
        remap = true
    )
    private void hello_new_generation_core$passEmptyHandToBlockInteraction(
            final ItemStack stack, final BlockState state, final Level level, final BlockPos pos,
            final Player player, final InteractionHand hand, final BlockHitResult hit,
            final CallbackInfoReturnable<ItemInteractionResult> cir) {
        final var item = stack.getItem();
        final boolean handled = item instanceof MixedBeerBlockItem
                || item instanceof BeerMugItem
                || item instanceof SpiceBlockItem;
        if (!handled) {
            // Empty hand / unrelated item: let the default block interaction (useWithoutItem) run so the
            // player can take the beer and toggle the drawer.
            cir.setReturnValue(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION);
        }
    }
}
