package net.minecraft.server.registry;

import java.util.Collections;
import java.util.List;
import net.minecraft.server.Item;
import net.minecraft.server.util.ResourceLocation;

/** 26.3-facing item tag identities consumed by legacy gameplay. */
public final class ItemTags {
    public static final ResourceLocation REGISTRY_KEY = minecraft("item");
    public static final ResourceLocation REGISTRY = REGISTRY_KEY;

    public static final TagKey<Item> WOLF_FOOD = create("wolf_food");

    private static final List<TagKey<Item>> SYNCHRONIZED_TAGS =
            Collections.singletonList(WOLF_FOOD);

    private ItemTags() {}

    public static List<TagKey<Item>> synchronizedTags() {
        return SYNCHRONIZED_TAGS;
    }

    public static boolean is(Item item, TagKey<Item> tag) {
        return ItemTagRegistryApi.isInTag(tag, item);
    }

    private static TagKey<Item> create(String path) {
        return TagKey.create(REGISTRY, minecraft(path));
    }

    private static ResourceLocation minecraft(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
