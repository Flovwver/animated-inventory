package ru.example.inventorygif.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import ru.example.inventorygif.InventoryGif;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads animation sprite sheets from the instance (game) directory instead of the mod jar,
 * and re-loads them automatically when files change on disk.
 *
 * Every distinct texture path (from the per-screen mapping or the global config)
 * gets its own {@link Loaded} entry: an externally registered DynamicTexture when
 * the PNG could be read, or the built-in jar texture as a fallback. Paths are
 * registered under ResourceLocations like {@code inventorygif:anim_0}.
 *
 * The default path (from {@link InventoryGifConfig#TEXTURE_PATH}) additionally gets
 * the built-in sprite sheet extracted next to it when missing, so the user
 * immediately sees where to drop a custom PNG.
 */
public final class AnimationTextureManager {

    /** Built-in fallback texture inside the mod jar. */
    private static final ResourceLocation FALLBACK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(InventoryGif.MODID, "textures/gui/animation.png");

    /** Class-path location of the built-in default, used for auto-extraction. */
    private static final String DEFAULT_TEXTURE_CLASSPATH = "/assets/inventorygif/textures/gui/animation.png";

    /** How often texture files are re-checked for changes, in milliseconds. */
    private static final long CHECK_INTERVAL_MS = 500L;

    /** One loaded (or failed-over) sprite sheet. */
    public static final class Loaded {
        ResourceLocation texture = FALLBACK_TEXTURE;
        int width = 128;
        int height = 1024;
        Path path;
        long mtime = Long.MIN_VALUE;
    }

    /** texturePath (as configured) -> loaded texture state. */
    private static final Map<String, Loaded> POOL = new HashMap<>();

    private static long nextCheckMs;

    private AnimationTextureManager() {
    }

    /**
     * Returns the loaded texture for the given configured path, reloading it first
     * if the file changed on disk since the last check (throttled per call).
     */
    public static Loaded get(String texturePath) {
        Loaded loaded = POOL.computeIfAbsent(texturePath, AnimationTextureManager::load);

        long now = System.currentTimeMillis();
        if (now >= nextCheckMs) {
            nextCheckMs = now + CHECK_INTERVAL_MS;
            long mtime = mtime(loaded.path);
            if (mtime != loaded.mtime) {
                reload(loaded);
            }
        }
        return loaded;
    }

    /** Clears the pool; everything is reloaded lazily on next access. */
    public static void invalidate() {
        for (Loaded loaded : POOL.values()) {
            Minecraft.getInstance().getTextureManager().release(loaded.texture);
        }
        POOL.clear();
    }

    private static Loaded load(String texturePath) {
        Loaded loaded = new Loaded();
        loaded.path = resolvePath(texturePath);
        loaded.texture = ResourceLocation.fromNamespaceAndPath(
                InventoryGif.MODID, "anim_" + POOL.size());
        reload(loaded);
        return loaded;
    }

    private static void reload(Loaded loaded) {
        loaded.mtime = mtime(loaded.path);

        if (!Files.isRegularFile(loaded.path) && isDefaultPath(loaded.path)) {
            extractDefaultTexture(loaded.path);
        }

        if (Files.isRegularFile(loaded.path)) {
            try (InputStream in = Files.newInputStream(loaded.path)) {
                NativeImage image = NativeImage.read(in);
                TextureManager textureManager = Minecraft.getInstance().getTextureManager();
                textureManager.release(loaded.texture);
                textureManager.register(loaded.texture, new DynamicTexture(image));
                loaded.width = image.getWidth();
                loaded.height = image.getHeight();
                InventoryGif.LOGGER.info("Loaded animation texture from {} ({}x{})",
                        loaded.path, loaded.width, loaded.height);
                return;
            } catch (IOException | RuntimeException e) {
                InventoryGif.LOGGER.warn("Failed to load animation texture from {}: {}. Using built-in texture.",
                        loaded.path, e.toString());
            }
        }

        // Fallback: built-in jar texture with dimensions implied by the config.
        loaded.texture = FALLBACK_TEXTURE;
        loaded.width = InventoryGifConfig.FRAME_SIZE.get();
        loaded.height = InventoryGifConfig.FRAME_SIZE.get() * InventoryGifConfig.FRAME_COUNT.get();
    }

    /** The default texture (from the global config) also gets the auto-extract helper. */
    private static boolean isDefaultPath(Path path) {
        return path.equals(resolvePath(InventoryGifConfig.TEXTURE_PATH.get().trim()));
    }

    private static Path resolvePath(String raw) {
        Path path;
        try {
            path = Path.of(raw);
        } catch (InvalidPathException e) {
            InventoryGif.LOGGER.warn("Invalid texturePath '{}', falling back to default", raw);
            path = Path.of("assets", "inventorygif", "animation.png");
        }
        return path.isAbsolute() ? path : FMLPaths.GAMEDIR.get().resolve(path);
    }

    private static long mtime(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.getLastModifiedTime(path).toMillis() : -1L;
        } catch (IOException e) {
            return -1L;
        }
    }

    /** Copies the built-in sprite sheet from the mod jar to the configured location, best effort. */
    private static void extractDefaultTexture(Path path) {
        try (InputStream in = AnimationTextureManager.class.getResourceAsStream(DEFAULT_TEXTURE_CLASSPATH)) {
            if (in == null) {
                return;
            }
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(in, path);
            InventoryGif.LOGGER.info("Extracted default animation texture to {}", path);
        } catch (IOException | RuntimeException e) {
            InventoryGif.LOGGER.warn("Could not extract default animation texture to {}: {}", path, e.toString());
        }
    }

    /** Texture to draw with (external file or built-in fallback). */
    public static ResourceLocation textureOf(Loaded loaded) {
        return loaded.texture;
    }

    /** Pixel width of the loaded sprite sheet. */
    public static int widthOf(Loaded loaded) {
        return loaded.width;
    }

    /** Pixel height of the loaded sprite sheet. */
    public static int heightOf(Loaded loaded) {
        return loaded.height;
    }
}
