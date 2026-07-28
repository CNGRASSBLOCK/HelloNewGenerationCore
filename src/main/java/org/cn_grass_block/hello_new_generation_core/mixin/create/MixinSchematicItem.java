package org.cn_grass_block.hello_new_generation_core.mixin.create;

import com.simibubi.create.AllDataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.loading.FMLPaths;
import org.cn_grass_block.hello_new_generation_core.HelloNewGenerationCoreMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;

import com.simibubi.create.content.schematics.SchematicItem;

/**
 * Redirect this mod's Create-format ship blueprints to the config folder so PCL exports them.
 *
 * <p>Create's {@link SchematicItem#loadSchematic(Level, ItemStack)} hardcodes the server-side read
 * to {@code CreatePaths.UPLOADED_SCHEMATICS_DIR/<owner>/<file>} — i.e. game-root
 * {@code schematics/uploaded/<owner>/}. That directory lives outside {@code config/}, so the PCL
 * launcher's modpack export never includes our ship-market Create blueprints.
 *
 * <p>We move those blueprints into {@code config/hello_new_generation_core/schematics/create/}
 * (alongside the {@code sable} and {@code toolgun} folders) and intercept {@code loadSchematic}:
 * whenever the blueprint's {@code SCHEMATIC_OWNER} equals this mod's id, we load the NBT from the
 * config folder instead. This one hook covers both call sites (write-size on item creation and the
 * SchematicPrinter read on placement), since both route through {@code loadSchematic}.
 *
 * <p>Player-uploaded blueprints (owner = a player name) are untouched and still read from the
 * vanilla uploaded dir. Mirrors Create's own read logic (GZIP + {@code NbtIo.read}) exactly.
 */
@Mixin(SchematicItem.class)
public abstract class MixinSchematicItem {

    @Inject(method = "loadSchematic", at = @At("HEAD"), cancellable = true)
    private static void hello_new_generation_core$loadFromConfig(Level level, ItemStack blueprint, CallbackInfoReturnable<StructureTemplate> cir) {
        String owner = blueprint.get(AllDataComponents.SCHEMATIC_OWNER);
        String schematic = blueprint.get(AllDataComponents.SCHEMATIC_FILE);

        if (owner == null || schematic == null || !schematic.endsWith(".nbt")) return;
        if (!HelloNewGenerationCoreMod.MODID.equals(owner)) return;

        Path dir = FMLPaths.CONFIGDIR.get()
                .resolve("hello_new_generation_core")
                .resolve("schematics")
                .resolve("create");
        Path path = dir.resolve(schematic.replace('\\', '/')).normalize();

        // Path-traversal guard: keep resolved file inside the create folder.
        if (!path.startsWith(dir)) {
            cir.setReturnValue(new StructureTemplate());
            return;
        }

        StructureTemplate template = new StructureTemplate();
        try (DataInputStream stream = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(path, StandardOpenOption.READ))))) {
            CompoundTag nbt = NbtIo.read(stream, NbtAccounter.create(536870912L));
            template.load(level.holderLookup(Registries.BLOCK), nbt);
        } catch (IOException e) {
            HelloNewGenerationCoreMod.LOGGER.warn("Failed to read create schematic from config: {}", path, e);
        }
        cir.setReturnValue(template);
    }
}
