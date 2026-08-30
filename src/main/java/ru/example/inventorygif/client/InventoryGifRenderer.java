package ru.example.inventorygif.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import java.util.ArrayList;
import java.util.List;
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
 * Position resolution (see {@link #resolveOffsets}): the position stored in the
 * per-screen entry wins; new menus inherit the survival inventory position on
 * first open. Dragging is handled by {@link AnimationDragHandler}; positions and
 * the per-screen animation binding live in {@link AnimationPositionStorage}.
 *
 * Which animation is drawn is resolved by {@link AnimationMapping} from the
 * per-screen binding; sprite sheets come from {@link AnimationLibrary}
 * ({@code <game>/inventorygif/animations/}) and are registered by
 * {@link AnimationTextureManager}.
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
        Window window = screen.getMinecraft().getWindow();
        int cx = Mth.clamp(x, 0, Math.max(0, window.getGuiScaledWidth() - scaledWidth(spec)));
        int cy = Mth.clamp(y, 0, Math.max(0, window.getGuiScaledHeight() - scaledHeight(spec)));
        return new int[]{cx, cy};
    }

    @SubscribeEvent
    public static void onRenderInventoryForeground(ContainerScreenEvent.Render.Foreground event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();

        // screenMode from config: "player" = survival/creative inventory, "all" = any container screen.
        if (!isAnimatedScreen(screen)) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();

        // Drop a stale drag if its screen was closed without a release event.
        AnimationDragHandler.ensureScreen(screen);

        for (PlacedImage image : placeImages(screen)) {
            drawImage(guiGraphics, screen, image);
        }
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
        Window window = screen.getMinecraft().getWindow();

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
            result.add(new PlacedImage(spec.spec(), loadFor(spec.spec()), x, y));
        }
        return result;
    }

    /** Draws one animation image (debug placeholder or sprite frame) at its position. */
    private static void drawImage(GuiGraphics guiGraphics, AbstractContainerScreen<?> screen, PlacedImage image) {
        AnimationMapping.AnimationSpec spec = image.spec();
        float scale = spec.scale();
        int frameWidth = spec.frameWidth();
        int frameHeight = spec.frameHeight();
        boolean debugPlaceholder = InventoryGifConfig.DEBUG_DRAW_PLACEHOLDER.get();

        guiGraphics.pose().pushPose();
        // ContainerScreenEvent.Render.Foreground fires while the pose is still
        // translated by (leftPos, topPos), so convert the screen-space origin
        // back into that coordinate space, otherwise the sprite would be drawn
        // at twice the GUI offset (visible position != hit-test position).
        guiGraphics.pose().translate(image.x() - screen.getGuiLeft(), image.y() - screen.getGuiTop(), 0);
        guiGraphics.pose().scale(scale, scale, 1.0f);

        if (debugPlaceholder) {
            // ARGB translucent green rectangle.
            guiGraphics.fill(0, 0, frameWidth, frameHeight, 0x8000FF00);
            guiGraphics.pose().popPose();
            return;
        }

        if (image.loaded().frameDelays != null && image.loaded().frameDelays.length > 1) {
            // GIF: the frame index follows the per-frame delays; the frame is a
            // vertical slice of the decoded sheet (v = index * frameHeight).
            int frameIndex = AnimationTextureManager.currentGifFrame(image.loaded(), Util.getMillis());
            float v = frameIndex * AnimationTextureManager.gifFrameHeight(image.loaded());
            guiGraphics.blit(AnimationTextureManager.textureOf(image.loaded()), 0, 0, 0f, v,
                    AnimationTextureManager.gifFrameWidth(image.loaded()),
                    AnimationTextureManager.gifFrameHeight(image.loaded()),
                    AnimationTextureManager.widthOf(image.loaded()),
                    AnimationTextureManager.heightOf(image.loaded()));
            guiGraphics.pose().popPose();
            return;
        }

        int frameCount = spec.frameCount();
        long frameDurationMs = spec.frameDurationMs();
        // Current frame from wall-clock time — no stored state, always loops.
        int frame = (int) ((Util.getMillis() / frameDurationMs) % frameCount);
        float v = frame * frameHeight; // frames are stacked vertically in the sheet

        // blit(texture, x, y, u, v, width, height, textureWidth, textureHeight)
        guiGraphics.blit(AnimationTextureManager.textureOf(image.loaded()), 0, 0, 0f, v,
                frameWidth, frameHeight,
                AnimationTextureManager.widthOf(image.loaded()), AnimationTextureManager.heightOf(image.loaded()));

        guiGraphics.pose().popPose();
    }
}
