package ru.example.inventorygif.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client config for Inventory GIF.
 *
 * Registered with ModConfig.Type.CLIENT, so NeoForge creates and loads
 * {@code config/inventorygif-client.toml} like most NeoForge mods do.
 * All rendering parameters of the animation are configured there.
 */
public final class InventoryGifConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Horizontal offset from the inventory GUI corner (guiLeft), in GUI pixels. */
    public static final ModConfigSpec.IntValue X_OFFSET = BUILDER
            .comment("Horizontal offset of the animation from the inventory GUI corner (guiLeft), in GUI pixels.",
                    "The vanilla inventory GUI is 176 px wide, so 178 places the animation just right of it.")
            .defineInRange("xOffset", 178, -10000, 10000);

    /** Vertical offset from the inventory GUI corner (guiTop), in GUI pixels. */
    public static final ModConfigSpec.IntValue Y_OFFSET = BUILDER
            .comment("Vertical offset of the animation from the inventory GUI corner (guiTop), in GUI pixels.")
            .defineInRange("yOffset", 20, -10000, 10000);

    /** Render scale. 1.0 = native frame size, 0.75 = 96x96 px for a 128x128 frame. */
    public static final ModConfigSpec.DoubleValue SCALE = BUILDER
            .comment("Render scale of the animation. 1.0 = native 128x128 px, 0.75 = 96x96 px on screen.")
            .defineInRange("scale", 0.75, 0.05, 10.0);

    /** How long each frame is displayed, in milliseconds (100 ms = 10 fps). */
    public static final ModConfigSpec.IntValue FRAME_DURATION_MS = BUILDER
            .comment("How long each animation frame is displayed, in milliseconds (100 ms = 10 fps).")
            .defineInRange("frameDurationMs", 100, 10, 60000);

    /** Number of frames in the sprite sheet (stacked vertically). */
    public static final ModConfigSpec.IntValue FRAME_COUNT = BUILDER
            .comment("Number of frames in the sprite sheet (frames are stacked vertically).")
            .defineInRange("frameCount", 8, 1, 1024);

    /** Width and height of a single frame in pixels (frames are square). */
    public static final ModConfigSpec.IntValue FRAME_SIZE = BUILDER
            .comment("Width and height of a single animation frame in pixels (frames must be square).")
            .defineInRange("frameSize", 128, 1, 2048);

    /** Path to the sprite sheet PNG, relative to the game directory (or absolute). */
    public static final ModConfigSpec.ConfigValue<String> TEXTURE_PATH = BUILDER
            .comment("Path to the sprite sheet PNG, relative to the game directory (the instance folder),",
                    "or an absolute path. Frames must be stacked vertically.",
                    "If the file is missing, the built-in default is extracted there automatically.",
                    "The file is re-read automatically when it changes on disk.")
            .define("texturePath", "assets/inventorygif/animation.png");

    /** Which screens the animation is drawn on: "player" or "all". */
    public static final ModConfigSpec.ConfigValue<String> SCREEN_MODE = BUILDER
            .comment("Which screens show the animation:",
                    "\"player\" = survival and creative player inventory (and modded screens extending them),",
                    "\"all\" = every container screen: chests, machines, modded GUIs (accessories etc.).")
            .define("screenMode", "player");

    /** Allow moving the animation by dragging it with the left mouse button. */
    public static final ModConfigSpec.BooleanValue DRAG_TO_MOVE = BUILDER
            .comment("Allow moving the animation by dragging it with the left mouse button.",
                    "The position is remembered per container screen: screens without their own",
                    "position reuse the survival inventory one, then these xOffset/yOffset defaults.",
                    "Right-click the animation to reset the position of the current screen.")
            .define("dragToMove", true);

    /** Debug helper: draw a translucent placeholder square instead of the texture. */
    public static final ModConfigSpec.BooleanValue DEBUG_DRAW_PLACEHOLDER = BUILDER
            .comment("Debug: draw a translucent placeholder square instead of the texture,",
                    "to verify position and scale without a valid PNG.")
            .define("debugDrawPlaceholder", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private InventoryGifConfig() {
    }
}
