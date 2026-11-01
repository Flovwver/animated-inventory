package ru.example.inventorygif.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Util;

/** Small animated preview; callers only request previews for visible/hovered entries. */
final class AnimationPreview {
    private AnimationPreview() {}

    static void draw(GuiGraphicsExtractor graphics, String name, int x, int y, int size) {
        AnimationLibrary.AnimationMeta meta = AnimationLibrary.get(name);
        if (meta == null) return;
        var loaded = AnimationTextureManager.get(meta);
        boolean timed = AnimationTextureManager.hasFrameDelays(loaded);
        int width = timed ? AnimationTextureManager.gifFrameWidth(loaded) : meta.frameWidth();
        int height = timed ? AnimationTextureManager.gifFrameHeight(loaded) : meta.frameHeight();
        if (width <= 0 || height <= 0) return;
        int frame = timed ? AnimationTextureManager.currentGifFrame(loaded, Util.getMillis())
                : (int) ((Util.getMillis() / Math.max(10, meta.frameDurationMs())) % Math.max(1, meta.frameCount()));
        float scale = Math.min((float) size / width, (float) size / height);
        graphics.fill(x, y, x + size, y + size, 0xB0202020);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x + (size - width * scale) / 2, y + (size - height * scale) / 2);
            graphics.pose().scale(scale, scale);
            graphics.blit(RenderPipelines.GUI_TEXTURED, AnimationTextureManager.textureOf(loaded),
                    0, 0, 0f, (float) frame * height, width, height,
                    AnimationTextureManager.widthOf(loaded), AnimationTextureManager.heightOf(loaded));
        } finally {
            graphics.pose().popMatrix();
        }
    }
}
