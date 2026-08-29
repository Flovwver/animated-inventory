package ru.example.inventorygif.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.fml.loading.FMLPaths;
import ru.example.inventorygif.InventoryGif;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Persistent per-screen animation settings, stored in
 * {@code config/inventorygif-positions.json} as a tree.
 *
 * Node types (key -> meaning):
 * - {@code InventoryScreen}/{@code CreativeModeInventoryScreen} — base nodes.
 * - {@code @mod:<mod>} — mod-level node (e.g. {@code @mod:Minecraft}).
 * - {@code @menu:<menuClass>} — shared settings for all screens of one menu.
 * - {@code <menuClass>|<titleKey>} — settings for one exact screen title
 *   (translation key, or the literal text for anvil-renamed containers).
 *
 * Every non-base node has {@code parentKey} and an {@code inherit} flag.
 * While {@code inherit} is true the node dynamically uses its parent's values;
 * editing any value turns inheritance off. New branches are auto-created with
 * inheritance on, so the fallback chain is always
 * title -> menu -> mod -> survival inventory -> config defaults.
 */
public final class AnimationPositionStorage {

    /** Per-screen animation settings. */
    public static final class Entry {
        public int x;
        public int y;
        /** Bound animation name (from AnimationLibrary); empty = default. */
        public String animation;
        /** Human-readable label, filled when the screen is first opened. */
        public String label;
        /** True = use the parent node's values dynamically. */
        public boolean inherit;
        /** Per-node render scale; <= 0 = use the parent/global value. */
        public double scale;
        /** Parent node key in the settings tree. */
        public String parentKey;

        Entry(int x, int y, String animation, String label, boolean inherit, double scale, String parentKey) {
            this.x = x;
            this.y = y;
            this.animation = animation;
            this.label = label;
            this.inherit = inherit;
            this.scale = scale;
            this.parentKey = parentKey;
        }
    }

    /** Key under which the survival player inventory entry is stored. */
    public static final String SURVIVAL_KEY = "net.minecraft.client.gui.screens.inventory.InventoryScreen";
    private static final String CREATIVE_KEY = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen";
    private static final String MOD_PREFIX = "@mod:";
    private static final String MENU_PREFIX = "@menu:";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("inventorygif-positions.json");

    private static final Set<String> TLD_PACKAGES = Set.of(
            "net", "org", "com", "io", "dev", "me", "xyz", "de", "fr", "uk", "ru", "cn", "jp", "ua", "pl", "it", "es", "se", "top", "site");

    /** key -> entry; null until first load. */
    private static Map<String, Entry> entries;

    private AnimationPositionStorage() {
    }

    /** Mapping key for the screen. Containers: {@code menuClass|titleKey}; player inventories: screen class. */
    public static String keyOf(Screen screen) {
        String menuClass = menuClassOf(screen);
        if (menuClass != null) {
            return menuClass + "|" + titleKeyOf(screen);
        }
        return screen.getClass().getName();
    }

