package ru.example.inventorygif.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import java.util.ArrayList;
import java.util.List;

/** Draws animated overlays in screen coordinates. */
public final class InventoryGifRenderer {

    static final int HANDLE_SIZE = 3;

    private InventoryGifRenderer() {
    }

    /** True if the animation is drawn on this screen under the current screenMode. */
    static boolean isAnimatedScreen(AbstractContainerScreen<?> screen) {
        final String screenMode = InventoryGifConfig.SCREEN_MODE.get().trim();
        return "all".equalsIgnoreCase(screenMode)
                || screen instanceof InventoryScreen
                || screen instanceof CreativeModeInventoryScreen;
    }

    /** On-screen width of the animation for the given spec, in GUI pixels. */
    static int scaledWidth(AnimationMapping.AnimationSpec spec) {
        return Math.round(spec.frameWidth() * spec.scale());
    }

    /** On-screen height of the animation for the given spec, in GUI pixels. */
    static int scaledHeight(AnimationMapping.AnimationSpec spec) {
        return Math.round(spec.frameHeight() * spec.scale());
    }

    /** Clamps a position so the image stays fully inside the window. */
    static int[] clampToWindow(AnimationMapping.AnimationSpec spec, int x, int y,
                               AbstractContainerScreen<?> screen) {
        Window window = net.minecraft.client.Minecraft.getInstance().getWindow();
        int cx = Mth.clamp(x, 0, Math.max(0, window.getGuiScaledWidth() - scaledWidth(spec)));
        int cy = Mth.clamp(y, 0, Math.max(0, window.getGuiScaledHeight() - scaledHeight(spec)));
        return new int[]{cx, cy};
    }

