package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.util.ResourceLocation;

public final class Registries {
    public static final SimpleRegistry<Object> REGISTRIES = new SimpleRegistry<Object>();
    public static final SimpleRegistry<Block> BLOCK = new SimpleRegistry<Block>();
    public static final SimpleRegistry<Item> ITEM = new SimpleRegistry<Item>();
    public static final SimpleRegistry<Class<?>> BLOCK_ENTITY_TYPE = new SimpleRegistry<Class<?>>();
    public static final SimpleRegistry<Class<?>> ENTITY_TYPE = new SimpleRegistry<Class<?>>();
    public static final SimpleRegistry<net.minecraft.server.BiomeBase> BIOME = new SimpleRegistry<net.minecraft.server.BiomeBase>();
    public static final SimpleRegistry<Class<?>> CHUNK_GENERATOR_TYPE = new SimpleRegistry<Class<?>>();
    public static final SimpleRegistry<Integer> WORLD_TYPE = new SimpleRegistry<Integer>();
    public static final SimpleRegistry<Block> FLUID = new SimpleRegistry<Block>();
    public static final SimpleRegistry<Class<?>> DIMENSION_TYPE = new SimpleRegistry<Class<?>>();
    public static final SimpleRegistry<Object> STAT = new SimpleRegistry<Object>();
    public static final SimpleRegistry<net.minecraft.server.EnumArt> PAINTING_MOTIVE = new SimpleRegistry<net.minecraft.server.EnumArt>();
    public static final SimpleRegistry<String> SOUND_EVENT = new SimpleRegistry<String>();
    public static final SimpleRegistry<Class<?>> SCREEN_HANDLER = new SimpleRegistry<Class<?>>();
    public static final SimpleRegistry<Schedule> SCHEDULE = new SimpleRegistry<Schedule>();
    public static final SimpleRegistry<SensorType> SENSOR_TYPE = new SimpleRegistry<SensorType>();
    public static final SimpleRegistry<MemoryModuleType> MEMORY_MODULE_TYPE = new SimpleRegistry<MemoryModuleType>();
    public static final SimpleRegistry<PointOfInterestType> POINT_OF_INTEREST_TYPE = new SimpleRegistry<PointOfInterestType>();
    public static final SimpleRegistry<ParticleType> PARTICLE_TYPE = new SimpleRegistry<ParticleType>();

    static {
        REGISTRIES.register(new ResourceLocation("minecraft","block"), BLOCK);
        REGISTRIES.register(new ResourceLocation("minecraft","item"), ITEM);
        REGISTRIES.register(new ResourceLocation("minecraft","block_entity_type"), BLOCK_ENTITY_TYPE);
        REGISTRIES.register(new ResourceLocation("minecraft","entity_type"), ENTITY_TYPE);
        REGISTRIES.register(new ResourceLocation("minecraft","biome"), BIOME);
        REGISTRIES.register(new ResourceLocation("minecraft","chunk_generator_type"), CHUNK_GENERATOR_TYPE);
        REGISTRIES.register(new ResourceLocation("minecraft","world_type"), WORLD_TYPE);
        REGISTRIES.register(new ResourceLocation("minecraft","fluid"), FLUID);
        REGISTRIES.register(new ResourceLocation("minecraft","dimension_type"), DIMENSION_TYPE);
        REGISTRIES.register(new ResourceLocation("minecraft","stat"), STAT);
        REGISTRIES.register(new ResourceLocation("minecraft","painting_motive"), PAINTING_MOTIVE);
        REGISTRIES.register(new ResourceLocation("minecraft","sound_event"), SOUND_EVENT);
        REGISTRIES.register(new ResourceLocation("minecraft","screen_handler"), SCREEN_HANDLER);
        REGISTRIES.register(new ResourceLocation("minecraft","schedule"), SCHEDULE);
        REGISTRIES.register(new ResourceLocation("minecraft","sensor_type"), SENSOR_TYPE);
        REGISTRIES.register(new ResourceLocation("minecraft","memory_module_type"), MEMORY_MODULE_TYPE);
        REGISTRIES.register(new ResourceLocation("minecraft","point_of_interest_type"), POINT_OF_INTEREST_TYPE);
        REGISTRIES.register(new ResourceLocation("minecraft","particle_type"), PARTICLE_TYPE);
    }

    private Registries() {}
}


