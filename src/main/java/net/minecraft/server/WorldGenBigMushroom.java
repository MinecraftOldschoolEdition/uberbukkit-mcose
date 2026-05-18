package net.minecraft.server;

import java.util.Random;

public class WorldGenBigMushroom extends WorldGenerator {
    private int type = -1;

    public WorldGenBigMushroom(int t) { this.type = t; }
    public WorldGenBigMushroom() {}

    public boolean a(World world, Random rand, int x, int y, int z) {
        int t = rand.nextInt(2);
        if (this.type >= 0) t = this.type;

        int height = rand.nextInt(3) + 4;
        if (y < 1 || y + height + 1 > 128) return false;

        boolean ok = true;
        for (int yy = y; yy <= y + 1 + height; ++yy) {
            byte radius = 3;
            if (yy == y) radius = 0;
            for (int xx = x - radius; xx <= x + radius && ok; ++xx) {
                for (int zz = z - radius; zz <= z + radius && ok; ++zz) {
                    if (yy >= 0 && yy < 128) {
                        int id = world.getTypeId(xx, yy, zz);
                        if (id != 0 && id != Block.LEAVES.id) ok = false;
                    } else ok = false;
                }
            }
        }
        if (!ok) return false;

        int below = world.getTypeId(x, y - 1, z);
        if (below != Block.DIRT.id && below != Block.GRASS.id) return false;
        if (!Block.BROWN_MUSHROOM.canPlace(world, x, y, z)) return false;

        world.setBlockStateAndData(x, y - 1, z, "minecraft:dirt");
        int capY = y + height;
        if (t == 1) capY = y + height - 3;

        for (int yy = capY; yy <= y + height; ++yy) {
            int radius = 1;
            if (yy < y + height) ++radius;
            if (t == 0) radius = 3;
            for (int xx = x - radius; xx <= x + radius; ++xx) {
                for (int zz = z - radius; zz <= z + radius; ++zz) {
                    int meta = 5;
                    if (xx == x - radius) --meta;
                    if (xx == x + radius) ++meta;
                    if (zz == z - radius) meta -= 3;
                    if (zz == z + radius) meta += 3;
                    if (t == 0 || yy < y + height) {
                        if ((xx == x - radius || xx == x + radius) && (zz == z - radius || zz == z + radius)) continue;
                        if (xx == x - (radius - 1) && zz == z - radius) meta = 1;
                        if (xx == x - radius && zz == z - (radius - 1)) meta = 1;
                        if (xx == x + (radius - 1) && zz == z - radius) meta = 3;
                        if (xx == x + radius && zz == z - (radius - 1)) meta = 3;
                        if (xx == x - (radius - 1) && zz == z + radius) meta = 7;
                        if (xx == x - radius && zz == z + (radius - 1)) meta = 7;
                        if (xx == x + (radius - 1) && zz == z + radius) meta = 9;
                        if (xx == x + radius && zz == z + (radius - 1)) meta = 9;
                    }
                    if (meta == 5 && yy < y + height) meta = 0;
                    if ((meta != 0 || y >= y + height - 1) && !Block.n[world.getTypeId(xx, yy, zz)]) {
                        setGeneratedBlock(world, xx, yy, zz, (Block.BROWN_MUSHROOM_CAP != null ? Block.BROWN_MUSHROOM_CAP.id : 99) + t, meta);
                    }
                }
            }
        }

        for (int i = 0; i < height; ++i) {
            int id = world.getTypeId(x, y + i, z);
            if (!Block.n[id]) setGeneratedBlock(world, x, y + i, z, (Block.BROWN_MUSHROOM_CAP != null ? Block.BROWN_MUSHROOM_CAP.id : 99) + t, 10);
        }
        return true;
    }
}

