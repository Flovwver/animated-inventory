package ru.example.inventorygif.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import ru.example.inventorygif.InventoryGif;

/**
 * Lets the user move the animation by dragging it with the mouse.
 *
 * Behaviour (only active when {@code dragToMove} is enabled in the config):
 * - Left-click on the animation starts a drag. The press is consumed so items
 *   in slots under the animation are not picked up.
 * - Dragging updates the offset live; the position is clamped to the window.
 * - Releasing the button saves the offset to {@link AnimationPositionStorage}
 *   under the class name of the current screen, so every container type keeps
 *   its own position.
 * - Right-click on the animation removes the stored position of that screen,
 *   which makes it fall back to the survival inventory position (or the config
 *   defaults if the survival inventory was never moved either).
 *
 * Uses ScreenEvent.MouseButtonPressed/Dragged/Released in the Pre phase: unlike
 * the render events, these ARE fired for container screens, and cancelling the
 * Pre event prevents the screen from handling the click.
 */
@EventBusSubscriber(modid = InventoryGif.MODID, value = Dist.CLIENT)
public final class AnimationDragHandler {

    /** Screen currently being dragged on; null when idle. */
    private static AbstractContainerScreen<?> dragScreen;
    private static int currentOffsetX;
    private static int currentOffsetY;
    private static double startMouseX;
    private static double startMouseY;

    private AnimationDragHandler() {
    }

    static boolean isDragging() {
        return dragScreen != null;
    }

    /** Live offsets while dragging the given screen, or null when not dragging it. */
    static int[] activeDragOffsets(AbstractContainerScreen<?> screen) {
        return dragScreen == screen ? new int[]{currentOffsetX, currentOffsetY} : null;
    }

    /**
     * Cancels the drag if the dragged screen is no longer open (e.g. the GUI was
     * closed with ESC mid-drag, in which case no release event arrives for it).
     * Called by the renderer every frame.
     */
    static void ensureScreen(AbstractContainerScreen<?> screen) {
        if (dragScreen != null && dragScreen != screen) {
            dragScreen = null;
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

        AnimationMapping.AnimationSpec spec = InventoryGifRenderer.resolveSpec(screen);
        int[] offsets = InventoryGifRenderer.resolveOffsets(screen);
        int[] origin = InventoryGifRenderer.computeOrigin(screen, offsets[0], offsets[1], spec);
        int width = InventoryGifRenderer.scaledWidth(spec);
        int height = InventoryGifRenderer.scaledHeight(spec);

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        boolean inside = mouseX >= origin[0] && mouseX < origin[0] + width
                && mouseY >= origin[1] && mouseY < origin[1] + height;

        if (!inside || !InventoryGifConfig.DRAG_TO_MOVE.get()) {
            return;
        }

        if (event.getButton() == 0) {
            // Start the drag from the position where the animation is actually
            // drawn (which may be the left-side fallback), so it does not jump.
            dragScreen = screen;
            currentOffsetX = origin[0] - screen.getGuiLeft();
            currentOffsetY = origin[1] - screen.getGuiTop();
            startMouseX = mouseX;
            startMouseY = mouseY;
            event.setCanceled(true);
        } else if (event.getButton() == 1) {
            // Right click: reset this screen's position/animation to its tree
            // fallback (shared menu-class entry, then the survival inventory).
            AnimationPositionStorage.resetToFallback(screen);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (dragScreen == null || event.getScreen() != dragScreen) {
            return;
        }
        currentOffsetX += (int) Math.round(event.getMouseX() - startMouseX);
        currentOffsetY += (int) Math.round(event.getMouseY() - startMouseY);
        startMouseX = event.getMouseX();
        startMouseY = event.getMouseY();
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (dragScreen == null) {
            return;
        }
        if (event.getScreen() == dragScreen) {
            // Save the clamped on-screen position, not the raw drag offset.
            int[] origin = InventoryGifRenderer.computeOrigin(dragScreen, currentOffsetX, currentOffsetY,
                    InventoryGifRenderer.resolveSpec(dragScreen));
            AnimationPositionStorage.Entry entry = AnimationPositionStorage.entryFor(dragScreen);
            entry.x = origin[0] - dragScreen.getGuiLeft();
            entry.y = origin[1] - dragScreen.getGuiTop();
            AnimationPositionStorage.save();
            event.setCanceled(true);
        }
        dragScreen = null;
    }
}
