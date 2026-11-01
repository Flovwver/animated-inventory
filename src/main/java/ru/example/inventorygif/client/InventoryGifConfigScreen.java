package ru.example.inventorygif.client;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.gui.entries.SubCategoryListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-game settings screen, opened with Ctrl+O from an inventory screen.
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
    record ScreenState(int categoryIndex, double scroll, Map<List<Integer>, Boolean> expanded) {
    }

    private static Screen configParent;

    private static final String CREATIVE_KEY =
            "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen";

    private InventoryGifConfigScreen() {
    }

    /** Creates the config screen; {@code parent} is the screen to return to on close. */
    public static Screen create(Screen parent) {
        configParent = parent;
        AnimationPositionStorage.beginEdit();
        InventoryGifConfig.SPEC.beginEdit();
        AnimationLibrary.beginEdit();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(new SettingsExitScreen(parent))
                .setTitle(Component.translatable("inventorygif.config.title"))
                .setSavingRunnable(() -> {
                    InventoryGifConfig.SPEC.commitEdit();
                    AnimationLibrary.commitEdit();
                    AnimationPositionStorage.commitEdit();
                });

        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(
                Component.translatable("inventorygif.config.category.general"));

        general.addEntry(new OpenAnimationsFolderEntry());

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

    static boolean hasPendingEdits() {
        return AnimationPositionStorage.hasPendingEdits() || InventoryGifConfig.SPEC.hasPendingEdits()
                || AnimationLibrary.hasPendingEdits();
    }

    static boolean flushDraft(Screen screen) {
        if (screen instanceof ClothConfigScreen cloth) {
            if (cloth.getCategorizedEntries().values().stream()
                    .flatMap(List::stream).anyMatch(entry -> entry.getError().isPresent())) return false;
            cloth.getCategorizedEntries().values().forEach(entries -> entries.forEach(entry -> entry.save()));
        }
        return true;
    }

    static Screen rebuild(Screen current) {
        ScreenState state = captureState(current);
        Screen rebuilt = create(configParent);
        restoreState(rebuilt, state);
        return rebuilt;
    }

    static ScreenState captureState(Screen screen) {
        if (screen instanceof ClothConfigScreen clothScreen) {
            Map<List<Integer>, Boolean> expanded = new HashMap<>();
            int categoryIndex = 0;

            for (var entries : clothScreen.getCategorizedEntries().values()) {
                captureExpanded(entries, List.of(categoryIndex++), expanded);
            }

            return new ScreenState(clothScreen.selectedCategoryIndex,
                    clothScreen.listWidget.getScroll(), Map.copyOf(expanded));
        }

        return new ScreenState(0, 0, Map.of());
    }

    /** Must run before setScreen: Cloth Config builds its list from the selected index during init. */
    static void restoreState(Screen screen, ScreenState state) {
        if (screen instanceof ClothConfigScreen clothScreen) {
            clothScreen.selectedCategoryIndex = state.categoryIndex();
            int categoryIndex = 0;

            for (var entries : clothScreen.getCategorizedEntries().values()) {
                restoreExpanded(entries, List.of(categoryIndex++), state.expanded());
            }

            // The list only exists after init. Restore scroll once, after its contents are ready.
            clothScreen.setAfterInitConsumer(initialized -> {
                // Cloth Config's first layout can reset the scroll. Apply it after that frame.
                boolean[] restored = {false};
                net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterExtract(initialized)
                        .register((current, graphics, mouseX, mouseY, delta) -> {
                            if (!restored[0]) {
                                clothScreen.listWidget.scrollTo(state.scroll(), false);
                                restored[0] = true;
                            }
                        });
                clothScreen.setAfterInitConsumer(null);
            });
        }
    }

    private static void captureExpanded(List<?> entries,
                                        List<Integer> parent, Map<List<Integer>, Boolean> expanded) {
        int subcategoryIndex = 0;

        for (var entry : entries) {
            if (entry instanceof SubCategoryListEntry subcategory) {
                List<Integer> path = new ArrayList<>(parent);
                path.add(subcategoryIndex++);
                expanded.put(List.copyOf(path), subcategory.isExpanded());
                captureExpanded(subcategory.getValue(), path, expanded);
            }
        }
    }

    private static void restoreExpanded(List<?> entries,
                                        List<Integer> parent, Map<List<Integer>, Boolean> expanded) {
        int subcategoryIndex = 0;

        for (var entry : entries) {
            if (entry instanceof SubCategoryListEntry subcategory) {
                List<Integer> path = new ArrayList<>(parent);
                path.add(subcategoryIndex++);
                Boolean wasExpanded = expanded.get(path);

                if (wasExpanded != null) {
                    subcategory.setExpanded(wasExpanded);
                }

                restoreExpanded(subcategory.getValue(), path, expanded);
            }
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

        // Show the same parent chain used by resolveEffectiveByKey at runtime.
        var survivalEntry = all.get(AnimationPositionStorage.SURVIVAL_KEY);
        var survivalSub = entries.startSubCategory(
                Component.literal(displayLabel(AnimationPositionStorage.SURVIVAL_KEY, survivalEntry)));
        survivalSub.setExpanded(true);
        survivalSub.addAll(nodeEntries(AnimationPositionStorage.SURVIVAL_KEY, all, animationNames, entries, parent));

        for (String child : children.getOrDefault(AnimationPositionStorage.SURVIVAL_KEY, List.of())) {
            survivalSub.add(buildNodeSubtree(child, all, children, animationNames, entries, 1, parent));
        }

        category.addEntry(new StableSubCategoryEntry(survivalSub.build()));

        // Creative has its own images and no parent in storage.
        var creativeSub = entries.startSubCategory(
                Component.literal(displayLabel(CREATIVE_KEY, all.get(CREATIVE_KEY))));
        creativeSub.setExpanded(true);
        creativeSub.addAll(nodeEntries(CREATIVE_KEY, all, animationNames, entries, parent));
        category.addEntry(new StableSubCategoryEntry(creativeSub.build()));
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
        sub.add(new StableSubCategoryEntry(defaultBranch.build()));

        for (String child : children.getOrDefault(key, List.of())) {
            sub.add(buildNodeSubtree(child, all, children, animationNames, entries, depth + 1, parent));
        }
        return new StableSubCategoryEntry(sub.build());
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

        list.add(entries.startTextDescription(Component.translatable(
                e.inherit ? "inventorygif.config.animations.source.inherited" : "inventorygif.config.animations.source.own",
                e.parentKey == null ? "" : displayLabel(e.parentKey, all.get(e.parentKey)))).build());

        if (e.parentKey != null && !e.parentKey.isEmpty()) {
            list.add(new SettingsActionEntry(Component.translatable(e.inherit
                    ? "inventorygif.config.animations.inherit.on" : "inventorygif.config.animations.inherit.off"), () -> {
                Screen currentScreen = Minecraft.getInstance().screen;
                if (!flushDraft(currentScreen)) return;
                if (e.inherit) {
                    materializeImages(e, AnimationPositionStorage.resolveEffectiveByKey(key));
                } else {
                    e.inherit = true;
                }
                AnimationPositionStorage.save();
                Minecraft.getInstance().setScreen(rebuild(currentScreen));
            }));
        }

        list.add(entries.startBooleanToggle(
                        Component.translatable("inventorygif.config.animations.locked"), e.locked)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("inventorygif.config.animations.locked.tooltip"))
                .setSaveConsumer(v -> {
                    if (e.locked == v) return;
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

            var card = entries.startSubCategory(Component.translatable(
                    "inventorygif.config.animations.card", slot + 1, current));
            card.setExpanded(true);
            card.add(new AnimationFieldEntry(
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
                        } else {
                            e.images.get(idx).animation = value;
                            AnimationPositionStorage.save();
                        }
                    }));

            card.add(entries.startIntField(
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

            card.add(entries.startIntField(
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

            card.add(entries.startDoubleField(
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
            card.add(new SettingsActionEntry(Component.translatable("inventorygif.config.animations.remove"), () -> {
                Screen currentScreen = Minecraft.getInstance().screen;
                if (!flushDraft(currentScreen)) return;
                materializeImages(e, effective);
                if (idx < e.images.size()) {
                    e.images.remove(idx);
                    AnimationPositionStorage.save();
                }
                Minecraft.getInstance().setScreen(rebuild(currentScreen));
            }));
            imagesSub.add(new StableSubCategoryEntry(card.build()));
        }

        imagesSub.add(new AddImageButtonEntry(e, effective, parent));

        list.add(new StableSubCategoryEntry(imagesSub.build()));
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
        if (key.equals(AnimationPositionStorage.SURVIVAL_KEY)) return Component.translatable("inventorygif.config.survival").getString();
        if (key.equals(CREATIVE_KEY)) return Component.translatable("inventorygif.config.creative").getString();
        if (key.startsWith("@menu:net.minecraft.")) {
            String menu = AnimationPositionStorage.shortName(key.substring("@menu:".length()));
            String translation = "inventorygif.menu." + menu;
            Component label = Component.translatable(translation);
            if (!label.getString().equals(translation)) return label.getString();
        }
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
                        if (draft[0] == v) {
                            return;
                        }

                        draft[0] = v;
                        AnimationLibrary.writeMeta(meta.name(), draft[0], draft[1], draft[2], draft[3]);
                    })
                    .build());
            sub.add(entries.startIntField(
                            Component.translatable("inventorygif.config.editor.frameWidth"), meta.frameWidth())
                    .setMin(1).setMax(2048)
                    .setSaveConsumer(v -> {
                        if (draft[1] == v) {
                            return;
                        }

                        draft[1] = v;
                        AnimationLibrary.writeMeta(meta.name(), draft[0], draft[1], draft[2], draft[3]);
                    })
                    .build());
            sub.add(entries.startIntField(
                            Component.translatable("inventorygif.config.editor.frameHeight"), meta.frameHeight())
                    .setMin(1).setMax(2048)
                    .setSaveConsumer(v -> {
                        if (draft[2] == v) {
                            return;
                        }

                        draft[2] = v;
                        AnimationLibrary.writeMeta(meta.name(), draft[0], draft[1], draft[2], draft[3]);
                    })
                    .build());
            sub.add(entries.startIntField(
                            Component.translatable("inventorygif.config.editor.frameDurationMs"),
                            (int) meta.frameDurationMs())
                    .setMin(10).setMax(60000)
                    .setSaveConsumer(v -> {
                        if (draft[3] == v) {
                            return;
                        }

                        draft[3] = v;
                        AnimationLibrary.writeMeta(meta.name(), draft[0], draft[1], draft[2], draft[3]);
                    })
                    .build());

            category.addEntry(new StableSubCategoryEntry(sub.build()));
        }
    }
    /** Receives both Save and Cancel; only Save has committed the draft beforehand. */
    private static final class SettingsExitScreen extends Screen {
        private final Screen destination;

        private SettingsExitScreen(Screen destination) {
            super(Component.empty());
            this.destination = destination;
        }

        @Override
        public void tick() {
            AnimationPositionStorage.cancelEdit();
            InventoryGifConfig.SPEC.cancelEdit();
            AnimationLibrary.cancelEdit();
            minecraft.setScreen(destination);
        }
    }
}