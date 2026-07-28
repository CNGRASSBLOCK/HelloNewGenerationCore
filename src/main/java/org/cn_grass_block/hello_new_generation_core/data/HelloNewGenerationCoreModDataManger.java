package org.cn_grass_block.hello_new_generation_core.data;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.fml.loading.FMLPaths;
import org.cn_grass_block.hello_new_generation_core.HelloNewGenerationCoreMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Auto-registers ship-market blueprint items by scanning the config schematic folders.
 *
 * <p>Blueprints live under {@code config/hello_new_generation_core/schematics/<type>/} where the
 * folder name determines how the blueprint is placed:
 * <ul>
 *   <li>{@code create/}  → Create structure NBT   (type "create")</li>
 *   <li>{@code sable/}   → Sable schematic         (type "sable")</li>
 *   <li>{@code toolgun/} → Aeronautics sub-level   (type "tool")</li>
 * </ul>
 *
 * <p>Each file becomes one blueprint item whose id is the file name without its extension
 * (lowercased, spaces → underscores). This replaces the old hand-maintained
 * {@code ship_blueprint.txt} three-line CSV, which was fragile and easy to misalign.
 */
public class HelloNewGenerationCoreModDataManger {
    public static final Map<String, CompoundTag> ship_blueprint_map = new HashMap<>();

    /**
     * Folder name → blueprint type token consumed by {@code ShipPlacerItem}, in scan-priority
     * order. On a cross-folder id collision the first-scanned folder wins, so this order is the
     * tie-break: create &gt; toolgun &gt; sable. A {@link LinkedHashMap} keeps the order stable
     * (unlike {@code Map.of}, whose iteration order is unspecified).
     */
    private static final Map<String, String> FOLDER_TO_TYPE = new LinkedHashMap<>();
    static {
        FOLDER_TO_TYPE.put("create", "create");
        FOLDER_TO_TYPE.put("toolgun", "tool");
        FOLDER_TO_TYPE.put("sable", "sable");
    }

    /** Valid item id per Forge registry rules: [a-z][a-z0-9_]{1,63}. */
    private static final Pattern VALID_ID = Pattern.compile("[a-z][a-z0-9_]{1,63}");

    public static void scanBlueprints() {
        ship_blueprint_map.clear();

        Path schematicsRoot = FMLPaths.CONFIGDIR.get()
                .resolve("hello_new_generation_core")
                .resolve("schematics");

        for (Map.Entry<String, String> entry : FOLDER_TO_TYPE.entrySet()) {
            String folder = entry.getKey();
            String type = entry.getValue();
            Path typeDir = schematicsRoot.resolve(folder);

            try {
                Files.createDirectories(typeDir);
            } catch (IOException e) {
                HelloNewGenerationCoreMod.LOGGER.error("Failed to create schematic dir {}: {}", typeDir, e.toString());
                continue;
            }

            try (Stream<Path> files = Files.walk(typeDir)) {
                List<Path> blueprintFiles = files.filter(Files::isRegularFile).sorted().toList();
                for (Path file : blueprintFiles) {
                    // path relative to the type folder (preserves any sub-directories), forward slashes.
                    String relPath = typeDir.relativize(file).toString().replace('\\', '/');
                    String id = toItemId(file.getFileName().toString());

                    if (id == null) {
                        HelloNewGenerationCoreMod.LOGGER.warn("Skipping blueprint with invalid id: {}/{}", folder, relPath);
                        continue;
                    }
                    if (ship_blueprint_map.containsKey(id)) {
                        HelloNewGenerationCoreMod.LOGGER.warn("Duplicate blueprint id '{}' ({}/{}), keeping first, skipping this one", id, folder, relPath);
                        continue;
                    }

                    CompoundTag data = new CompoundTag();
                    data.putString("type", type);
                    data.putString("path", relPath);
                    ship_blueprint_map.put(id, data);
                }
            } catch (IOException e) {
                HelloNewGenerationCoreMod.LOGGER.error("Failed to scan schematic dir {}: {}", typeDir, e.toString());
            }
        }

        HelloNewGenerationCoreMod.LOGGER.info("Registered {} ship blueprints from config folders", ship_blueprint_map.size());
    }

    /** Derive a valid item id from a file name, or null if it can't be made valid. */
    private static String toItemId(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        String id = stem.trim().toLowerCase().replace(' ', '_');
        return VALID_ID.matcher(id).matches() ? id : null;
    }
}
