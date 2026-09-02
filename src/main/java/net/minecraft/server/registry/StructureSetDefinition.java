package net.minecraft.server.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.server.util.ResourceLocation;

/**
 * Modern-shaped structure-set data projected onto the legacy world generator.
 * Structure selection and placement stay data-owned; the Java-backed legacy
 * structure generator remains responsible for actually placing blocks.
 */
public final class StructureSetDefinition {
    private final ResourceLocation id;
    private final List<StructureSelectionEntry> structures;
    private final LegacyRandomChanceStructurePlacement placement;

    public StructureSetDefinition(
            ResourceLocation id,
            List<StructureSelectionEntry> structures,
            LegacyRandomChanceStructurePlacement placement) {
        if (id == null || structures == null || structures.isEmpty()
                || placement == null) {
            throw new IllegalArgumentException(
                    "Structure set id, structures, and placement are required");
        }
        this.id = id;
        this.structures = Collections.unmodifiableList(
                new ArrayList<StructureSelectionEntry>(structures));
        this.placement = placement;
    }

    public ResourceLocation getId() {
        return this.id;
    }

    public List<StructureSelectionEntry> getStructures() {
        return this.structures;
    }

    public LegacyRandomChanceStructurePlacement getPlacement() {
        return this.placement;
    }

    public static final class StructureSelectionEntry {
        private final ResourceLocation structure;
        private final int weight;

        public StructureSelectionEntry(ResourceLocation structure, int weight) {
            if (structure == null || weight <= 0) {
                throw new IllegalArgumentException(
                        "Structure selection entry requires a positive weight");
            }
            this.structure = structure;
            this.weight = weight;
        }

        public ResourceLocation getStructure() {
            return this.structure;
        }

        public int getWeight() {
            return this.weight;
        }
    }
}
