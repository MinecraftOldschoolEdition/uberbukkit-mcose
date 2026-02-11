package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.util.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Wire format payload for registry ID synchronization.
 */
public final class RegistrySyncSnapshot {
    private static final int FORMAT_VERSION = 1;

    private final Map<String, Integer> itemIds = new HashMap<String, Integer>();
    private final Map<String, Integer> blockIds = new HashMap<String, Integer>();
    private final Map<String, Integer> entityIds = new HashMap<String, Integer>();

    public Map<String, Integer> getItemIds() { return itemIds; }
    public Map<String, Integer> getBlockIds() { return blockIds; }
    public Map<String, Integer> getEntityIds() { return entityIds; }

    public static RegistrySyncSnapshot captureLocal() {
        RegistrySyncSnapshot snapshot = new RegistrySyncSnapshot();

        for (ResourceLocation key : ItemRegistry.primaryKeys()) {
            Item item = ItemRegistry.get(key);
            if (item == null) {
                continue;
            }
            snapshot.itemIds.put(key.toString(), Integer.valueOf(ItemRegistry.getLegacyId(item)));
        }

        for (ResourceLocation key : BlockRegistry.primaryKeys()) {
            Block block = BlockRegistry.get(key);
            if (block == null) {
                continue;
            }
            snapshot.blockIds.put(key.toString(), Integer.valueOf(BlockRegistry.getLegacyId(block)));
        }

        for (ResourceLocation key : EntityTypeRegistry.primaryKeys()) {
            Integer id = EntityTypeRegistry.getLegacyId(key);
            if (id == null) {
                continue;
            }
            snapshot.entityIds.put(key.toString(), id);
        }

        return snapshot;
    }

    public byte[] toBytes() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeInt(FORMAT_VERSION);
            writeMap(out, itemIds);
            writeMap(out, blockIds);
            writeMap(out, entityIds);
            out.flush();
            return baos.toByteArray();
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    public static RegistrySyncSnapshot fromBytes(byte[] data) {
        RegistrySyncSnapshot snapshot = new RegistrySyncSnapshot();
        if (data == null || data.length == 0) {
            return snapshot;
        }

        DataInputStream in = null;
        try {
            in = new DataInputStream(new ByteArrayInputStream(data));
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                return snapshot;
            }
            readMap(in, snapshot.itemIds);
            readMap(in, snapshot.blockIds);
            readMap(in, snapshot.entityIds);
        } catch (Throwable ignored) {
        } finally {
            if (in != null) {
                try { in.close(); } catch (Throwable ignored) {}
            }
        }
        return snapshot;
    }

    private static void writeMap(DataOutputStream out, Map<String, Integer> values) throws Exception {
        out.writeInt(values.size());
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            out.writeUTF(entry.getKey());
            out.writeInt(entry.getValue().intValue());
        }
    }

    private static void readMap(DataInputStream in, Map<String, Integer> values) throws Exception {
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            String key = in.readUTF();
            int value = in.readInt();
            values.put(key, Integer.valueOf(value));
        }
    }
}
