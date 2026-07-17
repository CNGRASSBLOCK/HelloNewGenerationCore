package org.cn_grass_block.hello_new_generation_core.mixin.bitsntracks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Bits 'n' Tracks crash fix (ConcurrentModification in the physics tick).
 *
 * <p>{@code BntPhysicsEvents.onPhysicsTick} iterates the LIVE registry set returned by
 * {@code BntPhysicsRegistry.getEnabled(level)} (a fastutil {@code ObjectOpenHashSet}) and, inside that same loop,
 * calls {@code BntPhysicsRegistry.remove(kbe)} whenever a wheel's {@code KineticBlockEntity.isRemoved()} is true —
 * which mutates the very set being iterated. Removing from an {@code ObjectOpenHashSet} during its own iteration
 * corrupts the iterator's internal backing list ({@code this.wrapped} becomes null), so the next
 * {@code SetIterator.next()} throws {@code NullPointerException} and the Sable physics tick crashes — repeatedly,
 * so the world can't be re-entered.
 *
 * <p>This triggers whenever a physicalized Bits 'n' Tracks wheel/track block is deleted (Sable blueprint tool,
 * tool gun, etc.): the deletion flips {@code isRemoved()} and the next physics tick hits the unsafe removal.
 *
 * <p>Bits 'n' Tracks is a third-party mod we cannot edit. We {@code @WrapOperation} the {@code getEnabled} call
 * inside {@code onPhysicsTick} and hand the loop a snapshot copy instead of the live set. The in-loop
 * {@code remove(kbe)} then mutates only the live set (correctly pruning the removed wheel) while our iteration
 * runs over the stable copy — no iterator corruption. Behaviour is otherwise identical: a wheel removed earlier
 * in the same tick is still skipped by the {@code isRemoved()} guard. Mirrors the physics-tick
 * concurrent-mutation fix in {@code simulated.MixinServerLevelRopeManager}.
 */
@Mixin(targets = "dev.qwxon.bitsntracks.physics.BntPhysicsEvents", remap = false)
public abstract class MixinBntPhysicsEvents {

    @WrapOperation(
        method = "onPhysicsTick",
        at = @At(
            value = "INVOKE",
            target = "Ldev/qwxon/bitsntracks/physics/BntPhysicsRegistry;getEnabled(Lnet/minecraft/server/level/ServerLevel;)Ljava/util/Collection;"
        )
    )
    private static Collection<Object> hello_new_generation_core$snapshotEnabled(final ServerLevel level, final Operation<Collection<Object>> original) {
        final Collection<Object> live = original.call(level);
        // Iterate a copy so the in-loop BntPhysicsRegistry.remove(kbe) can't corrupt the live set's iterator.
        return live == null ? null : new ArrayList<>(live);
    }
}
