package net.minecraft.server;

import java.util.Arrays;
import java.util.Random;

/**
 * Marker subclass of WorldChunkManagerHell used exclusively by Alpha terrain
 * worlds. We don't change any behaviour – its sole purpose is to allow render
 * code to quickly tell that a given IBlockAccess is backed by an Alpha world
 * (via IBlockAccess#getWorldChunkManager()).
 */
public class WorldChunkManagerAlpha extends WorldChunkManager {

    private BiomeBase c; // The biome to use (e.g., BiomeBase.PLAINS or TAIGA for snow)
    // private Random d; // Seed, kept from original constructor but not used for noise gen here
    private final boolean snowWorld;

    // Canonical Alpha temperature and rainfall values
    private static final double ALPHA_TEMPERATURE = 0.508D;
    private static final double ALPHA_RAINFALL = 0.5D;

    public WorldChunkManagerAlpha(BiomeBase biomebase, long seed) {
        this(biomebase, seed, false);
    }

    public WorldChunkManagerAlpha(BiomeBase biomebase, long seed, boolean snowWorld) {
        // super(); // Do not initialize base noise generators for Alpha
        this.snowWorld = snowWorld;
        // Choose a snow-enabled biome when snowWorld is requested so precipitation renders as snow
        this.c = snowWorld ? BiomeBase.TAIGA : biomebase;
    }

    // Provides a single biome, consistently the Alpha one.
    @Override
    public BiomeBase getBiome(int i, int j) {
        return this.c;
    }

    // Provides an array of biomes, all consistently the Alpha one.
    @Override
    public BiomeBase[] getBiomeData(int i, int j, int k, int l) {
        if (this.d == null || this.d.length < k * l) {
            this.d = new BiomeBase[k * l];
        }
        Arrays.fill(this.d, this.c);
        return this.d;
    }
    
    // This is the main biome array generator, called by getBiomeData.
    // It should also fill the internal temperature and rain arrays for consistency if anything else uses them.
    @Override
    public BiomeBase[] a(BiomeBase[] abiomebase, int i, int j, int k, int l) {
        if (abiomebase == null || abiomebase.length < k * l) {
            abiomebase = new BiomeBase[k * l];
        }
        Arrays.fill(abiomebase, this.c);

        // Ensure internal temp/rain arrays are also consistent if they exist and are used
        if (this.temperature == null || this.temperature.length < k * l) {
            this.temperature = new double[k * l];
        }
        Arrays.fill(this.temperature, this.snowWorld ? 0.0D : ALPHA_TEMPERATURE);

        if (this.rain == null || this.rain.length < k * l) {
            this.rain = new double[k * l];
        }
        Arrays.fill(this.rain, ALPHA_RAINFALL);
        
        return abiomebase;
    }

    // Provides temperature values, consistently the Alpha default.
    @Override
    public double[] a(double[] adouble, int i, int j, int k, int l) { // This is getTemperatures
        if (adouble == null || adouble.length < k * l) {
            adouble = new double[k * l];
        }
        Arrays.fill(adouble, this.snowWorld ? 0.0D : ALPHA_TEMPERATURE);
        return adouble;
    }

    // Provides rainfall values, consistently the Alpha default.
    // The base class doesn't have a direct getRainfall array method like getTemperatures,
    // but rainfall is calculated within its main 'a' (loadBlockGeneratorData).
    // We ensure 'this.rain' is filled above.
    // For individual calls, if any:
    public double[] getRainfall(double[] arr, int x, int z, int width, int depth) {
         if (arr == null || arr.length < width * depth) {
            arr = new double[width * depth];
        }
        Arrays.fill(arr, ALPHA_RAINFALL);
        return arr;
    }
    
    // For CraftBukkit compatibility or other direct calls for single humidity point
    @Override
    public double getHumidity(int x, int z) {
        return ALPHA_RAINFALL;
    }
} 