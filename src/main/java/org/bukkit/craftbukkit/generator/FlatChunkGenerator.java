package org.bukkit.craftbukkit.generator;

import net.minecraft.server.Chunk;
import net.minecraft.server.IChunkProvider;
import net.minecraft.server.IProgressUpdate;
import net.minecraft.server.World;
import net.minecraft.server.ChunkProviderFlat;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.generator.BlockPopulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FlatChunkGenerator extends InternalChunkGenerator {
    private final IChunkProvider provider;

    public FlatChunkGenerator(World world, long seed) {
        this.provider = new ChunkProviderFlat(world, seed, false);
    }

    public byte[] generate(org.bukkit.World world, Random random, int x, int z) {
        throw new UnsupportedOperationException("Not supported.");
    }

    public boolean canSpawn(org.bukkit.World world, int x, int z) {
        return ((CraftWorld) world).getHandle().worldProvider.canSpawn(x, z);
    }

    public List<BlockPopulator> getDefaultPopulators(org.bukkit.World world) {
        return new ArrayList<BlockPopulator>();
    }

    public boolean isChunkLoaded(int i, int i1) {
        return provider.isChunkLoaded(i, i1);
    }

    public Chunk getOrCreateChunk(int i, int i1) {
        return provider.getOrCreateChunk(i, i1);
    }

    public Chunk getChunkAt(int i, int i1) {
        return provider.getChunkAt(i, i1);
    }

    public void getChunkAt(IChunkProvider icp, int i, int i1) {
        provider.getChunkAt(icp, i, i1);
    }

    public boolean saveChunks(boolean bln, IProgressUpdate ipu) {
        return provider.saveChunks(bln, ipu);
    }

    public boolean unloadChunks() {
        return provider.unloadChunks();
    }

    public boolean canSave() {
        return provider.canSave();
    }
}


