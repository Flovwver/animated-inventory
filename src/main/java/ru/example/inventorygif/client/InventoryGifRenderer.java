package ru.example.inventorygif.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import ru.example.inventorygif.InventoryGif;

/**
 * Renders the looping animation on top of container screens.
 *
 * Which screens are covered is controlled by the {@code screenMode} config option:
 * {@code "player"} (default) draws on the survival {@link InventoryScreen} and the
 * creative {@link CreativeModeInventoryScreen} (plus modded subclasses of them);
 * {@code "all"} draws on every {@link AbstractContainerScreen}, including chests,
 * machines and modded GUIs (accessories etc.).
 *
 * Why {@link ContainerScreenEvent.Render.Foreground} and not ScreenEvent.Render.Post:
 * in NeoForge 1.21.1 the render() method of AbstractContainerScreen is patched to
 * replicate Screen.render() in place (no super call), so ScreenEvent.Render.Pre/Post
 * are NOT fired for container screens. NeoForge instead fires
 * ContainerScreenEvent.Render.Background / Render.Foreground from inside
 * AbstractContainerScreen.render(). Foreground fires after all screen content but
 * before tooltips and the dragged item stack, which is exactly where an overlay
 * next to the inventory should be drawn. Note: at that point the pose matrix is
 * still translated by (leftPos, topPos), so screen-space coordinates must be
 * converted back before translating (see the render method below).
 *
 * The event is fired on the game event bus (NeoForge#EVENT_BUS), client side only.
 * This class is registered automatically by the @EventBusSubscriber annotation;
 * the bus is determined automatically (the deprecated bus= attribute is ignored).
 *
 * Position resolution (see {@link #resolveOffsets}): a position dragged and saved
 * for this exact screen wins; otherwise the position saved for the survival
 * inventory is reused; otherwise the {@code xOffset}/{@code yOffset} config
 * defaults apply. Dragging is handled by {@link AnimationDragHandler} and saved
 * positions live in {@link AnimationPositionStorage}.
 *
 * All parameters (offsets, scale, frame timing/count/size, texture path, debug flag)
 * come from the client config: {@code config/inventorygif-client.toml}
 * (see {@link InventoryGifConfig}). The sprite sheet is loaded from the instance
 * directory by {@link AnimationTextureManager}, not from inside the mod jar.
 */
@EventBusSubscriber(modid = InventoryGif.MODID, value = Dist.CLIENT)
public final class InventoryGifRenderer {

    private InventoryGifRenderer() {
    }

    /** True if the animation is drawn on this screen under the current screenMode. */
    static boolean isAnimatedScreen(AbstractContainerScreen<?> screen) {
        final String screenMode = InventoryGifConfig.SCREEN_MODE.get().trim();
        return "all".equalsIgnoreCase(screenMode)
                || screen instanceof InventoryScreen
                || screen instanceof CreativeModeInventoryScreen;
    }

    /** Animation parameters (texture, frames, scale) resolved for this screen. */
    static AnimationMapping.AnimationSpec resolveSpec(AbstractContainerScreen<?> screen) {
        return AnimationMapping.resolve(screen);
    }

    /** On-screen edge length of the animation square for the given spec, in GUI pixels. */
    static int scaledSize(AnimationMapping.AnimationSpec spec) {
        return Math.round(spec.frameSize() * spec.scale());
    }

    /**
     * Resolves the {xOffset, yOffset} for the screen: live drag value while
     * dragging, then the position saved for this exact screen, then the position
     * saved for the survival inventory, then the config defaults.
     */
    static int[] resolveOffsets(AbstractContainerScreen<?> screen) {
        int[] drag = AnimationDragHandler.activeDragOffsets(screen);
        if (drag != null) {
            return drag;
        }
        int[] saved = AnimationPositionStorage.get(screen.getClass().getName());
        if (saved != null) {
            return saved;
        }
        saved = AnimationPositionStorage.get(AnimationPositionStorage.SURVIVAL_KEY);
        if (saved != null) {
            return saved;
        }
        return new int[]{InventoryGifConfig.X_OFFSET.get(), InventoryGifConfig.Y_OFFSET.get()};
    }

