package net.minecraft.server.mod;

import net.minecraft.server.Block;
import net.minecraft.server.Entity;
import net.minecraft.server.EntityPlayer;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.World;
import net.minecraft.server.event.EventBus;
import net.minecraft.server.event.EventListener;
import net.minecraft.server.event.EventPriority;
import net.minecraft.server.event.events.AttackEntityEvent;
import net.minecraft.server.event.events.BlockBreakEvent;
import net.minecraft.server.event.events.BlockPlaceEvent;
import net.minecraft.server.event.events.InteractEntityEvent;
import net.minecraft.server.event.events.InventoryShortcutEvent;
import net.minecraft.server.event.events.PlayerDamageEvent;
import net.minecraft.server.event.events.PlayerDealDamageEvent;
import net.minecraft.server.event.events.PlayerFlightToggleEvent;
import net.minecraft.server.event.events.PlayerJumpEvent;
import net.minecraft.server.event.events.PlayerMoveEvent;
import net.minecraft.server.event.events.UseItemEvent;
import net.minecraft.server.registry.BlockCapabilityRegistryApi;
import net.minecraft.server.registry.BlockRegistryApi;
import net.minecraft.server.registry.EntityTypeRegistryApi;
import net.minecraft.server.registry.ItemCapabilityRegistryApi;
import net.minecraft.server.registry.ItemRegistry;
import net.minecraft.server.registry.LightLevelApi;
import net.minecraft.server.registry.ParticleType;
import net.minecraft.server.registry.ParticleTypeRegistryApi;
import net.minecraft.server.registry.PlayerCapabilityRegistryApi;
import net.minecraft.server.registry.RecipeRegistryApi;
import net.minecraft.server.registry.SoundEventRegistryApi;
import net.minecraft.server.util.ResourceLocation;

public final class ModContext {
    private final ModMetadata metadata;

    public ModContext(ModMetadata metadata) {
        this.metadata = metadata;
    }

    public ModMetadata getMetadata() {
        return metadata;
    }

    public EventBus getEventBus() {
        return EventBus.global();
    }

    public void registerItem(String key, Item item, int legacyId) {
        if (key == null || item == null) {
            return;
        }
        ItemRegistry.register(new ResourceLocation(key), item, legacyId);
    }

    public void registerBlock(String key, Block block, int legacyId) {
        if (key == null || block == null) {
            return;
        }
        BlockRegistryApi.register(new ResourceLocation(key), block, legacyId);
    }

    public void registerEntityType(String key, Class<?> entityType) {
        if (key == null || entityType == null) {
            return;
        }
        EntityTypeRegistryApi.register(new ResourceLocation(key), entityType);
    }

    public void registerShapedRecipe(String key, ItemStack output, Object... shapeAndMappings) {
        if (key == null) {
            return;
        }
        RecipeRegistryApi.registerShaped(new ResourceLocation(key), output, shapeAndMappings);
    }

    public void registerShapelessRecipe(String key, ItemStack output, Object... inputs) {
        if (key == null) {
            return;
        }
        RecipeRegistryApi.registerShapeless(new ResourceLocation(key), output, inputs);
    }

    public void registerSmeltingRecipe(String key, int inputId, ItemStack output) {
        if (key == null) {
            return;
        }
        RecipeRegistryApi.registerSmelting(new ResourceLocation(key), inputId, output);
    }

    public void registerFuel(String itemKey, int burnTicks) {
        if (itemKey == null) {
            return;
        }
        ItemCapabilityRegistryApi.registerFuel(itemKey, burnTicks);
    }

    public ItemCapabilityRegistryApi.ItemProperties getItemProperties(String itemIdentifier) {
        return ItemCapabilityRegistryApi.getProperties(itemIdentifier);
    }

    public int getItemMaxStackSize(String itemIdentifier) {
        return ItemCapabilityRegistryApi.getMaxStackSize(itemIdentifier);
    }

    public void registerBlockRedstoneBehavior(String blockKey, boolean powerSource, int maxPower) {
        if (blockKey == null) {
            return;
        }
        BlockCapabilityRegistryApi.registerRedstoneBehavior(blockKey, powerSource, maxPower);
    }

    public void registerBlockLightEmission(String blockKey, int lightEmission) {
        if (blockKey == null) {
            return;
        }
        BlockCapabilityRegistryApi.registerLightEmission(blockKey, lightEmission);
    }

    public void registerSoundEvent(String key, String legacySoundKey) {
        if (key == null || legacySoundKey == null || legacySoundKey.length() == 0) {
            return;
        }
        SoundEventRegistryApi.register(new ResourceLocation(key), legacySoundKey);
    }

