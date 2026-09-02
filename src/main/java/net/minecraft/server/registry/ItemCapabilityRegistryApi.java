package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.ItemComponentDefaults;
import net.minecraft.server.ItemStack;
import net.minecraft.server.ItemStackTemplate;
import net.minecraft.server.Material;
import net.minecraft.server.item.component.CookingFuel;
import net.minecraft.server.registry.number.NumberProviders;
import net.minecraft.server.util.ResourceLocation;
import uk.betacraft.uberbukkit.Uberbukkit;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * API-facing registry for item interaction capabilities.
 * Exposes furnace fuel duration semantics and core item properties.
 */
public final class ItemCapabilityRegistryApi {
    /** Legacy/mod overrides are deliberately outside synchronized data. */
    private static final Map<Item, Item> craftingRemainderOverrides =
            new IdentityHashMap<Item, Item>();
    private static final Map<ResourceLocation, Integer> furnaceFuelByKey = new LinkedHashMap<ResourceLocation, Integer>();
    private static final Map<Integer, Integer> furnaceFuelByLegacyId = new HashMap<Integer, Integer>();
    private static volatile Map<ResourceLocation, CookingFuel> cookingFuelByKey =
            Collections.emptyMap();
    private static volatile Map<Integer, CookingFuel> cookingFuelByLegacyId =
            Collections.emptyMap();
    private static volatile Map<ResourceLocation, ItemStackTemplate>
            craftingRemainderByKey = Collections.emptyMap();
    private static volatile Map<Item, ItemStackTemplate>
            craftingRemainderByItem = Collections.emptyMap();
    private static final Map<ResourceLocation, ItemProperties> propertiesByKey = new LinkedHashMap<ResourceLocation, ItemProperties>();
    private static final Map<Integer, ItemProperties> propertiesByLegacyId = new HashMap<Integer, ItemProperties>();
    private static final ItemRegistry.Listener ITEM_PROPERTY_LISTENER = new ItemRegistry.Listener() {
        public void onRegistered(ResourceLocation key, Item item) {
            registerOrRefreshProperties(key, item);
        }
    };
    private static boolean propertyListenerRegistered = false;
    private static boolean cookingFuelBindingsInitialized = false;
    private static boolean craftingRemainderBindingsInitialized = false;

    private ItemCapabilityRegistryApi() {}

    public static boolean registerFuel(ResourceLocation itemKey, int burnTicks) {
        if (itemKey == null) {
            return false;
        }
        Item item = ItemRegistry.get(itemKey);
        if (item == null) {
            return false;
        }
        return registerFuel(item, burnTicks);
    }

    public static boolean registerFuel(String itemIdentifier, int burnTicks) {
        if (itemIdentifier == null) {
            return false;
        }
        String normalized = ItemRegistry.normalizeInputIdentifier(itemIdentifier);
        if (normalized == null) {
            return false;
        }
        return registerFuel(new ResourceLocation(normalized), burnTicks);
    }

    public static synchronized boolean registerFuel(int legacyItemId, int burnTicks) {
        if (legacyItemId < 0 || legacyItemId >= Item.byId.length) {
            return false;
        }
        Item item = Item.byId[legacyItemId];
        return registerFuel(item, burnTicks);
    }

    public static synchronized boolean registerFuel(Item item, int burnTicks) {
        if (item == null || burnTicks <= 0) {
            return false;
        }

        ResourceLocation key = ItemRegistry.getKey(item);
        if (key == null) {
            return false;
        }

        int clamped = Math.max(0, burnTicks);
        furnaceFuelByKey.put(key, Integer.valueOf(clamped));
        furnaceFuelByLegacyId.put(Integer.valueOf(item.id), Integer.valueOf(clamped));
        registerOrRefreshProperties(key, item);
        return true;
    }

    public static int getFuelTicks(ResourceLocation itemKey) {
        if (itemKey == null) {
            return 0;
        }
        Item item = ItemRegistry.get(itemKey);
        return getFuelTicks(item);
    }

