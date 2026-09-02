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
        this.itemStack = stack == null ? null : stack.cloneItemStack();
    }

    private static int normalizeIncomingCreativeItemId(int itemId) {
        if (itemId == 150 && Block.FENCE_GATE != null) {
            return Block.FENCE_GATE.id;
        }
        if (itemId == Block.DOUBLE_STEP.id) {
            return Block.STEP.id;
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
            if (!isRegisteredItemId(normalizedId)) {
                throw new IOException("Invalid creative item id " + itemId);
            }
            if (count <= 0) {
                throw new IOException("Invalid creative item count " + count);
            }
            NBTTagCompound wireTag = PacketItemStackCodec.readTag(datainputstream, this.pvn);
            this.itemStack = PacketItemStackCodec.decode(normalizedId, count, damage, wireTag);
        } else if (itemId == -1) {
            this.itemStack = null;
        } else {
            throw new IOException("Invalid creative item id " + itemId);
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
            
            PacketItemStackCodec.writeTag(dataoutputstream, this.itemStack, this.pvn);
        }
    }

    public void a(NetHandler nethandler) {
        nethandler.handleCreativeSlot(this);
    }

    public int a() {
        return 4;
    }
}
