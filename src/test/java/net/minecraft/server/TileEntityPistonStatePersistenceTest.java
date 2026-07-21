package net.minecraft.server;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TileEntityPistonStatePersistenceTest {
    @Test
    public void movingPistonStoresModernMovedStateAndSourceFlag() {
        TileEntityPiston original = new TileEntityPiston(
                Block.DIODE_ON.id,
                13,
                5,
                false,
                true
        );
        original.x = 4;
        original.y = 64;
        original.z = 8;
        NBTTagCompound saved = new NBTTagCompound();

        original.b(saved);

        assertTrue(saved.hasKey("blockState"));
        assertFalse(saved.hasKey("blockId"));
        assertFalse(saved.hasKey("blockData"));
        assertTrue(saved.m("source"));
        NBTTagCompound movedState = saved.k("blockState");
        assertEquals("minecraft:repeater", movedState.getString("Name"));
        assertEquals("west", movedState.k("Properties").getString("facing"));
        assertEquals("4", movedState.k("Properties").getString("delay"));
        assertEquals("true", movedState.k("Properties").getString("powered"));

        TileEntityPiston restored = new TileEntityPiston();
        restored.a(saved);
        assertEquals(Block.DIODE_ON.id, restored.a());
        assertEquals(13, restored.e());
        assertEquals(5, restored.d());
        assertFalse(restored.c());

        NBTTagCompound resaved = new NBTTagCompound();
        restored.b(resaved);
        assertTrue(resaved.m("source"));
    }

    @Test
    public void legacyMovingPistonTagStillLoadsAndUpgradesOnWrite() {
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.a("x", 1);
        legacy.a("y", 2);
        legacy.a("z", 3);
        legacy.a("blockId", Block.TRAP_DOOR.id);
        legacy.a("blockData", 6);
        legacy.a("facing", 3);
        legacy.a("progress", 0.5F);
        legacy.a("extending", true);

        TileEntityPiston restored = new TileEntityPiston();
        restored.a(legacy);

        assertEquals(Block.TRAP_DOOR.id, restored.a());
        assertEquals(6, restored.e());
        assertEquals(3, restored.d());
        assertTrue(restored.c());

        NBTTagCompound upgraded = new NBTTagCompound();
        restored.b(upgraded);
        assertTrue(upgraded.hasKey("blockState"));
        assertFalse(upgraded.hasKey("blockId"));
        assertFalse(upgraded.hasKey("blockData"));
        assertFalse(upgraded.m("source"));
    }
}
