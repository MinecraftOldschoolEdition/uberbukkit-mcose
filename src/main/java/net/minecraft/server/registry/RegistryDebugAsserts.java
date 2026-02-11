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
        checkRegistryPresent("minecraft:sound_event", Registries.SOUND_EVENT.keys());
        checkRegistryPresent("minecraft:particle_type", Registries.PARTICLE_TYPE.keys());
        checkRegistryPresent("minecraft:item_capability", ItemCapabilityRegistryApi.keys());
        checkRegistryPresent("minecraft:block_capability", BlockCapabilityRegistryApi.keys());

        if (!BlockRegistry.runSanityChecks()) {
            throw new IllegalStateException("[RegistryDebugAsserts] Block sanity checks failed");
        }
        if (!ItemRegistry.runSanityChecks()) {
            throw new IllegalStateException("[RegistryDebugAsserts] Item sanity checks failed");
        }

        checkVanillaBlockCoverage();
        checkVanillaItemCoverage();
        checkBlockCapabilityCoverage();
        checkRenderRegistryCoverage();

        LegacyIdBridge.refresh();
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
