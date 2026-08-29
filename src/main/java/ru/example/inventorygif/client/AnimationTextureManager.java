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

/**
 * Loads the animation sprite sheet from the instance (game) directory instead of the mod jar,
 * and re-loads it automatically when the file changes on disk.
 *
 * The path is configured via {@link InventoryGifConfig#TEXTURE_PATH} (relative to the game
 * directory by default: {@code assets/inventorygif/animation.png}). If the file is missing,
 * the built-in default sprite sheet is extracted to that location so the user immediately
 * sees where to drop a custom PNG. If loading fails for any reason, the built-in jar
 * texture is used as a fallback, so the mod always renders something sensible.
 */
public final class AnimationTextureManager {

    /** Built-in fallback texture inside the mod jar. */
    private static final ResourceLocation FALLBACK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(InventoryGif.MODID, "textures/gui/animation.png");

    /** Resource location under which the externally loaded PNG is registered. */
    private static final ResourceLocation EXTERNAL_TEXTURE_ID =
            ResourceLocation.fromNamespaceAndPath(InventoryGif.MODID, "external_animation");

    /** Class-path location of the built-in default, used for auto-extraction. */
    private static final String DEFAULT_TEXTURE_CLASSPATH = "/assets/inventorygif/textures/gui/animation.png";

    /** How often the texture file is re-checked for changes, in milliseconds. */
    private static final long CHECK_INTERVAL_MS = 500L;

    private static Path currentPath;
    private static long currentMtime = Long.MIN_VALUE;
    private static long nextCheckMs;
    private static boolean usingExternalTexture;
    private static int textureWidth = 128;
    private static int textureHeight = 1024;

    private AnimationTextureManager() {
    }

    /**
     * Called every frame from the render handler; internally throttled.
     * Reloads the texture if the configured path or the file's modification time changed.
     */
    public static void tick() {
        long now = System.currentTimeMillis();
        if (now < nextCheckMs) {
            return;
        }
        nextCheckMs = now + CHECK_INTERVAL_MS;

        Path path = resolveConfiguredPath();
        long mtime = getMtime(path);
        if (path.equals(currentPath) && mtime == currentMtime) {
            return;
        }
        currentPath = path;
        currentMtime = mtime;

        reload(path);
    }

    /** The texture to render with: the external file if loaded, otherwise the jar fallback. */
    public static ResourceLocation getTexture() {
        return usingExternalTexture ? EXTERNAL_TEXTURE_ID : FALLBACK_TEXTURE;
    }

    public static int getTextureWidth() {
        return textureWidth;
    }

    public static int getTextureHeight() {
        return textureHeight;
    }

    private static void reload(Path path) {
        if (!Files.isRegularFile(path)) {
            extractDefaultTexture(path);
        }

        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                NativeImage image = NativeImage.read(in);
                TextureManager textureManager = Minecraft.getInstance().getTextureManager();
                textureManager.release(EXTERNAL_TEXTURE_ID);
                textureManager.register(EXTERNAL_TEXTURE_ID, new DynamicTexture(image));
                usingExternalTexture = true;
                textureWidth = image.getWidth();
                textureHeight = image.getHeight();
                InventoryGif.LOGGER.info("Loaded animation texture from {} ({}x{})", path, textureWidth, textureHeight);
                return;
            } catch (IOException | RuntimeException e) {
                InventoryGif.LOGGER.warn("Failed to load animation texture from {}: {}. Using built-in texture.",
                        path, e.toString());
            }
        }

        // Fallback: built-in jar texture with dimensions implied by the config.
        usingExternalTexture = false;
        textureWidth = InventoryGifConfig.FRAME_SIZE.get();
        textureHeight = InventoryGifConfig.FRAME_SIZE.get() * InventoryGifConfig.FRAME_COUNT.get();
    }

    private static Path resolveConfiguredPath() {
        String raw = InventoryGifConfig.TEXTURE_PATH.get().trim();
        Path path;
        try {
            path = Path.of(raw);
        } catch (InvalidPathException e) {
            InventoryGif.LOGGER.warn("Invalid texturePath '{}', falling back to default", raw);
            path = Path.of("assets", "inventorygif", "animation.png");
        }
        return path.isAbsolute() ? path : FMLPaths.GAMEDIR.get().resolve(path);
    }

    private static long getMtime(Path path) {
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
}
