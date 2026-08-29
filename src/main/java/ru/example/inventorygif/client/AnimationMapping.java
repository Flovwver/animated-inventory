package ru.example.inventorygif.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import javax.annotation.Nullable;

/**
 * Resolves which animation is drawn on a given screen.
 *
 * The animation comes from the per-screen entry in {@link AnimationPositionStorage}
 * (bound via the in-game settings screen). New menus inherit the survival
 * inventory binding on first open. If the bound animation no longer exists in
 * {@link AnimationLibrary}, the default one is used.
 *
 * Scale comes from the global config ({@code inventorygif-client.toml}); frame
 * parameters come from the animation metadata.
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

    private AnimationMapping() {
    }

    /** Resolves the animation for the given screen. */
    public static AnimationSpec resolve(AbstractContainerScreen<?> screen) {
        AnimationPositionStorage.Entry entry = AnimationPositionStorage.entryFor(screen);

        String name = entry.animation == null || entry.animation.isEmpty()
                ? AnimationLibrary.DEFAULT_NAME : entry.animation;
        AnimationLibrary.AnimationMeta meta = AnimationLibrary.get(name);
        if (meta == null) {
            meta = AnimationLibrary.get(AnimationLibrary.DEFAULT_NAME);
        }

        float scale = InventoryGifConfig.SCALE.get().floatValue();
        return new AnimationSpec(meta, Math.max(0.05f, Math.min(10.0f, scale)));
    }

    /** Metadata of the animation bound to the screen, or null if the folder is empty. */
    @Nullable
    public static AnimationLibrary.AnimationMeta metaFor(AbstractContainerScreen<?> screen) {
        return resolve(screen).meta();
    }
}