package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.util.ResourceLocation;

import java.util.Set;

/**
 * Runtime guardrails for registry integrity.
 */
public final class RegistryDebugAsserts {
    private RegistryDebugAsserts() {}

    public static void runCoreIntegrityChecks() {
        checkRegistryPresent("minecraft:block", Registries.BLOCK.keys());
        checkRegistryPresent("minecraft:item", Registries.ITEM.keys());
        checkRegistryPresent("minecraft:entity_type", Registries.ENTITY_TYPE.keys());
        checkRegistryPresent("minecraft:recipe_type", Registries.RECIPE_TYPE.keys());
        checkRegistryPresent("minecraft:spawn_group", Registries.SPAWN_GROUP.keys());
        checkRegistryPresent("minecraft:world_type", Registries.WORLD_TYPE.keys());
        checkRegistryPresent("minecraft:damage_type", Registries.DAMAGE_TYPE.keys());
        checkRegistryPresent("minecraft:dimension_type", Registries.DIMENSION_TYPE.keys());
        checkRegistryPresent("minecraft:worldgen/world_preset", Registries.WORLD_PRESET.keys());
        checkRegistryPresent("minecraft:worldgen/feature", Registries.CONFIGURED_FEATURE.keys());
        checkRegistryPresent("minecraft:worldgen/placed_feature", Registries.PLACED_FEATURE.keys());
        checkRegistryPresent("minecraft:worldgen/carver_type", Registries.CARVER_TYPE.keys());
        checkRegistryPresent("minecraft:worldgen/carver", Registries.CONFIGURED_CARVER.keys());
        checkRegistryPresent("minecraft:sound_event", Registries.SOUND_EVENT.keys());
        checkRegistryPresent("minecraft:particle_type", Registries.PARTICLE_TYPE.keys());
        checkRegistryPresent("minecraft:item_capability", ItemCapabilityRegistryApi.keys());
        checkRegistryPresent("minecraft:block_capability", BlockCapabilityRegistryApi.keys());
        checkRegistryPresent("minecraft:block_mining", BlockMiningRegistryApi.keys());
        checkRegistryPresent("minecraft:structure_type", StructureTypeRegistryApi.keys());
        checkRegistryPresent("minecraft:worldgen/structure_set", StructureSetRegistryApi.keys());

        if (!BlockRegistry.runSanityChecks()) {
            throw new IllegalStateException("[RegistryDebugAsserts] Block sanity checks failed");
        }
        if (!ItemRegistry.runSanityChecks()) {
            throw new IllegalStateException("[RegistryDebugAsserts] Item sanity checks failed");
        }

        checkVanillaBlockCoverage();
        checkVanillaItemCoverage();
        checkBlockCapabilityCoverage();
        checkBlockMiningCoverage();
        checkConfiguredCarverCoverage();
        checkRenderRegistryCoverage();

        LegacyIdBridge.refresh();
    }

    private static void checkConfiguredCarverCoverage() {
        ResourceLocation cave = new ResourceLocation("minecraft", "cave");
        ResourceLocation netherCave =
                new ResourceLocation("minecraft", "nether_cave");
        ResourceLocation skyCave =
                new ResourceLocation("minecraft", "sky_cave");
        if (Registries.CARVER_TYPE.get(cave) == null
                || Registries.CARVER_TYPE.get(netherCave) == null
                || Registries.CONFIGURED_CARVER.get(cave) == null
                || Registries.CONFIGURED_CARVER.get(netherCave) == null
                || Registries.CONFIGURED_CARVER.get(skyCave) == null) {
            throw new IllegalStateException(
                    "[RegistryDebugAsserts] Missing built-in configured carver data");
        }
        for (ResourceLocation key : Registries.CONFIGURED_CARVER.keys()) {
            ConfiguredCarverDefinition definition =
                    Registries.CONFIGURED_CARVER.get(key);
            if (definition == null || !key.equals(definition.getId())
                    || definition.getType() == null
                    || Registries.CARVER_TYPE.get(definition.getType()) == null) {
                throw new IllegalStateException(
                        "[RegistryDebugAsserts] Configured carver has no registered type: "
                                + key);
            }
        }
    }

    private static void checkVanillaBlockCoverage() {
        int vanillaBlocks = 0;
        for (int i = 0; i < Block.byId.length; i++) {
            if (Block.byId[i] == null) {
                continue;
            }
            vanillaBlocks++;
            if (BlockRegistry.getKey(Block.byId[i]) == null) {
                throw new IllegalStateException("[RegistryDebugAsserts] Missing block key for legacy id " + i);
            }
        }

        int registeredPrimary = BlockRegistry.primaryKeys().size();
        if (registeredPrimary < vanillaBlocks) {
            throw new IllegalStateException("[RegistryDebugAsserts] Block coverage mismatch: expected >= " + vanillaBlocks + " primary keys, found " + registeredPrimary);
        }
    }

    private static void checkVanillaItemCoverage() {
        int vanillaItems = 0;
        for (int i = 0; i < Item.byId.length; i++) {
            Item item = Item.byId[i];
            if (item == null) {
                continue;
            }
            vanillaItems++;
            if (ItemRegistry.getKey(item) == null) {
                throw new IllegalStateException("[RegistryDebugAsserts] Missing item key for legacy id " + i);
            }
        }

        int registeredPrimary = ItemRegistry.primaryKeys().size();
        if (registeredPrimary < vanillaItems) {
            throw new IllegalStateException("[RegistryDebugAsserts] Item coverage mismatch: expected >= " + vanillaItems + " primary keys, found " + registeredPrimary);
        }
    }

    private static void checkBlockCapabilityCoverage() {
        int required = 0;
        for (int i = 0; i < Block.byId.length; i++) {
            if (Block.byId[i] == null) {
                continue;
            }
            required++;
            if (BlockCapabilityRegistryApi.get(Block.byId[i]) == null) {
                throw new IllegalStateException("[RegistryDebugAsserts] Missing block capability for legacy id " + i);
            }
        }

        if (BlockCapabilityRegistryApi.size() < required) {
            throw new IllegalStateException("[RegistryDebugAsserts] Block capability coverage mismatch: expected >= " + required + ", found " + BlockCapabilityRegistryApi.size());
        }
    }

    private static void checkBlockMiningCoverage() {
        int required = 0;
        for (int i = 0; i < Block.byId.length; i++) {
            Block block = Block.byId[i];
            if (block == null) {
                continue;
            }
            required++;
            if (!BlockMiningRegistryApi.hasExplicitRule(block)) {
                throw new IllegalStateException("[RegistryDebugAsserts] Missing block mining rule for legacy id " + i);
            }
        }

        if (BlockMiningRegistryApi.size() < required) {
            throw new IllegalStateException("[RegistryDebugAsserts] Block mining coverage mismatch: expected >= " + required + ", found " + BlockMiningRegistryApi.size());
        }
    }

    private static void checkRenderRegistryCoverage() {
        if (SoundEventRegistryApi.size() < 40) {
            throw new IllegalStateException("[RegistryDebugAsserts] Sound registry unexpectedly small: " + SoundEventRegistryApi.size());
        }
        if (ParticleTypeRegistryApi.size() < 10) {
            throw new IllegalStateException("[RegistryDebugAsserts] Particle registry unexpectedly small: " + ParticleTypeRegistryApi.size());
        }
    }

    private static void checkRegistryPresent(String registryName, Set<ResourceLocation> keys) {
        if (keys == null || keys.isEmpty()) {
            throw new IllegalStateException("[RegistryDebugAsserts] Registry is empty: " + registryName);
        }
    }
}
