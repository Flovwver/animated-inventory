package ru.example.inventorygif.client;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/**
 * "Add image" button for a tree node. Since Cloth Config builds its widget
 * tree once up front, adding a slot means the tree no longer matches
 * on-screen — so this reopens the whole config screen after appending,
 * which loses scroll position but keeps every slot index correct (no stale
 * closures pointing at the wrong image). Caps at {@link #MAX_IMAGES} per
 * node so a misclick can't spiral into thousands of widgets again.
 */
public final class AddImageButtonEntry extends AbstractConfigListEntry<Void> {

    public static final int MAX_IMAGES = 32;

    private final Button button;

    public AddImageButtonEntry(AnimationPositionStorage.Entry node, AnimationPositionStorage.Entry effective,
                               Screen parentOfConfigScreen) {
        super(Component.translatable("inventorygif.config.animations.add_image"), false);

        this.button = Button.builder(Component.translatable("inventorygif.config.animations.add_image"), b -> {
                    materialize(node, effective);
                    if (node.images.size() < MAX_IMAGES) {
                        node.images.add(new AnimationPositionStorage.Image(
                                AnimationLibrary.DEFAULT_NAME, 0, 0, 0.0));
                        AnimationPositionStorage.save();
                    }
                    // Rebuild so the new slot's widgets actually exist.
                    Screen current = Minecraft.getInstance().screen;
                    InventoryGifConfigScreen.ScreenState state = InventoryGifConfigScreen.captureState(current);
                    Screen rebuilt = InventoryGifConfigScreen.create(parentOfConfigScreen);
                    InventoryGifConfigScreen.restoreState(rebuilt, state);
                    Minecraft.getInstance().setScreen(rebuilt);
                })
                .bounds(0, 0, 200, 20)
                .build();
    }

    private static void materialize(AnimationPositionStorage.Entry node, AnimationPositionStorage.Entry effective) {
        if (!node.inherit && node.images.size() == effective.images.size()) {
            return;
        }
        node.images.clear();
        effective.images.forEach(i -> node.images.add(i.copy()));
        node.inherit = false;
    }

    @Override
    public Void getValue() {
        return null;
    }

    @Override
    public Optional<Void> getDefaultValue() {
        return Optional.empty();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight,
                        int mouseX, int mouseY, boolean isHovered, float delta) {
        button.setX(x + entryWidth - 210);
        button.setY(y);
        button.setWidth(200);
        button.render(guiGraphics, mouseX, mouseY, delta);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(button);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return List.of(button);
    }

    @Override
    public boolean isEdited() {
        return false;
    }
}
