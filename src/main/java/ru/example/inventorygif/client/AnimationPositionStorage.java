package ru.example.inventorygif.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 * Every node holds a list of images, each with its own absolute position and
 * scale; all of them are drawn simultaneously and can be dragged individually.
 *
 * Every non-base node has {@code parentKey} and an {@code inherit} flag.
 * While {@code inherit} is true the node dynamically uses its parent's images;
 * editing any value turns inheritance off. New branches are auto-created with
 * inheritance on, so the fallback chain is always
 * title -> menu -> mod -> survival inventory -> config defaults.
 */
public final class AnimationPositionStorage {

    /** One image bound to a node, with its own absolute position and scale. */
    public static final class Image {
        public String animation;
        public int x;
        public int y;
        /** Per-image scale; <= 0 = global config scale. */
        public double scale;

        public Image(String animation, int x, int y, double scale) {
            this.animation = animation;
            this.x = x;
            this.y = y;
            this.scale = scale;
        }

        Image copy() {
            return new Image(animation, x, y, scale);
        }
    }

    /** Per-node animation settings. */
    public static final class Entry {
        /** Human-readable label, filled when the screen is first opened. */
        public String label;
        /** True = use the parent node's images dynamically. */
        public boolean inherit;
        /** Parent node key in the settings tree. */
        public String parentKey;
        /** All images of this node; each has its own position, drawn simultaneously. */
        public final List<Image> images = new ArrayList<>();

        Entry(String label, boolean inherit, String parentKey, List<Image> images) {
            this.label = label;
            this.inherit = inherit;
            this.parentKey = parentKey;
            if (images != null) {
                images.forEach(i -> this.images.add(i.copy()));
            }
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
     * inherit from their parent and copy its images as a starting point.
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
            entry = new Entry(label, true, parentKey, parent.images);
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
        return resolveEffectiveByKey(keyOf(screen));
    }

    /** Same as {@link #resolveEffective(Screen)} but for an arbitrary node key. */
    public static Entry resolveEffectiveByKey(String key) {
        Entry entry = entries().get(key);
        while (entry != null && entry.inherit && entry.parentKey != null) {
            entry = entries().get(entry.parentKey);
        }
        return entry != null ? entry : entries().get(SURVIVAL_KEY);
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
            if (v.label != null && !v.label.isEmpty()) {
                obj.addProperty("label", v.label);
            }
            obj.addProperty("inherit", v.inherit);
            if (v.parentKey != null && !v.parentKey.isEmpty()) {
                obj.addProperty("parent", v.parentKey);
            }
            JsonArray images = new JsonArray();
            for (Image img : v.images) {
                JsonObject imgObj = new JsonObject();
                imgObj.addProperty("animation", img.animation);
                imgObj.addProperty("x", img.x);
                imgObj.addProperty("y", img.y);
                if (img.scale > 0) {
                    imgObj.addProperty("scale", img.scale);
                }
                images.add(imgObj);
            }
            obj.add("images", images);
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

        // The legacy -> images migration needs sprite dimensions from the library.
        AnimationLibrary.animations();

        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root != null) {
                    for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                        JsonObject obj = e.getValue().getAsJsonObject();
                        String label = obj.has("label") ? obj.get("label").getAsString() : "";
                        boolean inherit = obj.has("inherit") && obj.get("inherit").getAsBoolean();
                        String parentKey = obj.has("parent") ? obj.get("parent").getAsString() : null;

                        List<Image> images = new ArrayList<>();
                        if (obj.has("images") && obj.get("images").isJsonArray()) {
                            for (JsonElement el : obj.getAsJsonArray("images")) {
                                if (!el.isJsonObject()) {
                                    continue;
                                }
                                JsonObject imgObj = el.getAsJsonObject();
                                String anim = imgObj.has("animation") ? imgObj.get("animation").getAsString() : "";
                                int ix = imgObj.has("x") ? imgObj.get("x").getAsInt() : 0;
                                int iy = imgObj.has("y") ? imgObj.get("y").getAsInt() : 0;
                                double iScale = imgObj.has("scale") ? imgObj.get("scale").getAsDouble() : 0.0;
                                images.add(new Image(anim, ix, iy, iScale));
                            }
                        } else {
                            // Legacy format: main image + relative extras.
                            int x = obj.has("x") ? obj.get("x").getAsInt() : InventoryGifConfig.X_OFFSET.get();
                            int y = obj.has("y") ? obj.get("y").getAsInt() : InventoryGifConfig.Y_OFFSET.get();
                            String animation = obj.has("animation") ? obj.get("animation").getAsString() : "";
                            double scale = obj.has("scale") ? obj.get("scale").getAsDouble() : 0.0;
                            JsonArray extras = obj.has("extras") && obj.get("extras").isJsonArray()
                                    ? obj.getAsJsonArray("extras") : new JsonArray();
                            images = migrateLegacyImages(animation, x, y, scale, extras);
                        }

                        entries.put(e.getKey(), new Entry(label, inherit, parentKey, images));
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
     * Converts the legacy layout (main image + extras with dx/dy relative to its
     * right edge) into the flat absolute-position image list.
     */
    private static List<Image> migrateLegacyImages(String animation, int x, int y,
                                                   double nodeScale, JsonArray extras) {
        List<Image> images = new ArrayList<>();
        String name = animation == null || animation.isEmpty()
                ? AnimationLibrary.DEFAULT_NAME : animation;
        AnimationLibrary.AnimationMeta meta = AnimationLibrary.get(name);
        if (meta == null) {
            meta = AnimationLibrary.get(AnimationLibrary.DEFAULT_NAME);
        }
        double effectiveScale = nodeScale > 0 ? nodeScale : InventoryGifConfig.SCALE.get();
        int width = meta != null ? Math.round(meta.frameWidth() * (float) effectiveScale) : 96;

        images.add(new Image(name, x, y, nodeScale));
        if (extras != null) {
            for (JsonElement el : extras) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject bObj = el.getAsJsonObject();
                String anim = bObj.has("animation") ? bObj.get("animation").getAsString() : "";
                int dx = bObj.has("dx") ? bObj.get("dx").getAsInt() : 0;
                int dy = bObj.has("dy") ? bObj.get("dy").getAsInt() : 0;
                double bScale = bObj.has("scale") ? bObj.get("scale").getAsDouble() : 0.0;
                if (anim == null || anim.isEmpty()) {
                    continue;
                }
                images.add(new Image(anim, x + width + 2 + dx, y + dy, bScale));
            }
        }
        return images;
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
                    Entry base = map.get(SURVIVAL_KEY);
                    migrated.put(modKey, new Entry(mod, true, SURVIVAL_KEY, base.images));
                }
                migrated.put(MENU_PREFIX + key,
                        new Entry(shortName(key), false, modKey, v.images));
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
        List<Image> survivalImages = new ArrayList<>();
        survivalImages.add(new Image(AnimationLibrary.DEFAULT_NAME,
                InventoryGifConfig.X_OFFSET.get(), InventoryGifConfig.Y_OFFSET.get(), 0.0));
        entries.put(SURVIVAL_KEY, new Entry("Survival Inventory", false, null, survivalImages));
        entries.put(CREATIVE_KEY, new Entry("Creative Inventory", false, null,
                List.of(new Image(AnimationLibrary.DEFAULT_NAME,
                        InventoryGifConfig.X_OFFSET.get(), InventoryGifConfig.Y_OFFSET.get(), 0.0))));
    }
}