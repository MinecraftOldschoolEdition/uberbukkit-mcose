package net.minecraft.server;

import java.util.Random;
import net.minecraft.server.registry.BlockRegistry;
import net.minecraft.server.util.ResourceLocation;

public class WorldGenClay extends WorldGenerator {

    private final BlockStateKey outputState;
    private final int targetBlockId;
    private final int size;
    private final Material requiredOriginFluidMaterial;

    public WorldGenClay(int i) {
        this.outputState = stateFromBlockId(Block.CLAY.id);
        this.targetBlockId = Block.SAND.id;
        this.size = i;
        this.requiredOriginFluidMaterial = Material.WATER;
    }

    public WorldGenClay(
            ResourceLocation outputState,
            ResourceLocation targetBlock,
            int size,
            Material requiredOriginFluidMaterial) {
        Block output = canonicalBlock(outputState, "output state");
        Block target = canonicalBlock(targetBlock, "target block");
        if (size <= 0) {
            throw new IllegalArgumentException(
                    "Legacy clay size must be positive");
        }
        if (requiredOriginFluidMaterial == null) {
            throw new IllegalArgumentException(
                    "Legacy clay origin fluid material is required");
        }
        this.outputState = stateFromBlockId(output.id);
        this.targetBlockId = target.id;
        this.size = size;
        this.requiredOriginFluidMaterial = requiredOriginFluidMaterial;
    }

    public boolean a(World world, Random random, int i, int j, int k) {
        if (world.getMaterial(i, j, k)
                != this.requiredOriginFluidMaterial) {
            return false;
        } else {
            float f = random.nextFloat() * 3.1415927F;
            double d0 = (double) ((float) (i + 8) + MathHelper.sin(f) * (float) this.size / 8.0F);
            double d1 = (double) ((float) (i + 8) - MathHelper.sin(f) * (float) this.size / 8.0F);
            double d2 = (double) ((float) (k + 8) + MathHelper.cos(f) * (float) this.size / 8.0F);
            double d3 = (double) ((float) (k + 8) - MathHelper.cos(f) * (float) this.size / 8.0F);
            double d4 = (double) (j + random.nextInt(3) + 2);
            double d5 = (double) (j + random.nextInt(3) + 2);

            for (int l = 0; l <= this.size; ++l) {
                double d6 = d0 + (d1 - d0) * (double) l / (double) this.size;
                double d7 = d4 + (d5 - d4) * (double) l / (double) this.size;
                double d8 = d2 + (d3 - d2) * (double) l / (double) this.size;
                double d9 = random.nextDouble() * (double) this.size / 16.0D;
                double d10 = (double) (MathHelper.sin((float) l * 3.1415927F / (float) this.size) + 1.0F) * d9 + 1.0D;
                double d11 = (double) (MathHelper.sin((float) l * 3.1415927F / (float) this.size) + 1.0F) * d9 + 1.0D;
                int i1 = MathHelper.floor(d6 - d10 / 2.0D);
                int j1 = MathHelper.floor(d6 + d10 / 2.0D);
                int k1 = MathHelper.floor(d7 - d11 / 2.0D);
                int l1 = MathHelper.floor(d7 + d11 / 2.0D);
                int i2 = MathHelper.floor(d8 - d10 / 2.0D); // CraftBukkit - d6 -> d8
                int j2 = MathHelper.floor(d8 + d10 / 2.0D); // CraftBukkit - d6 -> d8

                for (int k2 = i1; k2 <= j1; ++k2) {
                    for (int l2 = k1; l2 <= l1; ++l2) {
                        for (int i3 = i2; i3 <= j2; ++i3) {
                            double d12 = ((double) k2 + 0.5D - d6) / (d10 / 2.0D);
                            double d13 = ((double) l2 + 0.5D - d7) / (d11 / 2.0D);
                            double d14 = ((double) i3 + 0.5D - d8) / (d10 / 2.0D);

                            if (d12 * d12 + d13 * d13 + d14 * d14 < 1.0D) {
                                int j3 = world.getTypeId(k2, l2, i3);

                                if (j3 == this.targetBlockId) {
                                    setGeneratedBlock(
                                            world, k2, l2, i3,
                                            this.outputState);
                                }
                            }
                        }
                    }
                }
            }

            return true;
        }
    }

    private static Block canonicalBlock(
            ResourceLocation key, String description) {
        Block block = key == null ? null : BlockRegistry.get(key);
        if (block == null || !key.equals(BlockRegistry.getKey(block))) {
            throw new IllegalArgumentException(
                    "Legacy clay " + description
                            + " must be a registered canonical block: " + key);
        }
        return block;
    }
}
