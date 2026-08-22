package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.PacketLimits;
import net.minecraft.server.util.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wire format payload for registry ID synchronization.
 */
public final class RegistrySyncSnapshot {
    private static final int LEGACY_FORMAT_VERSION = 1;
    private static final int FORMAT_VERSION = 2;
    private static final int MAX_REGISTRY_ENTRIES = 4096;
    private static final int SHA_256_HEX_LENGTH = 64;

    private final Map<String, Integer> itemIds = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> blockIds = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> entityIds = new LinkedHashMap<String, Integer>();
    private int decodedFormatVersion;
    private boolean valid;
    private String synchronizedDataFingerprint = "";

    public Map<String, Integer> getItemIds() { return itemIds; }
    public Map<String, Integer> getBlockIds() { return blockIds; }
    public Map<String, Integer> getEntityIds() { return entityIds; }
    public int getFormatVersion() { return this.decodedFormatVersion; }
    public boolean isValid() { return this.valid; }
    public String getSynchronizedDataFingerprint() {
        return this.synchronizedDataFingerprint;
    }

    public boolean hasSynchronizedDataFingerprint() {
        return isSha256(this.synchronizedDataFingerprint);
    }

    public boolean matchesLocalSynchronizedData() {
        return this.hasSynchronizedDataFingerprint()
                && this.synchronizedDataFingerprint.equals(
                        RegistryDataFingerprint.captureSynchronizedData());
    }

    public static RegistrySyncSnapshot captureLocal() {
        RegistrySyncSnapshot snapshot = new RegistrySyncSnapshot();
        snapshot.decodedFormatVersion = FORMAT_VERSION;
        snapshot.valid = true;

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

        snapshot.synchronizedDataFingerprint =
                RegistryDataFingerprint.captureSynchronizedData();

        return snapshot;
    }

    public byte[] toBytes() {
        return this.toBytes(FORMAT_VERSION);
    }

    /** Encodes the v1 shape used by peers that did not negotiate data fingerprints. */
    public byte[] toLegacyBytes() {
        return this.toBytes(LEGACY_FORMAT_VERSION);
    }

    private byte[] toBytes(int formatVersion) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeInt(formatVersion);
            writeMap(out, itemIds);
            writeMap(out, blockIds);
            writeMap(out, entityIds);
            if (formatVersion >= FORMAT_VERSION) {
                if (!isSha256(this.synchronizedDataFingerprint)) {
                    throw new IllegalStateException("Invalid synchronized data fingerprint");
                }
                out.writeUTF(this.synchronizedDataFingerprint);
            }
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
            if (version != LEGACY_FORMAT_VERSION && version != FORMAT_VERSION) {
                return snapshot;
            }
            readMap(in, snapshot.itemIds);
            readMap(in, snapshot.blockIds);
            readMap(in, snapshot.entityIds);
            if (version >= FORMAT_VERSION) {
                snapshot.synchronizedDataFingerprint = in.readUTF();
                if (!isSha256(snapshot.synchronizedDataFingerprint)) {
                    return new RegistrySyncSnapshot();
                }
            }
            if (in.available() != 0) {
                return new RegistrySyncSnapshot();
            }
            snapshot.decodedFormatVersion = version;
            snapshot.valid = true;
        } catch (Throwable ignored) {
            return new RegistrySyncSnapshot();
        } finally {
            if (in != null) {
                try { in.close(); } catch (Throwable ignored) {}
            }
        }
        return snapshot;
    }

    private static void writeMap(DataOutputStream out, Map<String, Integer> values) throws Exception {
        if (values.size() > MAX_REGISTRY_ENTRIES) {
            throw new IllegalArgumentException("Registry map is too large: " + values.size());
        }
        out.writeInt(values.size());
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            PacketLimits.writeUtf(out, entry.getKey(), 128, "registry key");
            out.writeInt(entry.getValue().intValue());
        }
    }

    private static void readMap(DataInputStream in, Map<String, Integer> values) throws Exception {
        int size = in.readInt();
        if (size < 0 || size > MAX_REGISTRY_ENTRIES) {
            throw new IllegalArgumentException("Invalid registry map size: " + size);
        }
        for (int i = 0; i < size; i++) {
            String key = PacketLimits.readUtf(in, 128, "registry key");
            if (key.length() == 0 || values.containsKey(key)) {
                throw new IllegalArgumentException("Invalid or duplicate registry key");
            }
            int value = in.readInt();
            values.put(key, Integer.valueOf(value));
        }
    }

    private static boolean isSha256(String value) {
        if (value == null || value.length() != SHA_256_HEX_LENGTH) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }
}
