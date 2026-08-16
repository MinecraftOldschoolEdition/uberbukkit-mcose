package net.minecraft.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class Packet15PlacePlacementContextTest {
    @Test
    public void extractsAndStripsNegotiatedPlacementContext() throws Exception {
        assertNotNull(Block.TRAP_DOOR);
        Packet15Place outgoing = new Packet15Place();
        outgoing.pvn = 14;
        outgoing.a = 12;
        outgoing.b = 64;
        outgoing.c = -7;
        outgoing.face = 2;
        outgoing.itemstack = new ItemStack(Block.TRAP_DOOR);
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound context = new NBTTagCompound();
        context.a("hitY", 64.75D);
        context.a("createdRoot", true);
        root.a(Packet15Place.PLACEMENT_CONTEXT_TAG, context);
        outgoing.itemstack.tag = root;

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        outgoing.a(output);
        output.close();

        Packet15Place decoded = new Packet15Place();
        decoded.pvn = 14;
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        decoded.a(input);

        assertEquals(64.75D, decoded.placementHitY, 0.0D);
        assertNull(decoded.itemstack.tag);
        assertEquals(0, input.available());
    }

    @Test
    public void strippingContextPreservesRealItemNbt() throws Exception {
        Packet15Place outgoing = packetWithContext(false);
        outgoing.itemstack.tag.setString("owner", "player");

        Packet15Place decoded = roundTrip(outgoing);

        assertEquals(64.75D, decoded.placementHitY, 0.0D);
        assertNotNull(decoded.itemstack.tag);
        assertEquals("player", decoded.itemstack.tag.getString("owner"));
    }

    private static Packet15Place packetWithContext(boolean createdRoot) {
        Packet15Place packet = new Packet15Place();
        packet.pvn = 14;
        packet.a = 12;
        packet.b = 64;
        packet.c = -7;
        packet.face = 2;
        packet.itemstack = new ItemStack(Block.TRAP_DOOR);
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound context = new NBTTagCompound();
        context.a("hitY", 64.75D);
        context.a("createdRoot", createdRoot);
        root.a(Packet15Place.PLACEMENT_CONTEXT_TAG, context);
        packet.itemstack.tag = root;
        return packet;
    }

    private static Packet15Place roundTrip(Packet15Place outgoing) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        outgoing.a(output);
        output.close();

        Packet15Place decoded = new Packet15Place();
        decoded.pvn = 14;
        decoded.a(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        return decoded;
    }
}