    /**
     * Computes the on-screen origin {x, y} of the animation for the given offsets.
     *
     * While dragging the origin is clamped inside the window. Otherwise the
     * legacy fallback applies: if the right edge would overflow the window, the
     * animation flips to the free space on the left of the inventory GUI.
     */
    static int[] computeOrigin(AbstractContainerScreen<?> screen, int xOffset, int yOffset,
                               AnimationMapping.AnimationSpec spec) {
        Window window = screen.getMinecraft().getWindow();
        int scaled = scaledSize(spec);
        int originX = screen.getGuiLeft() + xOffset;
        int originY = screen.getGuiTop() + yOffset;

        if (AnimationDragHandler.isDragging()) {
            originX = Mth.clamp(originX, 0, Math.max(0, window.getGuiScaledWidth() - scaled));
            originY = Mth.clamp(originY, 0, Math.max(0, window.getGuiScaledHeight() - scaled));
        } else {
            if (originX + scaled > window.getGuiScaledWidth()) {
                originX = screen.getGuiLeft() - scaled - 2;
            }
            if (originY + scaled > window.getGuiScaledHeight()) {
                originY = Math.max(0, window.getGuiScaledHeight() - scaled);
            }
            // Safety net: never leave the animation fully off-screen (bad config or
            // saved value), otherwise it could never be grabbed again with the mouse.
            originX = Mth.clamp(originX, 0, Math.max(0, window.getGuiScaledWidth() - scaled));
            originY = Mth.clamp(originY, 0, Math.max(0, window.getGuiScaledHeight() - scaled));
        }
        return new int[]{originX, originY};
    }

    @SubscribeEvent
    public static void onRenderInventoryForeground(ContainerScreenEvent.Render.Foreground event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();

        // screenMode from config: "player" = survival/creative inventory, "all" = any container screen.
        if (!isAnimatedScreen(screen)) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();

        // Animation parameters: per-screen mapping, falling back to the global config.
        final AnimationMapping.AnimationSpec spec = resolveSpec(screen);
        final float scale = spec.scale();
        final long frameDurationMs = spec.frameDurationMs();
        final int frameCount = spec.frameCount();
        final int frameSize = spec.frameSize();
        final boolean debugPlaceholder = InventoryGifConfig.DEBUG_DRAW_PLACEHOLDER.get();

        // Load/reload the sprite sheet for this screen (throttled internally).
        AnimationTextureManager.Loaded loaded = AnimationTextureManager.get(spec.texturePath());

        // Drop a stale drag if its screen was closed without a release event.
        AnimationDragHandler.ensureScreen(screen);

        int[] offsets = resolveOffsets(screen);
        int[] origin = computeOrigin(screen, offsets[0], offsets[1], spec);
        int originX = origin[0];
        int originY = origin[1];

        // Current frame from wall-clock time — no stored state, always loops.
        int frame = (int) ((Util.getMillis() / frameDurationMs) % frameCount);
        float v = frame * frameSize; // frames are stacked vertically in the sheet

        guiGraphics.pose().pushPose();
        // ContainerScreenEvent.Render.Foreground fires while the pose is still
        // translated by (leftPos, topPos), so convert the screen-space origin
        // back into that coordinate space, otherwise the sprite would be drawn
        // at twice the GUI offset (visible position != hit-test position).
        guiGraphics.pose().translate(originX - screen.getGuiLeft(), originY - screen.getGuiTop(), 0);
        guiGraphics.pose().scale(scale, scale, 1.0f);

        if (debugPlaceholder) {
            // ARGB translucent green square with the frame band highlighted.
            guiGraphics.fill(0, 0, frameSize, frameSize, 0x8000FF00);
            guiGraphics.fill(0, frame * frameSize / frameCount, frameSize,
                    (frame + 1) * frameSize / frameCount, 0x80FF0000);
        } else {
            // blit(texture, x, y, u, v, width, height, textureWidth, textureHeight)
            guiGraphics.blit(AnimationTextureManager.textureOf(loaded), 0, 0, 0f, v, frameSize, frameSize,
                    AnimationTextureManager.widthOf(loaded), AnimationTextureManager.heightOf(loaded));
        }

        guiGraphics.pose().popPose();
    }
}
