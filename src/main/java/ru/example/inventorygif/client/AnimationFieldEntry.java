package ru.example.inventorygif.client;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Cloth Config list entry showing the bound animation as a button; clicking
 * opens {@link AnimationPickerScreen} instead of the built-in string
 * dropdown. The picker's own list scrolls, so unlike the dropdown it never
 * gets clipped regardless of how many animations are in the library.
 *
 * Changes update the settings draft; Save commits it and Cancel discards it.
 */
public final class AnimationFieldEntry extends AbstractConfigListEntry<String> {

    private final Button valueButton;
    private String value;

    public AnimationFieldEntry(Component fieldName, String initialValue, Consumer<String> onPick) {
        super(fieldName, false);
        this.value = initialValue == null ? "" : initialValue;
        this.valueButton = Button.builder(labelFor(this.value), b -> openPicker(onPick))
                .bounds(0, 0, 150, 20)
                .build();
    }

    private static Component labelFor(String value) {
        return value == null || value.isEmpty()
                ? Component.translatable("inventorygif.config.animations.image.none")
                : Component.literal(value);
    }

    private void openPicker(Consumer<String> onPick) {
        Screen current = Minecraft.getInstance().gui.screen();
        if (!InventoryGifConfigScreen.flushDraft(current)) return;
        Minecraft.getInstance().gui.setScreen(new AnimationPickerScreen(current, value, picked -> {
            value = picked;
            valueButton.setMessage(labelFor(value));
            onPick.accept(picked);
        }));
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public Optional<String> getDefaultValue() {
        return Optional.of("");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int index, int y, int x, int entryWidth, int entryHeight,
                        int mouseX, int mouseY, boolean isHovered, float delta) {
        guiGraphics.text(Minecraft.getInstance().font, getFieldName(), x, y + entryHeight / 2 - 4,
                0xFFFFFFFF, false);

        int buttonWidth = 150;
        valueButton.setX(x + entryWidth - buttonWidth);
        valueButton.setY(y);
        valueButton.setWidth(buttonWidth);
        valueButton.extractRenderState(guiGraphics, mouseX, mouseY, delta);
        AnimationPreview.draw(guiGraphics, value, x + entryWidth - buttonWidth - 22, y + 2, 16);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(valueButton);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return List.of(valueButton);
    }

    @Override
    public boolean isEdited() {
        return InventoryGifConfigScreen.hasPendingEdits();
    }
}
