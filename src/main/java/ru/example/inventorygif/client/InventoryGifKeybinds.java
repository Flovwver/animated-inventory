package ru.example.inventorygif.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;

public final class InventoryGifKeybinds {
    public static KeyMapping TOGGLE_LOCK;
    public static KeyMapping SETTINGS;
    private InventoryGifKeybinds() {}
    public static void register(RegisterKeyMappingsEvent event) {
        var category = new KeyMapping.Category(Identifier.fromNamespaceAndPath("inventorygif", "general"));
        event.registerCategory(category);
        TOGGLE_LOCK = new KeyMapping("key.inventorygif.toggle_lock",
                InputConstants.Type.KEYBOARD, InputConstants.KEY_L, category);
        event.register(TOGGLE_LOCK);
        SETTINGS = new KeyMapping("key.inventorygif.settings",
                InputConstants.Type.KEYBOARD, InputConstants.KEY_O, category);
        event.register(SETTINGS);
    }
    public static boolean allowKeyPress(Screen screen, KeyEvent input) {
        if (!(screen instanceof AbstractContainerScreen<?> container)
                || !InventoryGifRenderer.isAnimatedScreen(container)) return true;
        if (SETTINGS.matches(input) && input.hasControlDown()) {
            Minecraft.getInstance().gui.setScreen(InventoryGifConfigScreen.create(screen));
            return false;
        }
        if (!TOGGLE_LOCK.matches(input) || !input.hasControlDown()) return true;
        var entry = AnimationPositionStorage.entryFor(container);
        entry.locked = !entry.locked;
        AnimationPositionStorage.save();
        return false;
    }
}
