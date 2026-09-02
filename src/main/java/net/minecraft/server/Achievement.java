package net.minecraft.server;

import net.minecraft.server.registry.LegacyAdvancementDefinition;

public class Achievement extends Statistic {

    public int a;
    public int b;
    public Achievement c;
    private final String l;
    public ItemStack d;
    private boolean m;
    private String dataTitleTranslationKey;
    private String dataDescriptionTranslationKey;
    private boolean dataShowToast = true;
    private boolean dataAnnounceToChat = true;
    private boolean dataHidden;

    public Achievement(int i, String s, int j, int k, Item item, Achievement achievement) {
        this(i, s, j, k, new ItemStack(item), achievement);
    }

    public Achievement(int i, String s, int j, int k, Block block, Achievement achievement) {
        this(i, s, j, k, new ItemStack(block), achievement);
    }

    public Achievement(int i, String s, int j, int k, ItemStack itemstack, Achievement achievement) {
        super(5242880 + i, StatisticCollector.a("achievement." + s));
        this.d = itemstack;
        this.l = StatisticCollector.a("achievement." + s + ".desc");
        this.a = j;
        this.b = k;
        if (j < AchievementList.a) {
            AchievementList.a = j;
        }

        if (k < AchievementList.b) {
            AchievementList.b = k;
        }

        if (j > AchievementList.c) {
            AchievementList.c = j;
        }

        if (k > AchievementList.d) {
            AchievementList.d = k;
        }

        this.c = achievement;
    }

    public Achievement a() {
        this.g = true;
        return this;
    }

    public Achievement b() {
        this.m = true;
        return this;
    }

    public Achievement c() {
        super.d();
        AchievementList.e.add(this);
        return this;
    }

    public String getName() {
        return this.dataTitleTranslationKey == null
                ? this.f : StatisticCollector.a(this.dataTitleTranslationKey);
    }

    public String getDescription() {
        return this.dataDescriptionTranslationKey == null
                ? this.l : StatisticCollector.a(this.dataDescriptionTranslationKey);
    }

    public boolean isSpecial() { return this.m; }
    public boolean shouldShowToast() { return this.dataShowToast; }
    public boolean shouldAnnounceToChat() { return this.dataAnnounceToChat; }
    public boolean isHidden() { return this.dataHidden; }

    /** Applies a fully validated definition without changing this object's stat identity. */
    public void applyDefinition(
            LegacyAdvancementDefinition definition,
            Achievement resolvedParent) {
        if (definition == null) {
            throw new IllegalArgumentException("Advancement definition is required");
        }
        this.a = definition.getColumn();
        this.b = definition.getRow();
        this.c = resolvedParent;
        this.d = definition.createIconStack();
        this.m = definition.isSpecial();
        this.dataTitleTranslationKey = definition.getTitleTranslationKey();
        this.dataDescriptionTranslationKey = definition.getDescriptionTranslationKey();
        this.dataShowToast = definition.shouldShowToast();
        this.dataAnnounceToChat = definition.shouldAnnounceToChat();
        this.dataHidden = definition.isHidden();
    }
}
