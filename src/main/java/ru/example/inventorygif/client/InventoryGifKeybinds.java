package ru.example.inventorygif.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
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
    public static void register() {
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("inventorygif", "general"));
        TOGGLE_LOCK = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.inventorygif.toggle_lock",
                InputConstants.Type.KEYSYM, InputConstants.KEY_L, category));
        SETTINGS = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.inventorygif.settings",
                InputConstants.Type.KEYSYM, InputConstants.KEY_O, category));
    }
    public static boolean allowKeyPress(Screen screen, KeyEvent input) {
        if (!(screen instanceof AbstractContainerScreen<?> container)
                || !InventoryGifRenderer.isAnimatedScreen(container)) return true;
        if (SETTINGS.matches(input) && input.hasControlDown()) {
            Minecraft.getInstance().setScreen(InventoryGifConfigScreen.create(screen));
            return false;
        }
        if (!TOGGLE_LOCK.matches(input) || !input.hasControlDown()) return true;
        var entry = AnimationPositionStorage.entryFor(container);
        entry.locked = !entry.locked;
        AnimationPositionStorage.save();
        return false;
    }
}
