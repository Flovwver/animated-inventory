package ru.example.inventorygif.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves which images are drawn on a given screen, with what scale and where.
 *
 * The image list comes from the settings tree in {@link AnimationPositionStorage}
 * (title node -> menu node -> mod node -> survival inventory): the first node
 * up the chain with inheritance off wins; each image has its own absolute
 * position. Image scale falls back to the global config when the image does
 * not override it. Bindings pointing to missing animations are skipped; if the
 * bound animation does not exist, the default one is used.
 */
public final class AnimationMapping {

    /** Resolved animation for one image. */
    public record AnimationSpec(AnimationLibrary.AnimationMeta meta, float scale) {
        public int frameCount() {
            return meta.frameCount();
        }

        public int frameWidth() {
            return meta.frameWidth();
        }

        public int frameHeight() {
            return meta.frameHeight();
        }

        public long frameDurationMs() {
            return meta.frameDurationMs();
        }
    }

    /** One image to draw: its spec and absolute on-screen offset. */
    public record ImageSpec(AnimationSpec spec, int x, int y) {
    }

    private AnimationMapping() {
    }

    /**
     * Resolves all images for the screen with their absolute positions.
     * May be empty if the node has no images configured.
     */
    public static List<ImageSpec> resolveAll(AbstractContainerScreen<?> screen) {
        AnimationPositionStorage.Entry effective = AnimationPositionStorage.resolveEffective(screen);

        List<ImageSpec> result = new ArrayList<>();
        for (AnimationPositionStorage.Image image : effective.images) {
            String name = image.animation == null || image.animation.isEmpty()
                    ? AnimationLibrary.DEFAULT_NAME : image.animation;
            AnimationLibrary.AnimationMeta meta = AnimationLibrary.get(name);
            if (meta == null) {
                meta = AnimationLibrary.get(AnimationLibrary.DEFAULT_NAME);
            }
            if (meta == null) {
                continue;
            }

            float scale = image.scale > 0
                    ? (float) image.scale
                    : InventoryGifConfig.SCALE.get().floatValue();
            scale = Math.max(0.05f, Math.min(10.0f, scale));
            result.add(new ImageSpec(new AnimationSpec(meta, scale), image.x, image.y));
        }
        return result;
    }
}