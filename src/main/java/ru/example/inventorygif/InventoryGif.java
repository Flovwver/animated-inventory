package ru.example.inventorygif;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.example.inventorygif.client.*;

@Mod(value = InventoryGif.MODID, dist = Dist.CLIENT)
public final class InventoryGif {
    public static final String MODID = "inventorygif";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public InventoryGif(IEventBus modBus) {
        InventoryGifConfig.SPEC.save();
        modBus.addListener(InventoryGifKeybinds::register);
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Post event) -> {
            if (event.getScreen() instanceof AbstractContainerScreen<?> container) {
                InventoryGifRenderer.render(container, event.getGuiGraphics());
            }
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseButtonPressed.Pre event) -> {
            var pointer = new AnimationDragHandler.PointerEvent(event.getScreen(), event.getMouseButtonEvent());
            AnimationDragHandler.onMousePressed(pointer);
            if (!pointer.allowed()) event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseDragged.Pre event) -> {
            var pointer = new AnimationDragHandler.PointerEvent(event.getScreen(), event.getMouseButtonEvent());
            AnimationDragHandler.onMouseDragged(pointer);
            if (!pointer.allowed()) event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseButtonReleased.Pre event) -> {
            var pointer = new AnimationDragHandler.PointerEvent(event.getScreen(), event.getMouseButtonEvent());
            AnimationDragHandler.onMouseReleased(pointer);
            if (!pointer.allowed()) event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.KeyPressed.Pre event) -> {
            if (!InventoryGifKeybinds.allowKeyPress(event.getScreen(), event.getKeyEvent())) event.setCanceled(true);
        });
        LOGGER.info("Inventory GIF loaded for NeoForge");
    }
}
