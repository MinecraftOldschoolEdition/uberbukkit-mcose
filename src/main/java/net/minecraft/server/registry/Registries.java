package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.CraftingRecipe;
import net.minecraft.server.Item;
import net.minecraft.server.PaintingVariant;
import net.minecraft.server.WorldGenerator;
import net.minecraft.server.Achievement;
import net.minecraft.server.Statistic;
import net.minecraft.server.DamageType;
import net.minecraft.server.util.ResourceLocation;
import net.minecraft.server.registry.number.NumberProvider;

public final class Registries {
    // === Meta Registry ===
    public static final SimpleRegistry<Object> REGISTRIES = new SimpleRegistry<Object>();
    
    // === Core Content Registries ===
    public static final SimpleRegistry<Block> BLOCK = new SimpleRegistry<Block>();
    public static final SimpleRegistry<Item> ITEM = new SimpleRegistry<Item>();
    public static final SimpleRegistry<Class<?>> BLOCK_ENTITY_TYPE = new SimpleRegistry<Class<?>>();
    public static final SimpleRegistry<Class<?>> ENTITY_TYPE = new SimpleRegistry<Class<?>>();
    public static final SimpleRegistry<net.minecraft.server.BiomeBase> BIOME = new SimpleRegistry<net.minecraft.server.BiomeBase>();
    public static final SimpleRegistry<DamageType> DAMAGE_TYPE =
            new SimpleRegistry<DamageType>();
    
    // === World Generation ===
    public static final SimpleRegistry<Class<?>> CHUNK_GENERATOR_TYPE = new SimpleRegistry<Class<?>>();
    public static final SimpleRegistry<Integer> WORLD_TYPE = new SimpleRegistry<Integer>();
    public static final SimpleRegistry<Block> FLUID = new SimpleRegistry<Block>();
    public static final SimpleRegistry<DimensionTypeDefinition> DIMENSION_TYPE =
            new SimpleRegistry<DimensionTypeDefinition>();
    public static final SimpleRegistry<WorldPresetDefinition> WORLD_PRESET =
            new SimpleRegistry<WorldPresetDefinition>();
    public static final SimpleRegistry<ConfiguredFeatureDefinition> CONFIGURED_FEATURE =
            new SimpleRegistry<ConfiguredFeatureDefinition>();
    public static final SimpleRegistry<PlacedFeatureDefinition> PLACED_FEATURE =
            new SimpleRegistry<PlacedFeatureDefinition>();
    public static final SimpleRegistry<WorldGenerator> WORLD_FEATURE = new SimpleRegistry<WorldGenerator>();
    public static final SimpleRegistry<StructureType> STRUCTURE_TYPE = new SimpleRegistry<StructureType>();
    public static final SimpleRegistry<LootTable> LOOT_TABLE = new SimpleRegistry<LootTable>();
    public static final SimpleRegistry<NumberProvider> NUMBER_PROVIDER =
            new SimpleRegistry<NumberProvider>();
    
    // === Recipe System ===
    public static final SimpleRegistry<RecipeType<?>> RECIPE_TYPE = new SimpleRegistry<RecipeType<?>>();
    public static final SimpleRegistry<CraftingRecipe> RECIPE = new SimpleRegistry<CraftingRecipe>();
    
    // === Gameplay ===
    public static final SimpleRegistry<Statistic> STAT = new SimpleRegistry<Statistic>();
    public static final SimpleRegistry<Achievement> ACHIEVEMENT = new SimpleRegistry<Achievement>();
    public static final SimpleRegistry<LegacyAdvancementDefinition> ADVANCEMENT =
            new SimpleRegistry<LegacyAdvancementDefinition>();
    public static final SimpleRegistry<net.minecraft.server.EnumArt> PAINTING_MOTIVE = new SimpleRegistry<net.minecraft.server.EnumArt>();
    public static final SimpleRegistry<PaintingVariant> PAINTING_VARIANT = new SimpleRegistry<PaintingVariant>();
    public static final SimpleRegistry<JukeboxSong> JUKEBOX_SONG = new SimpleRegistry<JukeboxSong>();
    public static final SimpleRegistry<SpawnGroup> SPAWN_GROUP = new SimpleRegistry<SpawnGroup>();
    
    // === Client/Rendering (stubs for parity) ===
    public static final SimpleRegistry<String> SOUND_EVENT = new SimpleRegistry<String>();
    public static final SimpleRegistry<Class<?>> SCREEN_HANDLER = new SimpleRegistry<Class<?>>();
    public static final SimpleRegistry<ParticleType> PARTICLE_TYPE = new SimpleRegistry<ParticleType>();
    
    // === AI System ===
    public static final SimpleRegistry<Schedule> SCHEDULE = new SimpleRegistry<Schedule>();
    public static final SimpleRegistry<SensorType> SENSOR_TYPE = new SimpleRegistry<SensorType>();
    public static final SimpleRegistry<MemoryModuleType> MEMORY_MODULE_TYPE = new SimpleRegistry<MemoryModuleType>();
    public static final SimpleRegistry<PointOfInterestType> POINT_OF_INTEREST_TYPE = new SimpleRegistry<PointOfInterestType>();
    
