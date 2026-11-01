package ru.example.inventorygif.client;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A Cloth Config button entry that pushes parent node settings to all child nodes
 * that are inheriting.
 */
public final class PushSettingsButtonEntry extends AbstractConfigListEntry<Void> {

    private final Button button;
    private final String parentKey;
    private final Map<String, AnimationPositionStorage.Entry> allEntries;
    private final Map<String, List<String>> children;

    public PushSettingsButtonEntry(String parentKey, Map<String, AnimationPositionStorage.Entry> allEntries,
                                   Map<String, List<String>> children) {
        super(Component.translatable("inventorygif.config.animations.push_to_children"), false);
        this.parentKey = parentKey;
        this.allEntries = allEntries;
        this.children = children;

        this.button = Button.builder(Component.translatable("inventorygif.config.animations.push_to_children"),
                        b -> pushSettingsToChildren())
                .bounds(0, 0, 200, 20)
                .build();
    }

    private void pushSettingsToChildren() {
        AnimationPositionStorage.Entry parent = allEntries.get(parentKey);
        if (parent == null) {
            return;
        }
        pushRecursively(parentKey, parent);
        AnimationPositionStorage.save();
    }

    /**
     * Copies {@code source}'s images into every descendant of {@code key} at
     * every depth (menu -> title -> ...), turning inheritance off at each
     * node visited. Recursing into a node's own children after overwriting it
     * means the whole subtree ends up matching {@code source}, regardless of
     * how many levels deep it is or whether some node down the chain had
     * already been individually customized (materialized) earlier.
     */
    private void pushRecursively(String key, AnimationPositionStorage.Entry source) {
        for (String childKey : children.getOrDefault(key, List.of())) {
            AnimationPositionStorage.Entry child = allEntries.get(childKey);
            if (child == null) {
                continue;
            }
            child.images.clear();
            source.images.forEach(img -> child.images.add(img.copy()));
            child.inherit = false;
            pushRecursively(childKey, child);
        }
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
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int index, int y, int x, int entryWidth, int entryHeight,
                        int mouseX, int mouseY, boolean isHovered, float delta) {
        button.setX(x + entryWidth - 210);
        button.setY(y);
        button.setWidth(200);
        button.extractRenderState(guiGraphics, mouseX, mouseY, delta);
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
