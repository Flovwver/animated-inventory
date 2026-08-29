package ru.example.inventorygif.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.fml.loading.FMLPaths;
import ru.example.inventorygif.InventoryGif;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistent per-screen animation settings, stored in
 * {@code config/inventorygif-positions.json}.
 *
 * Every entry holds the dragged position ({@code x}/{@code y} offsets from the
 * GUI corner), the bound {@code animation} (name of a sprite sheet from
 * {@link AnimationLibrary}) and a human-readable {@code label} of the menu.
 *
 * On first run two base entries are seeded: the survival inventory and the
 * creative inventory, both bound to the default animation. When a container
 * screen is opened for the first time, a new entry is created for it with the
 * survival inventory settings copied over, so every menu starts from a
 * sensible place and can then be tweaked independently.
 *
 * The map key is the menu class name for containers (e.g. {@code ...ChestMenu})
 * or the screen class name for the player inventories.
 */
public final class AnimationPositionStorage {

    /** Per-screen animation settings. */
    public static final class Entry {
        public int x;
        public int y;
        /** Bound animation name (from AnimationLibrary); empty = default. */
        public String animation;
        /** Human-readable menu title, filled when the screen is first opened. */
        public String label;

        Entry(int x, int y, String animation, String label) {
            this.x = x;
            this.y = y;
            this.animation = animation;
            this.label = label;
        }
    }

    /** Key under which the survival player inventory entry is stored. */
    public static final String SURVIVAL_KEY = "net.minecraft.client.gui.screens.inventory.InventoryScreen";
    private static final String CREATIVE_KEY = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("inventorygif-positions.json");

    /** key -> entry; null until first load. */
    private static Map<String, Entry> entries;

    private AnimationPositionStorage() {
    }

    /** Mapping key for the screen: menu class for containers, screen class otherwise. */
    public static String keyOf(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> container
                && !(screen instanceof InventoryScreen)
                && !(screen instanceof CreativeModeInventoryScreen)
                && container.getMenu() != null) {
            return container.getMenu().getClass().getName();
        }
        return screen.getClass().getName();
    }

    /**
     * Returns the entry for the screen, creating it on first access.
     * New container entries inherit animation and position from the survival
     * inventory entry; the label is the human-readable menu title.
     */
    public static Entry entryFor(Screen screen) {
        String key = keyOf(screen);
        Map<String, Entry> map = entries();
        Entry entry = map.get(key);
        if (entry == null) {
            Entry base = map.get(SURVIVAL_KEY);
            String label = screen.getTitle() != null ? screen.getTitle().getString() : "";
            entry = new Entry(base.x, base.y, base.animation, label);
            map.put(key, entry);
            save();
        } else if (entry.label == null || entry.label.isEmpty()) {
            entry.label = screen.getTitle() != null ? screen.getTitle().getString() : "";
            save();
        }
        return entry;
    }

    /** Raw access for the settings screen: all known entries (creates base ones). */
    public static Map<String, Entry> all() {
        return entries();
    }

    /** Persists the current state to disk. */
    public static void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Entry> e : entries().entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("x", e.getValue().x);
            obj.addProperty("y", e.getValue().y);
            if (e.getValue().animation != null && !e.getValue().animation.isEmpty()) {
                obj.addProperty("animation", e.getValue().animation);
            }
            if (e.getValue().label != null && !e.getValue().label.isEmpty()) {
                obj.addProperty("label", e.getValue().label);
            }
            root.add(e.getKey(), obj);
        }
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root));
        } catch (IOException ex) {
            InventoryGif.LOGGER.warn("Failed to write animation positions to {}", FILE, ex);
        }
    }

    private static Map<String, Entry> entries() {
        if (entries == null) {
            load();
        }
        return entries;
    }

    private static void load() {
        entries = new LinkedHashMap<>();
        seedBaseEntries();

        if (!Files.exists(FILE)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(FILE)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                return;
            }
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                JsonObject obj = e.getValue().getAsJsonObject();
                int x = obj.has("x") ? obj.get("x").getAsInt() : InventoryGifConfig.X_OFFSET.get();
                int y = obj.has("y") ? obj.get("y").getAsInt() : InventoryGifConfig.Y_OFFSET.get();
                String animation = obj.has("animation") ? obj.get("animation").getAsString() : "";
                String label = obj.has("label") ? obj.get("label").getAsString() : "";
                entries.put(e.getKey(), new Entry(x, y, animation, label));
            }
        } catch (Exception e) {
            InventoryGif.LOGGER.warn("Failed to read animation positions from {}", FILE, e);
        }
    }

    /** Creates the two base entries (survival + creative) bound to the default animation. */
    private static void seedBaseEntries() {
        entries.put(SURVIVAL_KEY, new Entry(InventoryGifConfig.X_OFFSET.get(),
                InventoryGifConfig.Y_OFFSET.get(), AnimationLibrary.DEFAULT_NAME, "Survival Inventory"));
        entries.put(CREATIVE_KEY, new Entry(InventoryGifConfig.X_OFFSET.get(),
                InventoryGifConfig.Y_OFFSET.get(), AnimationLibrary.DEFAULT_NAME, "Creative Inventory"));
    }
}