    public static int getFuelTicks(String itemIdentifier) {
        return getFuelTicks(resolveItemByIdentifier(itemIdentifier));
    }

    public static int getFuelTicks(Item item) {
        if (item == null) {
            return 0;
        }

        Integer explicit = furnaceFuelByLegacyId.get(Integer.valueOf(item.id));
        if (explicit != null) {
            return explicit.intValue();
        }

        int itemId = item.id;
        ensureCookingFuelBindings();
        CookingFuel component = cookingFuelByLegacyId.get(Integer.valueOf(itemId));
        if (component != null) {
            // The beta 1.6 target did not yet accept saplings as furnace fuel.
            if (itemId == Block.SAPLING.id && Uberbukkit.getTargetPVN() < 11) {
                return 0;
            }
            if (itemId == Block.WOOD_PLATE.id
                    && Block.WOOD_PLATE.material != Material.WOOD) {
                return 0;
            }
            return resolveBurnTicks(component);
        }

        // Preserve the legacy extension rule for wooden block items registered
        // after the code-backed built-in attachment generation was published.
        if (itemId < 256 && Block.byId[itemId] != null && Block.byId[itemId].material == Material.WOOD) {
            return resolveBurnTicks(new CookingFuel(
                    NumberProviders.COOKING_TIME_WOOD_BLOCKS,
                    NumberProviders.COOKING_DEFAULT_SPEED_MULTIPLIER));
        }

        return 0;
    }

