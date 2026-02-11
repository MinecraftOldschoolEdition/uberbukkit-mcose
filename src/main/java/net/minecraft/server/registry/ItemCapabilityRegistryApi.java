package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.Material;
import net.minecraft.server.util.ResourceLocation;
import uk.betacraft.uberbukkit.Uberbukkit;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * API-facing registry for item interaction capabilities.
 * Exposes furnace fuel duration semantics and core item properties.
 */
public final class ItemCapabilityRegistryApi {
    private static final Map<ResourceLocation, Integer> furnaceFuelByKey = new LinkedHashMap<ResourceLocation, Integer>();
    private static final Map<Integer, Integer> furnaceFuelByLegacyId = new HashMap<Integer, Integer>();
    private static final Map<ResourceLocation, ItemProperties> propertiesByKey = new LinkedHashMap<ResourceLocation, ItemProperties>();
    private static final Map<Integer, ItemProperties> propertiesByLegacyId = new HashMap<Integer, ItemProperties>();
    private static final ItemRegistry.Listener ITEM_PROPERTY_LISTENER = new ItemRegistry.Listener() {
        public void onRegistered(ResourceLocation key, Item item) {
            registerOrRefreshProperties(key, item);
        }
    };
    private static boolean propertyListenerRegistered = false;

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
        if (itemId < 256 && Block.byId[itemId] != null && Block.byId[itemId].material == Material.WOOD) {
            return 300;
        }
        if (itemId == Item.STICK.id) {
            return 100;
        }
        if (itemId == Item.COAL.id) {
            return 1600;
        }
        if (itemId == Item.LAVA_BUCKET.id) {
            return 20000;
        }
        if (Uberbukkit.getTargetPVN() >= 11 && itemId == Block.SAPLING.id) {
            return 100;
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

    static synchronized int bootstrapDefaults() {
        if (registerFuel(Item.COAL, 1600)) {
            // defaults retained for explicit capability map entries
        }
        if (registerFuel(Item.LAVA_BUCKET, 20000)) {
            // defaults retained for explicit capability map entries
        }
        if (registerFuel(Item.STICK, 100)) {
            // defaults retained for explicit capability map entries
        }
        if (Uberbukkit.getTargetPVN() >= 11) {
            registerFuel(Block.SAPLING.id, 100);
        }

        ensurePropertiesCoverage();
        return propertiesByLegacyId.size();
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
            item.f(),
            getFuelTicks(item)
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
        private final int fuelTicks;

        private ItemProperties(
            ResourceLocation key,
            int legacyId,
            int maxStackSize,
            int maxDamage,
            boolean hasSubtypes,
            boolean damageable,
            int fuelTicks
        ) {
            this.key = key;
            this.legacyId = legacyId;
            this.maxStackSize = maxStackSize;
            this.maxDamage = maxDamage;
            this.hasSubtypes = hasSubtypes;
            this.damageable = damageable;
            this.fuelTicks = fuelTicks;
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
            return fuelTicks;
        }
    }
}
