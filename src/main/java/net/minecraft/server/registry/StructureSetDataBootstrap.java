package net.minecraft.server.registry;

import com.google.gson.JsonObject;
import java.io.File;
import java.util.List;
import java.util.Map;
import net.minecraft.server.BiomeBase;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;

/** Loads supported modern-shaped structure sets as one validated generation. */
public final class StructureSetDataBootstrap {
    public static final ResourceLocation HEROBRINE_SHRINE =
            StructureTypes.HEROBRINE_SHRINE;
    private static final int INFDEV_TERRAIN_TYPE = 7;
    private static boolean initialized;

    private StructureSetDataBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        publish(prepare(null));
        initialized = true;
    }

    public static synchronized void reload() {
        reload(null);
    }

    static synchronized void reload(
            RegistryDataLoader.ResourceProvider provider) {
        publish(prepare(provider));
        initialized = true;
    }

    static Map<ResourceLocation, StructureSetDefinition> loadForTests(
            RegistryDataLoader.ResourceProvider provider) {
        return prepare(provider);
    }

    public static StructureSetDefinition get(ResourceLocation key) {
        initialize();
        return StructureSetRegistryApi.get(key);
    }

    public static StructureSetDefinition herobrineShrine() {
        return get(HEROBRINE_SHRINE);
    }

    /**
     * Applies modern structure-biome validity with the one intentional legacy
     * exception: Infdev always permits shrine placement attempts.
     */
    public static boolean isHerobrineShrineAllowed(
            int terrainType, BiomeBase biome) {
        if (terrainType == INFDEV_TERRAIN_TYPE) return true;
        StructureType type = StructureTypes.get(
                StructureTypes.HEROBRINE_SHRINE);
        return type != null && type.isValidBiome(biome);
    }

    public static int size() {
        initialize();
        return StructureSetRegistryApi.size();
    }

    private static Map<ResourceLocation, StructureSetDefinition> prepare(
            RegistryDataLoader.ResourceProvider provider) {
        StructureTypes.initialize();
        RegistryDataLoader.ResourceProvider selectedProvider = provider == null
                ? RegistryDataLoader.createLayeredProvider(
                        configuredResourceRoot())
                : provider;
        Map<ResourceLocation, StructureSetDefinition> decoded =
                RegistryDataLoader.loadAll(
                        "worldgen/structure_set",
                        selectedProvider,
                        new RegistryDataLoader.Decoder<
                                StructureSetDefinition>() {
                            public StructureSetDefinition decode(
                                    ResourceLocation key,
                                    JsonObject json) {
                                return StructureSetCodec.decode(key, json);
                            }
                        });

        StructureSetDefinition shrine = decoded.get(HEROBRINE_SHRINE);
        if (shrine == null || !HEROBRINE_SHRINE.equals(shrine.getId())) {
            throw new IllegalStateException(
                    "Missing required structure set " + HEROBRINE_SHRINE);
        }
        List<StructureSetDefinition.StructureSelectionEntry> entries =
                shrine.getStructures();
        if (entries.size() != 1
                || !StructureTypes.HEROBRINE_SHRINE.equals(
                        entries.get(0).getStructure())) {
            throw new IllegalStateException(
                    "Herobrine shrine structure set must contain only "
                            + StructureTypes.HEROBRINE_SHRINE);
        }

        for (StructureSetDefinition definition : decoded.values()) {
            for (StructureSetDefinition.StructureSelectionEntry entry
                    : definition.getStructures()) {
                if (StructureTypes.get(entry.getStructure()) == null) {
                    throw new IllegalArgumentException(
                            "Structure set " + definition.getId()
                                    + " references unknown structure "
                                    + entry.getStructure());
                }
            }
        }
        return decoded;
    }

    private static void publish(
            Map<ResourceLocation, StructureSetDefinition> prepared) {
        if (!StructureSetRegistryApi.publishReplacementAtomic(prepared)) {
            throw new IllegalStateException(
                    "Prepared structure-set generation was rejected");
        }
    }

    private static File configuredResourceRoot() {
        String configured = System.getProperty("mcose.resourcesDir");
        return configured == null || configured.length() == 0
                ? null : new File(configured);
    }
}
