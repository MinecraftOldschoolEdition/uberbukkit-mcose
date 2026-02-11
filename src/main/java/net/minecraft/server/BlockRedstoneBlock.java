package net.minecraft.server;

/**
 * Full-cube constant redstone source.
 */
public class BlockRedstoneBlock extends BlockOreBlock {

    public BlockRedstoneBlock(int i, int j) {
        super(i, j);
    }

    public boolean isPowerSource() {
        return true;
    }

    public boolean a(IBlockAccess iblockaccess, int i, int j, int k, int l) {
        return true;
    }

    public boolean d(World world, int i, int j, int k, int l) {
        return true;
    }

    public void c(World world, int i, int j, int k) {
        super.c(world, i, j, k);
        this.notifyNeighbors(world, i, j, k);
    }

    public void remove(World world, int i, int j, int k) {
        this.notifyNeighbors(world, i, j, k);
        super.remove(world, i, j, k);
    }

    private void notifyNeighbors(World world, int i, int j, int k) {
        world.applyPhysics(i, j - 1, k, this.id);
        world.applyPhysics(i, j + 1, k, this.id);
        world.applyPhysics(i - 1, j, k, this.id);
        world.applyPhysics(i + 1, j, k, this.id);
        world.applyPhysics(i, j, k - 1, this.id);
        world.applyPhysics(i, j, k + 1, this.id);
    }
}
