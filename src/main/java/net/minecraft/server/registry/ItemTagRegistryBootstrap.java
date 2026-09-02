package net.minecraft.server.registry;

import java.util.List;
import java.util.Map;
import net.minecraft.server.Item;
import net.minecraft.server.ItemFood;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;

/** Resolves required 26.3-style item tags before gameplay starts. */
public final class ItemTagRegistryBootstrap {
    private static boolean initialized = false;

    private ItemTagRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;

        long itemRevision = ItemRegistry.registrationRevision();
        RegistryTagBindings<Item> candidate = loadCandidate(
                null, itemRevision);
        ItemTagRegistryApi.publishBootstrap(candidate, itemRevision);
        initialized = true;
        System.out.println("[ItemTagRegistryBootstrap] Resolved "
                + candidate.tagKeys().size() + " required item tags");
    }

    static RegistryTagBindings<Item> loadCandidate(
            RegistryDataLoader.ResourceProvider provider) {
        ItemRegistryBootstrap.initialize();
        return loadCandidate(provider, ItemRegistry.registrationRevision());
    }

    private static RegistryTagBindings<Item> loadCandidate(
            RegistryDataLoader.ResourceProvider provider,
            long itemRevision) {
        RegistryDataLoader.TagValueResolver valueResolver =
                new RegistryDataLoader.TagValueResolver() {
                    public boolean contains(ResourceLocation key) {
                        Item item = ItemRegistry.get(key);
                        ResourceLocation canonical = item == null
                                ? null : ItemRegistry.getKey(item);
                        return key != null && key.equals(canonical);
                    }
                };
        Map<ResourceLocation, List<ResourceLocation>> resolved =
                provider == null
                        ? RegistryDataLoader.loadAllTags(
                                "item", valueResolver)
                        : RegistryDataLoader.loadAllTags(
                                "item", provider, valueResolver);
        if (!resolved.containsKey(ItemTags.WOLF_FOOD.location())) {
            throw new IllegalStateException(
                    "Missing required item tag "
                            + ItemTags.WOLF_FOOD.location());
        }
        RegistryTagBindings<Item> bindings = RegistryTagBindings.create(
                ItemTags.REGISTRY, itemRevision, resolved,
                new RegistryTagBindings.Resolver<Item>() {
                    public Item get(ResourceLocation key) {
                        return ItemRegistry.get(key);
                    }

                    public ResourceLocation getKey(Item value) {
                        return ItemRegistry.getKey(value);
                    }
                });
        validateWolfFood(bindings);
        if (ItemRegistry.registrationRevision() != itemRevision) {
            throw new IllegalStateException(
                    "Item registry changed while item tags were being resolved");
        }
        return bindings;
    }

    private static void validateWolfFood(
            RegistryTagBindings<Item> bindings) {
        if (!bindings.tagKeys().contains(ItemTags.WOLF_FOOD)) {
            throw new IllegalStateException(
                    "Missing required item tag "
                            + ItemTags.WOLF_FOOD.location());
        }
        List<Item> values = bindings.values(ItemTags.WOLF_FOOD);
        for (int i = 0; i < values.size(); i++) {
            if (!(values.get(i) instanceof ItemFood)) {
                throw new IllegalStateException(
                        "Item tag " + ItemTags.WOLF_FOOD.location()
                                + " value " + ItemRegistry.getKey(values.get(i))
                                + " is not an ItemFood");
            }
        }
    }
}
