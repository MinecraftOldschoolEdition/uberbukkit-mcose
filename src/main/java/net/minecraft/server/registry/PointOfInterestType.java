package net.minecraft.server.registry;

/**
 * Describes a type of point of interest in the world (e.g., nether portal, grazing block).
 */
public final class PointOfInterestType {
    private final String description;
    private final int[] blockIds;

    public PointOfInterestType(String description, int... blockIds) {
        this.description = description;
        this.blockIds = blockIds == null ? new int[0] : blockIds.clone();
    }

    public String getDescription() { return description; }
    public int[] getBlockIds() { return blockIds; }
}


