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
        if (short1 > 256) {
            throw new IOException("Too many window items: " + short1);
        }

        this.b = new ItemStack[short1];
        NBTReadLimiter packetNbtLimiter = NBTReadLimiter.packet();

        for (int i = 0; i < short1; ++i) {
            short short2 = datainputstream.readShort();

            if (short2 >= 0) {
                byte b0 = datainputstream.readByte();
                short short3 = datainputstream.readShort();

                NBTTagCompound wireTag = PacketItemStackCodec.readTag(
                        datainputstream,
                        this.pvn,
                        packetNbtLimiter);
                this.b[i] = PacketItemStackCodec.decode(short2, b0, short3, wireTag);
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
                
                PacketItemStackCodec.writeTag(dataoutputstream, this.b[i], this.pvn);
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
