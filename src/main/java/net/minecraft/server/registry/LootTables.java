package net.minecraft.server.registry;

import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;

/**
 * Loads the built-in loot tables from the modern
 * {@code data/<namespace>/loot_table} tree.
 */
public final class LootTables {
    public static final ResourceLocation DUNGEON =
            new ResourceLocation("minecraft", "chests/dungeon");
    public static final ResourceLocation SIMPLE_DUNGEON_LOOT =
            new ResourceLocation("minecraft", "chests/simple_dungeon");
    public static final ResourceLocation MONSTER_DUNGEON_LOOT =
            new ResourceLocation("minecraft", "chests/monster_dungeon");

    private static final List<ResourceLocation> BUILT_IN_TABLES =
            Collections.unmodifiableList(Arrays.asList(
                    DUNGEON,
                    SIMPLE_DUNGEON_LOOT,
                    MONSTER_DUNGEON_LOOT));

    private static boolean initialized;

    private LootTables() {}

    public static synchronized void initialize() {
        if (initialized) return;

        Map<ResourceLocation, LootTable> decoded = RegistryDataLoader.loadRequired(
                "loot_table",
                BUILT_IN_TABLES,
                new RegistryDataLoader.Decoder<LootTable>() {
                    public LootTable decode(ResourceLocation key, JsonObject json) {
                        return LootTableCodec.decode(key, json);
                    }
                });
        if (!LootTableRegistryApi.publishAtomic(decoded)) {
            throw new IllegalStateException(
                    "Built-in loot tables could not be published atomically");
        }
        initialized = true;
    }

    public static LootTable get(ResourceLocation id) {
        initialize();
        return LootTableRegistryApi.get(id);
    }

    public static LootTable getByIdentifier(String any) {
        initialize();
        return LootTableRegistryApi.getByIdentifier(any);
    }

    public static ResourceLocation getKey(LootTable value) {
        initialize();
        return LootTableRegistryApi.getKey(value);
    }

    public static Set<ResourceLocation> keys() {
        initialize();
        return LootTableRegistryApi.keys();
    }

    public static Collection<LootTable> values() {
        initialize();
        return LootTableRegistryApi.values();
    }

    public static int size() {
        initialize();
        return LootTableRegistryApi.size();
    }

    public static String normalizeInputIdentifier(String any) {
        return LootTableRegistryApi.normalizeInputIdentifier(any);
    }

    public static String canonicalizeIdentifier(String any) {
        return LootTableRegistryApi.canonicalizeIdentifier(any);
    }
}
