package net.minecraft.server.registry;

/**
 * Immutable mining behavior definition for a block (or block metadata state).
 */
public final class BlockMiningRule {
    public static final BlockMiningRule VANILLA = new BlockMiningRule(MiningToolType.NONE, false, 1.0F);

    private final MiningToolType preferredTool;
    private final boolean enforcePreferredTool;
    private final float speedMultiplier;

    public BlockMiningRule(MiningToolType preferredTool, boolean enforcePreferredTool, float speedMultiplier) {
        this.preferredTool = preferredTool == null ? MiningToolType.NONE : preferredTool;
        this.enforcePreferredTool = enforcePreferredTool;
        this.speedMultiplier = speedMultiplier <= 0.0F ? 1.0F : speedMultiplier;
    }

    public MiningToolType getPreferredTool() {
        return this.preferredTool;
    }

    public boolean isEnforcePreferredTool() {
        return this.enforcePreferredTool;
    }

    public float getSpeedMultiplier() {
        return this.speedMultiplier;
    }
}
