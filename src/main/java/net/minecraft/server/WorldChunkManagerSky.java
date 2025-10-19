package net.minecraft.server;

import java.util.Arrays;

// WorldChunkManager for Sky worlds. Always returns BiomeGenSky.
public class WorldChunkManagerSky extends WorldChunkManager {

    private BiomeBase skyBiome;
    // Temperature and rainfall for Sky biome (typically no rain, moderate temp)
    private static final double SKY_TEMPERATURE = 0.5D;
    private static final double SKY_RAINFALL = 0.0D;

    public WorldChunkManagerSky() {
        this.skyBiome = BiomeBase.SKY; // Assumes BiomeBase.SKY is an instance of BiomeGenSky
        // Initialize temp/humidity arrays for a 1x1 area, they'll be resized if needed
        this.temperature = new double[]{SKY_TEMPERATURE};
        this.rain = new double[]{SKY_RAINFALL};
    }

    // Provides a single biome, consistently SKY.
    @Override
    public BiomeBase getBiome(int i, int j) {
        return this.skyBiome;
    }

    // Provides an array of biomes, all consistently SKY.
    @Override
    public BiomeBase[] getBiomeData(int i, int j, int k, int l) {
        if (this.d == null || this.d.length < k * l) { // this.d is biomeCache from superclass
            this.d = new BiomeBase[k * l];
        }
        Arrays.fill(this.d, this.skyBiome);
        return this.d;
    }
    
    // Main biome array generator, fills internal temp and rain arrays for consistency.
    @Override
    public BiomeBase[] a(BiomeBase[] abiomebase, int i, int j, int k, int l) {
        if (abiomebase == null || abiomebase.length < k * l) {
            abiomebase = new BiomeBase[k * l];
        }
        Arrays.fill(abiomebase, this.skyBiome);

        if (this.temperature == null || this.temperature.length < k * l) {
            this.temperature = new double[k * l];
        }
        Arrays.fill(this.temperature, SKY_TEMPERATURE);

        if (this.rain == null || this.rain.length < k * l) {
            this.rain = new double[k * l];
        }
        Arrays.fill(this.rain, SKY_RAINFALL);
        
        return abiomebase;
    }

    // Provides temperature values, consistently from SKY biome.
    @Override
    public double[] a(double[] adouble, int i, int j, int k, int l) { // This is getTemperatures
        if (adouble == null || adouble.length < k * l) {
            adouble = new double[k * l];
        }
        Arrays.fill(adouble, SKY_TEMPERATURE);
        return adouble;
    }
    
    // Provides humidity values, consistently from SKY biome.
    // For b1.7.3 server, WorldChunkManager has getHumidity(x,z)
    // However, the common pattern is to fill an array like temperatures.
    // Let's add a getRainfall similar to Alpha/Flat if it helps compatibility or is needed.
    public double[] getRainfall(double[] arr, int x, int z, int width, int depth) {
         if (arr == null || arr.length < width * depth) {
            arr = new double[width * depth];
        }
        Arrays.fill(arr, SKY_RAINFALL);
        return arr;
    }

    // Individual humidity point - not always present in base WorldChunkManager
    // but good for completeness if anything calls it directly.
    @Override
    public double getHumidity(int x, int z) { 
        return SKY_RAINFALL;
    }
} 