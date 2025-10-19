package net.minecraft.server;

import java.util.Arrays;

// WorldChunkManager for Flat worlds. Always returns BiomeBase.PLAINS.
public class WorldChunkManagerFlat extends WorldChunkManager {

    private BiomeBase flatBiome;
    // Canonical temperature and rainfall for Plains biome
    private static final double PLAINS_TEMPERATURE = 0.8D; // Using D for double, matching WorldChunkManagerAlpha
    private static final double PLAINS_RAINFALL = 0.4D;

    public WorldChunkManagerFlat() {
        this.flatBiome = BiomeBase.PLAINS;
        // Initialize temp/humidity arrays for a 1x1 area, they'll be resized if needed
        // These fields (temperature, rain) are inherited from WorldChunkManager
        // and are expected to be populated by the main data generation method 'a(BiomeBase[], ...)'
        // For constructor, we can initialize them minimally or defer to first call of 'a'
        this.temperature = new double[]{PLAINS_TEMPERATURE};
        this.rain = new double[]{PLAINS_RAINFALL}; 
    }

    // Provides a single biome, consistently PLAINS.
    @Override
    public BiomeBase getBiome(int i, int j) {
        return this.flatBiome;
    }

    // Provides an array of biomes, all consistently PLAINS.
    // This is often called by Chunk.generateSkylightMap or similar lighting calcs.
    @Override
    public BiomeBase[] getBiomeData(int i, int j, int k, int l) {
        if (this.d == null || this.d.length < k * l) { // this.d is likely the biomeCache from superclass
            this.d = new BiomeBase[k * l];
        }
        Arrays.fill(this.d, this.flatBiome);
        return this.d;
    }
    
    // This is the main biome array generator, called by getBiomeData if superclass logic is involved,
    // or directly by some parts of the game.
    // It should also fill the internal temperature and rain arrays for consistency.
    @Override
    public BiomeBase[] a(BiomeBase[] abiomebase, int i, int j, int k, int l) {
        if (abiomebase == null || abiomebase.length < k * l) {
            abiomebase = new BiomeBase[k * l];
        }
        Arrays.fill(abiomebase, this.flatBiome);

        // These fields (temperature, rain) are from the superclass WorldChunkManager
        if (this.temperature == null || this.temperature.length < k * l) {
            this.temperature = new double[k * l];
        }
        Arrays.fill(this.temperature, PLAINS_TEMPERATURE);

        if (this.rain == null || this.rain.length < k * l) {
            this.rain = new double[k * l];
        }
        Arrays.fill(this.rain, PLAINS_RAINFALL);
        
        return abiomebase;
    }

    // Provides temperature values, consistently from PLAINS biome.
    @Override
    public double[] a(double[] adouble, int i, int j, int k, int l) { // This is getTemperatures
        if (adouble == null || adouble.length < k * l) {
            adouble = new double[k * l];
        }
        Arrays.fill(adouble, PLAINS_TEMPERATURE);
        return adouble;
    }
    
    // For CraftBukkit compatibility or other direct calls for single humidity point
    // This method might not be in the base WorldChunkManager for b1.7.3 vanilla, but good to have for compatibility.
    @Override
    public double getHumidity(int x, int z) { // This method might need to be named differently if overriding from base
        return PLAINS_RAINFALL;
    }
    
    // If WorldChunkManager has a getRainfall method like WorldChunkManagerAlpha had, implement it:
    // public double[] getRainfall(double[] arr, int x, int z, int width, int depth) {
    //      if (arr == null || arr.length < width * depth) {
    //         arr = new double[width * depth];
    //     }
    //     Arrays.fill(arr, this.flatBiome.getHumidity());
    //     return arr;
    // }
} 