    public String resolveSoundKey(String anyIdentifier) {
        if (anyIdentifier == null) {
            return null;
        }
        return SoundEventRegistryApi.resolveLegacyKey(anyIdentifier);
    }

    public void registerParticleType(String key, String legacyParticleKey) {
        if (key == null || legacyParticleKey == null || legacyParticleKey.length() == 0) {
            return;
        }
        ParticleTypeRegistryApi.register(new ResourceLocation(key), new ParticleType(legacyParticleKey));
    }

    public String resolveParticleKey(String anyIdentifier) {
        if (anyIdentifier == null) {
            return null;
        }
        return ParticleTypeRegistryApi.resolveLegacyKey(anyIdentifier);
    }

    public int getCombinedLight(World world, int x, int y, int z) {
        return LightLevelApi.getCombinedLight(world, x, y, z);
    }

    public int getSkyLight(World world, int x, int y, int z) {
        return LightLevelApi.getSkyLight(world, x, y, z);
    }

    public int getBlockLight(World world, int x, int y, int z) {
        return LightLevelApi.getBlockLight(world, x, y, z);
    }

    public boolean canSeeSky(World world, int x, int y, int z) {
        return LightLevelApi.canSeeSky(world, x, y, z);
    }

    public int getBlockLightEmission(Block block) {
        return LightLevelApi.getBlockEmission(block);
    }

    public int getBlockLightOpacity(Block block) {
        return LightLevelApi.getBlockOpacity(block);
    }

    public int getEntityCombinedLight(Entity entity) {
        return LightLevelApi.getEntityCombinedLight(entity);
    }

    public int getEntitySkyLight(Entity entity) {
        return LightLevelApi.getEntitySkyLight(entity);
    }

    public int getEntityBlockLight(Entity entity) {
        return LightLevelApi.getEntityBlockLight(entity);
    }

    public boolean isEntityInDirectSunlight(Entity entity) {
        return LightLevelApi.isEntityInDirectSunlight(entity);
    }

    public void setPlayerAllowFlight(EntityPlayer player, boolean allowFlight) {
        PlayerCapabilityRegistryApi.setAllowFlying(player, allowFlight);
    }

    public void setPlayerFlying(EntityPlayer player, boolean flying) {
        PlayerCapabilityRegistryApi.setFlying(player, flying);
    }

    public void setPlayerInvulnerable(EntityPlayer player, boolean invulnerable) {
        PlayerCapabilityRegistryApi.setInvulnerable(player, invulnerable);
    }

    public void setPlayerInstabuild(EntityPlayer player, boolean instabuild) {
        PlayerCapabilityRegistryApi.setInstabuild(player, instabuild);
    }

    public void onPlayerMove(EventPriority priority, EventListener<PlayerMoveEvent> listener) {
        PlayerCapabilityRegistryApi.onMove(priority, listener);
    }

    public void onPlayerJump(EventPriority priority, EventListener<PlayerJumpEvent> listener) {
        PlayerCapabilityRegistryApi.onJump(priority, listener);
    }

    public void onPlayerFlightToggle(EventPriority priority, EventListener<PlayerFlightToggleEvent> listener) {
        PlayerCapabilityRegistryApi.onFlightToggle(priority, listener);
    }

    public void onPlayerDamageTaken(EventPriority priority, EventListener<PlayerDamageEvent> listener) {
        PlayerCapabilityRegistryApi.onDamageTaken(priority, listener);
    }

    public void onPlayerDamageDealt(EventPriority priority, EventListener<PlayerDealDamageEvent> listener) {
        PlayerCapabilityRegistryApi.onDamageDealt(priority, listener);
    }

    public void onBlockBreak(EventPriority priority, EventListener<BlockBreakEvent> listener) {
        PlayerCapabilityRegistryApi.onBlockBreak(priority, listener);
    }

    public void onBlockPlace(EventPriority priority, EventListener<BlockPlaceEvent> listener) {
        PlayerCapabilityRegistryApi.onBlockPlace(priority, listener);
    }

    public void onUseItem(EventPriority priority, EventListener<UseItemEvent> listener) {
        PlayerCapabilityRegistryApi.onUseItem(priority, listener);
    }

    public void onAttackEntity(EventPriority priority, EventListener<AttackEntityEvent> listener) {
        PlayerCapabilityRegistryApi.onAttackEntity(priority, listener);
    }

    public void onInteractEntity(EventPriority priority, EventListener<InteractEntityEvent> listener) {
        PlayerCapabilityRegistryApi.onInteractEntity(priority, listener);
    }

    public void onInventoryShortcut(EventPriority priority, EventListener<InventoryShortcutEvent> listener) {
        PlayerCapabilityRegistryApi.onInventoryShortcut(priority, listener);
    }
}
