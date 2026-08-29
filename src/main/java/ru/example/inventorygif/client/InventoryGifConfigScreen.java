package ru.example.inventorygif.client;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;

/**
 * In-game settings screen, opened via the "Config" button in the NeoForge mod list.
 *
 * Built with Cloth Config API. Every entry is bound to a {@link InventoryGifConfig}
 * value: the screen shows the live value, and pressing "Done" writes changes back
 * to the ModConfigSpec, which NeoForge then saves to
 * {@code config/inventorygif-client.toml} automatically.
 */
public final class InventoryGifConfigScreen {

    private InventoryGifConfigScreen() {
    }

    /** Creates the config screen; {@code parent} is the screen to return to on close. */
    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("inventorygif.config.title"))
                .setSavingRunnable(() -> {
                    InventoryGifConfig.SPEC.save();
                    AnimationPositionStorage.save();
                });

        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(
                Component.translatable("inventorygif.config.category.general"));

        general.addEntry(entries.startIntField(
                        Component.translatable("inventorygif.config.xOffset"), InventoryGifConfig.X_OFFSET.get())
                .setDefaultValue(178)
                .setMin(-10000).setMax(10000)
                .setTooltip(Component.translatable("inventorygif.config.xOffset.tooltip"))
                .setSaveConsumer(InventoryGifConfig.X_OFFSET::set)
                .build());

        general.addEntry(entries.startIntField(
                        Component.translatable("inventorygif.config.yOffset"), InventoryGifConfig.Y_OFFSET.get())
                .setDefaultValue(20)
                .setMin(-10000).setMax(10000)
                .setTooltip(Component.translatable("inventorygif.config.yOffset.tooltip"))
                .setSaveConsumer(InventoryGifConfig.Y_OFFSET::set)
                .build());

        general.addEntry(entries.startDoubleField(
                        Component.translatable("inventorygif.config.scale"), InventoryGifConfig.SCALE.get())
                .setDefaultValue(0.75)
                .setMin(0.05).setMax(10.0)
                .setTooltip(Component.translatable("inventorygif.config.scale.tooltip"))
                .setSaveConsumer(InventoryGifConfig.SCALE::set)
                .build());

        general.addEntry(entries.startIntField(
                        Component.translatable("inventorygif.config.frameDurationMs"),
                        InventoryGifConfig.FRAME_DURATION_MS.get())
                .setDefaultValue(100)
                .setMin(10).setMax(60000)
                .setTooltip(Component.translatable("inventorygif.config.frameDurationMs.tooltip"))
                .setSaveConsumer(InventoryGifConfig.FRAME_DURATION_MS::set)
                .build());

        general.addEntry(entries.startIntField(
                        Component.translatable("inventorygif.config.frameCount"), InventoryGifConfig.FRAME_COUNT.get())
                .setDefaultValue(8)
                .setMin(1).setMax(1024)
                .setTooltip(Component.translatable("inventorygif.config.frameCount.tooltip"))
                .setSaveConsumer(InventoryGifConfig.FRAME_COUNT::set)
                .build());

        general.addEntry(entries.startIntField(
                        Component.translatable("inventorygif.config.frameSize"), InventoryGifConfig.FRAME_SIZE.get())
                .setDefaultValue(128)
                .setMin(1).setMax(2048)
                .setTooltip(Component.translatable("inventorygif.config.frameSize.tooltip"))
                .setSaveConsumer(InventoryGifConfig.FRAME_SIZE::set)
                .build());

        general.addEntry(entries.startStrField(
                        Component.translatable("inventorygif.config.texturePath"), InventoryGifConfig.TEXTURE_PATH.get())
                .setDefaultValue("assets/inventorygif/animation.png")
                .setTooltip(Component.translatable("inventorygif.config.texturePath.tooltip"))
                .setSaveConsumer(InventoryGifConfig.TEXTURE_PATH::set)
                .build());

        general.addEntry(entries.startStringDropdownMenu(
                        Component.translatable("inventorygif.config.screenMode"), InventoryGifConfig.SCREEN_MODE.get())
                .setDefaultValue("player")
                .setSelections(List.of("player", "all"))
                .setSuggestionMode(false)
                .setTooltip(Component.translatable("inventorygif.config.screenMode.tooltip"))
                .setSaveConsumer(InventoryGifConfig.SCREEN_MODE::set)
                .build());

        general.addEntry(entries.startBooleanToggle(
                        Component.translatable("inventorygif.config.dragToMove"),
                        InventoryGifConfig.DRAG_TO_MOVE.get())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("inventorygif.config.dragToMove.tooltip"))
                .setSaveConsumer(InventoryGifConfig.DRAG_TO_MOVE::set)
                .build());

        general.addEntry(entries.startBooleanToggle(
                        Component.translatable("inventorygif.config.debugDrawPlaceholder"),
                        InventoryGifConfig.DEBUG_DRAW_PLACEHOLDER.get())
                .setDefaultValue(false)
                .setTooltip(Component.translatable("inventorygif.config.debugDrawPlaceholder.tooltip"))
                .setSaveConsumer(InventoryGifConfig.DEBUG_DRAW_PLACEHOLDER::set)
                .build());

        addAnimationCategory(builder, entries);

        return builder.build();
    }

    /**
     * "Animations" category: one dropdown per known menu, bound to the per-screen
     * entry in {@link AnimationPositionStorage}. Menus appear here automatically
     * after they are opened once in game.
     */
    private static void addAnimationCategory(ConfigBuilder builder, ConfigEntryBuilder entries) {
        ConfigCategory category = builder.getOrCreateCategory(
                Component.translatable("inventorygif.config.category.animations"));

        category.addEntry(entries.startTextDescription(
                        Component.translatable("inventorygif.config.animations.description"))
                .build());

        List<String> animationNames = List.copyOf(AnimationLibrary.animations().keySet());
        if (animationNames.isEmpty()) {
            category.addEntry(entries.startTextDescription(
                            Component.translatable("inventorygif.config.animations.empty"))
                    .build());
            return;
        }

        for (Map.Entry<String, AnimationPositionStorage.Entry> e : AnimationPositionStorage.all().entrySet()) {
            AnimationPositionStorage.Entry entry = e.getValue();
            String current = entry.animation == null || entry.animation.isEmpty()
                    ? AnimationLibrary.DEFAULT_NAME : entry.animation;

            String title = entry.label == null || entry.label.isEmpty() ? e.getKey() : entry.label;
            category.addEntry(entries.startStringDropdownMenu(
                            Component.literal(title), current)
                    .setDefaultValue(AnimationLibrary.DEFAULT_NAME)
                    .setSelections(animationNames)
                    .setSuggestionMode(false)
                    .setTooltip(Component.literal(e.getKey()))
                    .setSaveConsumer(value -> entry.animation = value)
                    .build());
        }
    }
}
