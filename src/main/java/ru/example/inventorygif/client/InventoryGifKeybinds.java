package ru.example.inventorygif.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import ru.example.inventorygif.InventoryGif;

@EventBusSubscriber(modid = InventoryGif.MODID, value = Dist.CLIENT)
public final class InventoryGifKeybinds {

    public static final String CATEGORY = "key.categories.inventorygif";
    public static KeyMapping TOGGLE_LOCK;

    private InventoryGifKeybinds() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterKeyMappingsEvent event) {
        TOGGLE_LOCK = new KeyMapping("key.inventorygif.toggle_lock",
                InputConstants.Type.KEYSYM, InputConstants.KEY_L, CATEGORY);
        event.register(TOGGLE_LOCK);
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || !InventoryGifRenderer.isAnimatedScreen(screen)
                || TOGGLE_LOCK == null
                || !TOGGLE_LOCK.matches(event.getKeyCode(), event.getScanCode())
                || !Screen.hasControlDown()) {
            return;
        }

        AnimationPositionStorage.Entry entry = AnimationPositionStorage.entryFor(screen);
        entry.locked = !entry.locked;
        AnimationPositionStorage.save();
        event.setCanceled(true);
    }
}