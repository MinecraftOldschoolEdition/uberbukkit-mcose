package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;

public class BlockFence extends Block {
    private boolean modernFencingBounding = false;

    public BlockFence(int i, int j) {
        super(i, j, Material.WOOD);
        // Match client collision to prevent rubber banding; default to modern bounding unless explicitly disabled
        modernFencingBounding = PoseidonConfig.getInstance().getBoolean("world-settings.use-modern-fence-bounding-boxes", true);
    }

    @Override
    public boolean canPlace(World world, int i, int j, int k) {
        // Allow fence to be placed anywhere, regardless of the block below
        return true;
    }

    public AxisAlignedBB e(World world, int x, int y, int z) {
        // For modern per-arm collision, return null here so only sub-AABBs from a(...) are used.
        // Fallback to vanilla full column when legacy mode is enabled.
        if (!modernFencingBounding) {
            return AxisAlignedBB.b((double) x, (double) y, (double) z, (double) (x + 1), (double) ((float) y + 1.5F), (double) (z + 1));
        }
        return null;
    }

    private boolean isFenceAtSide(IBlockAccess world, int x, int y, int z, int sideX, int sideZ) {
        int id = world.getTypeId(x, y, z);
        if (id == this.id) return true;
        if (id == Block.FENCE_GATE.id) {
            int meta = (world instanceof World) ? ((World) world).getData(x, y, z) : 0;
            int dir = BlockFenceGate.func_35290_f(meta); // 0/2: Z axis, 1/3: X axis
            if (dir == 0 || dir == 2) {
                return sideZ != 0; // connect only from north/south
            } else {
                return sideX != 0; // connect only from west/east
            }
        }
        Block b = Block.byId[id];
        return b != null && b.material.h() && b.b() && b.material != Material.PUMPKIN;
    }

    @Override
    public void a(World world, int x, int y, int z, AxisAlignedBB clip, java.util.ArrayList list) {
        if (!modernFencingBounding) {
            super.a(world, x, y, z, clip, list);
            return;
        }

        // Center post
        AxisAlignedBB post = AxisAlignedBB.b(x + 0.375D, y + 0.0D, z + 0.375D, x + 0.625D, y + 1.5D, z + 0.625D);
        if (clip == null || clip.a(post)) list.add(post);

        boolean north = this.isFenceAtSide(world, x, y, z - 1, 0, -1);
        boolean south = this.isFenceAtSide(world, x, y, z + 1, 0, 1);
        boolean west  = this.isFenceAtSide(world, x - 1, y, z, -1, 0);
        boolean east  = this.isFenceAtSide(world, x + 1, y, z, 1, 0);

        // North arm
        if (north) {
            AxisAlignedBB a = AxisAlignedBB.b(x + 0.375D, y + 0.0D, z + 0.0D, x + 0.625D, y + 1.5D, z + 0.375D);
            if (clip == null || clip.a(a)) list.add(a);
        }
        // South arm
        if (south) {
            AxisAlignedBB a = AxisAlignedBB.b(x + 0.375D, y + 0.0D, z + 0.625D, x + 0.625D, y + 1.5D, z + 1.0D);
            if (clip == null || clip.a(a)) list.add(a);
        }
        // West arm
        if (west) {
            AxisAlignedBB a = AxisAlignedBB.b(x + 0.0D, y + 0.0D, z + 0.375D, x + 0.375D, y + 1.5D, z + 0.625D);
            if (clip == null || clip.a(a)) list.add(a);
        }
        // East arm
        if (east) {
            AxisAlignedBB a = AxisAlignedBB.b(x + 0.625D, y + 0.0D, z + 0.375D, x + 1.0D, y + 1.5D, z + 0.625D);
            if (clip == null || clip.a(a)) list.add(a);
        }
    }

    public boolean b(IBlockAccess iblockaccess, int i, int j, int k) {
        int l = iblockaccess.getTypeId(i, j, k);

        if (l == this.id) return true; // connect to other fences
        if (l == Block.FENCE_GATE.id) return true; // connect to fence gate (client expectation)

        Block block = Block.byId[l];
        if (block == null) return false;
        // connect to solid, normal render blocks except pumpkins (matches client logic)
        return block.material.h() && block.b() && block.material != Material.PUMPKIN;
    }


    public boolean a() {
        return false;
    }

    public boolean b() {
        return false;
    }
}
