package ru.example.inventorygif.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
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
 * next to the inventory should be drawn.
 *
 * The event is fired on the game event bus (NeoForge#EVENT_BUS), client side only.
 * This class is registered automatically by the @EventBusSubscriber annotation;
 * the bus is determined automatically (the deprecated bus= attribute is ignored).
 *
 * All parameters (offsets, scale, frame timing/count/size, texture path, debug flag)
 * come from the client config: {@code config/inventorygif-client.toml}
 * (see {@link InventoryGifConfig}). The sprite sheet is loaded from the instance
 * directory by {@link AnimationTextureManager}, not from inside the mod jar.
 */
@EventBusSubscriber(modid = InventoryGif.MODID, value = Dist.CLIENT)
public final class InventoryGifRenderer {

    private InventoryGifRenderer() {
    }

    @SubscribeEvent
    public static void onRenderInventoryForeground(ContainerScreenEvent.Render.Foreground event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();

        // screenMode from config: "player" = survival/creative inventory, "all" = any container screen.
        final String screenMode = InventoryGifConfig.SCREEN_MODE.get().trim();
        if (!"all".equalsIgnoreCase(screenMode)
                && !(screen instanceof InventoryScreen)
                && !(screen instanceof CreativeModeInventoryScreen)) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();

        // All parameters come from config/inventorygif-client.toml (InventoryGifConfig).
        final int xOffset = InventoryGifConfig.X_OFFSET.get();
        final int yOffset = InventoryGifConfig.Y_OFFSET.get();
        final float scale = InventoryGifConfig.SCALE.get().floatValue();
        final long frameDurationMs = InventoryGifConfig.FRAME_DURATION_MS.get();
        final int frameCount = InventoryGifConfig.FRAME_COUNT.get();
        final int frameSize = InventoryGifConfig.FRAME_SIZE.get();
        final boolean debugPlaceholder = InventoryGifConfig.DEBUG_DRAW_PLACEHOLDER.get();

        // Load/reload the sprite sheet from the instance directory (throttled internally).
        AnimationTextureManager.tick();

        int originX = screen.getGuiLeft() + xOffset;
        int originY = screen.getGuiTop() + yOffset;

        // Keep the animation on-screen: if the right edge would overflow the window,
        // fall back to the free space on the left of the inventory GUI instead.
        Window window = screen.getMinecraft().getWindow();
        int scaledWidth = Math.round(frameSize * scale);
        int scaledHeight = Math.round(frameSize * scale);
        if (originX + scaledWidth > window.getGuiScaledWidth()) {
            originX = screen.getGuiLeft() - scaledWidth - 2;
        }
        if (originY + scaledHeight > window.getGuiScaledHeight()) {
            originY = Math.max(0, window.getGuiScaledHeight() - scaledHeight);
        }

        // Current frame from wall-clock time — no stored state, always loops.
        int frame = (int) ((Util.getMillis() / frameDurationMs) % frameCount);
        float v = frame * frameSize; // frames are stacked vertically in the sheet

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(originX, originY, 0);
        guiGraphics.pose().scale(scale, scale, 1.0f);

        if (debugPlaceholder) {
            // ARGB translucent green square with the frame band highlighted.
            guiGraphics.fill(0, 0, frameSize, frameSize, 0x8000FF00);
            guiGraphics.fill(0, frame * frameSize / frameCount, frameSize,
                    (frame + 1) * frameSize / frameCount, 0x80FF0000);
        } else {
            // blit(texture, x, y, u, v, width, height, textureWidth, textureHeight)
            guiGraphics.blit(AnimationTextureManager.getTexture(), 0, 0, 0f, v, frameSize, frameSize,
                    AnimationTextureManager.getTextureWidth(), AnimationTextureManager.getTextureHeight());
        }

        guiGraphics.pose().popPose();
    }
}
