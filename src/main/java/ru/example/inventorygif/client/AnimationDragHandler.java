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
    /** Live (unclamped) position of the dragged image. */
    private static int currentX;
    private static int currentY;
    private static double startMouseX;
    private static double startMouseY;

    private AnimationDragHandler() {
    }

    static boolean isDragging() {
        return dragScreen != null;
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

        if (event.getButton() == 0) {
            InventoryGifRenderer.PlacedImage hit = images.get(hitIndex);
            dragScreen = screen;
            dragIndex = hitIndex;
            currentX = hit.x();
            currentY = hit.y();
            startMouseX = mouseX;
            startMouseY = mouseY;
            event.setCanceled(true);
        } else if (event.getButton() == 1) {
            // Right click: reset the whole node to inherit from its parent.
            AnimationPositionStorage.resetToFallback(screen);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (dragScreen == null || event.getScreen() != dragScreen) {
            return;
        }
        currentX += (int) Math.round(event.getMouseX() - startMouseX);
        currentY += (int) Math.round(event.getMouseY() - startMouseY);
        startMouseX = event.getMouseX();
        startMouseY = event.getMouseY();
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (dragScreen == null) {
            return;
        }
        if (event.getScreen() == dragScreen && dragIndex >= 0) {
            // Save the clamped on-screen position of the dragged image only.
            AnimationPositionStorage.Entry entry = AnimationPositionStorage.entryFor(dragScreen);
            List<AnimationMapping.ImageSpec> specs = AnimationMapping.resolveAll(dragScreen);
            if (dragIndex < specs.size() && dragIndex < entry.images.size()) {
                AnimationMapping.ImageSpec spec = specs.get(dragIndex);
                int[] clamped = InventoryGifRenderer.clampToWindow(
                        spec.spec(), currentX, currentY, dragScreen);
                entry.inherit = false;
                entry.images.get(dragIndex).x = clamped[0];
                entry.images.get(dragIndex).y = clamped[1];
                AnimationPositionStorage.save();
            }
            event.setCanceled(true);
        }
        dragScreen = null;
        dragIndex = -1;
    }
}