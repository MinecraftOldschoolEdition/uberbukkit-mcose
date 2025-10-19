package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Sync single creative-mode inventory slot from client to server.
 * slot -1 = creative drop (client is throwing item from palette)
 */
public class Packet107CreativeSetSlot extends Packet {
    public int slot;
    public ItemStack itemStack;

    public Packet107CreativeSetSlot() {}

    public Packet107CreativeSetSlot(int slot, ItemStack stack) {
        this.slot = slot;
        this.itemStack = stack;
    }

    public void a(DataInputStream datainputstream) throws IOException {
        this.slot = datainputstream.readShort();
        this.itemStack = PacketHelper.readItemStack(datainputstream);
    }

    public void a(DataOutputStream dataoutputstream) throws IOException {
        dataoutputstream.writeShort(this.slot);
        PacketHelper.writeItemStack(this.itemStack, dataoutputstream);
    }

    public void a(NetHandler nethandler) {
        nethandler.handleCreativeSlot(this);
    }

    public int a() {
        return 4;
    }
}


