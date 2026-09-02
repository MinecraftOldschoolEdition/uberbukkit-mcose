package net.minecraft.server.registry;

import net.minecraft.server.DeathMessageRules;

/**
 * Central bootstrap that initializes all registries in deterministic order.
 */
public final class RegistryBootstrap {
    private static boolean initialized = false;

    private RegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        long start = System.currentTimeMillis();

        BlockRegistryBootstrap.initialize();
        ItemRegistryBootstrap.initialize();
        ItemTagRegistryBootstrap.initialize();
        NumberProviderRegistryBootstrap.initialize();
        NaturalGrowthRegistryBootstrap.initialize();
        ItemCapabilityRegistryBootstrap.initialize();
        BlockCapabilityRegistryBootstrap.initialize();
        BlockMiningRegistryBootstrap.initialize();
        FireSpreadDataBootstrap.initialize();
        if (!BlockRegistry.runSanityChecks()) {
            throw new IllegalStateException("Block registry sanity checks failed");
        }
        if (!ItemRegistry.runSanityChecks()) {
            throw new IllegalStateException("Item registry sanity checks failed");
        }
        LegacyIdBridge.refresh();
        System.out.println("[RegistryBootstrap] Block key hash: " + BlockRegistry.keysetFingerprint());
        System.out.println("[RegistryBootstrap] Item key hash: " + ItemRegistry.keysetFingerprint());

        BlockEntityTypeRegistryBootstrap.initialize();
        EntityTypeRegistryBootstrap.initialize();
        BiomeRegistryBootstrap.initialize();
        BiomeSpawnSettingsBootstrap.initialize();
        DamageTypeRegistryBootstrap.initialize();
        DeathMessageRules.initialize();
        ChunkGeneratorTypeRegistryBootstrap.initialize();
        WorldTypeRegistryBootstrap.initialize();
        FluidRegistryBootstrap.initialize();
        DimensionTypeRegistryBootstrap.initialize();
        WorldPresetDataBootstrap.initialize();

        RecipeTypeRegistryBootstrap.initialize();
        RecipeRegistryBootstrap.initialize();

        WorldFeatureRegistryBootstrap.initialize();
        TreeDecoratorTypeRegistryBootstrap.initialize();
        FoliagePlacerTypeRegistryBootstrap.initialize();
        FeatureRegistryBootstrap.initialize();
        ConfiguredFeatureDataBootstrap.initialize();
        PlacedFeatureDataBootstrap.initialize();
        CarverRegistryBootstrap.initialize();
        ConfiguredCarverDataBootstrap.initialize();
        SurfaceBuilderRegistryBootstrap.initialize();

        LootTables.initialize();
        StructureTypes.initialize();
        StructureSetDataBootstrap.initialize();
        SpawnGroupRegistryBootstrap.initialize();
        JukeboxSongRegistryBootstrap.initialize();
        PaintingMotiveRegistryBootstrap.initialize();
        SoundEventRegistryBootstrap.initialize();
        ScreenHandlerRegistryBootstrap.initialize();
        ParticleTypeRegistryBootstrap.initialize();
        ScheduleRegistryBootstrap.initialize();
        SensorTypeRegistryBootstrap.initialize();
        MemoryModuleTypeRegistryBootstrap.initialize();
        PointOfInterestRegistryBootstrap.initialize();
        // Excluded polished registries keep existing behavior.
        StatRegistryBootstrap.initialize();
        AchievementRegistryBootstrap.initialize();

        RegistryDebugAsserts.runCoreIntegrityChecks();
        RegistryRuntime.captureAndPublish();
        String synchronizedDataFingerprint =
                RegistryDataFingerprint.captureSynchronizedData();

        long elapsed = System.currentTimeMillis() - start;
        initialized = true;
        System.out.println("[RegistryBootstrap] All server registries initialized in " + elapsed + "ms");
        System.out.println("[RegistryBootstrap] Synchronized data hash: "
                + synchronizedDataFingerprint);
    }

    public static synchronized boolean isInitialized() {
        return initialized;
    }
}
