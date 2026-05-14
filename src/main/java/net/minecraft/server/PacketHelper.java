package net.minecraft.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Utility read/write helpers for ItemStack to match legacy Packet encoding. */
public final class PacketHelper {
    private PacketHelper() {}

    public static ItemStack readItemStack(DataInputStream in) throws IOException {
        short id = in.readShort();
        if (id < 0) return null;
        byte count = in.readByte();
        short damage = in.readShort();

        // Read NBT data if present
        NBTTagCompound legacyTag = null;
        legacyTag = PacketLimits.readCompressedNBT(in, PacketLimits.MAX_ITEM_NBT_BYTES, "item NBT");

        return LegacyItemStackCodec.decode(id, count, damage, legacyTag);
    }

    public static void writeItemStack(ItemStack stack, DataOutputStream out) throws IOException {
        if (stack == null) {
            out.writeShort(-1);
            return;
        }

        LegacyItemStackCodec.LegacyStackData encoded = LegacyItemStackCodec.encode(stack, true);
        if (encoded == null) {
            out.writeShort(-1);
            return;
        }

        out.writeShort(encoded.legacyId);
        out.writeByte(encoded.count);
        out.writeShort(encoded.damage);

        // Write NBT data if present
        if (encoded.tag != null) {
            try {
                PacketLimits.writeCompressedNBT(out, encoded.tag, PacketLimits.MAX_ITEM_NBT_BYTES, "item NBT");
            } catch (Exception e) {
                System.err.println("[PacketHelper] Error writing item NBT: " + e.getMessage());
                out.writeShort(-1);
            }
        } else {
            out.writeShort(-1);
        }
    }
}
