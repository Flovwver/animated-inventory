package ru.example.inventorygif.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Full-screen animation picker: search box + scrollable list of every
 * animation in {@link AnimationLibrary}. Used instead of Cloth Config's
 * string dropdown, which does not scroll and gets clipped when the popup
 * would extend past the bottom of the window — a real problem once the
 * animations folder has more entries than fit in the remaining space
 * under whichever image slot is being edited.
 */
public final class AnimationPickerScreen extends Screen {

    private final Screen parent;
    private final String currentValue;
    private final Consumer<String> onPick;

    private EditBox searchBox;
    private AnimationSelectionList list;
    private List<String> allNames = List.of();

    public AnimationPickerScreen(@Nullable Screen parent, String currentValue, Consumer<String> onPick) {
        super(Component.translatable("inventorygif.config.animations.picker.title"));
        this.parent = parent;
        this.currentValue = currentValue == null ? "" : currentValue;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        allNames = new ArrayList<>(AnimationLibrary.animations().keySet());

        searchBox = new EditBox(font, width / 2 - 150, 30, 300, 20,
                Component.translatable("inventorygif.config.animations.picker.search"));
        searchBox.setHint(Component.translatable("inventorygif.config.animations.picker.search"));
        searchBox.setResponder(query -> list.updateFilter(query));
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        list = new AnimationSelectionList(minecraft, width, height - 90, 58, 22);
        list.updateFilter("");
        addRenderableWidget(list);

        addRenderableWidget(Button.builder(
                        Component.translatable("inventorygif.config.animations.picker.clear"),
                        b -> pick(""))
                .bounds(width / 2 - 205, height - 28, 130, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(width / 2 - 65, height - 28, 130, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 + 75, height - 28, 130, 20)
                .build());
    }

    private void pick(String name) {
        onPick.accept(name);
        onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class AnimationSelectionList extends ObjectSelectionList<AnimationSelectionList.Entry> {

        AnimationSelectionList(Minecraft mc, int width, int height, int y0, int itemHeight) {
            super(mc, width, height, y0, itemHeight);
        }

        void updateFilter(String query) {
            clearEntries();
            String q = query.toLowerCase();
            for (String name : allNames) {
                if (q.isEmpty() || name.toLowerCase().contains(q)) {
                    addEntry(new Entry(name));
                }
            }
        }

        @Override
        public int getRowWidth() {
            return Math.min(360, width - 40);
        }

        @Override
        protected int getScrollbarPosition() {
            return width / 2 + getRowWidth() / 2 + 6;
        }

        final class Entry extends ObjectSelectionList.Entry<Entry> {
            private final String name;

            Entry(String name) {
                this.name = name;
            }

            @Override
            public Component getNarration() {
                return Component.literal(name);
            }

            @Override
            public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                                int mouseX, int mouseY, boolean hovered, float partialTick) {
                boolean selected = name.equals(currentValue);
                int color = selected ? 0xFFFFFF55 : 0xFFFFFFFF;
                guiGraphics.drawString(font, name, left + 4, top + height / 2 - 4, color);

                AnimationLibrary.AnimationMeta meta = AnimationLibrary.get(name);
                if (meta != null) {
                    String info = meta.frameCount() + " fr, " + meta.frameWidth() + "x" + meta.frameHeight();
                    int infoWidth = font.width(info);
                    guiGraphics.drawString(font, info, left + width - infoWidth - 4,
                            top + height / 2 - 4, 0xFFAAAAAA);
                }
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 0) {
                    pick(name);
                    return true;
                }
                return false;
            }
        }
    }
}
