package net.minecraft.server.registry;

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
        initialized = true;

        long start = System.currentTimeMillis();

        BlockRegistryBootstrap.initialize();
        ItemRegistryBootstrap.initialize();
        NaturalGrowthRegistryBootstrap.initialize();
        ItemCapabilityRegistryBootstrap.initialize();
        BlockCapabilityRegistryBootstrap.initialize();
        BlockMiningRegistryBootstrap.initialize();
        BlockRegistry.runSanityChecks();
        ItemRegistry.runSanityChecks();
        LegacyIdBridge.refresh();
        System.out.println("[RegistryBootstrap] Block key hash: " + BlockRegistry.keysetFingerprint());
        System.out.println("[RegistryBootstrap] Item key hash: " + ItemRegistry.keysetFingerprint());

        BlockEntityTypeRegistryBootstrap.initialize();
        EntityTypeRegistryBootstrap.initialize();
        BiomeRegistryBootstrap.initialize();
        ChunkGeneratorTypeRegistryBootstrap.initialize();
        WorldTypeRegistryBootstrap.initialize();
        FluidRegistryBootstrap.initialize();
        DimensionTypeRegistryBootstrap.initialize();

        RecipeTypeRegistryBootstrap.initialize();
        RecipeRegistryBootstrap.initialize();

        WorldFeatureRegistryBootstrap.initialize();
        TreeDecoratorTypeRegistryBootstrap.initialize();
        FoliagePlacerTypeRegistryBootstrap.initialize();
        FeatureRegistryBootstrap.initialize();
        CarverRegistryBootstrap.initialize();
        SurfaceBuilderRegistryBootstrap.initialize();

        LootTables.initialize();
        StructureTypes.initialize();
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

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("[RegistryBootstrap] All server registries initialized in " + elapsed + "ms");
    }

    public static synchronized boolean isInitialized() {
        return initialized;
    }
}
