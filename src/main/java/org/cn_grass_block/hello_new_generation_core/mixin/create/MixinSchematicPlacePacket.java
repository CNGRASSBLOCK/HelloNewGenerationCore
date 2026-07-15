package org.cn_grass_block.hello_new_generation_core.mixin.create;

import com.simibubi.create.content.schematics.packet.SchematicPlacePacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SchematicPlacePacket.class)
public abstract class MixinSchematicPlacePacket {
    @Redirect(method = "handle", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isCreative()Z"))
    private static boolean isCreative(ServerPlayer instance) {
        return true;
    }
}
