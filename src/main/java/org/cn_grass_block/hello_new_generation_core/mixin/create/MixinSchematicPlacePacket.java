package org.cn_grass_block.hello_new_generation_core.mixin.create;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.schematics.packet.SchematicPlacePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.cn_grass_block.hello_new_generation_core.HelloNewGenerationCoreMod;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Bypass the creative-mode check for this mod's ship-market blueprints only.
 *
 * <p>Create's {@link SchematicPlacePacket#handle(ServerPlayer)} enforces {@code isCreative()}
 * before placing. We redirect that check: if the blueprint's {@code SCHEMATIC_OWNER} equals
 * this mod's ID, return true (allow placement in any game mode); otherwise forward to the
 * original {@code player.isCreative()} so player-uploaded schematics still require creative.
 */
@Mixin(SchematicPlacePacket.class)
public abstract class MixinSchematicPlacePacket {
    @Shadow(remap = false)
    @Final
    private ItemStack stack;

    @Redirect(method = "handle", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isCreative()Z"), remap = false)
    private boolean hello_new_generation_core$bypassForModBlueprints(ServerPlayer player) {
        String owner = this.stack.get(AllDataComponents.SCHEMATIC_OWNER);
        if (HelloNewGenerationCoreMod.MODID.equals(owner)) {
            return true;  // Mod blueprints: always allow
        }
        return player.isCreative();  // Player blueprints: original check
    }
}
