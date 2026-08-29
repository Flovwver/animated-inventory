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
        // GIF support: one texture per frame + per-frame delays.
        ResourceLocation[] frameTextures;
        long[] frameDelays;
        int frameWidth;
        int frameHeight;
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
            releaseGif(loaded);
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
            if (loaded.meta.gif()) {
                loadGif(loaded);
                return;
            }
            loadPng(loaded);
            return;
        }

        // Fallback: built-in jar texture with dimensions implied by the meta.
        loaded.frameTextures = null;
        loaded.texture = FALLBACK_TEXTURE;
        loaded.width = loaded.meta.frameWidth();
        loaded.height = loaded.meta.frameHeight() * loaded.meta.frameCount();
    }

    private static void loadPng(Loaded loaded) {
        try (InputStream in = Files.newInputStream(loaded.meta.png())) {
            NativeImage image = NativeImage.read(in);
            TextureManager textureManager = Minecraft.getInstance().getTextureManager();
            releaseGif(loaded);
            textureManager.release(loaded.texture);
            textureManager.register(loaded.texture, new DynamicTexture(image));
            loaded.width = image.getWidth();
            loaded.height = image.getHeight();
            loaded.frameTextures = null;
        } catch (IOException | RuntimeException e) {
            InventoryGif.LOGGER.warn("Failed to load animation texture from {}: {}. Using built-in texture.",
                    loaded.meta.png(), e.toString());
            loaded.frameTextures = null;
        }
    }

    /**
     * Decodes a GIF into one dynamic texture per frame with its own delay, all
     * drawn as full frames (no sprite sheet needed).
     */
    private static void loadGif(Loaded loaded) {
        try (InputStream in = Files.newInputStream(loaded.meta.png())) {
            javax.imageio.stream.ImageInputStream iis =
                    javax.imageio.ImageIO.createImageInputStream(in);
            java.util.Iterator<javax.imageio.ImageReader> readers =
                    javax.imageio.ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                iis.close();
                throw new IOException("No GIF reader available");
            }
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(iis, false, true);
                int n = Math.max(1, reader.getNumImages(true));
                releaseGif(loaded);
                TextureManager tm = Minecraft.getInstance().getTextureManager();
                loaded.frameTextures = new ResourceLocation[n];
                loaded.frameDelays = new long[n];
                loaded.frameWidth = reader.getWidth(0);
                loaded.frameHeight = reader.getHeight(0);
                for (int i = 0; i < n; i++) {
                    java.awt.image.BufferedImage bi = reader.read(i);
                    NativeImage ni = bufferedToNative(bi);
                    ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(
                            InventoryGif.MODID, "gif_" + loaded.texture + "_" + i);
                    tm.register(rl, new DynamicTexture(ni));
                    loaded.frameTextures[i] = rl;
                    loaded.frameDelays[i] = Math.max(10L, readGifDelay(reader, i));
                }
                InventoryGif.LOGGER.info("Loaded GIF animation {} ({} frames {}x{})",
                        loaded.meta.png(), n, loaded.frameWidth, loaded.frameHeight);
            } finally {
                reader.dispose();
                iis.close();
            }
        } catch (IOException | RuntimeException e) {
            InventoryGif.LOGGER.warn("Failed to load GIF {}: {}",
                    loaded.meta.png(), e.toString());
            releaseGif(loaded);
            loaded.frameTextures = null;
        }
    }

    private static void releaseGif(Loaded loaded) {
        if (loaded.frameTextures == null) {
            return;
        }
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation rl : loaded.frameTextures) {
            tm.release(rl);
        }
        loaded.frameTextures = null;
        loaded.frameDelays = null;
    }

    /** Converts a BufferedImage into a RGBA NativeImage. */
    private static NativeImage bufferedToNative(java.awt.image.BufferedImage bi) {
        NativeImage ni = new NativeImage(bi.getWidth(), bi.getHeight(), true);
        for (int y = 0; y < bi.getHeight(); y++) {
            for (int x = 0; x < bi.getWidth(); x++) {
                int argb = bi.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                ni.setPixelRGBA(x, y, (r << 24) | (g << 16) | (b << 8) | a);
            }
        }
        return ni;
    }

    /** Reads a GIF's per-frame delay (in ms) from its graphic control extension. */
    private static long readGifDelay(javax.imageio.ImageReader reader, int index) {
        try {
            javax.imageio.metadata.IIOMetadata metadata = reader.getImageMetadata(index);
            javax.imageio.metadata.IIOMetadataNode root = (javax.imageio.metadata.IIOMetadataNode)
                    metadata.getAsTree(metadata.getNativeMetadataFormatName());
            org.w3c.dom.NodeList nodes = root.getElementsByTagName("GraphicControlExtension");
            if (nodes.getLength() > 0) {
                org.w3c.dom.Node gce = nodes.item(0);
                // delayTime is usually an attribute on GraphicControlExtension.
                org.w3c.dom.Node attr = gce.getAttributes().getNamedItem("delayTime");
                if (attr != null) {
                    return Integer.parseInt(attr.getNodeValue()) * 10L;
                }
                // Fallback: some formats expose it as a nested delayTime element.
                var delays = gce.getChildNodes();
                for (int i = 0; i < delays.getLength(); i++) {
                    org.w3c.dom.Node child = delays.item(i);
                    if (child.getNodeName().equals("delayTime") && child.getFirstChild() != null) {
                        return Integer.parseInt(child.getFirstChild().getNodeValue()) * 10L;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return 100L;
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

    /** True if this animation is a decoded GIF with one texture per frame. */
    public static boolean isGif(Loaded loaded) {
        return loaded.frameTextures != null;
    }

    /** Number of decoded GIF frames. */
    public static int gifFrameCount(Loaded loaded) {
        return loaded.frameTextures == null ? 0 : loaded.frameTextures.length;
    }

    /** Texture of the given GIF frame. */
    public static ResourceLocation gifFrameTexture(Loaded loaded, int index) {
        return loaded.frameTextures[index];
    }

    /** GIF frame width in pixels. */
    public static int gifFrameWidth(Loaded loaded) {
        return loaded.frameWidth;
    }

    /** GIF frame height in pixels. */
    public static int gifFrameHeight(Loaded loaded) {
        return loaded.frameHeight;
    }

    /** Currently visible GIF frame index for the given wall-clock time. */
    public static int currentGifFrame(Loaded loaded, long timeMs) {
        long[] delays = loaded.frameDelays;
        long total = 0;
        for (long d : delays) {
            total += d;
        }
        if (total <= 0) {
            return 0;
        }
        long t = Math.floorMod(timeMs, total);
        long acc = 0;
        for (int i = 0; i < delays.length; i++) {
            acc += delays[i];
            if (t < acc) {
                return i;
            }
        }
        return delays.length - 1;
    }
}