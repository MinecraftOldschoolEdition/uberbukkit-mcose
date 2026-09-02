package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

/**
 * Immutable, data-backed metadata for one legacy dimension type.
 *
 * <p>Phase A deliberately does not make these values authoritative for the
 * legacy provider clock, lighting, save, or packet paths. They describe the
 * existing engine contract and give world presets a modern symbolic target.</p>
 */
public final class DimensionTypeDefinition {
    public static final int MIN_Y = -2032;
    public static final int MAX_Y = 2031;
    public static final int MAX_HEIGHT = 4064;
    private final ResourceLocation id;
    private final boolean hasFixedTime;
    private final boolean hasSkylight;
    private final boolean hasCeiling;
    private final boolean hasEnderDragonFight;
    private final double coordinateScale;
    private final int minY;
    private final int height;
    private final int logicalHeight;
    private final ResourceLocation infiniburnTag;
    private final float ambientLight;
    private final LightLevelProvider monsterSpawnLightLevel;
    private final int monsterSpawnBlockLightLimit;
    private final String skybox;
    private final String cardinalLight;

    public DimensionTypeDefinition(
            ResourceLocation id,
            boolean hasFixedTime,
            boolean hasSkylight,
            boolean hasCeiling,
            boolean hasEnderDragonFight,
            double coordinateScale,
            int minY,
            int height,
            int logicalHeight,
            ResourceLocation infiniburnTag,
            float ambientLight,
            LightLevelProvider monsterSpawnLightLevel,
            int monsterSpawnBlockLightLimit,
            String skybox,
            String cardinalLight) {
        if (id == null || infiniburnTag == null || monsterSpawnLightLevel == null) {
            throw new IllegalArgumentException("Dimension type values cannot be null");
        }
        if (!(coordinateScale >= 1.0E-5D && coordinateScale <= 3.0E7D)
                || Double.isInfinite(coordinateScale) || Double.isNaN(coordinateScale)) {
            throw new IllegalArgumentException("coordinate_scale is out of range");
        }
        if (height < 16 || height > MAX_HEIGHT || height % 16 != 0) {
            throw new IllegalArgumentException(
                    "height must be between 16 and " + MAX_HEIGHT
                            + " and a multiple of 16");
        }
        if (minY < MIN_Y || minY > MAX_Y || minY % 16 != 0) {
            throw new IllegalArgumentException(
                    "min_y must be between " + MIN_Y + " and " + MAX_Y
                            + " and a multiple of 16");
        }
        if ((long)minY + (long)height > (long)MAX_Y + 1L) {
            throw new IllegalArgumentException(
                    "min_y + height cannot be higher than " + (MAX_Y + 1));
        }
        if (logicalHeight < 0 || logicalHeight > height) {
            throw new IllegalArgumentException("logical_height must be between 0 and height");
        }
        if (Float.isInfinite(ambientLight) || Float.isNaN(ambientLight)) {
            throw new IllegalArgumentException("ambient_light must be finite");
        }
        if (monsterSpawnBlockLightLimit < 0 || monsterSpawnBlockLightLimit > 15) {
            throw new IllegalArgumentException(
                    "monster_spawn_block_light_limit must be between 0 and 15");
        }
        if (!isSkybox(skybox)) {
            throw new IllegalArgumentException("Unsupported skybox '" + skybox + "'");
        }
        if (!isCardinalLight(cardinalLight)) {
            throw new IllegalArgumentException(
                    "Unsupported cardinal_light '" + cardinalLight + "'");
        }
        this.id = id;
        this.hasFixedTime = hasFixedTime;
        this.hasSkylight = hasSkylight;
        this.hasCeiling = hasCeiling;
        this.hasEnderDragonFight = hasEnderDragonFight;
        this.coordinateScale = coordinateScale;
        this.minY = minY;
        this.height = height;
        this.logicalHeight = logicalHeight;
        this.infiniburnTag = infiniburnTag;
        this.ambientLight = ambientLight;
        this.monsterSpawnLightLevel = monsterSpawnLightLevel;
        this.monsterSpawnBlockLightLimit = monsterSpawnBlockLightLimit;
        this.skybox = skybox;
        this.cardinalLight = cardinalLight;
    }

    public ResourceLocation getId() { return this.id; }
    public boolean hasFixedTime() { return this.hasFixedTime; }
    public boolean hasSkylight() { return this.hasSkylight; }
    public boolean hasCeiling() { return this.hasCeiling; }
    public boolean hasEnderDragonFight() { return this.hasEnderDragonFight; }
    public double getCoordinateScale() { return this.coordinateScale; }
    public int getMinY() { return this.minY; }
    public int getHeight() { return this.height; }
    public int getLogicalHeight() { return this.logicalHeight; }
    public ResourceLocation getInfiniburnTag() { return this.infiniburnTag; }
    public float getAmbientLight() { return this.ambientLight; }
    public LightLevelProvider getMonsterSpawnLightLevel() {
        return this.monsterSpawnLightLevel;
    }
    public int getMonsterSpawnBlockLightLimit() {
        return this.monsterSpawnBlockLightLimit;
    }
    public String getSkybox() { return this.skybox; }
    public String getCardinalLight() { return this.cardinalLight; }

    private static boolean isSkybox(String value) {
        return "none".equals(value) || "overworld".equals(value) || "end".equals(value);
    }

    private static boolean isCardinalLight(String value) {
        return "default".equals(value) || "nether".equals(value);
    }

    /** Constant or inclusive-uniform 0..15 light threshold. */
    public static final class LightLevelProvider {
        public static final ResourceLocation CONSTANT =
                new ResourceLocation("minecraft", "constant");
        public static final ResourceLocation UNIFORM =
                new ResourceLocation("minecraft", "uniform");

        private final ResourceLocation type;
        private final int minInclusive;
        private final int maxInclusive;

        public LightLevelProvider(
                ResourceLocation type, int minInclusive, int maxInclusive) {
            if (!CONSTANT.equals(type) && !UNIFORM.equals(type)) {
                throw new IllegalArgumentException(
                        "Unsupported monster spawn light provider " + type);
            }
            if (minInclusive < 0 || maxInclusive > 15 || minInclusive > maxInclusive) {
                throw new IllegalArgumentException(
                        "Monster spawn light range must be within 0..15");
            }
            if (CONSTANT.equals(type) && minInclusive != maxInclusive) {
                throw new IllegalArgumentException("Constant light provider must have one value");
            }
            this.type = type;
            this.minInclusive = minInclusive;
            this.maxInclusive = maxInclusive;
        }

        public ResourceLocation getType() { return this.type; }
        public int getMinInclusive() { return this.minInclusive; }
        public int getMaxInclusive() { return this.maxInclusive; }
        public boolean isConstant() { return CONSTANT.equals(this.type); }
    }
}
