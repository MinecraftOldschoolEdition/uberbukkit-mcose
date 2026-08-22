package net.minecraft.server;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import sun.misc.Unsafe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class BetaRedstoneWiringParityTest {
    private static final int[] REPEATER_OUTPUT_SIDES = new int[]{3, 4, 2, 5};
    private static final int[] WIRE_CONNECTION_SIDES = new int[]{2, 3, 0, 1};

    @Test
    public void directlyPoweredDustUsesBetaFullStrength() throws Exception {
        BlockRedstoneWire wire = (BlockRedstoneWire)Block.REDSTONE_WIRE;
        WeakPowerWorld world = WeakPowerWorld.create();
        Method update = BlockRedstoneWire.class.getDeclaredMethod(
                "a",
                World.class,
                Integer.TYPE,
                Integer.TYPE,
                Integer.TYPE,
                Integer.TYPE,
                Integer.TYPE,
                Integer.TYPE
        );
        update.setAccessible(true);

        update.invoke(wire, world, 0, 64, 0, 0, 64, 0);

        assertEquals(15, world.metadata);
        }

    @Test
    public void redstoneTorchUsesBetaStrongPowerFaces() {
        BlockRedstoneTorch active = (BlockRedstoneTorch)Block.REDSTONE_TORCH_ON;
        BlockRedstoneTorch idle = (BlockRedstoneTorch)Block.REDSTONE_TORCH_OFF;
        SingleBlockAccess access = new SingleBlockAccess();
        int[] blockedStrongSides = new int[]{-1, 5, 4, 3, 2, 1};

        for (int metadata = 1; metadata <= 5; ++metadata) {
            access.set(Block.REDSTONE_TORCH_ON.id, metadata);
            for (int side = 0; side < 6; ++side) {
                assertEquals(side != blockedStrongSides[metadata], active.a(access, 0, 64, 0, side));
            }

            access.set(Block.REDSTONE_TORCH_OFF.id, metadata);
            for (int side = 0; side < 6; ++side) {
                assertFalse(idle.a(access, 0, 64, 0, side));
            }
        }
    }

    @Test
    public void repeaterUsesBetaOutputAndWireConnectionDirections() {
        BlockDiode active = (BlockDiode)Block.DIODE_ON;
        BlockDiode idle = (BlockDiode)Block.DIODE_OFF;
        SingleBlockAccess access = new SingleBlockAccess();

        for (int orientation = 0; orientation < 4; ++orientation) {
            access.set(Block.DIODE_ON.id, orientation);
            for (int side = 0; side < 6; ++side) {
                assertEquals(side == REPEATER_OUTPUT_SIDES[orientation], active.a(access, 0, 64, 0, side));
                assertFalse(idle.a(access, 0, 64, 0, side));
                assertEquals(
                        side == WIRE_CONNECTION_SIDES[orientation],
                        BlockRedstoneWire.c(access, 0, 64, 0, side)
                );
            }
        }
    }

    private static final class WeakPowerWorld extends World {
        private int metadata;

        private WeakPowerWorld() {
            super(null, "unused", 0L, null, null, org.bukkit.World.Environment.NORMAL);
        }

        static WeakPowerWorld create() throws Exception {
            WeakPowerWorld world = (WeakPowerWorld)unsafe().allocateInstance(WeakPowerWorld.class);
            world.metadata = 15;
            return world;
        }

        public int getTypeId(int x, int y, int z) {
            return x == 0 && y == 64 && z == 0 ? Block.REDSTONE_WIRE.id : 0;
        }

        public int getData(int x, int y, int z) {
            return x == 0 && y == 64 && z == 0 ? this.metadata : 0;
        }

        public int getMaxIndirectPowerAt(int x, int y, int z) {
            return x == 0 && y == 64 && z == 0 ? 7 : 0;
        }

        public void setData(int x, int y, int z, int metadata) {
            this.metadata = metadata & 15;
        }
    }

    private static final class SingleBlockAccess implements IBlockAccess {
        private int blockId;
        private int metadata;

        void set(int blockId, int metadata) {
            this.blockId = blockId;
            this.metadata = metadata;
        }

        public int getTypeId(int x, int y, int z) {
            return this.blockId;
        }

        public TileEntity getTileEntity(int x, int y, int z) {
            return null;
        }

        public int getData(int x, int y, int z) {
            return this.metadata;
        }

        public Material getMaterial(int x, int y, int z) {
            return this.blockId == 0 ? Material.AIR : Block.byId[this.blockId].material;
        }

        public boolean e(int x, int y, int z) {
            return false;
        }
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe)field.get(null);
    }
}
