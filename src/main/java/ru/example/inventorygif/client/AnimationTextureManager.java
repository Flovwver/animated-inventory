package ru.example.inventorygif.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import ru.example.inventorygif.InventoryGif;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Registers sprite sheets from {@link AnimationLibrary} as dynamic textures.
 *
 * Every animation gets its own {@link Loaded} entry keyed by animation name:
 * an externally registered DynamicTexture when the PNG could be read, or the
 * built-in jar texture as a fallback. Files are re-read automatically when
 * they change on disk (throttled).
 */
public final class AnimationTextureManager {

    /** Built-in fallback texture inside the mod jar. */
    private static final ResourceLocation FALLBACK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(InventoryGif.MODID, "textures/gui/animation.png");

    /** How often texture files are re-checked for changes, in milliseconds. */
    private static final long CHECK_INTERVAL_MS = 500L;

    /** One loaded (or failed-over) sprite sheet. */
    public static final class Loaded {
        ResourceLocation texture = FALLBACK_TEXTURE;
        int width = 128;
        int height = 1024;
        long mtime = Long.MIN_VALUE;
        AnimationLibrary.AnimationMeta meta;
    }

    /** animation name -> loaded texture state. */
    private static final Map<String, Loaded> POOL = new HashMap<>();

    private static long nextCheckMs;

    private AnimationTextureManager() {
    }

    /**
     * Returns the loaded texture for the given animation metadata, reloading it
     * first if the file changed on disk since the last check (throttled per call).
     */
    public static Loaded get(AnimationLibrary.AnimationMeta meta) {
        Loaded loaded = POOL.computeIfAbsent(meta.name(), name -> load(meta));

        long now = System.currentTimeMillis();
        if (now >= nextCheckMs) {
            nextCheckMs = now + CHECK_INTERVAL_MS;
            long mtime = mtime(meta);
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

    private static Loaded load(AnimationLibrary.AnimationMeta meta) {
        Loaded loaded = new Loaded();
        loaded.meta = meta;
        loaded.texture = ResourceLocation.fromNamespaceAndPath(
                InventoryGif.MODID, "anim_" + POOL.size());
        reload(loaded);
        return loaded;
    }

    private static void reload(Loaded loaded) {
        loaded.mtime = mtime(loaded.meta);

        if (Files.isRegularFile(loaded.meta.png())) {
            try (InputStream in = Files.newInputStream(loaded.meta.png())) {
                NativeImage image = NativeImage.read(in);
                TextureManager textureManager = Minecraft.getInstance().getTextureManager();
                textureManager.release(loaded.texture);
                textureManager.register(loaded.texture, new DynamicTexture(image));
                loaded.width = image.getWidth();
                loaded.height = image.getHeight();
                return;
            } catch (IOException | RuntimeException e) {
                InventoryGif.LOGGER.warn("Failed to load animation texture from {}: {}. Using built-in texture.",
                        loaded.meta.png(), e.toString());
            }
        }

        // Fallback: built-in jar texture with dimensions implied by the meta.
        loaded.texture = FALLBACK_TEXTURE;
        loaded.width = loaded.meta.frameWidth();
        loaded.height = loaded.meta.frameHeight() * loaded.meta.frameCount();
    }

    private static long mtime(AnimationLibrary.AnimationMeta meta) {
        try {
            return Files.isRegularFile(meta.png()) ? Files.getLastModifiedTime(meta.png()).toMillis() : -1L;
        } catch (IOException e) {
            return -1L;
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