    public static int getFuelTicks(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return 0;
        }
        return getFuelTicks(stack.getItem());
    }

    public static boolean isFuel(ItemStack stack) {
        return getFuelTicks(stack) > 0;
    }

    public static boolean isFuel(Item item) {
        return getFuelTicks(item) > 0;
    }

    public static int getMaxStackSize(ResourceLocation itemKey) {
        ItemProperties props = getProperties(itemKey);
        return props == null ? 0 : props.getMaxStackSize();
    }

    public static int getMaxStackSize(String itemIdentifier) {
        return getMaxStackSize(resolveItemByIdentifier(itemIdentifier));
    }

    public static int getMaxStackSize(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return 0;
        }
        return getMaxStackSize(stack.getItem());
    }

    public static int getMaxStackSize(Item item) {
        ItemProperties props = getProperties(item);
        return props == null ? 0 : props.getMaxStackSize();
    }

    public static ItemProperties getProperties(ResourceLocation itemKey) {
        if (itemKey == null) {
            return null;
        }
        ensurePropertiesCoverage();
        ItemProperties existing = propertiesByKey.get(itemKey);
        if (existing != null) {
            return existing;
        }
        Item item = ItemRegistry.get(itemKey);
        if (item == null) {
            return null;
        }
        registerOrRefreshProperties(itemKey, item);
        return propertiesByKey.get(itemKey);
    }

    public static ItemProperties getProperties(String itemIdentifier) {
        return getProperties(resolveItemByIdentifier(itemIdentifier));
    }

    public static ItemProperties getProperties(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        return getProperties(stack.getItem());
    }

    public static ItemProperties getProperties(Item item) {
        ensurePropertiesCoverage();
        if (item == null) {
            return null;
        }
        ItemProperties existing = propertiesByLegacyId.get(Integer.valueOf(item.id));
        if (existing != null) {
            return existing;
        }
        registerOrRefreshProperties(ItemRegistry.getKey(item), item);
        return propertiesByLegacyId.get(Integer.valueOf(item.id));
    }

    public static synchronized int size() {
        ensurePropertiesCoverage();
        return propertiesByLegacyId.size();
    }

    public static synchronized Set<ResourceLocation> keys() {
        ensurePropertiesCoverage();
        return Collections.unmodifiableSet(propertiesByKey.keySet());
    }

    /** Immutable code-backed component assignments; plugin literal overrides are excluded. */
    public static Map<ResourceLocation, CookingFuel> snapshotCookingFuelBindings() {
        ensureCookingFuelBindings();
        return cookingFuelByKey;
    }

    public static CookingFuel getCookingFuel(Item item) {
        ensureCookingFuelBindings();
        return getCookingFuelIfPresent(item);
    }

    /** Cache-safe lookup used while constructing default item components. */
    public static CookingFuel getCookingFuelIfPresent(Item item) {
        if (item == null) return null;
        return cookingFuelByLegacyId.get(Integer.valueOf(item.id));
    }

    /** Creates a fresh remainder for one consumed item. Legacy API overrides win. */
    public static ItemStack createCraftingRemainder(Item consumedItem) {
        if (consumedItem == null) return null;
        boolean overridden;
        Item legacy;
        synchronized (ItemCapabilityRegistryApi.class) {
            overridden = craftingRemainderOverrides.containsKey(consumedItem);
            legacy = craftingRemainderOverrides.get(consumedItem);
        }
        if (overridden) return legacy == null ? null : new ItemStack(legacy);
        ensureCraftingRemainderBindings();
        ItemStackTemplate template = craftingRemainderByItem.get(consumedItem);
        return template == null ? null : template.create();
    }

    /** Item-only adapter retained for the legacy container-item interface. */
    public static Item getLegacyCraftingRemainderItem(Item consumedItem) {
        if (consumedItem == null) return null;
        synchronized (ItemCapabilityRegistryApi.class) {
            if (craftingRemainderOverrides.containsKey(consumedItem)) {
                return craftingRemainderOverrides.get(consumedItem);
            }
        }
        ensureCraftingRemainderBindings();
        ItemStackTemplate template = craftingRemainderByItem.get(consumedItem);
        return template == null ? null : template.getItem();
    }

    /** Immutable canonical bindings; legacy API overrides are excluded. */
    public static Map<ResourceLocation, ItemStackTemplate>
            snapshotCraftingRemainderBindings() {
        ensureCraftingRemainderBindings();
        return craftingRemainderByKey;
    }

    /** Compatibility adapter for the legacy Item container-item setter. */
    public static synchronized void setLegacyCraftingRemainderOverride(
            Item consumedItem,
            Item remainderItem) {
        if (consumedItem == null) {
            throw new IllegalArgumentException("Consumed item cannot be null");
        }
        if (consumedItem.getMaxStackSize() > 1) {
            throw new IllegalArgumentException(
                    "Max stack size must be 1 for items with crafting results");
        }
        craftingRemainderOverrides.put(consumedItem, remainderItem);
    }

    public static synchronized void clearLegacyCraftingRemainderOverride(
            Item consumedItem) {
        if (consumedItem != null) {
            craftingRemainderOverrides.remove(consumedItem);
        }
    }

    static boolean publishCraftingRemainderBindings(
            final long expectedItemRevision,
            Map<ResourceLocation, ItemStackTemplate> staged) {
        if (staged == null) {
            throw new IllegalArgumentException(
                    "Crafting-remainder bindings cannot be null");
        }
        final LinkedHashMap<ResourceLocation, ItemStackTemplate> byKey =
                new LinkedHashMap<ResourceLocation, ItemStackTemplate>();
        final IdentityHashMap<Item, ItemStackTemplate> byItem =
                new IdentityHashMap<Item, ItemStackTemplate>();
        for (Map.Entry<ResourceLocation, ItemStackTemplate> entry
                : staged.entrySet()) {
            ResourceLocation inputKey = entry.getKey();
            ItemStackTemplate template = entry.getValue();
            Item input = inputKey == null ? null : ItemRegistry.get(inputKey);
            Item output = template == null ? null : template.getItem();
            ResourceLocation outputKey = template == null
                    ? null : template.getItemKey();
            if (input == null || template == null || output == null
                    || !inputKey.equals(ItemRegistry.getKey(input))
                    || outputKey == null || ItemRegistry.get(outputKey) != output
                    || !outputKey.equals(ItemRegistry.getKey(output))) {
                throw new IllegalArgumentException(
                        "Crafting-remainder binding must use canonical items: "
                                + inputKey + " -> " + outputKey);
            }
            if (input.getMaxStackSize() > 1) {
                throw new IllegalArgumentException(
                        "Crafting-remainder input must stack to one: " + inputKey);
            }
            if (byItem.put(input, template) != null) {
                throw new IllegalArgumentException(
                        "Duplicate crafting-remainder input item " + inputKey);
            }
            byKey.put(inputKey, template);
        }
        final boolean[] published = new boolean[] {false};
        boolean revisionMatched = ItemRegistry.publishIfRevision(
                expectedItemRevision,
                new Runnable() {
                    public void run() {
                        craftingRemainderByKey = Collections.unmodifiableMap(byKey);
                        craftingRemainderByItem = Collections.unmodifiableMap(byItem);
                        craftingRemainderBindingsInitialized = true;
                        published[0] = true;
                    }
                });
        return revisionMatched && published[0];
    }

    static synchronized int bootstrapDefaults() {
        NumberProviderRegistryBootstrap.initialize();
        ItemRegistry.keys();

        ensurePropertiesCoverage();
        if (cookingFuelBindingsInitialized) {
            return propertiesByLegacyId.size();
        }

        LinkedHashMap<ResourceLocation, CookingFuel> byKey =
                new LinkedHashMap<ResourceLocation, CookingFuel>();
        HashMap<Integer, CookingFuel> byLegacyId =
                new HashMap<Integer, CookingFuel>();

        for (int i = 0; i < Block.byId.length; i++) {
            Block block = Block.byId[i];
            if (block == null || block.material != Material.WOOD
                    || i >= Item.byId.length || Item.byId[i] == null) {
                continue;
            }
            bindCookingFuel(byKey, byLegacyId, Item.byId[i],
                    NumberProviders.COOKING_TIME_WOOD_BLOCKS);
        }
        // Pre-PVN12 servers construct this block with stone material. Retain
        // its canonical wood-item attachment for client/server fingerprint
        // parity; getFuelTicks gates the old profile's non-fuel behavior.
        bindCookingFuel(byKey, byLegacyId, Item.byId[Block.WOOD_PLATE.id],
                NumberProviders.COOKING_TIME_WOOD_BLOCKS);
        bindCookingFuel(byKey, byLegacyId, Item.COAL,
                NumberProviders.COOKING_TIME_COAL);
        bindCookingFuel(byKey, byLegacyId, Item.byId[Block.COAL_BLOCK.id],
                NumberProviders.COOKING_TIME_COAL_BLOCK);
        bindCookingFuel(byKey, byLegacyId, Item.LAVA_BUCKET,
                NumberProviders.COOKING_TIME_LAVA_BUCKET);
        bindCookingFuel(byKey, byLegacyId, Item.STICK,
                NumberProviders.COOKING_TIME_WOOD_ITEMS_EXTRA_SMALL);
        // Keep client/server assignments identical at every target PVN. The
        // historical availability gate is applied only when resolving ticks.
        bindCookingFuel(byKey, byLegacyId, Item.byId[Block.SAPLING.id],
                NumberProviders.COOKING_TIME_DRY_PLANTS);

        cookingFuelByKey = Collections.unmodifiableMap(byKey);
        cookingFuelByLegacyId = Collections.unmodifiableMap(byLegacyId);
        cookingFuelBindingsInitialized = true;
        ItemComponentDefaults.clear();
        return propertiesByLegacyId.size();
    }

    private static synchronized void ensureCookingFuelBindings() {
        if (!cookingFuelBindingsInitialized) {
            bootstrapDefaults();
        }
    }

    private static void ensureCraftingRemainderBindings() {
        if (!craftingRemainderBindingsInitialized) {
            ItemCapabilityRegistryBootstrap.initialize();
        }
    }

    private static void bindCookingFuel(
            Map<ResourceLocation, CookingFuel> byKey,
            Map<Integer, CookingFuel> byLegacyId,
            Item item,
            ResourceLocation burnTimeKey) {
        if (item == null) {
            throw new IllegalStateException(
                    "Built-in cooking-fuel attachment has no item for " + burnTimeKey);
        }
        ResourceLocation itemKey = ItemRegistry.getKey(item);
        if (itemKey == null) {
            throw new IllegalStateException(
                    "Built-in cooking-fuel item has no canonical key: " + item.id);
        }
        CookingFuel component = new CookingFuel(
                burnTimeKey, NumberProviders.COOKING_DEFAULT_SPEED_MULTIPLIER);
        byKey.put(itemKey, component);
        byLegacyId.put(Integer.valueOf(item.id), component);
    }

    private static int resolveBurnTicks(CookingFuel component) {
        return component.resolveBurnTimeTicks();
    }

    private static synchronized void ensurePropertiesCoverage() {
        if (!propertyListenerRegistered) {
            ItemRegistry.addListener(ITEM_PROPERTY_LISTENER);
            propertyListenerRegistered = true;
        }

        // Ensure ItemRegistry has assigned canonical keys for all current items.
        ItemRegistry.keys();

        for (int i = 0; i < Item.byId.length; i++) {
            Item item = Item.byId[i];
            if (item == null) {
                continue;
            }
            registerOrRefreshProperties(ItemRegistry.getKey(item), item);
        }
    }

    private static synchronized void registerOrRefreshProperties(ResourceLocation key, Item item) {
        if (item == null) {
            return;
        }
        ItemProperties props = buildProperties(key, item);
        propertiesByLegacyId.put(Integer.valueOf(item.id), props);
        if (props.getKey() != null) {
            propertiesByKey.put(props.getKey(), props);
        }
    }

    private static ItemProperties buildProperties(ResourceLocation key, Item item) {
        ResourceLocation resolvedKey = key;
        if (resolvedKey == null) {
            resolvedKey = ItemRegistry.getKey(item);
        }

        int limit = item.getMaxStackSize();
        // New items default to 64 unless the item explicitly specifies another valid value.
        if (limit <= 0) {
            limit = 64;
        }

        return new ItemProperties(
            resolvedKey,
            item.id,
            limit,
            item.e(),
            item.d(),
            item.f()
        );
    }

    private static Item resolveItemByIdentifier(String itemIdentifier) {
        if (itemIdentifier == null) {
            return null;
        }
        String normalized = ItemRegistry.normalizeInputIdentifier(itemIdentifier);
        if (normalized == null) {
            return null;
        }
        return ItemRegistry.get(new ResourceLocation(normalized));
    }

    public static final class ItemProperties {
        private final ResourceLocation key;
        private final int legacyId;
        private final int maxStackSize;
        private final int maxDamage;
        private final boolean hasSubtypes;
        private final boolean damageable;

        private ItemProperties(
            ResourceLocation key,
            int legacyId,
            int maxStackSize,
            int maxDamage,
            boolean hasSubtypes,
            boolean damageable
        ) {
            this.key = key;
            this.legacyId = legacyId;
            this.maxStackSize = maxStackSize;
            this.maxDamage = maxDamage;
            this.hasSubtypes = hasSubtypes;
            this.damageable = damageable;
        }

        public ResourceLocation getKey() {
            return key;
        }

        public int getLegacyId() {
            return legacyId;
        }

        public int getMaxStackSize() {
            return maxStackSize;
        }

        public int getMaxDamage() {
            return maxDamage;
        }

        public boolean hasSubtypes() {
            return hasSubtypes;
        }

        public boolean isDamageable() {
            return damageable;
        }

        public int getFuelTicks() {
            Item item = this.legacyId < 0 || this.legacyId >= Item.byId.length
                    ? null : Item.byId[this.legacyId];
            return ItemCapabilityRegistryApi.getFuelTicks(item);
        }
    }
}
