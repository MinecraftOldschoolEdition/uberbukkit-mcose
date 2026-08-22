package net.minecraft.server.registry;

import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;

/** Loads legacy structure configuration while generators remain Java-backed. */
public final class StructureTypes {
    public static final ResourceLocation DUNGEON =
            new ResourceLocation("minecraft", "dungeon");
    public static final ResourceLocation HEROBRINE_SHRINE =
            new ResourceLocation("minecraft", "herobrine_shrine");

    private static final ResourceLocation LEGACY_ORDER =
            new ResourceLocation("minecraft", "legacy_order");
    private static boolean initialized;

    private StructureTypes() {}

    public static synchronized void initialize() {
        if (initialized) return;

        // The descriptor reference remains metadata only, but preserve the
        // original bootstrap dependency and registry API visibility.
        LootTables.initialize();
        List<ResourceLocation> keys = RegistryDataLoader.loadRequiredTag(
                "worldgen/structure", LEGACY_ORDER);
        List<ResourceLocation> expected = Arrays.asList(DUNGEON, HEROBRINE_SHRINE);
        if (!expected.equals(keys)) {
            throw new IllegalStateException("Legacy structure order must remain " + expected
                    + " (was " + keys + ")");
        }

        Map<ResourceLocation, StructureType> decoded = RegistryDataLoader.loadRequired(
                "worldgen/structure",
                keys,
                new RegistryDataLoader.Decoder<StructureType>() {
                    public StructureType decode(ResourceLocation key, JsonObject json) {
                        return StructureTypeCodec.decode(key, json);
                    }
                });
        if (!StructureTypeRegistryApi.publishAtomic(decoded)) {
            throw new IllegalStateException(
                    "Built-in structure descriptors could not be published atomically");
        }
        initialized = true;
    }

    public static StructureType get(ResourceLocation id) {
        initialize();
        return StructureTypeRegistryApi.get(id);
    }

    public static StructureType getByIdentifier(String any) {
        initialize();
        return StructureTypeRegistryApi.getByIdentifier(any);
    }

    public static ResourceLocation getKey(StructureType value) {
        initialize();
        return StructureTypeRegistryApi.getKey(value);
    }

    public static Set<ResourceLocation> keys() {
        initialize();
        return StructureTypeRegistryApi.keys();
    }

    public static Collection<StructureType> values() {
        initialize();
        return StructureTypeRegistryApi.values();
    }

    public static int size() {
        initialize();
        return StructureTypeRegistryApi.size();
    }

    public static String normalizeInputIdentifier(String any) {
        return StructureTypeRegistryApi.normalizeInputIdentifier(any);
    }

    public static String canonicalizeIdentifier(String any) {
        return StructureTypeRegistryApi.canonicalizeIdentifier(any);
    }
}