    public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor guiGraphics) {

        // screenMode from config: "player" = survival/creative inventory, "all" = any container screen.
        if (!isAnimatedScreen(screen)) {
            return;
        }

        // Drop a stale drag if its screen was closed without a release event.
        AnimationDragHandler.ensureScreen(screen);

        boolean locked = AnimationPositionStorage.entryFor(screen).locked;
        for (PlacedImage image : placeImages(screen)) {
            drawImage(guiGraphics, screen, image);
            if (!locked) {
                drawResizeHandle(guiGraphics, screen, image);
            }
        }
    }

    static int handleX(PlacedImage image) {
        return image.x() + scaledWidth(image.spec()) - HANDLE_SIZE;
    }

    static int handleY(PlacedImage image) {
        return image.y() + scaledHeight(image.spec()) - HANDLE_SIZE;
    }

    private static void drawResizeHandle(GuiGraphicsExtractor guiGraphics, AbstractContainerScreen<?> screen,
                                         PlacedImage image) {
        if (!InventoryGifConfig.DRAG_TO_MOVE.get()) {
            return;
        }
        int handleX = handleX(image);
        int handleY = handleY(image);

        guiGraphics.pose().pushMatrix();

        guiGraphics.fill(handleX, handleY, handleX + HANDLE_SIZE, handleY + HANDLE_SIZE, 0xC0FFFFFF);
        guiGraphics.outline(handleX, handleY, HANDLE_SIZE, HANDLE_SIZE, 0xFF000000);
        guiGraphics.pose().popMatrix();
    }

    /** One image laid out on the screen, ready to draw. */
    record PlacedImage(AnimationMapping.AnimationSpec spec, AnimationTextureManager.Loaded loaded,
                       int x, int y) {
    }

    /** Loads the sprite sheet for a spec (throttled internally). */
    static AnimationTextureManager.Loaded loadFor(AnimationMapping.AnimationSpec spec) {
        return AnimationTextureManager.get(spec.meta());
    }

    /**
     * Resolves and lays out all images of the screen at their own absolute
     * positions (clamped inside the window). While dragging, the dragged image
     * is overridden with the live drag position.
     */
    static List<PlacedImage> placeImages(AbstractContainerScreen<?> screen) {
        List<PlacedImage> result = new ArrayList<>();
        Window window = net.minecraft.client.Minecraft.getInstance().getWindow();

        List<AnimationMapping.ImageSpec> specs = AnimationMapping.resolveAll(screen);
        int[] drag = AnimationDragHandler.activeDragImage(screen);

        for (int i = 0; i < specs.size(); i++) {
            AnimationMapping.ImageSpec spec = specs.get(i);
            int x = Mth.clamp(spec.x(), 0, Math.max(0, window.getGuiScaledWidth() - scaledWidth(spec.spec())));
            int y = Mth.clamp(spec.y(), 0, Math.max(0, window.getGuiScaledHeight() - scaledHeight(spec.spec())));
            if (drag != null && drag[0] == i) {
                x = Mth.clamp(drag[1], 0, Math.max(0, window.getGuiScaledWidth() - scaledWidth(spec.spec())));
                y = Mth.clamp(drag[2], 0, Math.max(0, window.getGuiScaledHeight() - scaledHeight(spec.spec())));
            }
            Double liveScale = AnimationDragHandler.activeResizeScale(screen, i);
            AnimationMapping.AnimationSpec effectiveSpec = liveScale != null
                    ? new AnimationMapping.AnimationSpec(spec.spec().meta(), liveScale.floatValue())
                    : spec.spec();
            result.add(new PlacedImage(effectiveSpec, loadFor(effectiveSpec), x, y));
        }
        return result;
    }

    /** Draws one animation image (debug placeholder or sprite frame) at its position. */
    private static void drawImage(GuiGraphicsExtractor guiGraphics, AbstractContainerScreen<?> screen, PlacedImage image) {
        AnimationMapping.AnimationSpec spec = image.spec();
        float scale = spec.scale();
        int frameWidth = spec.frameWidth();
        int frameHeight = spec.frameHeight();
        boolean debugPlaceholder = InventoryGifConfig.DEBUG_DRAW_PLACEHOLDER.get();

        guiGraphics.pose().pushMatrix();
        // Fabric extraction callbacks use screen coordinates.
        guiGraphics.pose().translate(image.x(), image.y());
        guiGraphics.pose().scale(scale, scale);

        if (debugPlaceholder) {
            // ARGB translucent green rectangle.
            guiGraphics.fill(0, 0, frameWidth, frameHeight, 0x8000FF00);
            guiGraphics.pose().popMatrix();
            return;
        }

        if (image.loaded().frameDelays != null && image.loaded().frameDelays.length > 1) {
            // GIF: the frame index follows the per-frame delays; the frame is a
            // vertical slice of the decoded sheet (v = index * frameHeight).
            int frameIndex = AnimationTextureManager.currentGifFrame(image.loaded(), Util.getMillis());
            float v = frameIndex * AnimationTextureManager.gifFrameHeight(image.loaded());
            guiGraphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, AnimationTextureManager.textureOf(image.loaded()), 0, 0, 0f, v,
                    AnimationTextureManager.gifFrameWidth(image.loaded()),
                    AnimationTextureManager.gifFrameHeight(image.loaded()),
                    AnimationTextureManager.widthOf(image.loaded()),
                    AnimationTextureManager.heightOf(image.loaded()));
            guiGraphics.pose().popMatrix();
            return;
        }

        int frameCount = spec.frameCount();
        long frameDurationMs = spec.frameDurationMs();
        // Current frame from wall-clock time — no stored state, always loops.
        int frame = (int) ((Util.getMillis() / frameDurationMs) % frameCount);
        float v = frame * frameHeight; // frames are stacked vertically in the sheet

        // blit(texture, x, y, u, v, width, height, textureWidth, textureHeight)
        guiGraphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, AnimationTextureManager.textureOf(image.loaded()), 0, 0, 0f, v,
                frameWidth, frameHeight,
                AnimationTextureManager.widthOf(image.loaded()), AnimationTextureManager.heightOf(image.loaded()));

        guiGraphics.pose().popMatrix();
    }
}
