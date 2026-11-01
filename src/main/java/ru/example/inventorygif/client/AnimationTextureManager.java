package ru.example.inventorygif.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

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
    private static final Identifier FALLBACK_TEXTURE =
            Identifier.fromNamespaceAndPath(InventoryGif.MODID, "textures/gui/animation.png");

    /** How often texture files are re-checked for changes, in milliseconds. */
    private static final long CHECK_INTERVAL_MS = 500L;

    /** Executor for asynchronous GIF decoding. */
    private static final ExecutorService GIF_DECODE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "InventoryGif-GIF-Decoder");
        t.setDaemon(true);
        return t;
    });

    /** GIF names currently being decoded asynchronously. */
    private static final Set<String> GIF_DECODING = ConcurrentHashMap.newKeySet();

    /** One loaded (or failed-over) sprite sheet. */
    public static final class Loaded {
        Identifier texture = FALLBACK_TEXTURE;
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

        if (!meta.equals(loaded.meta)) {
            loaded.meta = meta;
            reload(loaded);
        }

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
        GIF_DECODING.clear();
    }

    private static Loaded load(AnimationLibrary.AnimationMeta meta) {
        Loaded loaded = new Loaded();
        loaded.meta = meta;
        loaded.texture = Identifier.fromNamespaceAndPath(
                InventoryGif.MODID, "anim_" + POOL.size());
        reload(loaded);
        return loaded;
    }

    private static void reload(Loaded loaded) {
        loaded.mtime = mtime(loaded.meta);

        if (Files.isRegularFile(loaded.meta.png())) {
            if (loaded.meta.gif()) {
                loadGifAsync(loaded);
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
            tm.register(loaded.texture, new DynamicTexture(() -> "Inventory GIF", image));
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

    /**
     * Decodes a GIF asynchronously so it doesn't block the render thread.
     * The UI doesn't refresh while decoding, but frames are drawn as soon as ready.
     */
    private static void loadGifAsync(Loaded loaded) {
        String name = loaded.meta.name();
        if (!GIF_DECODING.add(name)) {
            return; // already decoding
        }
        loaded.mtime = mtime(loaded.meta);

        GIF_DECODE_EXECUTOR.execute(() -> {
            try {
                DecodedGif decoded = decodeGif(loaded.meta.png());
                Minecraft.getInstance().execute(() -> applyDecodedGif(loaded, decoded));
            } catch (Exception e) {
                InventoryGif.LOGGER.warn("Failed to load GIF {}: {}", loaded.meta.png(), e.toString());
                Minecraft.getInstance().execute(() -> {
                    loaded.texture = FALLBACK_TEXTURE;
                    loaded.frameDelays = null;
                });
            } finally {
                GIF_DECODING.remove(name);
            }
        });
    }

    /** Output of successful GIF decode. */
    private record DecodedGif(NativeImage sheet, long[] delays, int frameWidth, int frameHeight) {
    }

    /** Decodes a GIF file into frames composited onto a sheet with per-frame delays. */
    private static DecodedGif decodeGif(java.nio.file.Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
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
                return new DecodedGif(sheet, delays, fw, fh);
            } finally {
                try {
                    reader.dispose();
                    iis.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** Applies a successfully decoded GIF to the loaded state and registers its texture. */
    private static void applyDecodedGif(Loaded loaded, DecodedGif decoded) {
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        tm.release(loaded.texture);
        tm.register(loaded.texture, new DynamicTexture(() -> "Inventory GIF", decoded.sheet()));
        loaded.width = decoded.frameWidth();
        loaded.height = decoded.frameHeight() * decoded.delays().length;
        loaded.frameDelays = decoded.delays();
        loaded.frameWidth = decoded.frameWidth();
        loaded.frameHeight = decoded.frameHeight();
        InventoryGif.LOGGER.info("Loaded GIF animation {} ({} frames {}x{})",
                loaded.meta.png(), decoded.delays().length, decoded.frameWidth(), decoded.frameHeight());
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
        // Bulk raster read instead of one getRGB() call per pixel — for a
        // multi-frame GIF this is the difference between a few milliseconds and
        // a noticeable hitch, since getRGB(x, y) has per-call overhead that adds
        // up fast at hundreds of thousands of calls.
        int[] argbRow = new int[fw];
        for (int y = 0; y < fh; y++) {
            frame.getRGB(0, y, fw, 1, argbRow, 0, fw);
            for (int x = 0; x < fw; x++) {
                int argb = argbRow[x];
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int gg = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                sheet.setPixel(x, oy + y, (a << 24) | (r << 16) | (gg << 8) | b);
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

    public static Identifier textureOf(Loaded loaded) {
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