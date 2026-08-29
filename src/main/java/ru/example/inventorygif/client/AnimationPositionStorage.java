package ru.example.inventorygif.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.neoforged.fml.loading.FMLPaths;
import ru.example.inventorygif.InventoryGif;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistent storage for per-screen animation positions.
 *
 * Positions are kept as offsets from the container GUI corner (guiLeft/guiTop),
 * the same space as the {@code xOffset}/{@code yOffset} config values, so a
 * stored position can be dropped straight into the renderer.
 *
 * The map key is the screen class name (e.g. {@code net.minecraft...InventoryScreen}),
 * which means every container type (survival inventory, chest, furnace, modded GUI)
 * gets its own remembered position.
 *
 * Data lives in {@code config/inventorygif-positions.json} next to the regular
 * NeoForge config files and is loaded lazily on first access, saved on every change.
 */
public final class AnimationPositionStorage {

    /** Key under which the survival player inventory position is stored. */
    public static final String SURVIVAL_KEY = "net.minecraft.client.gui.screens.inventory.InventoryScreen";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("inventorygif-positions.json");

    /** key -> {xOffset, yOffset}; null until first load. */
    private static Map<String, int[]> positions;

    private AnimationPositionStorage() {
    }

    /** Returns the stored {x, y} offset for the key, or null if none was saved. */
    public static int[] get(String key) {
        return positions().get(key);
    }

    /** Stores the offset for the key and writes the file to disk. */
    public static void set(String key, int xOffset, int yOffset) {
        positions().put(key, new int[]{xOffset, yOffset});
        save();
    }

    /** Removes the stored position for the key (screen falls back to defaults). */
    public static void clear(String key) {
        if (positions().remove(key) != null) {
            save();
        }
    }

    private static Map<String, int[]> positions() {
        if (positions == null) {
            load();
        }
        return positions;
    }

    private static void load() {
        positions = new HashMap<>();
        if (!Files.exists(FILE)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(FILE)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                JsonObject obj = entry.getValue().getAsJsonObject();
                positions.put(entry.getKey(),
                        new int[]{obj.get("x").getAsInt(), obj.get("y").getAsInt()});
            }
        } catch (Exception e) {
            InventoryGif.LOGGER.warn("Failed to read animation positions from {}", FILE, e);
        }
    }

    private static void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, int[]> entry : positions().entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("x", entry.getValue()[0]);
            obj.addProperty("y", entry.getValue()[1]);
            root.add(entry.getKey(), obj);
        }
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root));
        } catch (IOException e) {
            InventoryGif.LOGGER.warn("Failed to write animation positions to {}", FILE, e);
        }
    }
}
