package net.minecraft.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Packet104WindowItems extends Packet {

    public int a;
    public ItemStack[] b;

    public Packet104WindowItems() {
    }

    public Packet104WindowItems(int i, List list) {
        this.a = i;
        this.b = new ItemStack[list.size()];

        for (int j = 0; j < this.b.length; ++j) {
            ItemStack itemstack = (ItemStack) list.get(j);

            this.b[j] = itemstack == null ? null : itemstack.cloneItemStack();
        }
    }

    public void a(DataInputStream datainputstream) throws IOException {
        this.a = datainputstream.readByte();
        int short1 = datainputstream.readUnsignedShort();
        if (short1 > 1024) {
            throw new IOException("Too many window items: " + short1);
        }

        this.b = new ItemStack[short1];

        for (int i = 0; i < short1; ++i) {
            short short2 = datainputstream.readShort();

            if (short2 >= 0) {
                byte b0 = datainputstream.readByte();
                short short3 = datainputstream.readShort();

                this.b[i] = new ItemStack(short2, b0, short3);
                
                // Read NBT data if present (MCOSE protocol extension)
                if (this.pvn >= 14) {
                    this.b[i].tag = PacketLimits.readCompressedNBT(datainputstream, PacketLimits.MAX_ITEM_NBT_BYTES, "item NBT");
                }
            }
        }
    }

    public void a(DataOutputStream dataoutputstream) throws IOException {
        dataoutputstream.writeByte(this.a);
        dataoutputstream.writeShort(this.b.length);

        for (int i = 0; i < this.b.length; ++i) {
            if (this.b[i] == null) {
                dataoutputstream.writeShort(-1);
            } else {
                dataoutputstream.writeShort((short) this.b[i].id);
                dataoutputstream.writeByte((byte) this.b[i].count);
                dataoutputstream.writeShort((short) this.b[i].getData());
                
                // Write NBT data if present (MCOSE protocol extension)
                if (this.pvn >= 14) {
                    if (this.b[i].tag != null) {
                        try {
                            PacketLimits.writeCompressedNBT(dataoutputstream, this.b[i].tag, PacketLimits.MAX_ITEM_NBT_BYTES, "item NBT");
                        } catch (Exception e) {
                            dataoutputstream.writeShort(-1);
                        }
                    } else {
                        dataoutputstream.writeShort(-1);
                    }
                }
            }
        }
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        return 3 + this.b.length * 5;
    }
}
