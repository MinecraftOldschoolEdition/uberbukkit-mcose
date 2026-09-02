package net.minecraft.server.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.server.BlockStateKey;
import net.minecraft.server.util.ResourceLocation;

/** Immutable supported projection of a 26.3 configured worldgen feature. */
public final class ConfiguredFeatureDefinition {
    private final ResourceLocation id;
    private final ResourceLocation type;
    private final ResourceLocation state;
    private final ResourceLocation targetBlock;
    private final int size;
    private final float discardChanceOnAirExposure;
    private final BlockStateKey springState;
    private final boolean requiresBlockBelow;
    private final int rockCount;
    private final int holeCount;
    private final List<ResourceLocation> validBlocks;
    private final ResourceLocation lakeFluidProviderType;
    private final BlockStateKey lakeFluidState;
    private final ResourceLocation lakeBarrierProviderType;
    private final BlockStateKey lakeBarrierState;
    private final ResourceLocation canPlaceFeaturePredicate;
    private final ResourceLocation canReplaceWithAirOrFluidPredicate;
    private final ResourceLocation canReplaceWithBarrierPredicate;

    /** Creates a configured feature whose registered type has no configuration fields. */
    public ConfiguredFeatureDefinition(ResourceLocation id, ResourceLocation type) {
        if (id == null || type == null) {
            throw new IllegalArgumentException(
                    "Configured feature identifiers cannot be null");
        }
        this.id = id;
        this.type = type;
        this.state = null;
        this.targetBlock = null;
        this.size = 0;
        this.discardChanceOnAirExposure = 0.0F;
        this.springState = null;
        this.requiresBlockBelow = false;
        this.rockCount = 0;
        this.holeCount = 0;
        this.validBlocks = Collections.emptyList();
        this.lakeFluidProviderType = null;
        this.lakeFluidState = null;
        this.lakeBarrierProviderType = null;
        this.lakeBarrierState = null;
        this.canPlaceFeaturePredicate = null;
        this.canReplaceWithAirOrFluidPredicate = null;
        this.canReplaceWithBarrierPredicate = null;
    }

    public ConfiguredFeatureDefinition(
            ResourceLocation id,
            ResourceLocation type,
            ResourceLocation state,
            ResourceLocation targetBlock,
            int size,
            float discardChanceOnAirExposure) {
        if (id == null || type == null || state == null || targetBlock == null) {
            throw new IllegalArgumentException(
                    "Configured feature identifiers cannot be null");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Configured feature size must be positive");
        }
        if (Float.isNaN(discardChanceOnAirExposure)
                || discardChanceOnAirExposure < 0.0F
                || discardChanceOnAirExposure > 1.0F) {
            throw new IllegalArgumentException(
                    "Discard chance must be between zero and one");
        }
        this.id = id;
        this.type = type;
        this.state = state;
        this.targetBlock = targetBlock;
        this.size = size;
        this.discardChanceOnAirExposure = discardChanceOnAirExposure;
        this.springState = null;
        this.requiresBlockBelow = false;
        this.rockCount = 0;
        this.holeCount = 0;
        this.validBlocks = Collections.emptyList();
        this.lakeFluidProviderType = null;
        this.lakeFluidState = null;
        this.lakeBarrierProviderType = null;
        this.lakeBarrierState = null;
        this.canPlaceFeaturePredicate = null;
        this.canReplaceWithAirOrFluidPredicate = null;
        this.canReplaceWithBarrierPredicate = null;
    }

    public ConfiguredFeatureDefinition(
            ResourceLocation id,
            ResourceLocation type,
            BlockStateKey springState,
            boolean requiresBlockBelow,
            int rockCount,
            int holeCount,
            List<ResourceLocation> validBlocks) {
        if (id == null || type == null || springState == null
                || validBlocks == null || validBlocks.isEmpty()) {
            throw new IllegalArgumentException(
                    "Spring feature identifiers, state, and valid blocks are required");
        }
        if (rockCount < 0 || rockCount > 5
                || holeCount < 0 || holeCount > 5) {
            throw new IllegalArgumentException(
                    "Spring rock and hole counts must be between 0 and 5");
        }
        ArrayList<ResourceLocation> copied =
                new ArrayList<ResourceLocation>(validBlocks.size());
        for (int i = 0; i < validBlocks.size(); i++) {
            ResourceLocation block = validBlocks.get(i);
            if (block == null) {
                throw new IllegalArgumentException(
                        "Spring valid blocks cannot contain null");
            }
            copied.add(block);
        }
        this.id = id;
        this.type = type;
        this.state = null;
        this.targetBlock = null;
        this.size = 0;
        this.discardChanceOnAirExposure = 0.0F;
        this.springState = springState;
        this.requiresBlockBelow = requiresBlockBelow;
        this.rockCount = rockCount;
        this.holeCount = holeCount;
        this.validBlocks = Collections.unmodifiableList(copied);
        this.lakeFluidProviderType = null;
        this.lakeFluidState = null;
        this.lakeBarrierProviderType = null;
        this.lakeBarrierState = null;
        this.canPlaceFeaturePredicate = null;
        this.canReplaceWithAirOrFluidPredicate = null;
        this.canReplaceWithBarrierPredicate = null;
    }

