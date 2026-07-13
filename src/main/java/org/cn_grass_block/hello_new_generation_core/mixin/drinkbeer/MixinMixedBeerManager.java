package org.cn_grass_block.hello_new_generation_core.mixin.drinkbeer;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * DrinkBeer crash fix: {@code MixedBeerManager.useMixedBeer} builds the effects to apply to the drinker with
 * {@code new MobEffectInstance(Holder.direct((MobEffect) pair.getKey()), duration)}. The pair was produced by
 * {@code getBeerStatusEffectList}, which takes each effect's REGISTERED holder out of the food properties and
 * strips it down to the raw {@link MobEffect} value via {@code Holder.value()}. Re-wrapping that value with
 * {@code Holder.direct(...)} yields an UNREGISTERED (keyless) holder.
 *
 * <p>When the drinker is a player, that effect is stored on the entity. On the next world save,
 * {@code MobEffectInstance.save} calls {@code MobEffect.CODEC} which requires a registry key — an unregistered
 * "Direct" holder throws {@code IllegalStateException: Unregistered holder in ResourceKey[minecraft:mob_effect]}
 * and crashes the integrated server during "Saving entity NBT". Drinking ANY mixed/spiced beer poisons the save.
 *
 * <p>DrinkBeer is a third-party mod we cannot edit, so we redirect the {@code Holder.direct} call inside
 * {@code useMixedBeer}: instead of a keyless direct holder we look the effect back up in the level's
 * {@code MOB_EFFECT} registry via {@code wrapAsHolder}, which returns the proper registered reference holder.
 * That holder has a registry key, so the resulting {@code MobEffectInstance} saves cleanly. If the value somehow
 * is not registered, {@code wrapAsHolder} still falls back to a direct holder, so behaviour never regresses.
 */
@Mixin(targets = "lekavar.lma.drinkbeer.managers.MixedBeerManager", remap = false)
public class MixinMixedBeerManager {

    @Redirect(
        method = "useMixedBeer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/Holder;direct(Ljava/lang/Object;)Lnet/minecraft/core/Holder;",
            remap = true
        )
    )
    private static Holder<MobEffect> hello_new_generation_core$registeredEffectHolder(
            final Object effectValue, final ItemStack stack, final Level level, final LivingEntity entity) {
        final MobEffect effect = (MobEffect) effectValue;
        // wrapAsHolder returns the REGISTERED reference holder (with a registry key) when the value is registered,
        // so the MobEffectInstance saves cleanly. Falls back to a direct holder only if truly unregistered.
        return level.registryAccess().registryOrThrow(Registries.MOB_EFFECT).wrapAsHolder(effect);
    }
}
