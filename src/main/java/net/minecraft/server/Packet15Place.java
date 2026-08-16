package net.minecraft.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Packet15Place extends Packet {

    public static final String PLACEMENT_CONTEXT_TAG = "__MCOSEPlacementContext";
    private static final String HIT_Y_TAG = "hitY";
    private static final String CREATED_ROOT_TAG = "createdRoot";

    public int a;
    public int b;
    public int c;
    public int face;
    public ItemStack itemstack;
    public int data;
    public double placementHitY = Double.NaN;

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

                if (short1 >= Item.byId.length || Item.byId[short1] == null) {
                    throw new IOException("Invalid item id " + short1 + " in block place packet");
                }

                if (b0 <= 0) {
                    throw new IOException("Invalid item count " + b0 + " for item " + short1 + " in block place packet");
                }

                this.itemstack = new ItemStack(short1, b0, short2);

                // Read NBT data if present (MCOSE protocol extension, pvn >= 14)
                if (this.pvn >= 14) {
                    this.itemstack.tag = PacketLimits.readCompressedNBT(datainputstream, PacketLimits.MAX_ITEM_NBT_BYTES, "item NBT");
                    this.extractPlacementContext();
                }
            } else if (short1 == -1) {
                this.itemstack = null;
            } else {
                throw new IOException("Invalid item id " + short1 + " in block place packet");
            }
        }
    }

    private void extractPlacementContext() {
        if (this.itemstack == null || this.itemstack.tag == null
                || !this.itemstack.tag.hasKey(PLACEMENT_CONTEXT_TAG)) {
            return;
        }

        NBTTagCompound root = this.itemstack.tag;
        NBTTagCompound context = root.k(PLACEMENT_CONTEXT_TAG);
        double hitY = context.h(HIT_Y_TAG);
        boolean createdRoot = context.c(CREATED_ROOT_TAG) != 0;
        root.remove(PLACEMENT_CONTEXT_TAG);
        if (createdRoot && root.getKeys().isEmpty()) {
            this.itemstack.tag = null;
        }
        if (!Double.isNaN(hitY) && !Double.isInfinite(hitY)) {
            this.placementHitY = hitY;
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
                            PacketLimits.writeCompressedNBT(dataoutputstream, this.itemstack.tag, PacketLimits.MAX_ITEM_NBT_BYTES, "item NBT");
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
