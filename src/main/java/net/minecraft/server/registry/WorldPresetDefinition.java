package net.minecraft.server.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.util.ResourceLocation;

/** Immutable legacy projection of the 26.3 world-preset dimension map. */
public final class WorldPresetDefinition {
    private final ResourceLocation id;
    private final Map<ResourceLocation, DimensionStem> dimensions;

    public WorldPresetDefinition(
            ResourceLocation id,
            Map<ResourceLocation, DimensionStem> dimensions) {
        if (id == null || dimensions == null || dimensions.isEmpty()) {
            throw new IllegalArgumentException("World preset values cannot be null or empty");
        }
        LinkedHashMap<ResourceLocation, DimensionStem> copy =
                new LinkedHashMap<ResourceLocation, DimensionStem>();
        for (Map.Entry<ResourceLocation, DimensionStem> entry : dimensions.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException(
                        "World preset dimensions cannot contain null");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        if (!copy.containsKey(WorldPresetGeneratorRouting.OVERWORLD)) {
            throw new IllegalArgumentException("World preset is missing minecraft:overworld");
        }
        this.id = id;
        this.dimensions = Collections.unmodifiableMap(copy);
    }

    public ResourceLocation getId() { return this.id; }

    public Map<ResourceLocation, DimensionStem> getDimensions() {
        return this.dimensions;
    }

    public DimensionStem getDimension(ResourceLocation key) {
        return key == null ? null : this.dimensions.get(key);
    }

    public static final class DimensionStem {
        private final ResourceLocation dimensionType;
        private final ResourceLocation generatorType;

        public DimensionStem(
                ResourceLocation dimensionType,
                ResourceLocation generatorType) {
            if (dimensionType == null || generatorType == null) {
                throw new IllegalArgumentException("Dimension stem values cannot be null");
            }
            this.dimensionType = dimensionType;
            this.generatorType = generatorType;
        }

        public ResourceLocation getDimensionType() { return this.dimensionType; }
        public ResourceLocation getGeneratorType() { return this.generatorType; }
    }
}
