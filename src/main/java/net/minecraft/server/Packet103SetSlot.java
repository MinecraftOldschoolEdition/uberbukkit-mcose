package net.minecraft.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Packet103SetSlot extends Packet {

    public int a;
    public int b;
    public ItemStack c;

    public Packet103SetSlot() {
    }

    public Packet103SetSlot(int i, int j, ItemStack itemstack) {
        this.a = i;
        this.b = j;
        this.c = itemstack == null ? itemstack : itemstack.cloneItemStack();
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public void a(DataInputStream datainputstream) throws IOException {
        this.a = datainputstream.readByte();
        this.b = datainputstream.readShort();
        short short1 = datainputstream.readShort();

        if (short1 >= 0) {
            byte b0 = datainputstream.readByte();
            short short2 = 0;
            // uberbukkit
            if (this.pvn >= 8) {
                short2 = datainputstream.readShort();
            } else {
                short2 = datainputstream.readByte();
            }

            this.c = new ItemStack(short1, b0, short2);
            
            // Read NBT data if present (MCOSE protocol extension)
            if (this.pvn >= 14) {
                this.c.tag = PacketLimits.readCompressedNBT(datainputstream, PacketLimits.MAX_ITEM_NBT_BYTES, "item NBT");
            }
        } else {
            this.c = null;
        }
    }

    public void a(DataOutputStream dataoutputstream) throws IOException {
        dataoutputstream.writeByte(this.a);
        dataoutputstream.writeShort(this.b);
        if (this.c == null) {
            dataoutputstream.writeShort(-1);
        } else {
            dataoutputstream.writeShort(this.c.id);
            dataoutputstream.writeByte(this.c.count);
            // uberbukkit
            if (this.pvn >= 8) {
                dataoutputstream.writeShort(this.c.getData());
            } else {
                dataoutputstream.writeByte(this.c.getData());
            }
            
            // Write NBT data if present (MCOSE protocol extension)
            if (this.pvn >= 14) {
                if (this.c.tag != null) {
                    try {
                        PacketLimits.writeCompressedNBT(dataoutputstream, this.c.tag, PacketLimits.MAX_ITEM_NBT_BYTES, "item NBT");
                    } catch (Exception e) {
                        dataoutputstream.writeShort(-1);
                    }
                } else {
                    dataoutputstream.writeShort(-1);
                }
            }
        }
    }

    public int a() {
        // uberbukkit
        return this.pvn >= 8 ? 8 : 7;
    }
}
