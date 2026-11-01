package ru.example.inventorygif.client;

import me.shedaniel.clothconfig2.gui.entries.SubCategoryListEntry;
import net.minecraft.client.gui.components.events.GuiEventListener;

/** Keeps nested input focus when Cloth Config selects an already focused branch again. */
final class StableSubCategoryEntry extends SubCategoryListEntry {
    StableSubCategoryEntry(SubCategoryListEntry original) {
        super(original.getFieldName(), original.getValue(), original.isExpanded());
        setTooltipSupplier(original.getTooltipSupplier());
        setRequiresRestart(original.isRequiresRestart());
    }

    @Override
    public void setFocused(GuiEventListener child) {
        // Cloth's ElementEntry always unfocuses its old child, even when unchanged.
        // For a nested container this clears the descendant that just received the click.
        if (getFocused() != child) {
            super.setFocused(child);
        }
    }
}
