package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

/** Explicit lifecycle ownership for the legacy global registries. */
final class RegistryRuntimeAccessFactory {
    private RegistryRuntimeAccessFactory() {}

    static RegistryAccess captureStatic() {
        RegistryAccess.Builder out = RegistryAccess.builder();
        add(out, "block", Registries.BLOCK);
        add(out, "item", Registries.ITEM);
        add(out, "block_entity_type", Registries.BLOCK_ENTITY_TYPE);
        add(out, "entity_type", Registries.ENTITY_TYPE);
        add(out, "chunk_generator_type", Registries.CHUNK_GENERATOR_TYPE);
        add(out, "world_type", Registries.WORLD_TYPE);
        add(out, "fluid", Registries.FLUID);
        add(out, "recipe_type", Registries.RECIPE_TYPE);
        add(out, "stat", Registries.STAT);
        add(out, "achievement", Registries.ACHIEVEMENT);
        add(out, "painting_motive", Registries.PAINTING_MOTIVE);
        add(out, "spawn_group", Registries.SPAWN_GROUP);
        add(out, "sound_event", Registries.SOUND_EVENT);
        add(out, "screen_handler", Registries.SCREEN_HANDLER);
        add(out, "particle_type", Registries.PARTICLE_TYPE);
        add(out, "schedule", Registries.SCHEDULE);
        add(out, "sensor_type", Registries.SENSOR_TYPE);
        add(out, "memory_module_type", Registries.MEMORY_MODULE_TYPE);
        add(out, "point_of_interest_type", Registries.POINT_OF_INTEREST_TYPE);
        add(out, "tree_decorator_type", Registries.TREE_DECORATOR_TYPE);
        add(out, "foliage_placer_type", Registries.FOLIAGE_PLACER_TYPE);
        add(out, "feature", Registries.FEATURE);
        add(out, "carver", Registries.CARVER);
        add(out, "surface_builder", Registries.SURFACE_BUILDER);
        return out.build();
    }

    static RegistryAccess captureWorld() {
        RegistryAccess.Builder out = RegistryAccess.builder();
        add(out, "biome", Registries.BIOME);
        add(out, "dimension_type", Registries.DIMENSION_TYPE);
        add(out, "world_feature", Registries.WORLD_FEATURE);
        add(out, "structure_type", Registries.STRUCTURE_TYPE);
        add(out, "painting_variant", Registries.PAINTING_VARIANT);
        add(out, "jukebox_song", Registries.JUKEBOX_SONG);
        return out.build();
    }

    static RegistryAccess captureReloadable() {
        RegistryAccess.Builder out = RegistryAccess.builder();
        add(out, "loot_table", Registries.LOOT_TABLE);
        add(out, "recipe", Registries.RECIPE);
        return out.build();
    }

    private static <T> void add(
            RegistryAccess.Builder out,
            String path,
            SimpleRegistry<T> registry) {
        out.add(new ResourceLocation("minecraft", path), registry);
    }
}
