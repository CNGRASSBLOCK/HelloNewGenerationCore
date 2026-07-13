package org.cn_grass_block.hello_new_generation_core.mixin.minecraft;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Memory-leak fix: vanilla {@code VillagerTrades.TreasureMapForEmeralds.getOffer} calls
 * {@code ServerLevel.findNearestMapStructure(..., 100, true)} to locate the target structure (e.g. woodland
 * mansion) when a cartographer's explorer-map trade is generated. Mansions/ocean monuments are extremely sparse,
 * so the search scans an enormous radius and — with the {@code true} flag — force-loads a huge number of chunks
 * synchronously. Under C2ME's async chunk system this balloons memory and hangs the server (AllTheLeaks flags it).
 *
 * <p>We inject at HEAD and short-circuit the whole trade to {@code null}, so the explorer-map trade is simply
 * never offered and the expensive structure search never runs. This disables ALL TreasureMapForEmeralds trades
 * (woodland mansion, ocean monument, and any modded ones built on this class), per the chosen scope.
 */
@Mixin(VillagerTrades.TreasureMapForEmeralds.class)
public class MixinTreasureMapForEmeralds {

    @Inject(
        method = "getOffer",
        at = @At("HEAD"),
        cancellable = true,
        remap = true
    )
    private void hello_new_generation_core$disableExplorerMapTrade(
            final Entity trader, final RandomSource random, final CallbackInfoReturnable<MerchantOffer> cir) {
        // Skip the findNearestMapStructure force-load entirely: no offer = no search = no leak.
        cir.setReturnValue(null);
    }
}
