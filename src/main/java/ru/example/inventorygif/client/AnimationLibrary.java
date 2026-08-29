package ru.example.inventorygif.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.neoforged.fml.loading.FMLPaths;
import ru.example.inventorygif.InventoryGif;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Library of user animations living in {@code <game>/inventorygif/animations/}.
 *
 * Every animation is a {@code name.png} sprite sheet or a {@code name.gif}, plus
 * an optional {@code name.json} metadata file (JSON wins over the derived/self
 * described values):
 * <pre>{ "frameCount": 8, "frameWidth": 128, "frameHeight": 128, "frameDurationMs": 100 }</pre>
 *
 * PNG frames may be non-square (frameWidth and frameHeight are independent). If
 * the JSON is missing, the frame size is derived from the image itself (frameWidth =
 * image width, frameHeight = image height / default frame count). GIFs are self
 * describing: frames and per-frame delays come straight from the file, so a GIF
 * plays at its original speed.
 *
 * On first run the folder is created and the built-in example animation (PNG +
 * JSON) is extracted into it, so the user sees a working example of the format.
 * The folder is re-scanned automatically every ~2 seconds, so dropping a new
 * PNG/GIF + JSON makes it available in the settings screen without a restart.
 */
public final class AnimationLibrary {

    /** Metadata of one animation sprite sheet. */
    public record AnimationMeta(String name, Path png, int frameCount, int frameWidth,
                                int frameHeight, long frameDurationMs, boolean gif) {
    }

    /** Name of the built-in example animation (extracted on first run). */
    public static final String DEFAULT_NAME = "animation";

    private static final Gson GSON = new Gson();
    private static final Path DIR = FMLPaths.GAMEDIR.get().resolve("inventorygif").resolve("animations");

    private static final String EXAMPLE_PNG_CLASSPATH = "/assets/inventorygif/textures/gui/animation.png";
    private static final String EXAMPLE_JSON_CLASSPATH = "/assets/inventorygif/example_animation.json";

    /** How often the folder is re-scanned, in milliseconds. */
    private static final long RESCAN_INTERVAL_MS = 2000L;

    private static final Map<String, AnimationMeta> animations = new LinkedHashMap<>();
    private static long nextScanMs;
    private static boolean exampleExtracted;

    private AnimationLibrary() {
    }

    /** All known animations, keyed by name. Scans the folder on first call, then throttled. */
    public static Map<String, AnimationMeta> animations() {
        long now = System.currentTimeMillis();
        if (now >= nextScanMs) {
            nextScanMs = now + RESCAN_INTERVAL_MS;
            scan();
        }
        return animations;
    }

    /** Metadata of the given animation, or null if it does not exist on disk. */
    @Nullable
    public static AnimationMeta get(String name) {
        return animations().get(name);
    }

    /** True if the animation exists in the folder. */
    public static boolean exists(String name) {
        return animations().containsKey(name);
    }

    /** Writes the metadata JSON next to the animation's PNG and rescans. */
    public static void writeMeta(String name, int frameCount, int frameWidth,
                                 int frameHeight, long frameDurationMs) {
        AnimationMeta meta = animations().get(name);
        if (meta == null) {
            return;
        }
        writeMetaFile(meta.png().resolveSibling(name + ".json"),
                frameCount, frameWidth, frameHeight, frameDurationMs);
        forceRescan();
    }

    /** Forces the next {@link #animations()} call to rescan the folder. */
    public static void forceRescan() {
        nextScanMs = 0;
    }

    /** Writes the metadata JSON with pretty printing; clamps the values. */
    private static void writeMetaFile(Path json, int frameCount, int frameWidth,
                                      int frameHeight, long frameDurationMs) {
        JsonObject obj = new JsonObject();
        obj.addProperty("frameCount", Math.max(1, Math.min(1024, frameCount)));
        obj.addProperty("frameWidth", Math.max(1, Math.min(2048, frameWidth)));
        obj.addProperty("frameHeight", Math.max(1, Math.min(2048, frameHeight)));
        obj.addProperty("frameDurationMs", Math.max(10, Math.min(60000, frameDurationMs)));
        try {
            Files.writeString(json, new GsonBuilder().setPrettyPrinting().create().toJson(obj));
            InventoryGif.LOGGER.info("Wrote animation meta {}", json);
        } catch (IOException e) {
            InventoryGif.LOGGER.warn("Could not write animation meta {}: {}", json, e.toString());
        }
    }

