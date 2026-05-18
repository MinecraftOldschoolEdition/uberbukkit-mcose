package net.minecraft.server;

import java.util.Random;

public class WorldGenFlowers extends WorldGenerator {

    private int a;
    private BlockStateKey state;

    public WorldGenFlowers(int i) {
        this.a = i;
        this.state = stateFromBlockId(i);
    }

    public WorldGenFlowers(String blockId) {
        this.state = stateFromIdentifier(blockId);
        this.a = BlockStateBridge.toLegacy(this.state).blockId;
    }

    public boolean a(World world, Random random, int i, int j, int k) {
        for (int l = 0; l < 64; ++l) {
            int i1 = i + random.nextInt(8) - random.nextInt(8);
            int j1 = j + random.nextInt(4) - random.nextInt(4);
            int k1 = k + random.nextInt(8) - random.nextInt(8);

            if (world.isEmpty(i1, j1, k1) && ((BlockFlower) Block.byId[this.a]).f(world, i1, j1, k1)) {
                setGeneratedBlock(world, i1, j1, k1, this.state);
            }
        }

        return true;
    }
}
