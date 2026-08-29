package ru.example.inventorygif.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.fml.loading.FMLPaths;
import ru.example.inventorygif.InventoryGif;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-screen animation mapping, loaded from {@code config/inventorygif-animations.json}.
 *
 * Format:
 * <pre>
 * {
 *   "comments": { ... human help, ignored by the mod ... },
 *   "default": { "texturePath": "...", "frameCount": 8, ... },
 *   "net.minecraft.world.inventory.ChestMenu": { "texturePath": "my/chest.png" }
 * }
 * </pre>
 *
 * Resolution for a screen: exact menu class of the container ({@code ChestMenu},
 * {@code FurnaceMenu}, ...) wins; then the screen class (useful for player
 * inventories, which have no distinct menu class); then {@code default};
 * then the global values from {@code config/inventorygif-client.toml}.
 *
 * Missing fields in an entry fall back to the {@code default} entry and then
 * to the global config, so an override can be as small as just a texture path.
 * The file is re-read automatically every ~2 seconds, so edits apply live.
 */
public final class AnimationMapping {

    /** Resolved animation parameters for one screen. */
    public record AnimationSpec(String texturePath, int frameCount, int frameSize,
                                long frameDurationMs, float scale) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("inventorygif-animations.json");

    /** How often the mapping file is re-checked for changes, in milliseconds. */
    private static final long CHECK_INTERVAL_MS = 2000L;

    private static final String KEY_TEXTURE = "texturePath";
    private static final String KEY_FRAMES = "frameCount";
    private static final String KEY_FRAME_SIZE = "frameSize";
    private static final String KEY_DURATION = "frameDurationMs";
    private static final String KEY_SCALE = "scale";

    private static Map<String, JsonObject> entries = Map.of();
    private static long loadedMtime = Long.MIN_VALUE;
    private static long nextCheckMs;

    private AnimationMapping() {
    }

    /** Re-reads the mapping file if it changed on disk; throttled, call every frame. */
    public static void tick() {
        long now = System.currentTimeMillis();
        if (now < nextCheckMs) {
            return;
        }
        nextCheckMs = now + CHECK_INTERVAL_MS;

        long mtime = mtime();
        if (mtime == loadedMtime) {
            return;
        }
        load(mtime);
    }

    /** Clears cached entries so the next {@link #tick()} reloads the file. */
    public static void invalidate() {
        loadedMtime = Long.MIN_VALUE;
    }

    /** Mapping key for the container menu of the screen (null-safe). */
    public static String menuKey(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> container && container.getMenu() != null) {
            return container.getMenu().getClass().getName();
        }
        return screen.getClass().getName();
    }

    /** Resolves the animation parameters for the given screen. */
    public static AnimationSpec resolve(AbstractContainerScreen<?> screen) {
        tick();

        JsonObject entry = null;
        JsonObject fallback = entries.get("default");

        if (screen.getMenu() != null) {
            entry = entries.get(screen.getMenu().getClass().getName());
        }
        if (entry == null) {
            entry = entries.get(screen.getClass().getName());
        }

        String texturePath = str(KEY_TEXTURE, entry, fallback, InventoryGifConfig.TEXTURE_PATH.get().trim());
        int frameCount = integer(KEY_FRAMES, entry, fallback, InventoryGifConfig.FRAME_COUNT.get());
        int frameSize = integer(KEY_FRAME_SIZE, entry, fallback, InventoryGifConfig.FRAME_SIZE.get());
        long frameDuration = integer(KEY_DURATION, entry, fallback,
                (int) InventoryGifConfig.FRAME_DURATION_MS.get());
        float scale = (float) dbl(KEY_SCALE, entry, fallback, InventoryGifConfig.SCALE.get());

        // Guard against nonsense values written by hand into the JSON.
        frameCount = Math.max(1, Math.min(1024, frameCount));
        frameSize = Math.max(1, Math.min(2048, frameSize));
        frameDuration = Math.max(10, Math.min(60000, frameDuration));
        scale = Math.max(0.05f, Math.min(10.0f, scale));

        return new AnimationSpec(texturePath, frameCount, frameSize, frameDuration, scale);
    }

    private static String str(String key, @Nullable JsonObject entry, @Nullable JsonObject fallback,
                              String global) {
        String value = get(entry, key);
        if (value != null) {
            return value;
        }
        value = get(fallback, key);
        return value != null ? value : global;
    }

    private static int integer(String key, @Nullable JsonObject entry, @Nullable JsonObject fallback,
                               int global) {
        Double value = getNumber(entry, key);
        if (value != null) {
            return value.intValue();
        }
        value = getNumber(fallback, key);
        return value != null ? value.intValue() : global;
    }

    private static double dbl(String key, @Nullable JsonObject entry, @Nullable JsonObject fallback,
                              double global) {
        Double value = getNumber(entry, key);
        if (value != null) {
            return value;
        }
        value = getNumber(fallback, key);
        return value != null ? value : global;
    }

    @Nullable
    private static String get(@Nullable JsonObject obj, String key) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    @Nullable
    private static Double getNumber(@Nullable JsonObject obj, String key) {
        try {
            if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
                return obj.get(key).getAsDouble();
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private static long mtime() {
        try {
            return Files.isRegularFile(FILE) ? Files.getLastModifiedTime(FILE).toMillis() : -1L;
        } catch (IOException e) {
            return -1L;
        }
    }

    private static void load(long mtime) {
        loadedMtime = mtime;

        if (!Files.isRegularFile(FILE)) {
            entries = Map.of();
            writeTemplate();
            return;
        }

        Map<String, JsonObject> parsed = new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(FILE)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root != null) {
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    if (e.getValue().isJsonObject()) {
                        parsed.put(e.getKey(), e.getValue().getAsJsonObject());
                    }
                }
            }
            entries = parsed;
        } catch (Exception e) {
            InventoryGif.LOGGER.warn("Failed to read animation mapping from {}", FILE, e);
        }
    }

    /** Writes a self-documenting template with commented-out examples on first run. */
    private static void writeTemplate() {
        String template = """
                {
                  "comments": {
                    "about": "Per-screen animation overrides. Key = full class name of the container menu (e.g. net.minecraft.world.inventory.ChestMenu) or of the screen (for player inventories). Entry named \\"default\\" applies to every screen without its own entry.",
                    "fields": "texturePath (relative to the game folder or absolute), frameCount, frameSize, frameDurationMs, scale. Missing fields fall back to \\"default\\", then to inventorygif-client.toml.",
                    "reload": "This file is re-read automatically about every 2 seconds.",
                    "example_chest": "\\"net.minecraft.world.inventory.ChestMenu\\": { \\"texturePath\\": \\"inventorygif/chest.png\\", \\"frameCount\\": 8, \\"frameSize\\": 128 }",
                    "example_survival_inventory": "\\"net.minecraft.client.gui.screens.inventory.InventoryScreen\\": { \\"texturePath\\": \\"inventorygif/player.png\\" }"
                  }
                }
                """;
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, template);
        } catch (IOException e) {
            InventoryGif.LOGGER.warn("Could not write animation mapping template to {}: {}", FILE, e.toString());
        }
    }
}

