package ru.example.inventorygif.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves which animation is drawn on a given screen and with which scale.
 *
 * The values come from the settings tree in {@link AnimationPositionStorage}
 * (title node -> menu node -> mod node -> survival inventory): the first node
 * up the chain with inheritance off wins. Scale falls back to the global
 * config when no node overrides it. If the bound animation no longer exists
 * in {@link AnimationLibrary}, the default one is used.
 */
public final class AnimationMapping {

    /** Resolved animation for one screen. */
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

    /** One image to draw: its spec and offset relative to the node origin. */
    public record ImageBinding(AnimationSpec spec, int dx, int dy) {
    }

    private AnimationMapping() {
    }

    /** Resolves the animation for the given screen. */
    public static AnimationSpec resolve(AbstractContainerScreen<?> screen) {
        return resolveAll(screen).get(0).spec();
    }

    /**
     * Resolves all images for the screen: the main animation (dx=dy=0, drawn at
     * the node position) plus its extras. Bindings pointing to missing
     * animations are skipped; if the main one is missing the default is used.
     */
    public static List<ImageBinding> resolveAll(AbstractContainerScreen<?> screen) {
        AnimationPositionStorage.Entry effective = AnimationPositionStorage.resolveEffective(screen);

        String name = effective.animation == null || effective.animation.isEmpty()
                ? AnimationLibrary.DEFAULT_NAME : effective.animation;
        AnimationLibrary.AnimationMeta meta = AnimationLibrary.get(name);
        if (meta == null) {
            meta = AnimationLibrary.get(AnimationLibrary.DEFAULT_NAME);
        }

        float scale = effective.scale > 0
                ? (float) effective.scale
                : InventoryGifConfig.SCALE.get().floatValue();
        scale = Math.max(0.05f, Math.min(10.0f, scale));

        List<ImageBinding> result = new ArrayList<>();
        AnimationSpec mainSpec = new AnimationSpec(meta, scale);
        result.add(new ImageBinding(mainSpec, 0, 0));

        for (AnimationPositionStorage.Binding binding : effective.extras) {
            String extraName = binding.animation == null || binding.animation.isEmpty()
                    ? AnimationLibrary.DEFAULT_NAME : binding.animation;
            AnimationLibrary.AnimationMeta extraMeta = AnimationLibrary.get(extraName);
            if (extraMeta == null) {
                continue;
            }
            float extraScale = binding.scale > 0
                    ? (float) binding.scale
                    : scale;
            extraScale = Math.max(0.05f, Math.min(10.0f, extraScale));
            result.add(new ImageBinding(new AnimationSpec(extraMeta, extraScale), binding.dx, binding.dy));
        }
        return result;
    }

    /** Metadata of the animation bound to the screen, or null if the folder is empty. */
    @Nullable
    public static AnimationLibrary.AnimationMeta metaFor(AbstractContainerScreen<?> screen) {
        return resolve(screen).meta();
    }
}