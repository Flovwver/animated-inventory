package ru.example.inventorygif.client;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/** Opens the folder used by the animation library. */
public final class OpenAnimationsFolderEntry extends AbstractConfigListEntry<Void> {

    private final Button button;

    public OpenAnimationsFolderEntry() {
        super(Component.translatable("inventorygif.config.open_folder"), false);
        this.button = Button.builder(Component.translatable("inventorygif.config.open_folder"),
                        b -> AnimationLibrary.openFolder())
                .bounds(0, 0, 200, 20)
                .build();
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
