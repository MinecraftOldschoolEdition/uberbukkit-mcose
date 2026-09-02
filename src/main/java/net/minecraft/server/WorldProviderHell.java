package net.minecraft.server;

import net.minecraft.server.registry.WorldPresetGeneratorRouting;
import net.minecraft.server.util.ResourceLocation;

public class WorldProviderHell extends WorldProvider {

    public WorldProviderHell() {
    }

    public void a() {
        this.b = new WorldChunkManagerHell(BiomeBase.HELL, 1.0D, 0.0D);
        this.c = true;
        this.d = true;
        this.e = true;
        this.dimension = -1;
    }

    protected void c() {
        float f = 0.1F;

        for (int i = 0; i <= 15; ++i) {
            float f1 = 1.0F - (float) i / 15.0F;

            this.f[i] = (1.0F - f1) / (f1 * 3.0F + 1.0F) * (1.0F - f) + f;
        }
    }

    public IChunkProvider getChunkProvider() {
        int terrainType = this.getNetherVariantTerrainType();
        ResourceLocation generatorKey = WorldPresetGeneratorRouting.generatorKey(
                terrainType, WorldPresetGeneratorRouting.THE_NETHER);
        if (WorldPresetGeneratorRouting.NETHER_SKY.equals(generatorKey)) {
            MinecraftServer.log.info("[WorldProviderHell] Selecting NetherSkyLevelSource (overworld terrainType=SKY)");
            return new ChunkProviderNetherSky(this.a, this.a.getSeed());
        }
        if (WorldPresetGeneratorRouting.CLASSIC_NETHER.equals(generatorKey)) {
            MinecraftServer.log.info("[WorldProviderHell] Selecting ClassicHellLevelSource (terrainType=CLASSIC)");
            return new net.minecraft.server.Classic.ChunkProviderHellClassic(this.a, this.a.getSeed());
        }
        if (WorldPresetGeneratorRouting.NETHER.equals(generatorKey)) {
            MinecraftServer.log.info("[WorldProviderHell] Selecting default Nether generator");
            return new ChunkProviderHell(this.a, this.a.getSeed());
        }
        throw new IllegalStateException(
                "Unsupported Nether generator " + generatorKey
                        + " for terrain type " + terrainType);
    }

    public boolean isNetherSkyVariant() {
        return WorldPresetGeneratorRouting.NETHER_SKY.equals(
                WorldPresetGeneratorRouting.generatorKey(
                        this.getNetherVariantTerrainType(),
                        WorldPresetGeneratorRouting.THE_NETHER));
    }

    public int getNetherVariantTerrainType() {
        int localTerrainType = this.a != null && this.a.worldData != null
                ? this.a.worldData.getTerrainType()
                : 0;
        Integer overworldTerrainType = null;
        String configuredLevelType = null;

        if (this.a instanceof WorldServer) {
            try {
                MinecraftServer server = ((WorldServer) this.a).server;
                if (server != null) {
                    configuredLevelType = server.configuredLevelType;
                    WorldServer overworld = server.getWorldServer(0);
                    if (overworld != null && overworld.worldData != null) {
                        overworldTerrainType = Integer.valueOf(overworld.worldData.getTerrainType());
                    }
                }
            } catch (Throwable ignore) {}
        }

        return resolveNetherVariantTerrainType(localTerrainType, overworldTerrainType, configuredLevelType);
    }

    static int resolveNetherVariantTerrainType(int localTerrainType, Integer overworldTerrainType, String configuredLevelType) {
        if (overworldTerrainType != null) {
            return overworldTerrainType.intValue();
        }
        if (configuredLevelType != null && configuredLevelType.equalsIgnoreCase("SKY")) {
            return 3;
        }
        if (configuredLevelType != null && configuredLevelType.equalsIgnoreCase("CLASSIC")) {
            return 6;
        }
        return localTerrainType;
    }

    static boolean isNetherSkyTerrainType(int terrainType) {
        return terrainType == 3;
    }

    public boolean canSpawn(int i, int j) {
        int k = this.a.a(i, j);

        return k == Block.BEDROCK.id ? false : (k == 0 ? false : Block.o[k]);
    }

    public float a(long i, float f) {
        return 0.5F;
    }

    public boolean d() {
        return false;
    }
}
