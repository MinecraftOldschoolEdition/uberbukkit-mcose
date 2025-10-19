package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Utility read/write helpers for ItemStack to match legacy Packet encoding. */
public final class PacketHelper {
    private PacketHelper() {}

    public static ItemStack readItemStack(DataInputStream in) throws IOException {
        short id = in.readShort();
        if (id < 0) return null;
        byte count = in.readByte();
        short damage = in.readShort();
        return new ItemStack(id, count, damage);
    }

    public static void writeItemStack(ItemStack stack, DataOutputStream out) throws IOException {
        if (stack == null) {
            out.writeShort(-1);
            return;
        }
        out.writeShort(stack.id);
        out.writeByte(stack.count);
        out.writeShort(stack.getData());
    }
}


