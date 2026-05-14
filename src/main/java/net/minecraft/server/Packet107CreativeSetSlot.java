package net.minecraft.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Sync single creative-mode inventory slot from client to server.
 * slot -1 = creative drop (client is throwing item from palette)
 */
public class Packet107CreativeSetSlot extends Packet {
    public int slot;
    public ItemStack itemStack;

    public Packet107CreativeSetSlot() {}

    public Packet107CreativeSetSlot(int slot, ItemStack stack) {
        this.slot = slot;
        this.itemStack = stack;
    }

    private static int normalizeIncomingCreativeItemId(int itemId) {
        if (itemId == 150 && Block.FENCE_GATE != null) {
            return Block.FENCE_GATE.id;
        }
        return itemId;
    }

    private static boolean isRegisteredItemId(int itemId) {
        return itemId >= 0 && itemId < Item.byId.length && Item.byId[itemId] != null;
    }

    public void a(DataInputStream datainputstream) throws IOException {
        this.slot = datainputstream.readShort();
        
        // Read ItemStack with pvn-aware NBT support (matches Packet103SetSlot)
        short itemId = datainputstream.readShort();
        if (itemId >= 0) {
            byte count = datainputstream.readByte();
            short damage = datainputstream.readShort();
            int normalizedId = normalizeIncomingCreativeItemId(itemId);
            if (isRegisteredItemId(normalizedId)) {
                this.itemStack = new ItemStack(normalizedId, count, damage);
            } else {
                this.itemStack = null;
            }
            
            // Read NBT data if present (MCOSE protocol extension, pvn >= 14)
            if (this.pvn >= 14) {
                NBTTagCompound tag = PacketLimits.readCompressedNBT(datainputstream, PacketLimits.MAX_ITEM_NBT_BYTES, "item NBT");
                if (this.itemStack != null) {
                    this.itemStack.tag = tag;
                }
            }
        } else {
            this.itemStack = null;
        }
    }

    public void a(DataOutputStream dataoutputstream) throws IOException {
        dataoutputstream.writeShort(this.slot);
        
        // Write ItemStack with pvn-aware NBT support (matches Packet103SetSlot)
        if (this.itemStack == null) {
            dataoutputstream.writeShort(-1);
        } else {
            dataoutputstream.writeShort(this.itemStack.id);
            dataoutputstream.writeByte(this.itemStack.count);
            dataoutputstream.writeShort(this.itemStack.getData());
            
            // Write NBT data if present (MCOSE protocol extension, pvn >= 14)
            if (this.pvn >= 14) {
                if (this.itemStack.tag != null) {
                    try {
                        PacketLimits.writeCompressedNBT(dataoutputstream, this.itemStack.tag, PacketLimits.MAX_ITEM_NBT_BYTES, "item NBT");
                    } catch (Exception e) {
                        dataoutputstream.writeShort(-1);
                    }
                } else {
                    dataoutputstream.writeShort(-1);
                }
            }
        }
    }

    public void a(NetHandler nethandler) {
        nethandler.handleCreativeSlot(this);
    }

    public int a() {
        return 4;
    }
}
