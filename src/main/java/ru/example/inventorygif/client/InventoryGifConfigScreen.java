package ru.example.inventorygif.client;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-game settings screen, opened via the "Config" button in the NeoForge mod list.
 *
 * Built with Cloth Config API. Three categories:
 * - General: global rendering options (bound to {@link InventoryGifConfig}).
 * - Animations: a collapsible tree of per-menu settings — base inventories,
 *   then mod -> menu -> title nodes. Every node holds a list of images
 *   (animation, position, scale) and an "inherit from parent" toggle.
 * - Sprite editor: collapsible per-animation frame parameters, written to
 *   the animation JSON files.
 */
public final class InventoryGifConfigScreen {

    /** Snapshot of the selected tab and scroll position during screen rebuilds. */
    record ScreenState(int categoryIndex, double scroll) {
    }

    private static final String CREATIVE_KEY =
            "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen";

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

        addAnimationCategory(builder, entries, parent);
        addAnimationEditorCategory(builder, entries);

        return builder.build();
    }

    static ScreenState captureState(Screen screen) {
        if (screen instanceof ClothConfigScreen clothScreen) {
            return new ScreenState(clothScreen.selectedCategoryIndex, clothScreen.listWidget.getScroll());
        }
        return new ScreenState(0, 0);
    }

    static void restoreState(Screen screen, ScreenState state) {
        if (screen instanceof ClothConfigScreen clothScreen) {
            clothScreen.selectedCategoryIndex = state.categoryIndex();
            clothScreen.listWidget.scrollTo(state.scroll(), false);
        }
    }

    /**
     * "Animations" category: a collapsible tree of per-node settings.
     * Base inventories at the top, then mod -> menu -> title subcategories.
     */
    private static void addAnimationCategory(ConfigBuilder builder, ConfigEntryBuilder entries, Screen parent) {
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

        Map<String, AnimationPositionStorage.Entry> all = AnimationPositionStorage.all();
        Map<String, List<String>> children = groupChildren(all);

        // Base inventories: the root of the fallback chain.
        var base = entries.startSubCategory(Component.translatable("inventorygif.config.animations.base"));
        base.setExpanded(true);
        var survivalEntry = all.get(AnimationPositionStorage.SURVIVAL_KEY);
        var survivalSub = entries.startSubCategory(
            Component.literal(displayLabel(AnimationPositionStorage.SURVIVAL_KEY, survivalEntry)));
        survivalSub.setExpanded(true);
        survivalSub.addAll(nodeEntries(AnimationPositionStorage.SURVIVAL_KEY, all, animationNames, entries, parent));
        base.add(survivalSub.build());

        var creativeSub = entries.startSubCategory(
            Component.literal(displayLabel(CREATIVE_KEY, all.get(CREATIVE_KEY))));
        creativeSub.setExpanded(true);
        creativeSub.addAll(nodeEntries(CREATIVE_KEY, all, animationNames, entries, parent));
        base.add(creativeSub.build());
        category.addEntry(base.build());

        // Mod-level subtrees.
        for (String modKey : children.getOrDefault(AnimationPositionStorage.SURVIVAL_KEY, List.of())) {
            if (!modKey.startsWith("@mod:")) {
                continue;
            }
            category.addEntry(buildNodeSubtree(modKey, all, children, animationNames, entries, 0, parent));
        }
    }

    /** Builds a collapsible node: a "Default" branch with its own fields plus child node subtrees. */
    private static AbstractConfigListEntry buildNodeSubtree(String key,
                                                            Map<String, AnimationPositionStorage.Entry> all,
                                                            Map<String, List<String>> children,
                                                            List<String> animationNames,
                                                            ConfigEntryBuilder entries, int depth, Screen parent) {
        AnimationPositionStorage.Entry entry = all.get(key);
        var sub = entries.startSubCategory(Component.literal(displayLabel(key, entry)));
        sub.setExpanded(depth <= 0);
        sub.setTooltip(Component.literal(key));

        // The node's own values live in a nested "Default" branch, so the node
        // itself is a clean container holding Default + child nodes.
        var defaultBranch = entries.startSubCategory(
                Component.translatable("inventorygif.config.animations.default"));
        defaultBranch.setExpanded(depth <= 0);
        defaultBranch.addAll(nodeEntries(key, all, animationNames, entries, parent));
        defaultBranch.add(new DeleteNodeButtonEntry(key, children, parent));
        sub.add(defaultBranch.build());

        for (String child : children.getOrDefault(key, List.of())) {
            sub.add(buildNodeSubtree(child, all, children, animationNames, entries, depth + 1, parent));
        }
        return sub.build();
    }

    /** Fields of one tree node: inherit toggle plus the list of image slots. */
    private static List<AbstractConfigListEntry> nodeEntries(String key,
                                                             Map<String, AnimationPositionStorage.Entry> all,
                                                             List<String> animationNames,
                                                             ConfigEntryBuilder entries, Screen parent) {
        AnimationPositionStorage.Entry e = all.get(key);
        // When the node inherits, display and edit the effective (parent) values;
        // the first edit materializes them into this node.
        AnimationPositionStorage.Entry effective = AnimationPositionStorage.resolveEffectiveByKey(key);
        List<AnimationPositionStorage.Image> display = e.inherit ? effective.images : e.images;
        List<AbstractConfigListEntry> list = new ArrayList<>();

        if (e.parentKey != null && !e.parentKey.isEmpty()) {
            list.add(entries.startBooleanToggle(
                            Component.translatable("inventorygif.config.animations.inherit"), e.inherit)
                    .setDefaultValue(true)
                    .setTooltip(Component.translatable("inventorygif.config.animations.inherit.tooltip",
                            displayLabel(e.parentKey, all.get(e.parentKey))))
                    .setSaveConsumer(v -> {
                        e.inherit = v;
                        AnimationPositionStorage.save();
                    })
                    .build());
        }

        list.add(entries.startBooleanToggle(
                        Component.translatable("inventorygif.config.animations.locked"), e.locked)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("inventorygif.config.animations.locked.tooltip"))
                .setSaveConsumer(v -> {
                    e.locked = v;
                    AnimationPositionStorage.save();
                })
                .build());

        var imagesSub = entries.startSubCategory(
                Component.translatable("inventorygif.config.animations.images"));
        imagesSub.setExpanded(false);

        // Ровно по одному набору виджетов на реально существующую картинку —
        // никаких пустых слотов "про запас", в отличие от старого фиксированного
        // IMAGE_SLOTS. Список картинок при удалении меняет размер во время
        // построения экрана, поэтому и добавление, и удаление слота
        // пересоздают весь конфиг-экран (см. AddImageButtonEntry / onPick ниже) —
        // так индексы слотов в уже построенных виджетах никогда не рассинхронизируются
        // со списком e.images/effective.images.
        for (int slot = 0; slot < display.size(); slot++) {
            final int idx = slot;
            AnimationPositionStorage.Image img = display.get(slot);
            String current = img.animation != null ? img.animation : "";

            imagesSub.add(new AnimationFieldEntry(
                    Component.translatable("inventorygif.config.animations.image", slot + 1),
                    current,
                    v -> {
                        String value = v == null ? "" : v;
                        if (value.equals(current)) {
                            return;
                        }
                        materializeImages(e, effective);
                        if (value.isEmpty()) {
                            if (idx < e.images.size()) {
                                e.images.remove(idx);
                            }
                            AnimationPositionStorage.save();
                            // Слот исчез — список сдвинулся, пересобираем экран,
                            // чтобы виджеты снова совпадали с e.images по индексам.
                            Screen currentScreen = Minecraft.getInstance().screen;
                            ScreenState state = captureState(currentScreen);
                            Screen rebuilt = InventoryGifConfigScreen.create(parent);
                            restoreState(rebuilt, state);
                            Minecraft.getInstance().setScreen(rebuilt);
                        } else {
                            e.images.get(idx).animation = value;
                            AnimationPositionStorage.save();
                        }
                    }));

            imagesSub.add(entries.startIntField(
                            Component.translatable("inventorygif.config.animations.image.x"), img.x)
                    .setMin(-10000).setMax(10000)
                    .setSaveConsumer(v -> {
                        if (idx >= display.size() || v == display.get(idx).x) {
                            return;
                        }
                        materializeImages(e, effective);
                        e.images.get(idx).x = v;
                        AnimationPositionStorage.save();
                    })
                    .build());

            imagesSub.add(entries.startIntField(
                            Component.translatable("inventorygif.config.animations.image.y"), img.y)
                    .setMin(-10000).setMax(10000)
                    .setSaveConsumer(v -> {
                        if (idx >= display.size() || v == display.get(idx).y) {
                            return;
                        }
                        materializeImages(e, effective);
                        e.images.get(idx).y = v;
                        AnimationPositionStorage.save();
                    })
                    .build());

            imagesSub.add(entries.startDoubleField(
                            Component.translatable("inventorygif.config.animations.image.scale"),
                            img.scale > 0 ? img.scale : 0.0)
                    .setMin(0.0).setMax(10.0)
                    .setTooltip(Component.translatable("inventorygif.config.animations.image.scale.tooltip"))
                    .setSaveConsumer(v -> {
                        if (idx >= display.size() || Math.abs(v - display.get(idx).scale) < 1.0E-9) {
                            return;
                        }
                        materializeImages(e, effective);
                        e.images.get(idx).scale = v;
                        AnimationPositionStorage.save();
                    })
                    .build());
        }

        imagesSub.add(new AddImageButtonEntry(e, effective, parent));

        list.add(imagesSub.build());
        return list;
    }

    /** Copies the effective (possibly inherited) images into the node, turning inheritance off. */
    private static void materializeImages(AnimationPositionStorage.Entry node,
                                          AnimationPositionStorage.Entry effective) {
        if (!node.inherit && node.images.size() == effective.images.size()) {
            return; // already explicit and aligned
        }
        node.images.clear();
        effective.images.forEach(i -> node.images.add(i.copy()));
        node.inherit = false;
    }

    /** Groups entries by parent key for tree building. */
    private static Map<String, List<String>> groupChildren(Map<String, AnimationPositionStorage.Entry> all) {
        Map<String, List<String>> children = new HashMap<>();
        for (Map.Entry<String, AnimationPositionStorage.Entry> e : all.entrySet()) {
            String key = e.getKey();
            if (key.equals(AnimationPositionStorage.SURVIVAL_KEY) || key.equals(CREATIVE_KEY)) {
                continue;
            }
            String parent = e.getValue().parentKey != null && !e.getValue().parentKey.isEmpty()
                    ? e.getValue().parentKey : AnimationPositionStorage.SURVIVAL_KEY;
            children.computeIfAbsent(parent, k -> new ArrayList<>()).add(key);
        }
        return children;
    }

    /** Human-readable node title: label, then key-specific fallbacks. */
    private static String displayLabel(String key, AnimationPositionStorage.Entry entry) {
        if (entry != null && entry.label != null && !entry.label.isEmpty()) {
            return entry.label;
        }
        if (key.startsWith("@mod:")) {
            return key.substring("@mod:".length());
        }
        if (key.startsWith("@menu:")) {
            return AnimationPositionStorage.shortName(key.substring("@menu:".length()));
        }
        int separator = key.indexOf('|');
        if (separator >= 0) {
            String titleKey = key.substring(separator + 1);
            int lastDot = titleKey.lastIndexOf('.');
            return lastDot >= 0 ? titleKey.substring(lastDot + 1) : titleKey;
        }
        return AnimationPositionStorage.shortName(key);
    }

    /**
     * "Sprite editor" category: one collapsible block per animation, writing
     * {@code name.json} next to the PNG immediately when a value is changed.
     */
    private static void addAnimationEditorCategory(ConfigBuilder builder, ConfigEntryBuilder entries) {
        ConfigCategory category = builder.getOrCreateCategory(
                Component.translatable("inventorygif.config.category.editor"));

        category.addEntry(entries.startTextDescription(
                        Component.translatable("inventorygif.config.editor.description"))
                .build());

        var allAnimations = AnimationLibrary.animations();
        if (allAnimations.isEmpty()) {
            category.addEntry(entries.startTextDescription(
                            Component.translatable("inventorygif.config.animations.empty"))
                    .build());
            return;
        }

        // Draft values per animation: field consumers write the full draft, so
        // changing several fields before closing the screen never loses edits.
        Map<String, int[]> drafts = new HashMap<>();

        for (AnimationLibrary.AnimationMeta meta : allAnimations.values()) {
            var sub = entries.startSubCategory(Component.literal(meta.name()));
            sub.setExpanded(false);
            sub.setTooltip(Component.literal(meta.png().toString()));

            int[] draft = drafts.computeIfAbsent(meta.name(), k -> new int[]{
                    meta.frameCount(), meta.frameWidth(), meta.frameHeight(), (int) meta.frameDurationMs()});

            sub.add(entries.startIntField(
                            Component.translatable("inventorygif.config.editor.frameCount"), meta.frameCount())
                    .setMin(1).setMax(1024)
                    .setSaveConsumer(v -> {
                        draft[0] = v;
                        AnimationLibrary.writeMeta(meta.name(), draft[0], draft[1], draft[2], draft[3]);
                    })
                    .build());
            sub.add(entries.startIntField(
                            Component.translatable("inventorygif.config.editor.frameWidth"), meta.frameWidth())
                    .setMin(1).setMax(2048)
                    .setSaveConsumer(v -> {
                        draft[1] = v;
                        AnimationLibrary.writeMeta(meta.name(), draft[0], draft[1], draft[2], draft[3]);
                    })
                    .build());
            sub.add(entries.startIntField(
                            Component.translatable("inventorygif.config.editor.frameHeight"), meta.frameHeight())
                    .setMin(1).setMax(2048)
                    .setSaveConsumer(v -> {
                        draft[2] = v;
                        AnimationLibrary.writeMeta(meta.name(), draft[0], draft[1], draft[2], draft[3]);
                    })
                    .build());
            sub.add(entries.startIntField(
                            Component.translatable("inventorygif.config.editor.frameDurationMs"),
                            (int) meta.frameDurationMs())
                    .setMin(10).setMax(60000)
                    .setSaveConsumer(v -> {
                        draft[3] = v;
                        AnimationLibrary.writeMeta(meta.name(), draft[0], draft[1], draft[2], draft[3]);
                    })
                    .build());

            category.addEntry(sub.build());
        }
    }
}