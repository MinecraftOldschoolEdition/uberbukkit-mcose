package net.minecraft.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Packet15Place extends Packet {

    public int a;
    public int b;
    public int c;
    public int face;
    public ItemStack itemstack;
    public int data;

    public Packet15Place() {
    }

    public void a(DataInputStream datainputstream) throws IOException {
        if (this.pvn <= 6) {
            this.data = datainputstream.readShort();
            this.a = datainputstream.readInt();
            this.b = datainputstream.read();
            this.c = datainputstream.readInt();
            this.face = datainputstream.read();

        } else if (this.pvn >= 7) {
            this.a = datainputstream.readInt();
            this.b = datainputstream.read();
            this.c = datainputstream.readInt();
            this.face = datainputstream.read();

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

                this.itemstack = new ItemStack(short1, b0, short2);
                
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
                                this.itemstack.tag = (NBTTagCompound) nbtBase;
                            }
                        } catch (Exception ex) {
                            // Ignore NBT read errors
                        }
                    }
                }
            } else {
                this.itemstack = null;
            }
        }
    }

    public void a(DataOutputStream dataoutputstream) throws IOException {
        if (this.pvn <= 6) {
            dataoutputstream.writeShort(this.data);
            dataoutputstream.writeInt(this.a);
            dataoutputstream.write(this.b);
            dataoutputstream.writeInt(this.c);
            dataoutputstream.write(this.face);

        } else if (this.pvn >= 7) {
            dataoutputstream.writeInt(this.a);
            dataoutputstream.write(this.b);
            dataoutputstream.writeInt(this.c);
            dataoutputstream.write(this.face);

            if (this.itemstack == null) {
                dataoutputstream.writeShort(-1);
            } else {
                dataoutputstream.writeShort(this.itemstack.id);
                dataoutputstream.writeByte(this.itemstack.count);
                // uberbukkit
                if (this.pvn >= 8) {
                    dataoutputstream.writeShort(this.itemstack.getData());
                } else {
                    dataoutputstream.writeByte(this.itemstack.getData());
                }
                
                // Write NBT data if present (MCOSE protocol extension, pvn >= 14)
                if (this.pvn >= 14) {
                    if (this.itemstack.tag != null) {
                        try {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            GZIPOutputStream gzos = new GZIPOutputStream(baos);
                            DataOutputStream nbtOut = new DataOutputStream(gzos);
                            NBTBase.a(this.itemstack.tag, nbtOut);
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
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        // uberbukkit
        return this.pvn >= 8 ? 15 : 14;
    }
}
