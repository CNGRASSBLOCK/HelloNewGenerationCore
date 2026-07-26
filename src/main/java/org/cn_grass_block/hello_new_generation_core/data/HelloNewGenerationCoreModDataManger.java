package org.cn_grass_block.hello_new_generation_core.data;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.fml.loading.FMLPaths;
import org.cn_grass_block.hello_new_generation_core.HelloNewGenerationCoreMod;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class HelloNewGenerationCoreModDataManger {
    public static final Map<String, CompoundTag> ship_blueprint_map = new HashMap<>();

    public static void readJson() {
        Path configRoot = FMLPaths.CONFIGDIR.get().resolve("hello_new_generation_core");
        try {
            Files.createDirectories(configRoot);
        } catch (IOException e) {
            HelloNewGenerationCoreMod.LOGGER.error("Failed to create config directory: {}", e.toString());
            return;
        }

        //船只蓝图
        Path shipBlueprintPath = configRoot.resolve("ship_blueprint.txt");
        try {
            if (!Files.exists(shipBlueprintPath)) {
                Files.createFile(shipBlueprintPath);
            }
        } catch (IOException e) {
            HelloNewGenerationCoreMod.LOGGER.error("Failed to create ship_blueprint.txt: {}", e.toString());
            return;
        }

        File ship_blueprint = shipBlueprintPath.toFile();

        String[] id = new String[0];
        String[] type = new String[0];
        String[] path = new String[0];
        try (FileReader fr = new FileReader(ship_blueprint); BufferedReader br = new BufferedReader(fr)) {
            String ids = br.readLine();
            if (ids != null) id = ids.split(",");
            for (int i = 0; i < id.length; i++) {
                id[i] = id[i].trim().replaceAll(" ", "_");
            }
            String types = br.readLine();
            if (types != null) type = types.split(",");
            for (int i = 0; i < type.length; i++) {
                type[i] = type[i].trim();
                if (!type[i].equals("create") && !type[i].equals("sable") && !type[i].equals("tool")) type[i] = "create";
            }
            String paths = br.readLine();
            if (paths != null) path = paths.split(",");
            for (int i = 0; i < path.length; i++) {
                path[i] = path[i].trim();
            }
        } catch (IOException e) {
            HelloNewGenerationCoreMod.LOGGER.error(e.toString());
            return;
        }

        if (id.length == type.length && id.length == path.length) for (int i = 0; i < id.length; i++) {
            CompoundTag data = new CompoundTag();
            data.putString("type", type[i]);
            data.putString("path", path[i]);
            ship_blueprint_map.put(id[i], data);
        }
    }
}
