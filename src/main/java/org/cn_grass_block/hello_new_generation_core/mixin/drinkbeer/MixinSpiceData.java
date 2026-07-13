package org.cn_grass_block.hello_new_generation_core.mixin.drinkbeer;

import lekavar.lma.drinkbeer.utils.dataComponent.SpiceData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * DrinkBeer Refill fix: {@code SpiceData.fromSpiceList} reads {@code list.get(1)} (and {@code get(2)})
 * unconditionally once the previous slot holds a positive value, without checking the list size. The
 * bartending table builds the spice list incrementally — the first spice produces a list of size 1 — so the
 * very first spice added throws {@code IndexOutOfBoundsException: Index 1 out of bounds for length 1}. The
 * server swallows the failed {@code ServerboundUseItemOnPacket} ("suppressing error"), so the interaction
 * silently does nothing and the bartending table appears completely unresponsive.
 *
 * <p>DrinkBeer is a third-party mod we cannot edit, so we replace the broken read with a bounds-safe version
 * that preserves the original semantics exactly: read slot A; only if A &gt; 0 read slot B; only if B &gt; 0
 * read slot C; any slot that is absent or non-positive stays 0.
 */
@Mixin(value = SpiceData.class, remap = false)
public class MixinSpiceData {

    @Inject(method = "fromSpiceList", at = @At("HEAD"), cancellable = true)
    private static void hello_new_generation_core$safeFromSpiceList(final List<Integer> spices, final CallbackInfoReturnable<SpiceData> cir) {
        int spiceA = 0;
        int spiceB = 0;
        int spiceC = 0;

        if (spices != null && !spices.isEmpty() && spices.get(0) > 0) {
            spiceA = spices.get(0);
            if (spices.size() > 1 && spices.get(1) > 0) {
                spiceB = spices.get(1);
                if (spices.size() > 2 && spices.get(2) > 0) {
                    spiceC = spices.get(2);
                }
            }
        }

        cir.setReturnValue(new SpiceData(spiceA, spiceB, spiceC));
    }
}
