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

    // ---------------------------------------------------------------- GIF --

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
                reader.setInput(iis, false, false);
                int n = Math.max(1, reader.getNumImages(true));

                int[] canvasSize = readCanvasSize(reader);
                int fw = canvasSize[0];
                int fh = canvasSize[1];

                NativeImage sheet = new NativeImage(fw, fh * n, true);
                long[] delays = new long[n];

                // ImageIO отдаёт каждый GIF-кадр как отдельный (часто урезанный)
                // прямоугольник, без композиции с предыдущими кадрами — поэтому
                // собираем полный канвас сами, кадр за кадром.
                BufferedImage canvas = new BufferedImage(fw, fh, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = canvas.createGraphics();
                BufferedImage previousSnapshot = null;
                int prevDisposal = 0;
                int prevLeft = 0, prevTop = 0, prevW = 0, prevH = 0;

                for (int i = 0; i < n; i++) {
                    if (prevDisposal == 2) {
                        g.setComposite(AlphaComposite.Clear);
                        g.fillRect(prevLeft, prevTop, prevW, prevH);
                        g.setComposite(AlphaComposite.SrcOver);
                    } else if (prevDisposal == 3 && previousSnapshot != null) {
                        g.setComposite(AlphaComposite.Src);
                        g.drawImage(previousSnapshot, 0, 0, null);
                        g.setComposite(AlphaComposite.SrcOver);
                    }

                    GifFrameInfo info = readGifFrameInfo(reader, i);
                    if (info.disposal() == 3) {
                        previousSnapshot = copy(canvas);
                    }

                    BufferedImage bi = reader.read(i);
                    g.setComposite(AlphaComposite.SrcOver);
                    g.drawImage(bi, info.left(), info.top(), null);

                    fillSheet(sheet, canvas, i, fw, fh);
                    delays[i] = Math.max(10L, info.delayMs());

                    prevDisposal = info.disposal();
                    prevLeft = info.left();
                    prevTop = info.top();
                    prevW = bi.getWidth();
                    prevH = bi.getHeight();
                }
                g.dispose();

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

    private record GifFrameInfo(int left, int top, int disposal, long delayMs) {
    }

    private static GifFrameInfo readGifFrameInfo(ImageReader reader, int index) {
        int left = 0, top = 0, disposal = 0;
        long delay = 100L;
        try {
            IIOMetadata metadata = reader.getImageMetadata(index);
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());

            NodeList imgDescs = root.getElementsByTagName("ImageDescriptor");
            if (imgDescs.getLength() > 0) {
                NamedNodeMap attrs = imgDescs.item(0).getAttributes();
                left = parseIntAttr(attrs, "imageLeftPosition", 0);
                top = parseIntAttr(attrs, "imageTopPosition", 0);
            }

            NodeList gceList = root.getElementsByTagName("GraphicControlExtension");
            if (gceList.getLength() > 0) {
                NamedNodeMap attrs = gceList.item(0).getAttributes();
                org.w3c.dom.Node delayAttr = attrs.getNamedItem("delayTime");
                if (delayAttr != null) {
                    delay = Integer.parseInt(delayAttr.getNodeValue()) * 10L;
                }
                org.w3c.dom.Node disposalAttr = attrs.getNamedItem("disposalMethod");
                if (disposalAttr != null) {
                    disposal = switch (disposalAttr.getNodeValue()) {
                        case "restoreToBackgroundColor" -> 2;
                        case "restoreToPrevious" -> 3;
                        case "doNotDispose" -> 1;
                        default -> 0;
                    };
                }
            }
        } catch (Exception ignored) {
        }
        return new GifFrameInfo(left, top, disposal, delay);
    }

    private static int parseIntAttr(NamedNodeMap attrs, String name, int def) {
        org.w3c.dom.Node n = attrs.getNamedItem(name);
        if (n == null) {
            return def;
        }
        try {
            return Integer.parseInt(n.getNodeValue());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int[] readCanvasSize(ImageReader reader) {
        try {
            IIOMetadata streamMeta = reader.getStreamMetadata();
            if (streamMeta != null) {
                IIOMetadataNode root = (IIOMetadataNode) streamMeta.getAsTree(streamMeta.getNativeMetadataFormatName());
                NodeList lsd = root.getElementsByTagName("LogicalScreenDescriptor");
                if (lsd.getLength() > 0) {
                    NamedNodeMap attrs = lsd.item(0).getAttributes();
                    int w = parseIntAttr(attrs, "logicalScreenWidth", -1);
                    int h = parseIntAttr(attrs, "logicalScreenHeight", -1);
                    if (w > 0 && h > 0) {
                        return new int[]{w, h};
                    }
                }
            }
        } catch (Exception ignored) {
        }
        try {
            return new int[]{reader.getWidth(0), reader.getHeight(0)};
        } catch (IOException e) {
            return new int[]{1, 1};
        }
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage c = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = c.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return c;
    }

    /** Writes a full, already-composited frame into the sheet at row {@code frameIndex}. */
    private static void fillSheet(NativeImage sheet, BufferedImage frame, int frameIndex, int fw, int fh) {
        int oy = frameIndex * fh;
        for (int y = 0; y < fh; y++) {
            for (int x = 0; x < fw; x++) {
                int argb = frame.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int gg = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                // NativeImage.setPixelRGBA packs ABGR (alpha high byte), which
                // uploads little-endian as RGBA bytes for GL_RGBA.
                sheet.setPixelRGBA(x, oy + y, (a << 24) | (b << 16) | (gg << 8) | r);
            }
        }
    }

    // ------------------------------------------------------------ common --

    private static long mtime(AnimationLibrary.AnimationMeta meta) {
        try {
            return Files.isRegularFile(meta.png()) ? Files.getLastModifiedTime(meta.png()).toMillis() : -1L;
        } catch (IOException e) {
            return -1L;
        }
    }

    public static ResourceLocation textureOf(Loaded loaded) {
        return loaded.texture;
    }

    public static int widthOf(Loaded loaded) {
        return loaded.width;
    }

    public static int heightOf(Loaded loaded) {
        return loaded.height;
    }

    /** True for a decoded GIF or AVIF (both carry explicit per-frame delays). */
    public static boolean hasFrameDelays(Loaded loaded) {
        return loaded.frameDelays != null;
    }

    public static int gifFrameWidth(Loaded loaded) {
        return loaded.frameWidth;
    }

    public static int gifFrameHeight(Loaded loaded) {
        return loaded.frameHeight;
    }

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