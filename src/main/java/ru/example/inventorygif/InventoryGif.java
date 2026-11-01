package ru.example.inventorygif;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.example.inventorygif.client.*;

public final class InventoryGif implements ClientModInitializer {
    public static final String MODID = "inventorygif";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    @Override public void onInitializeClient() {
        InventoryGifConfig.SPEC.save();
        InventoryGifKeybinds.register();
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container)) return;
            ScreenEvents.afterExtract(screen).register((s, graphics, x, y, delta) ->
                    InventoryGifRenderer.render(container, graphics));
            ScreenMouseEvents.allowMouseClick(screen).register((s, input) -> {
                var event = new AnimationDragHandler.PointerEvent(s, input);
                AnimationDragHandler.onMousePressed(event);
                return event.allowed();
            });
            ScreenMouseEvents.allowMouseDrag(screen).register((s, input, dx, dy) -> {
                var event = new AnimationDragHandler.PointerEvent(s, input);
                AnimationDragHandler.onMouseDragged(event);
                return event.allowed();
            });
            ScreenMouseEvents.allowMouseRelease(screen).register((s, input) -> {
                var event = new AnimationDragHandler.PointerEvent(s, input);
                AnimationDragHandler.onMouseReleased(event);
                return event.allowed();
            });
            ScreenKeyboardEvents.allowKeyPress(screen).register(InventoryGifKeybinds::allowKeyPress);
        });
        LOGGER.info("Inventory GIF loaded for Fabric");
    }
}