    private static void scan() {
        try {
            Files.createDirectories(DIR);
        } catch (IOException e) {
            InventoryGif.LOGGER.warn("Could not create animations folder {}: {}", DIR, e.toString());
            return;
        }

        if (!exampleExtracted) {
            exampleExtracted = true;
            extractExample();
        }

        Map<String, AnimationMeta> found = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(DIR)) {
            files.filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".png") || n.endsWith(".gif");
                    })
                    .sorted()
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        int dot = fileName.lastIndexOf('.');
                        String name = dot >= 0 ? fileName.substring(0, dot) : fileName;
                        AnimationMeta meta = readMeta(name, p);
                        if (meta != null) {
                            found.put(name, meta);
                        }
                    });
        } catch (IOException e) {
            InventoryGif.LOGGER.warn("Could not scan animations folder {}: {}", DIR, e.toString());
            return;
        }

        animations.clear();
        animations.putAll(found);
    }

    @Nullable
    private static AnimationMeta readMeta(String name, Path file) {
        boolean isGif = file.getFileName().toString().toLowerCase().endsWith(".gif");

        int frameCount;
        int frameWidth;
        int frameHeight;
        long frameDurationMs = InventoryGifConfig.FRAME_DURATION_MS.get();

        if (isGif) {
            int[] gifHdr = gifHeader(file);
            if (gifHdr == null) {
                return null;
            }
            frameCount = Math.max(1, countGifFrames(file));
            frameWidth = gifHdr[0];
            frameHeight = gifHdr[1];
        } else {
            int[] dims = imageSize(file);
            if (dims == null) {
                return null;
            }
            int defaultFrameCount = Math.max(1, InventoryGifConfig.FRAME_COUNT.get());
            frameCount = defaultFrameCount;
            frameWidth = dims[0];
            frameHeight = Math.max(1, (int) Math.ceil(dims[1] / (double) defaultFrameCount));
        }

        // Optional JSON sidecar overrides the derived values. For PNG it is
        // auto-created with the derived values as a working example; GIFs are
        // self describing, so the file is only read if the user added it.
        Path json = file.resolveSibling(name + ".json");
        if (!isGif && !Files.isRegularFile(json)) {
            writeMetaFile(json, frameCount, frameWidth, frameHeight, frameDurationMs);
        }
        if (Files.isRegularFile(json)) {
            try (Reader reader = Files.newBufferedReader(json)) {
                JsonObject obj = GSON.fromJson(reader, JsonObject.class);
                if (obj != null) {
                    if (obj.has("frameCount")) {
                        frameCount = obj.get("frameCount").getAsInt();
                    }
                    if (obj.has("frameWidth")) {
                        frameWidth = obj.get("frameWidth").getAsInt();
                    }
                    if (obj.has("frameHeight")) {
                        frameHeight = obj.get("frameHeight").getAsInt();
                    }
                    if (obj.has("frameDurationMs")) {
                        frameDurationMs = obj.get("frameDurationMs").getAsLong();
                    }
                }
            } catch (Exception e) {
                InventoryGif.LOGGER.warn("Bad animation meta {}: {}", json, e.toString());
            }
        }

        frameCount = Math.max(1, Math.min(1024, frameCount));
        frameWidth = Math.max(1, Math.min(2048, frameWidth));
        frameHeight = Math.max(1, Math.min(2048, frameHeight));
        frameDurationMs = Math.max(10, Math.min(60000, frameDurationMs));
        return new AnimationMeta(name, file, frameCount, frameWidth, frameHeight, frameDurationMs, isGif);
    }

    /** Reads GIF logical screen width and height from the header (no decode). */
    @Nullable
    private static int[] gifHeader(Path file) {
        // GIF header: 6-byte signature, then logical screen width(2 LE), height(2 LE).
        try (InputStream in = Files.newInputStream(file)) {
            byte[] header = in.readNBytes(10);
            if (header.length < 10) {
                return null;
            }
            int width = (header[6] & 0xFF) | ((header[7] & 0xFF) << 8);
            int height = (header[8] & 0xFF) | ((header[9] & 0xFF) << 8);
            return width > 0 && height > 0 ? new int[]{width, height} : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Counts GIF frames by walking the block structure without fully decoding.
     * Returns 1 on any parsing problem so the animation still renders.
     */
    private static int countGifFrames(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            skip(in, 6); // signature
            byte[] ls = in.readNBytes(7); // logical screen descriptor
            if (ls.length < 7) {
                return 1;
            }
            int flags = ls[4] & 0xFF;
            if ((flags & 0x80) != 0) {
                skip(in, 3L * (1 << ((flags & 0x07) + 1))); // global color table
            }
            int frames = 0;
            while (true) {
                int id = in.read();
                if (id < 0 || id == 0x3B) { // EOF or trailer
                    break;
                }
                if (id == 0x2C) { // image descriptor -> one frame
                    frames++;
                    byte[] desc = in.readNBytes(9);
                    if (desc.length < 9) {
                        break;
                    }
                    int f = desc[8] & 0xFF;
                    if ((f & 0x80) != 0) {
                        skip(in, 3L * (1 << ((f & 0x07) + 1))); // local color table
                    }
                    if (in.read() < 0) { // LZW min code size
                        break;
                    }
                    skipSubBlocks(in);
                } else if (id == 0x21) { // extension block
                    if (in.read() < 0) {
                        break;
                    }
                    skipSubBlocks(in);
                } else {
                    break; // unexpected byte
                }
            }
            return Math.max(1, frames);
        } catch (IOException e) {
            return 1;
        }
    }

    private static void skipSubBlocks(InputStream in) throws IOException {
        while (true) {
            int size = in.read();
            if (size <= 0) {
                return;
            }
            skip(in, size);
        }
    }

    private static void skip(InputStream in, long n) throws IOException {
        long remaining = n;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
            } else if (in.read() < 0) {
                return;
            } else {
                remaining--;
            }
        }
    }

    /** Reads PNG dimensions from the file header without decoding the image. */
    @Nullable
    private static int[] imageSize(Path png) {
        // PNG header: 8-byte signature, then IHDR length(4)+type(4), then width(4), height(4).
        try (InputStream in = Files.newInputStream(png)) {
            byte[] header = in.readNBytes(24);
            if (header.length < 24) {
                return null;
            }
            int width = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16)
                    | ((header[18] & 0xFF) << 8) | (header[19] & 0xFF);
            int height = ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16)
                    | ((header[22] & 0xFF) << 8) | (header[23] & 0xFF);
            return width > 0 && height > 0 ? new int[]{width, height} : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Extracts the built-in example PNG + JSON so the user sees the expected format. */
    private static void extractExample() {
        extract(EXAMPLE_PNG_CLASSPATH, DIR.resolve(DEFAULT_NAME + ".png"));
        extract(EXAMPLE_JSON_CLASSPATH, DIR.resolve(DEFAULT_NAME + ".json"));
    }

    private static void extract(String classpath, Path target) {
        if (Files.exists(target)) {
            return;
        }
        try (InputStream in = AnimationLibrary.class.getResourceAsStream(classpath)) {
            if (in == null) {
                return;
            }
            Files.copy(in, target);
            InventoryGif.LOGGER.info("Extracted example animation to {}", target);
        } catch (IOException | RuntimeException e) {
            InventoryGif.LOGGER.warn("Could not extract example animation to {}: {}", target, e.toString());
        }
    }
}