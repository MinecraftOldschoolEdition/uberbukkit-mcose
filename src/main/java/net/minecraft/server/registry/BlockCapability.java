package net.minecraft.server.registry;

import net.minecraft.server.IBlockAccess;
import net.minecraft.server.World;

/**
 * Capability descriptor for block interactions/properties.
 * Starts with redstone semantics and can be expanded over time.
 */
public final class BlockCapability {
    public static final int UNSET_LIGHT_EMISSION = -1;

    public interface DirectRedstonePowerResolver {
        int getPower(World world, int x, int y, int z, int side);
    }

    public interface IndirectRedstonePowerResolver {
        int getPower(IBlockAccess blockAccess, int x, int y, int z, int side);
    }

    public interface LightEmissionResolver {
        int getLightEmission(IBlockAccess blockAccess, int x, int y, int z);
    }

    private final boolean redstonePowerSource;
    private final int maxRedstonePower;
    private final DirectRedstonePowerResolver directResolver;
    private final IndirectRedstonePowerResolver indirectResolver;
    private final int lightEmission;
    private final LightEmissionResolver lightEmissionResolver;
    private final boolean decayEnabled;

    public BlockCapability(boolean redstonePowerSource, int maxRedstonePower) {
        this(redstonePowerSource, maxRedstonePower, null, null, UNSET_LIGHT_EMISSION, null, false);
    }

    public BlockCapability(boolean redstonePowerSource, int maxRedstonePower, int lightEmission) {
        this(redstonePowerSource, maxRedstonePower, null, null, lightEmission, null, false);
    }

    public BlockCapability(
            boolean redstonePowerSource,
            int maxRedstonePower,
            DirectRedstonePowerResolver directResolver,
            IndirectRedstonePowerResolver indirectResolver
    ) {
        this(redstonePowerSource, maxRedstonePower, directResolver, indirectResolver, UNSET_LIGHT_EMISSION, null, false);
    }

    public BlockCapability(
            boolean redstonePowerSource,
            int maxRedstonePower,
            DirectRedstonePowerResolver directResolver,
            IndirectRedstonePowerResolver indirectResolver,
            int lightEmission,
            LightEmissionResolver lightEmissionResolver
    ) {
        this(redstonePowerSource, maxRedstonePower, directResolver, indirectResolver, lightEmission, lightEmissionResolver, false);
    }

    public BlockCapability(
            boolean redstonePowerSource,
            int maxRedstonePower,
            DirectRedstonePowerResolver directResolver,
            IndirectRedstonePowerResolver indirectResolver,
            int lightEmission,
            LightEmissionResolver lightEmissionResolver,
            boolean decayEnabled
    ) {
        this.redstonePowerSource = redstonePowerSource;
        this.maxRedstonePower = clampPower(maxRedstonePower);
        this.directResolver = directResolver;
        this.indirectResolver = indirectResolver;
        this.lightEmission = lightEmission == UNSET_LIGHT_EMISSION ? UNSET_LIGHT_EMISSION : clampLight(lightEmission);
        this.lightEmissionResolver = lightEmissionResolver;
        this.decayEnabled = decayEnabled;
    }

    public boolean isRedstonePowerSource() {
        return redstonePowerSource;
    }

    public int getMaxRedstonePower() {
        return maxRedstonePower;
    }

    public DirectRedstonePowerResolver getDirectResolver() {
        return directResolver;
    }

    public IndirectRedstonePowerResolver getIndirectResolver() {
        return indirectResolver;
    }

    public boolean hasExplicitLightEmission() {
        return lightEmission != UNSET_LIGHT_EMISSION;
    }

    public int getLightEmission() {
        return hasExplicitLightEmission() ? lightEmission : 0;
    }

    public LightEmissionResolver getLightEmissionResolver() {
        return lightEmissionResolver;
    }

    public boolean isDecayEnabled() {
        return decayEnabled;
    }

    static int clampPower(int power) {
        if (power < 0) {
            return 0;
        }
        if (power > 15) {
            return 15;
        }
        return power;
    }

    static int clampLight(int light) {
        if (light < 0) {
            return 0;
        }
        if (light > 15) {
            return 15;
        }
        return light;
    }
}
