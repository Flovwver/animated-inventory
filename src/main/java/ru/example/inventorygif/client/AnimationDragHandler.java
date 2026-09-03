package ru.example.inventorygif.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import ru.example.inventorygif.InventoryGif;

import java.util.List;

/**
 * Lets the user move the animation images by dragging them with the mouse.
 *
 * Behaviour (only active when {@code dragToMove} is enabled in the config):
 * - Left-click on any image starts a drag of that exact image. The press is
 *   consumed so items in slots under the image are not picked up.
 * - Dragging updates the dragged image's position live, clamped to the window;
 *   other images of the node stay in place.
 * - Releasing the button writes the position into the node's image entry
 *   (inheritance off) and saves.
 * - Right-click on an image resets the whole node to inherit from its parent.
 *
 * Uses ScreenEvent.MouseButtonPressed/Dragged/Released in the Pre phase: unlike
 * the render events, these ARE fired for container screens, and cancelling the
 * Pre event prevents the screen from handling the click.
 */
@EventBusSubscriber(modid = InventoryGif.MODID, value = Dist.CLIENT)
public final class AnimationDragHandler {

    /** Screen currently being dragged on; null when idle. */
    private static AbstractContainerScreen<?> dragScreen;
    /** Index of the dragged image within the node's image list. */
    private static int dragIndex = -1;
    /** Live (unclamped) position of the dragged image, for rendering/clamping. */
    private static int currentX;
    private static int currentY;
    /**
     * Image position and mouse position at the moment the drag started — kept
     * fixed for the whole drag. Every subsequent position is computed directly
     * from this anchor (imageStart + (mouseNow - mouseStart)) instead of by
     * accumulating per-event deltas, so there is nothing for rounding error to
     * build up in even over a long, slow drag.
     */
    private static int dragStartImageX;
    private static int dragStartImageY;
    private static double dragStartMouseX;
    private static double dragStartMouseY;

    private static AbstractContainerScreen<?> resizeScreen;
    private static int resizeIndex = -1;
    private static double resizeStartMouseDist;
    private static double resizeStartScale;
    private static double currentResizeScale;
    private static int resizeAnchorX;
    private static int resizeAnchorY;

    private AnimationDragHandler() {
    }

    static boolean isDragging() {
        return dragScreen != null;
    }

    static boolean isResizing() {
        return resizeScreen != null;
    }

    static Double activeResizeScale(AbstractContainerScreen<?> screen, int index) {
        return resizeScreen == screen && resizeIndex == index ? currentResizeScale : null;
    }

    /**
     * Live position override for the dragged image on the given screen:
     * {@code {index, x, y}}, or null when not dragging it.
     */
    static int[] activeDragImage(AbstractContainerScreen<?> screen) {
        return dragScreen == screen && dragIndex >= 0
                ? new int[]{dragIndex, currentX, currentY} : null;
    }

