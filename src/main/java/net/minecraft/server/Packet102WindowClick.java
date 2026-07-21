package net.minecraft.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Packet102WindowClick extends Packet {

    public int a;
    public int b;
    public int c;
    public short d;
    public ItemStack e;
    public boolean f;

    public Packet102WindowClick() {
    }

    public int getContainerInput() {
        return ContainerInput.decodeInput(this.c, this.f);
    }

    public int getMouseButton() {
        return ContainerInput.decodeButton(this.c);
    }

    public boolean hasExtendedContainerInput() {
        return ContainerInput.isExtendedButton(this.c);
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public void a(DataInputStream datainputstream) throws IOException {
        this.a = datainputstream.readByte();
        this.b = datainputstream.readShort();
        this.c = datainputstream.readByte();
        this.d = datainputstream.readShort();
        // uberbukkit
        if (this.pvn >= 11) {
            this.f = datainputstream.readBoolean();
        } else {
            this.f = false;
        }

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

            if (short1 >= Item.byId.length || Item.byId[short1] == null) {
                throw new IOException("Invalid item id " + short1 + " in window click packet");
            }
            if (b0 <= 0) {
                throw new IOException("Invalid item count " + b0 + " in window click packet");
            }

            this.e = new ItemStack(short1, b0, short2);
            
            // Read NBT data if present (MCOSE protocol extension, pvn >= 14)
            if (this.pvn >= 14) {
                this.e.tag = PacketLimits.readCompressedNBT(datainputstream, PacketLimits.MAX_ITEM_NBT_BYTES, "item NBT");
            }
        } else if (short1 == -1) {
            this.e = null;
        } else {
            throw new IOException("Invalid item id " + short1 + " in window click packet");
        }
    }

    public void a(DataOutputStream dataoutputstream) throws IOException {
        dataoutputstream.writeByte(this.a);
        dataoutputstream.writeShort(this.b);
        dataoutputstream.writeByte(this.c);
        dataoutputstream.writeShort(this.d);
        // uberbukkit
        if (this.pvn >= 11) {
            dataoutputstream.writeBoolean(this.f);
        }

        if (this.e == null) {
            dataoutputstream.writeShort(-1);
        } else {
            dataoutputstream.writeShort(this.e.id);
            dataoutputstream.writeByte(this.e.count);
            // uberbukkit
            if (this.pvn >= 8) {
                dataoutputstream.writeShort(this.e.getData());
            } else {
                dataoutputstream.writeByte(this.e.getData());
            }
            
            // Write NBT data if present (MCOSE protocol extension, pvn >= 14)
            if (this.pvn >= 14) {
                if (this.e.tag != null) {
                    try {
                        PacketLimits.writeCompressedNBT(dataoutputstream, this.e.tag, PacketLimits.MAX_ITEM_NBT_BYTES, "item NBT");
                    } catch (Exception ex) {
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
        return this.pvn >= 8 ? 11 : 10;
    }
}