    /** Menu class name for container screens, null for player inventories. */
    public static String menuClassOf(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> container
                && !(screen instanceof InventoryScreen)
                && !(screen instanceof CreativeModeInventoryScreen)
                && container.getMenu() != null) {
            return container.getMenu().getClass().getName();
        }
        return null;
    }

    /**
     * Stable title identity: the translation key when the title is translatable
     * (standard containers, modded menus), otherwise the literal text — which is
     * the case for containers renamed on an anvil (vanilla creates Component.literal).
     */
    public static String titleKeyOf(Screen screen) {
        Component title = screen.getTitle();
        if (title != null && title.getContents() instanceof TranslatableContents translatable) {
            return translatable.getKey();
        }
        return title != null ? title.getString() : "";
    }

    /** Mod group name guessed from the menu class package (for tree grouping). */
    public static String modOf(String className) {
        if (className.startsWith("net.minecraft.")) {
            return "Minecraft";
        }
        String[] parts = className.split("\\.");
        String segment = parts.length > 2 && TLD_PACKAGES.contains(parts[0]) ? parts[2] : parts[0];
        return segment.isEmpty() ? segment
                : Character.toUpperCase(segment.charAt(0)) + segment.substring(1);
    }

    /** Short class name (without package). */
    public static String shortName(String className) {
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(dot + 1) : className;
    }

    /**
     * Returns the title-level entry for the screen, auto-creating the full
     * branch (mod node -> menu node -> title node) on first access. New nodes
     * inherit from their parent and copy its current values as a starting point.
     */
    public static Entry entryFor(Screen screen) {
        ensureBranch(screen);
        return entries().get(keyOf(screen));
    }

    private static void ensureBranch(Screen screen) {
        String menuClass = menuClassOf(screen);
        if (menuClass == null) {
            return; // player inventories: base entries are always seeded
        }
        String mod = modOf(menuClass);
        ensureNode(MOD_PREFIX + mod, SURVIVAL_KEY, mod, null);
        ensureNode(MENU_PREFIX + menuClass, MOD_PREFIX + mod, shortName(menuClass), null);
        String label = screen.getTitle() != null ? screen.getTitle().getString() : "";
        ensureNode(keyOf(screen), MENU_PREFIX + menuClass, label, label);
    }

    private static void ensureNode(String key, String parentKey, String label, String titleLabel) {
        Map<String, Entry> map = entries();
        Entry entry = map.get(key);
        if (entry == null) {
            Entry parent = map.get(parentKey);
            entry = new Entry(parent.x, parent.y, parent.animation, label, true, parent.scale, parentKey);
            map.put(key, entry);
            save();
        } else if ((entry.label == null || entry.label.isEmpty()) && titleLabel != null) {
            entry.label = titleLabel;
            save();
        }
    }

    /**
     * Effective values for the screen: walks up the parent chain to the first
     * node with inheritance off; the survival inventory is the final fallback.
     */
    public static Entry resolveEffective(Screen screen) {
        ensureBranch(screen);
        Entry entry = entries().get(keyOf(screen));
        while (entry != null && entry.inherit && entry.parentKey != null) {
            entry = entries().get(entry.parentKey);
        }
        if (entry == null) {
            entry = entries().get(SURVIVAL_KEY);
        }
        return entry;
    }

    /** Resets the screen's title node to inherit from its parent again. */
    public static void resetToFallback(Screen screen) {
        Entry entry = entryFor(screen);
        entry.inherit = true;
        save();
    }

    /** Raw access for the settings screen: all known entries (creates base ones). */
    public static Map<String, Entry> all() {
        return entries();
    }

    /** Persists the current state to disk. */
    public static void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Entry> e : entries().entrySet()) {
            Entry v = e.getValue();
            JsonObject obj = new JsonObject();
            obj.addProperty("x", v.x);
            obj.addProperty("y", v.y);
            if (v.animation != null && !v.animation.isEmpty()) {
                obj.addProperty("animation", v.animation);
            }
            if (v.label != null && !v.label.isEmpty()) {
                obj.addProperty("label", v.label);
            }
            obj.addProperty("inherit", v.inherit);
            obj.addProperty("scale", v.scale);
            if (v.parentKey != null && !v.parentKey.isEmpty()) {
                obj.addProperty("parent", v.parentKey);
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

        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root != null) {
                    for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                        JsonObject obj = e.getValue().getAsJsonObject();
                        int x = obj.has("x") ? obj.get("x").getAsInt() : InventoryGifConfig.X_OFFSET.get();
                        int y = obj.has("y") ? obj.get("y").getAsInt() : InventoryGifConfig.Y_OFFSET.get();
                        String animation = obj.has("animation") ? obj.get("animation").getAsString() : "";
                        String label = obj.has("label") ? obj.get("label").getAsString() : "";
                        boolean inherit = obj.has("inherit") && obj.get("inherit").getAsBoolean();
                        double scale = obj.has("scale") ? obj.get("scale").getAsDouble() : 0.0;
                        String parentKey = obj.has("parent") ? obj.get("parent").getAsString() : null;
                        entries.put(e.getKey(), new Entry(x, y, animation, label, inherit, scale, parentKey));
                    }
                }
            } catch (Exception e) {
                InventoryGif.LOGGER.warn("Failed to read animation positions from {}", FILE, e);
            }
        }

        migrateLegacyKeys();
        if (!Files.exists(FILE)) {
            save();
        }
    }

    /**
     * Upgrades older config formats:
     * - legacy class-only keys ({@code ChestMenu}) become {@code @menu:ChestMenu} nodes;
     * - title keys without a stored parent get {@code @menu:<class>} as parent.
     */
    private static void migrateLegacyKeys() {
        Map<String, Entry> map = entries();
        Map<String, Entry> migrated = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> e : map.entrySet()) {
            String key = e.getKey();
            Entry v = e.getValue();
            if (key.equals(SURVIVAL_KEY) || key.equals(CREATIVE_KEY) || key.startsWith("@")) {
                migrated.put(key, v);
                continue;
            }
            int separator = key.indexOf('|');
            if (separator < 0) {
                // Legacy shared menu entry -> menu node under its mod.
                String mod = modOf(key);
                String modKey = MOD_PREFIX + mod;
                if (!migrated.containsKey(modKey)) {
                    migrated.put(modKey, new Entry(v.x, v.y, v.animation, mod, true, 0.0, SURVIVAL_KEY));
                }
                migrated.put(MENU_PREFIX + key,
                        new Entry(v.x, v.y, v.animation, shortName(key), false, v.scale, modKey));
            } else {
                // Title entry: default its parent to the menu node.
                if (v.parentKey == null || v.parentKey.isEmpty()) {
                    v.parentKey = MENU_PREFIX + key.substring(0, separator);
                }
                migrated.put(key, v);
            }
        }
        entries = migrated;
    }

    /** Creates the two base entries (survival + creative) bound to the default animation. */
    private static void seedBaseEntries() {
        entries.put(SURVIVAL_KEY, new Entry(InventoryGifConfig.X_OFFSET.get(),
                InventoryGifConfig.Y_OFFSET.get(), AnimationLibrary.DEFAULT_NAME,
                "Survival Inventory", false, 0.0, null));
        entries.put(CREATIVE_KEY, new Entry(InventoryGifConfig.X_OFFSET.get(),
                InventoryGifConfig.Y_OFFSET.get(), AnimationLibrary.DEFAULT_NAME,
                "Creative Inventory", false, 0.0, null));
    }
}