    // === World Gen Details ===
    public static final SimpleRegistry<TreeDecoratorType> TREE_DECORATOR_TYPE = new SimpleRegistry<TreeDecoratorType>();
    public static final SimpleRegistry<FoliagePlacerType> FOLIAGE_PLACER_TYPE = new SimpleRegistry<FoliagePlacerType>();
    public static final SimpleRegistry<FeatureType> FEATURE = new SimpleRegistry<FeatureType>();
    public static final SimpleRegistry<CarverType> CARVER_TYPE =
            new SimpleRegistry<CarverType>();
    /** Legacy source/binary alias; configured carvers are in CONFIGURED_CARVER. */
    @Deprecated
    public static final SimpleRegistry<CarverType> CARVER = CARVER_TYPE;
    public static final SimpleRegistry<ConfiguredCarverDefinition> CONFIGURED_CARVER =
            new SimpleRegistry<ConfiguredCarverDefinition>();
    public static final SimpleRegistry<SurfaceBuilderType> SURFACE_BUILDER = new SimpleRegistry<SurfaceBuilderType>();

    static {
        // Core content
        REGISTRIES.register(new ResourceLocation("minecraft", "block"), BLOCK);
        REGISTRIES.register(new ResourceLocation("minecraft", "item"), ITEM);
        REGISTRIES.register(new ResourceLocation("minecraft", "block_entity_type"), BLOCK_ENTITY_TYPE);
        REGISTRIES.register(new ResourceLocation("minecraft", "entity_type"), ENTITY_TYPE);
        REGISTRIES.register(new ResourceLocation("minecraft", "biome"), BIOME);
        REGISTRIES.register(new ResourceLocation("minecraft", "damage_type"), DAMAGE_TYPE);
        
        // World generation
        REGISTRIES.register(new ResourceLocation("minecraft", "chunk_generator_type"), CHUNK_GENERATOR_TYPE);
        REGISTRIES.register(new ResourceLocation("minecraft", "world_type"), WORLD_TYPE);
        REGISTRIES.register(new ResourceLocation("minecraft", "fluid"), FLUID);
        REGISTRIES.register(new ResourceLocation("minecraft", "dimension_type"), DIMENSION_TYPE);
        REGISTRIES.register(new ResourceLocation("minecraft", "worldgen/world_preset"), WORLD_PRESET);
        REGISTRIES.register(new ResourceLocation("minecraft", "worldgen/feature"), CONFIGURED_FEATURE);
        REGISTRIES.register(new ResourceLocation("minecraft", "worldgen/placed_feature"), PLACED_FEATURE);
        REGISTRIES.register(new ResourceLocation("minecraft", "world_feature"), WORLD_FEATURE);
        REGISTRIES.register(new ResourceLocation("minecraft", "structure_type"), STRUCTURE_TYPE);
        REGISTRIES.register(new ResourceLocation("minecraft", "loot_table"), LOOT_TABLE);
        REGISTRIES.register(new ResourceLocation("minecraft", "number_provider"), NUMBER_PROVIDER);
        
        // Recipe system
        REGISTRIES.register(new ResourceLocation("minecraft", "recipe_type"), RECIPE_TYPE);
        REGISTRIES.register(new ResourceLocation("minecraft", "recipe"), RECIPE);
        
        // Gameplay
        REGISTRIES.register(new ResourceLocation("minecraft", "stat"), STAT);
        REGISTRIES.register(new ResourceLocation("minecraft", "achievement"), ACHIEVEMENT);
        REGISTRIES.register(new ResourceLocation("minecraft", "advancement"), ADVANCEMENT);
        REGISTRIES.register(new ResourceLocation("minecraft", "painting_motive"), PAINTING_MOTIVE);
        REGISTRIES.register(new ResourceLocation("minecraft", "painting_variant"), PAINTING_VARIANT);
        REGISTRIES.register(new ResourceLocation("minecraft", "jukebox_song"), JUKEBOX_SONG);
        REGISTRIES.register(new ResourceLocation("minecraft", "spawn_group"), SPAWN_GROUP);
        
        // Client/Rendering
        REGISTRIES.register(new ResourceLocation("minecraft", "sound_event"), SOUND_EVENT);
        REGISTRIES.register(new ResourceLocation("minecraft", "screen_handler"), SCREEN_HANDLER);
        REGISTRIES.register(new ResourceLocation("minecraft", "particle_type"), PARTICLE_TYPE);
        
        // AI System
        REGISTRIES.register(new ResourceLocation("minecraft", "schedule"), SCHEDULE);
        REGISTRIES.register(new ResourceLocation("minecraft", "sensor_type"), SENSOR_TYPE);
        REGISTRIES.register(new ResourceLocation("minecraft", "memory_module_type"), MEMORY_MODULE_TYPE);
        REGISTRIES.register(new ResourceLocation("minecraft", "point_of_interest_type"), POINT_OF_INTEREST_TYPE);
        
        // World gen details
        REGISTRIES.register(new ResourceLocation("minecraft", "tree_decorator_type"), TREE_DECORATOR_TYPE);
        REGISTRIES.register(new ResourceLocation("minecraft", "foliage_placer_type"), FOLIAGE_PLACER_TYPE);
        REGISTRIES.register(new ResourceLocation("minecraft", "feature"), FEATURE);
        REGISTRIES.register(
                new ResourceLocation("minecraft", "worldgen/carver_type"),
                CARVER_TYPE);
        REGISTRIES.register(
                new ResourceLocation("minecraft", "worldgen/carver"),
                CONFIGURED_CARVER);
        REGISTRIES.register(new ResourceLocation("minecraft", "carver"), CARVER_TYPE);
        REGISTRIES.register(
                new ResourceLocation("minecraft", "worldgen/configured_carver"),
                CONFIGURED_CARVER);
        REGISTRIES.register(new ResourceLocation("minecraft", "surface_builder"), SURFACE_BUILDER);
    }

    private Registries() {}
}