    public ConfiguredFeatureDefinition(
            ResourceLocation id,
            ResourceLocation type,
            ResourceLocation lakeFluidProviderType,
            BlockStateKey lakeFluidState,
            ResourceLocation lakeBarrierProviderType,
            BlockStateKey lakeBarrierState,
            ResourceLocation canPlaceFeaturePredicate,
            ResourceLocation canReplaceWithAirOrFluidPredicate,
            ResourceLocation canReplaceWithBarrierPredicate) {
        if (id == null || type == null
                || lakeFluidProviderType == null || lakeFluidState == null
                || lakeBarrierProviderType == null || lakeBarrierState == null
                || canPlaceFeaturePredicate == null
                || canReplaceWithAirOrFluidPredicate == null
                || canReplaceWithBarrierPredicate == null) {
            throw new IllegalArgumentException(
                    "Lake feature providers, states, and predicates are required");
        }
        this.id = id;
        this.type = type;
        this.state = null;
        this.targetBlock = null;
        this.size = 0;
        this.discardChanceOnAirExposure = 0.0F;
        this.springState = null;
        this.requiresBlockBelow = false;
        this.rockCount = 0;
        this.holeCount = 0;
        this.validBlocks = Collections.emptyList();
        this.lakeFluidProviderType = lakeFluidProviderType;
        this.lakeFluidState = lakeFluidState;
        this.lakeBarrierProviderType = lakeBarrierProviderType;
        this.lakeBarrierState = lakeBarrierState;
        this.canPlaceFeaturePredicate = canPlaceFeaturePredicate;
        this.canReplaceWithAirOrFluidPredicate =
                canReplaceWithAirOrFluidPredicate;
        this.canReplaceWithBarrierPredicate = canReplaceWithBarrierPredicate;
    }

    public ResourceLocation getId() { return this.id; }
    public ResourceLocation getType() { return this.type; }
    public ResourceLocation getState() { return this.state; }
    public ResourceLocation getTargetBlock() { return this.targetBlock; }
    public int getSize() { return this.size; }
    public float getDiscardChanceOnAirExposure() {
        return this.discardChanceOnAirExposure;
    }
    public BlockStateKey getSpringState() { return this.springState; }
    public boolean requiresBlockBelow() { return this.requiresBlockBelow; }
    public int getRockCount() { return this.rockCount; }
    public int getHoleCount() { return this.holeCount; }
    public List<ResourceLocation> getValidBlocks() { return this.validBlocks; }
    public ResourceLocation getLakeFluidProviderType() {
        return this.lakeFluidProviderType;
    }
    public BlockStateKey getLakeFluidState() { return this.lakeFluidState; }
    public ResourceLocation getLakeBarrierProviderType() {
        return this.lakeBarrierProviderType;
    }
    public BlockStateKey getLakeBarrierState() { return this.lakeBarrierState; }
    public ResourceLocation getCanPlaceFeaturePredicate() {
        return this.canPlaceFeaturePredicate;
    }
    public ResourceLocation getCanReplaceWithAirOrFluidPredicate() {
        return this.canReplaceWithAirOrFluidPredicate;
    }
    public ResourceLocation getCanReplaceWithBarrierPredicate() {
        return this.canReplaceWithBarrierPredicate;
    }
    public boolean hasOreConfiguration() {
        return this.state != null && this.targetBlock != null && this.size > 0;
    }
    public boolean hasSpringConfiguration() {
        return this.springState != null && !this.validBlocks.isEmpty();
    }
    public boolean hasLakeConfiguration() {
        return this.lakeFluidProviderType != null
                && this.lakeFluidState != null
                && this.lakeBarrierProviderType != null
                && this.lakeBarrierState != null
                && this.canPlaceFeaturePredicate != null
                && this.canReplaceWithAirOrFluidPredicate != null
                && this.canReplaceWithBarrierPredicate != null;
    }
}
