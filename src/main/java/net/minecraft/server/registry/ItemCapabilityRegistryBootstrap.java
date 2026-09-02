package net.minecraft.server.registry;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStackTemplate;
import net.minecraft.server.item.component.CraftingRemainderCodec;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;

/**
 * Bootstrap for item capability defaults.
 */
public final class ItemCapabilityRegistryBootstrap {
    private static final ResourceLocation WATER_BUCKET = key("water_bucket");
    private static final ResourceLocation LAVA_BUCKET = key("lava_bucket");
    private static final ResourceLocation MILK_BUCKET = key("milk_bucket");
    private static boolean initialized = false;

    private ItemCapabilityRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        int count = ItemCapabilityRegistryApi.bootstrapDefaults();
        PreparedRemainders prepared = prepare(null);
        publish(prepared);
        initialized = true;

        System.out.println("[ItemCapabilityRegistryBootstrap] Registered " + count
                + " item capabilities and " + prepared.bindings.size()
                + " crafting remainders");
    }

    public static synchronized void reload() {
        reload(null);
    }

    static synchronized void reload(
            RegistryDataLoader.ResourceProvider provider) {
        ItemCapabilityRegistryApi.bootstrapDefaults();
        PreparedRemainders prepared = prepare(provider);
        publish(prepared);
        initialized = true;
    }

    private static PreparedRemainders prepare(
            RegistryDataLoader.ResourceProvider provider) {
        ItemRegistryBootstrap.initialize();
        final long revision = ItemRegistry.getRegistrationRevision();
        RegistryDataLoader.Decoder<ItemStackTemplate> decoder =
                new RegistryDataLoader.Decoder<ItemStackTemplate>() {
                    public ItemStackTemplate decode(
                            ResourceLocation key,
                            JsonObject json) {
                        return CraftingRemainderCodec.decode(key, json);
                    }
                };
        Map<ResourceLocation, ItemStackTemplate> decoded = provider == null
                ? RegistryDataLoader.loadAll(
                        "item_component/crafting_remainder", decoder)
                : RegistryDataLoader.loadAll(
                        "item_component/crafting_remainder", provider, decoder);
        LinkedHashMap<ResourceLocation, ItemStackTemplate> staged =
                new LinkedHashMap<ResourceLocation, ItemStackTemplate>();
        for (Map.Entry<ResourceLocation, ItemStackTemplate> entry
                : decoded.entrySet()) {
            ResourceLocation inputKey = entry.getKey();
            Item input = ItemRegistry.get(inputKey);
            if (input == null || !inputKey.equals(ItemRegistry.getKey(input))) {
                throw new IllegalArgumentException(
                        "Crafting-remainder component targets missing or "
                                + "non-canonical item " + inputKey);
            }
            if (input.getMaxStackSize() > 1) {
                throw new IllegalArgumentException(
                        "Crafting-remainder input must stack to one: " + inputKey);
            }
            staged.put(inputKey, entry.getValue());
        }
        require(staged, WATER_BUCKET);
        require(staged, LAVA_BUCKET);
        require(staged, MILK_BUCKET);
        if (revision != ItemRegistry.getRegistrationRevision()) {
            throw new IllegalStateException(
                    "Item registry changed while crafting remainders were decoded");
        }
        return new PreparedRemainders(revision, staged);
    }

    private static void publish(
            PreparedRemainders prepared) {
        if (!ItemCapabilityRegistryApi.publishCraftingRemainderBindings(
                prepared.itemRegistryRevision, prepared.bindings)) {
            throw new IllegalStateException(
                    "Prepared crafting-remainder generation was rejected");
        }
    }

    private static void require(
            Map<ResourceLocation, ItemStackTemplate> bindings,
            ResourceLocation key) {
        if (!bindings.containsKey(key)) {
            throw new IllegalArgumentException(
                    "Missing required legacy crafting remainder for " + key);
        }
    }

    private static ResourceLocation key(String path) {
        return new ResourceLocation("minecraft", path);
    }

    private static final class PreparedRemainders {
        final long itemRegistryRevision;
        final Map<ResourceLocation, ItemStackTemplate> bindings;

        PreparedRemainders(
                long itemRegistryRevision,
                Map<ResourceLocation, ItemStackTemplate> bindings) {
            this.itemRegistryRevision = itemRegistryRevision;
            this.bindings = bindings;
        }
    }
}