    /**
     * Cancels the drag if the dragged screen is no longer open (e.g. the GUI was
     * closed with ESC mid-drag, in which case no release event arrives for it).
     * Called by the renderer every frame.
     */
    static void ensureScreen(AbstractContainerScreen<?> screen) {
        if (dragScreen != null && dragScreen != screen) {
            dragScreen = null;
            dragIndex = -1;
        }
        if (resizeScreen != null && resizeScreen != screen) {
            resizeScreen = null;
            resizeIndex = -1;
        }
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        if (!InventoryGifRenderer.isAnimatedScreen(screen)) {
            return;
        }

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        // Hit-test every image of the node; each image is dragged individually.
        List<InventoryGifRenderer.PlacedImage> images = InventoryGifRenderer.placeImages(screen);

        if (event.getButton() == 0 && InventoryGifConfig.DRAG_TO_MOVE.get()
                && !AnimationPositionStorage.entryFor(screen).locked) {
            for (int i = 0; i < images.size(); i++) {
                InventoryGifRenderer.PlacedImage image = images.get(i);
                int handleX = InventoryGifRenderer.handleX(image);
                int handleY = InventoryGifRenderer.handleY(image);
                if (mouseX >= handleX && mouseX < handleX + InventoryGifRenderer.HANDLE_SIZE
                    && mouseY >= handleY && mouseY < handleY + InventoryGifRenderer.HANDLE_SIZE) {
                    resizeScreen = screen;
                    resizeIndex = i;
                    resizeAnchorX = image.x();
                    resizeAnchorY = image.y();
                    resizeStartScale = image.spec().scale();
                    double dx = mouseX - resizeAnchorX;
                    double dy = mouseY - resizeAnchorY;
                    resizeStartMouseDist = Math.max(1.0, Math.sqrt(dx * dx + dy * dy));
                    currentResizeScale = resizeStartScale;
                    event.setCanceled(true);
                    return;
                }
            }
        }

        int hitIndex = -1;
        for (int i = 0; i < images.size(); i++) {
            InventoryGifRenderer.PlacedImage image = images.get(i);
            if (mouseX >= image.x() && mouseX < image.x() + InventoryGifRenderer.scaledWidth(image.spec())
                    && mouseY >= image.y() && mouseY < image.y() + InventoryGifRenderer.scaledHeight(image.spec())) {
                hitIndex = i;
                break;
            }
        }

        if (hitIndex < 0 || !InventoryGifConfig.DRAG_TO_MOVE.get()) {
            return;
        }
        if (AnimationPositionStorage.entryFor(screen).locked) {
            return;
        }

        if (event.getButton() == 0) {
            InventoryGifRenderer.PlacedImage hit = images.get(hitIndex);
            dragScreen = screen;
            dragIndex = hitIndex;
            dragStartImageX = hit.x();
            dragStartImageY = hit.y();
            dragStartMouseX = mouseX;
            dragStartMouseY = mouseY;
            currentX = hit.x();
            currentY = hit.y();
            event.setCanceled(true);
        } else if (event.getButton() == 1) {
            // Right click: reset the whole node to inherit from its parent.
            AnimationPositionStorage.resetToFallback(screen);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (resizeScreen != null && event.getScreen() == resizeScreen) {
            double dx = event.getMouseX() - resizeAnchorX;
            double dy = event.getMouseY() - resizeAnchorY;
            double distance = Math.max(1.0, Math.sqrt(dx * dx + dy * dy));
            double newScale = resizeStartScale * (distance / resizeStartMouseDist);
            currentResizeScale = Math.max(0.05, Math.min(10.0, newScale));
            event.setCanceled(true);
            return;
        }
        if (dragScreen == null || event.getScreen() != dragScreen) {
            return;
        }
        // Computed directly from the fixed press-time anchor every time, not by
        // accumulating rounded per-frame deltas — see field comments above.
        currentX = dragStartImageX + (int) Math.round(event.getMouseX() - dragStartMouseX);
        currentY = dragStartImageY + (int) Math.round(event.getMouseY() - dragStartMouseY);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (resizeScreen != null) {
            if (event.getScreen() == resizeScreen && resizeIndex >= 0) {
                AnimationPositionStorage.Entry entry = AnimationPositionStorage.entryFor(resizeScreen);
                AnimationPositionStorage.Entry effective = AnimationPositionStorage.resolveEffective(resizeScreen);
                if (entry.inherit || entry.images.size() != effective.images.size()) {
                    entry.images.clear();
                    effective.images.forEach(i -> entry.images.add(i.copy()));
                    entry.inherit = false;
                }
                if (resizeIndex < entry.images.size()) {
                    entry.images.get(resizeIndex).scale = currentResizeScale;
                    AnimationPositionStorage.save();
                }
                event.setCanceled(true);
            }
            resizeScreen = null;
            resizeIndex = -1;
            return;
        }
        if (dragScreen == null) {
            return;
        }
        if (event.getScreen() == dragScreen && dragIndex >= 0) {
            // Save the clamped on-screen position of the dragged image only.
            // If the node was inheriting, the effective (parent) images are
            // materialized into it first so the indices line up.
            AnimationPositionStorage.Entry entry = AnimationPositionStorage.entryFor(dragScreen);
            AnimationPositionStorage.Entry effective = AnimationPositionStorage.resolveEffective(dragScreen);
            List<AnimationMapping.ImageSpec> specs = AnimationMapping.resolveAll(dragScreen);
            if (dragIndex < specs.size()) {
                if (entry.inherit || entry.images.size() != effective.images.size()) {
                    entry.images.clear();
                    effective.images.forEach(i -> entry.images.add(i.copy()));
                    entry.inherit = false;
                }
                AnimationMapping.ImageSpec spec = specs.get(dragIndex);
                int[] clamped = InventoryGifRenderer.clampToWindow(
                        spec.spec(), currentX, currentY, dragScreen);
                if (dragIndex < entry.images.size()) {
                    entry.images.get(dragIndex).x = clamped[0];
                    entry.images.get(dragIndex).y = clamped[1];
                }
                AnimationPositionStorage.save();
            }
            event.setCanceled(true);
        }
        dragScreen = null;
        dragIndex = -1;
    }
}