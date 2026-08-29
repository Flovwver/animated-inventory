package ru.example.inventorygif.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import ru.example.inventorygif.InventoryGif;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Registers sprite sheets from {@link AnimationLibrary} as dynamic textures.
 *
 * PNGs are single sprite sheets (frames stacked vertically). GIFs are decoded
 * into a single vertically-stacked sheet too, keeping each frame's own delay
 * for accurate timing. Every animation gets its own {@link Loaded} entry keyed
 * by animation name. Files are re-read automatically when they change on disk
 * (throttled).
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
        /** GIF frame delays in ms (null for PNG). */
        long[] frameDelays;
        /** GIF single frame size (for GIF only). */
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
            } else {
                loadPng(loaded);
            }
            return;
        }

        // Fallback: built-in jar texture with dimensions implied by the meta.
        loaded.texture = FALLBACK_TEXTURE;
        loaded.frameDelays = null;
        loaded.width = loaded.meta.frameWidth();
        loaded.height = loaded.meta.frameHeight() * loaded.meta.frameCount();
    }

    private static void loadPng(Loaded loaded) {
        try (InputStream in = Files.newInputStream(loaded.meta.png())) {
            NativeImage image = NativeImage.read(in);
            TextureManager tm = Minecraft.getInstance().getTextureManager();
            tm.release(loaded.texture);
            tm.register(loaded.texture, new DynamicTexture(image));
            loaded.width = image.getWidth();
            loaded.height = image.getHeight();
            loaded.frameDelays = null;
        } catch (IOException | RuntimeException e) {
            loaded.texture = FALLBACK_TEXTURE;
            loaded.frameDelays = null;
            InventoryGif.LOGGER.warn("Failed to load animation texture from {}: {}. Using built-in texture.",
                    loaded.meta.png(), e.toString());
        }
    }

    /**
     * Decodes a GIF and stacks its frames vertically into one sprite sheet
     * (like a PNG), recording each frame's own delay for accurate timing.
     */
    private static void loadGif(Loaded loaded) {
        try (InputStream in = Files.newInputStream(loaded.meta.png())) {
            ImageInputStream iis = ImageIO.createImageInputStream(in);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                iis.close();
                throw new IOException("No GIF reader available");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, false, true);
                int n = Math.max(1, reader.getNumImages(true));
                int fw = reader.getWidth(0);
                int fh = reader.getHeight(0);
                NativeImage sheet = new NativeImage(fw, fh * n, true);
                long[] delays = new long[n];
                for (int i = 0; i < n; i++) {
                    BufferedImage bi = reader.read(i);
                    fillSheet(sheet, bi, i, fw, fh);
                    delays[i] = Math.max(10L, readGifDelay(reader, i));
                }
                TextureManager tm = Minecraft.getInstance().getTextureManager();
                tm.release(loaded.texture);
                tm.register(loaded.texture, new DynamicTexture(sheet));
                loaded.width = fw;
                loaded.height = fh * n;
                loaded.frameDelays = delays;
                loaded.frameWidth = fw;
                loaded.frameHeight = fh;
                InventoryGif.LOGGER.info("Loaded GIF animation {} ({} frames {}x{})",
                        loaded.meta.png(), n, fw, fh);
            } finally {
                try {
                    reader.dispose();
                    iis.close();
                } catch (IOException ignored) {
                }
            }
        } catch (IOException | RuntimeException e) {
            loaded.texture = FALLBACK_TEXTURE;
            loaded.frameDelays = null;
            InventoryGif.LOGGER.warn("Failed to load GIF {}: {}", loaded.meta.png(), e.toString());
        }
    }

    /** Writes a BufferedImage frame into the sheet at row {@code frameIndex}. */
    private static void fillSheet(NativeImage sheet, BufferedImage bi, int frameIndex, int fw, int fh) {
        int bw = bi.getWidth();
        int bh = bi.getHeight();
        int ox = 0;
        int oy = frameIndex * fh;
        for (int y = 0; y < bh && y < fh; y++) {
            for (int x = 0; x < bw && x < fw; x++) {
                int argb = bi.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                sheet.setPixelRGBA(ox + x, oy + y, (r << 24) | (g << 16) | (b << 8) | a);
            }
        }
    }

    /** Reads a GIF's per-frame delay (in ms) from its graphic control extension. */
    private static long readGifDelay(ImageReader reader, int index) {
        try {
            IIOMetadata metadata = reader.getImageMetadata(index);
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());
            var nodes = root.getElementsByTagName("GraphicControlExtension");
            if (nodes.getLength() > 0) {
                org.w3c.dom.Node gce = nodes.item(0);
                org.w3c.dom.Node attr = gce.getAttributes().getNamedItem("delayTime");
                if (attr != null) {
                    return Integer.parseInt(attr.getNodeValue()) * 10L;
                }
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

    /** True if this animation is a decoded GIF (per-frame delays present). */
    public static boolean isGif(Loaded loaded) {
        return loaded.frameDelays != null;
    }

    /** GIF frame size (each vertical slice). */
    public static int gifFrameWidth(Loaded loaded) {
        return loaded.frameWidth;
    }

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