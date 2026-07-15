package org.cn_grass_block.hello_new_generation_core.mixin.simulated;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.index.SimItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Create: Aeronautics (simulated) fix for ropes left dangling when a physicalized structure a rope was attached to
 * is deleted (e.g. with the tool gun) — either the whole sub-level structure, or just the connector block while the
 * rest of the structure survives.
 *
 * <p>Two failures this addresses:
 * <ul>
 *   <li><b>Hard crash:</b> when a structure is deleted, the rope's {@link RopeAttachment} still points at the now
 *       removed sub-level. If the OTHER end is still live, {@code areAttachmentsLoaded()} returns {@code true}
 *       (it early-returns on the first live sub-level), so {@code prePhysicsTick -> reattachConstraints} runs and
 *       feeds {@code subLevel=null} (world) + a plotgrid-space anchor into Sable's {@code addConstraint}, which
 *       throws {@code IllegalArgumentException: ...coordinate spaces} and crashes the physics tick every tick.</li>
 *   <li><b>Silent dangle:</b> the mod's only self-cleanup ({@code destroyRopeIfAttachmentBroken}) runs in the
 *       START-owner holder's block-entity tick and only checks the END. If the deleted connector was the OWNER
 *       (START), nothing ever tears the rope down: the surviving END keeps {@code attachedRopeID} set (cannot be
 *       reused) and no {@code ROPE_COUPLING} item drops. Whole-structure deletes can also make
 *       {@code areAttachmentsLoaded()} return {@code false}, which drops the strand from the physics sim but leaves
 *       it in the manager map forever.</li>
 * </ul>
 *
 * <p>Fix: at the HEAD of {@link ServerLevelRopeManager#physicsTick} — which runs every physics tick, unconditionally,
 * before the crash-prone loop — sweep every strand and tear down any whose START or END is broken. "Broken" means
 * the end is anchored to a sub-level that no longer exists (structure deleted), or its holder block-entity is gone
 * while its chunk/sub-level is loaded (connector removed but structure survives). Teardown reuses the mod's own
 * {@code destroyRope} on the surviving owner when possible (drops the coupling + break FX, detaches both ends,
 * removes the strand); if the owner itself was deleted, we detach the surviving end, drop the item at that end's
 * real-world position, and remove the strand from the physics system + manager. Healthy ropes, and ropes merely in
 * temporarily-unloaded chunks, are untouched.
 */
@Mixin(value = ServerLevelRopeManager.class, remap = false)
public abstract class MixinServerLevelRopeManager {

    @Inject(method = "physicsTick", at = @At("HEAD"))
    private void hello_new_generation_core$cullBrokenRopes(
            final SubLevelPhysicsSystem physicsSystem, final double timeStep, final CallbackInfo ci) {
        final ServerLevel level = physicsSystem.getLevel();
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        final ServerLevelRopeManager self = (ServerLevelRopeManager) (Object) this;
        List<ServerRopeStrand> broken = null;
        for (final ServerRopeStrand strand : self.getAllStrands()) {
            if (hello_new_generation_core$isBrokenEnd(level, container, strand, RopeAttachmentPoint.START)
                    || hello_new_generation_core$isBrokenEnd(level, container, strand, RopeAttachmentPoint.END)) {
                if (broken == null) {
                    broken = new ArrayList<>();
                }
                broken.add(strand);
            }
        }
        if (broken == null) {
            return; // fast path: nothing broken
        }
        final boolean dropItem = level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
        for (final ServerRopeStrand strand : broken) {
            hello_new_generation_core$tearDown(level, container, self, strand, dropItem);
        }
    }

    /**
     * @return {@code true} if this end's anchor is permanently gone: its sub-level was deleted, or (for a live
     *         sub-level / world anchor whose chunk is loaded) no rope-holder block-entity remains at the anchor.
     *         Ends whose chunk is not currently loaded are treated as NOT broken to avoid culling on chunk unload.
     */
    private static boolean hello_new_generation_core$isBrokenEnd(
            final ServerLevel level, final ServerSubLevelContainer container,
            final ServerRopeStrand strand, final RopeAttachmentPoint point) {
        final RopeAttachment attachment = strand.getAttachment(point);
        if (attachment == null) {
            return false;
        }
        final UUID subLevelID = attachment.subLevelID();
        if (subLevelID != null && container.getSubLevel(subLevelID) == null) {
            return true; // structure this end was attached to was deleted
        }
        // Anchor's sub-level is live (or it's a world anchor): the block must be loaded before we can judge it.
        final BlockPos pos = attachment.blockAttachment();
        if (subLevelID == null
                && !PhysicsChunkTicketManager.isChunkLoadedEnough(level, pos.getX() >> 4, pos.getZ() >> 4)) {
            return false; // world anchor in an unloaded chunk — don't cull, just not ticking
        }
        return hello_new_generation_core$holderAt(level, pos) == null; // connector block removed
    }

    private static RopeStrandHolderBehavior hello_new_generation_core$holderAt(final ServerLevel level, final BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SmartBlockEntity smartBlockEntity)) {
            return null;
        }
        return smartBlockEntity.getBehaviour(RopeStrandHolderBehavior.TYPE);
    }

    /**
     * Drop + disconnect a broken rope. Preferred path: the surviving START-owner tears it down normally (drops the
     * coupling at the owner connector, detaches the far end, removes the strand). If the owner connector itself was
     * the deleted one, detach the surviving END, drop the item at that end's real-world position, and remove the
     * strand from the physics system + manager.
     */
    private static void hello_new_generation_core$tearDown(
            final ServerLevel level, final ServerSubLevelContainer container,
            final ServerLevelRopeManager manager, final ServerRopeStrand strand, final boolean dropItem) {
        final RopeAttachment startAttachment = strand.getAttachment(RopeAttachmentPoint.START);
        final RopeStrandHolderBehavior owner =
                startAttachment == null ? null : hello_new_generation_core$holderAt(level, startAttachment.blockAttachment());
        if (owner != null && owner.ownsRope() && owner.getOwnedStrand() != null) {
            // Owner still present: let the mod drop + detach + remove exactly as a normal break, at the owner block.
            owner.destroyRope(null, owner.getAttachmentPoint(), dropItem);
            return;
        }

        // Owner connector was the deleted end: detach whatever end survives so its binding is cleared.
        RopeAttachment survivorAttachment = null;
        for (final RopeAttachmentPoint point : RopeAttachmentPoint.values()) {
            final RopeAttachment attachment = strand.getAttachment(point);
            if (attachment == null) {
                continue;
            }
            final RopeStrandHolderBehavior holder = hello_new_generation_core$holderAt(level, attachment.blockAttachment());
            if (holder != null) {
                holder.detachRope();
                holder.blockEntity.notifyUpdate();
                survivorAttachment = attachment;
            }
        }
        if (dropItem && survivorAttachment != null) {
            final Vec3 pos = survivorAttachment.blockAttachment().getCenter();
            level.addFreshEntity(new ItemEntity(level, pos.x, pos.y, pos.z, new ItemStack(SimItems.ROPE_COUPLING.get())));
        }
        container.physicsSystem().removeObject(strand);
        manager.removeStrand(strand.getUUID());
    }
}
