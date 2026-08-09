package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Packet5EntityEquipment extends Packet {

    public int a;
    public int b;
    public int c;
    public int d;
    public ItemStack[] items; // uberbukkit - vanilla alpha support

    public Packet5EntityEquipment() {
    }

    // pvn >= 7
    public Packet5EntityEquipment(int i, int j, ItemStack itemstack) {
        this.a = i;
        this.b = j;
        if (itemstack == null) {
            this.c = -1;
            this.d = 0;
        } else {
            this.c = itemstack.id;
            this.d = itemstack.getData();
        }
    }

    // pvn <= 6
    // uberbukkit - added constructor for the alpha variant of packet 5
    public Packet5EntityEquipment(int i, ItemStack[] aitemstack) {
        this.a = i;
        this.items = new ItemStack[aitemstack.length];

        for (int j = 0; j < this.items.length; ++j) {
            this.items[j] = aitemstack[j] == null ? null : aitemstack[j].cloneItemStack();
        }
    }

    public void a(DataInputStream datainputstream) throws IOException {
        this.a = datainputstream.readInt();
        // uberbukkit start
        if (this.pvn >= 7) {
            this.b = datainputstream.readShort();
            this.c = datainputstream.readShort();

            if (this.pvn >= 8) {
                this.d = datainputstream.readShort();
            } else {
                this.d = 0;
            }
        } else {
            short short1 = datainputstream.readShort();

            int expectedLength = getLegacyInventorySize(this.a);
            if (short1 != expectedLength) {
                throw new IOException("Invalid legacy inventory length " + short1 + " for inventory " + this.a + " (expected " + expectedLength + ")");
            }

            this.items = new ItemStack[short1];

            for (int i = 0; i < short1; ++i) {
                short short2 = datainputstream.readShort();

                if (short2 >= 0) {
                    byte b0 = datainputstream.readByte();
                    short short3 = datainputstream.readShort();

                    if (short2 >= Item.byId.length || Item.byId[short2] == null) {
                        throw new IOException("Invalid item id " + short2 + " in legacy inventory packet");
                    }

                    int maxStackSize = new ItemStack(short2, 1, 0).getNetworkMaxStackSize();
                    if (b0 <= 0 || b0 > maxStackSize) {
                        throw new IOException("Invalid item count " + b0 + " for item " + short2 + " in legacy inventory packet (expected 1-" + maxStackSize + ")");
                    }

                    this.items[i] = new ItemStack(short2, b0, short3);
                } else if (short2 != -1) {
                    throw new IOException("Invalid item id " + short2 + " in legacy inventory packet");
                }
            }
        }
        // uberbukkit end
    }

    public void a(DataOutputStream dataoutputstream) throws IOException {
        dataoutputstream.writeInt(this.a);
        // uberbukkit start
        if (this.pvn >= 7) {
            dataoutputstream.writeShort(this.b);
            dataoutputstream.writeShort(this.c);

            if (this.pvn >= 8) {
                dataoutputstream.writeShort(this.d);
            }
        } else {
            dataoutputstream.writeShort(this.items.length);

            for (int i = 0; i < this.items.length; ++i) {
                if (this.items[i] == null) {
                    dataoutputstream.writeShort(-1);
                } else {
                    dataoutputstream.writeShort((short) this.items[i].id);
                    dataoutputstream.writeByte((byte) this.items[i].count);
                    dataoutputstream.writeShort((short) this.items[i].damage);
                }
            }
        }
        // uberbukkit end
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        // uberbukkit - size varies between pvns
        return this.pvn >= 7 ? 8 : (6 + this.items.length * 5);
    }

    private static int getLegacyInventorySize(int inventoryId) throws IOException {
        if (inventoryId == -1) {
            return 36;
        }
        if (inventoryId == -2 || inventoryId == -3) {
            return 4;
        }
        throw new IOException("Invalid legacy inventory id " + inventoryId);
    }

    // uberbukkit
    @Override
    public Packet clone() {
        Packet5EntityEquipment packet = new Packet5EntityEquipment();
        packet.a = this.a;
        packet.b = this.b;
        packet.c = this.c;
        packet.d = this.d;
        packet.items = this.items;
        return packet;
    }
}
