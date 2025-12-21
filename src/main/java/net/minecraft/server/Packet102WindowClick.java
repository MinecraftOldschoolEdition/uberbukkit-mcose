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

            this.e = new ItemStack(short1, b0, short2);
            
            // Read NBT data if present (MCOSE protocol extension, pvn >= 14)
            if (this.pvn >= 14) {
                short nbtLength = datainputstream.readShort();
                if (nbtLength > 0) {
                    byte[] nbtBytes = new byte[nbtLength];
                    datainputstream.readFully(nbtBytes);
                    try {
                        ByteArrayInputStream bais = new ByteArrayInputStream(nbtBytes);
                        GZIPInputStream gzis = new GZIPInputStream(bais);
                        DataInputStream nbtIn = new DataInputStream(gzis);
                        NBTBase nbtBase = NBTBase.b(nbtIn);
                        nbtIn.close();
                        if (nbtBase instanceof NBTTagCompound) {
                            this.e.tag = (NBTTagCompound) nbtBase;
                        }
                    } catch (Exception ex) {
                        // Ignore NBT read errors
                    }
                }
            }
        } else {
            this.e = null;
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
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        GZIPOutputStream gzos = new GZIPOutputStream(baos);
                        DataOutputStream nbtOut = new DataOutputStream(gzos);
                        NBTBase.a(this.e.tag, nbtOut);
                        nbtOut.close();
                        byte[] nbtBytes = baos.toByteArray();
                        dataoutputstream.writeShort(nbtBytes.length);
                        dataoutputstream.write(nbtBytes);
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
