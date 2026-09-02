package net.minecraft.server.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.ItemStack;
import net.minecraft.server.util.ResourceLocation;

/** Validated data-owned semantics bound to one immutable legacy achievement identity. */
public final class LegacyAdvancementDefinition {
    private final ResourceLocation id;
    private final ResourceLocation parent;
    private final ResourceLocation icon;
    private final int iconLegacyId;
    private final int iconLegacyDamage;
    private final String titleTranslationKey;
    private final String descriptionTranslationKey;
    private final String frame;
    private final boolean showToast;
    private final boolean announceToChat;
    private final boolean hidden;
    private final int column;
    private final int row;
    private final Map<String, ResourceLocation> criteria;
    private final List<List<String>> requirements;

    public LegacyAdvancementDefinition(
            ResourceLocation id,
            ResourceLocation parent,
            ResourceLocation icon,
            int iconLegacyId,
            int iconLegacyDamage,
            String titleTranslationKey,
            String descriptionTranslationKey,
            String frame,
            boolean showToast,
            boolean announceToChat,
            boolean hidden,
            int column,
            int row,
            Map<String, ResourceLocation> criteria,
            List<List<String>> requirements) {
        this.id = id;
        this.parent = parent;
        this.icon = icon;
        this.iconLegacyId = iconLegacyId;
        this.iconLegacyDamage = iconLegacyDamage;
        this.titleTranslationKey = titleTranslationKey;
        this.descriptionTranslationKey = descriptionTranslationKey;
        this.frame = frame;
        this.showToast = showToast;
        this.announceToChat = announceToChat;
        this.hidden = hidden;
        this.column = column;
        this.row = row;
        this.criteria = Collections.unmodifiableMap(
                new LinkedHashMap<String, ResourceLocation>(criteria));
        List<List<String>> copied = new ArrayList<List<String>>();
        for (List<String> group : requirements) {
            copied.add(Collections.unmodifiableList(new ArrayList<String>(group)));
        }
        this.requirements = Collections.unmodifiableList(copied);
    }

    public ResourceLocation getId() { return id; }
    public ResourceLocation getParent() { return parent; }
    public ResourceLocation getIcon() { return icon; }
    public int getIconLegacyId() { return iconLegacyId; }
    public int getIconLegacyDamage() { return iconLegacyDamage; }
    public String getTitleTranslationKey() { return titleTranslationKey; }
    public String getDescriptionTranslationKey() { return descriptionTranslationKey; }
    public String getFrame() { return frame; }
    public boolean isSpecial() { return "challenge".equals(frame); }
    public boolean shouldShowToast() { return showToast; }
    public boolean shouldAnnounceToChat() { return announceToChat; }
    public boolean isHidden() { return hidden; }
    public int getColumn() { return column; }
    public int getRow() { return row; }
    public Map<String, ResourceLocation> getCriteria() { return criteria; }
    public Set<String> getCriterionNames() { return criteria.keySet(); }
    public List<List<String>> getRequirements() { return requirements; }

    public ItemStack createIconStack() {
        return new ItemStack(iconLegacyId, 1, iconLegacyDamage);
    }
}
