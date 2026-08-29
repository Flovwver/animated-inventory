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

    /** Animation parameters (metadata + scale) resolved for this screen. */
    static AnimationMapping.AnimationSpec resolveSpec(AbstractContainerScreen<?> screen) {
        return AnimationMapping.resolve(screen);
    }

    /** On-screen width of the animation for the given spec, in GUI pixels. */
    static int scaledWidth(AnimationMapping.AnimationSpec spec) {
        return Math.round(spec.frameWidth() * spec.scale());
    }

    /** On-screen height of the animation for the given spec, in GUI pixels. */
    static int scaledHeight(AnimationMapping.AnimationSpec spec) {
        return Math.round(spec.frameHeight() * spec.scale());
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
        AnimationPositionStorage.Entry effective = AnimationPositionStorage.resolveEffective(screen);
        return new int[]{effective.x, effective.y};
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
        int scaledW = scaledWidth(spec);
        int scaledH = scaledHeight(spec);
        int originX = screen.getGuiLeft() + xOffset;
        int originY = screen.getGuiTop() + yOffset;

        if (AnimationDragHandler.isDragging()) {
            originX = Mth.clamp(originX, 0, Math.max(0, window.getGuiScaledWidth() - scaledW));
            originY = Mth.clamp(originY, 0, Math.max(0, window.getGuiScaledHeight() - scaledH));
        } else {
            if (originX + scaledW > window.getGuiScaledWidth()) {
                originX = screen.getGuiLeft() - scaledW - 2;
            }
            if (originY + scaledH > window.getGuiScaledHeight()) {
                originY = Math.max(0, window.getGuiScaledHeight() - scaledH);
            }
            // Safety net: never leave the animation fully off-screen (bad config or
            // saved value), otherwise it could never be grabbed again with the mouse.
            originX = Mth.clamp(originX, 0, Math.max(0, window.getGuiScaledWidth() - scaledW));
            originY = Mth.clamp(originY, 0, Math.max(0, window.getGuiScaledHeight() - scaledH));
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

        // Drop a stale drag if its screen was closed without a release event.
        AnimationDragHandler.ensureScreen(screen);

        int[] offsets = resolveOffsets(screen);
        List<PlacedImage> images = placeImages(screen, offsets[0], offsets[1]);

        for (PlacedImage image : images) {
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
     * Resolves and lays out all images of the screen: the main one at the node
     * position, extras offset from its right edge. Each is clamped on-screen.
     */
    static List<PlacedImage> placeImages(AbstractContainerScreen<?> screen, int xOffset, int yOffset) {
        List<PlacedImage> result = new ArrayList<>();
        Window window = screen.getMinecraft().getWindow();

        List<AnimationMapping.ImageBinding> bindings = AnimationMapping.resolveAll(screen);
        AnimationMapping.AnimationSpec mainSpec = bindings.get(0).spec();
        int[] mainOrigin = computeOrigin(screen, xOffset, yOffset, mainSpec);
        int mainWidth = scaledWidth(mainSpec);

        for (int i = 0; i < bindings.size(); i++) {
            AnimationMapping.ImageBinding binding = bindings.get(i);
            int x;
            int y;
            if (i == 0) {
                x = mainOrigin[0];
                y = mainOrigin[1];
            } else {
                x = mainOrigin[0] + mainWidth + 2 + binding.dx();
                y = mainOrigin[1] + binding.dy();
                // Keep extras inside the window too.
                x = Mth.clamp(x, 0, Math.max(0, window.getGuiScaledWidth() - scaledWidth(binding.spec())));
                y = Mth.clamp(y, 0, Math.max(0, window.getGuiScaledHeight() - scaledHeight(binding.spec())));
            }
            result.add(new PlacedImage(binding.spec(), loadFor(binding.spec()), x, y));
        }
        return result;
    }

    /** Draws one animation image (debug placeholder or sprite frame) at its position. */
    private static void drawImage(GuiGraphics guiGraphics, AbstractContainerScreen<?> screen, PlacedImage image) {
        AnimationMapping.AnimationSpec spec = image.spec();
        float scale = spec.scale();
        int frameCount = spec.frameCount();
        int frameWidth = spec.frameWidth();
        int frameHeight = spec.frameHeight();
        long frameDurationMs = spec.frameDurationMs();
        boolean debugPlaceholder = InventoryGifConfig.DEBUG_DRAW_PLACEHOLDER.get();

        // Current frame from wall-clock time — no stored state, always loops.
        int frame = (int) ((Util.getMillis() / frameDurationMs) % frameCount);
        float v = frame * frameHeight; // frames are stacked vertically in the sheet

        guiGraphics.pose().pushPose();
        // ContainerScreenEvent.Render.Foreground fires while the pose is still
        // translated by (leftPos, topPos), so convert the screen-space origin
        // back into that coordinate space, otherwise the sprite would be drawn
        // at twice the GUI offset (visible position != hit-test position).
        guiGraphics.pose().translate(image.x() - screen.getGuiLeft(), image.y() - screen.getGuiTop(), 0);
        guiGraphics.pose().scale(scale, scale, 1.0f);

        if (debugPlaceholder) {
            // ARGB translucent green rectangle with the frame band highlighted.
            guiGraphics.fill(0, 0, frameWidth, frameHeight, 0x8000FF00);
            guiGraphics.fill(0, frame * frameHeight / frameCount, frameWidth,
                    (frame + 1) * frameHeight / frameCount, 0x80FF0000);
        } else {
            // blit(texture, x, y, u, v, width, height, textureWidth, textureHeight)
            guiGraphics.blit(AnimationTextureManager.textureOf(image.loaded()), 0, 0, 0f, v,
                    frameWidth, frameHeight,
                    AnimationTextureManager.widthOf(image.loaded()), AnimationTextureManager.heightOf(image.loaded()));
        }

        guiGraphics.pose().popPose();
    }
}
