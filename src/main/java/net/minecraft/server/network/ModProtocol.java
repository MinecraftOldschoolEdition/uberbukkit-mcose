package net.minecraft.server.network;

import net.minecraft.server.PacketLimits;
import net.minecraft.server.registry.RegistryDataFingerprint;
import net.minecraft.server.registry.RegistrySyncSnapshot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

/**
 * Versioned mod handshake and registry sync payload helpers.
 */
public final class ModProtocol {
    public static final int PROTOCOL_VERSION = 4;
    public static final int PROTOCOL_VERSION_LEGACY = 1;
    public static final int PROTOCOL_VERSION_ITEMS = 2;
    public static final int PROTOCOL_VERSION_EXPERIMENTAL = 5;
    private static final int FEATURE_BITS_INTRODUCED_IN = 2;

    public static final int FEATURE_CHUNK_ZSTD = 1 << 0;
    public static final int FEATURE_ITEM_STACK_V2 = 1 << 1;
    public static final int FEATURE_ITEM_COMPONENTS = 1 << 2;
    public static final int FEATURE_REGIONCORE_ITEMS = 1 << 3;
    public static final int FEATURE_ENTITY_WIRE_V2 = 1 << 4;
    public static final int FEATURE_ENTITY_DATA_V2 = 1 << 5;
    public static final int FEATURE_REGIONCORE_ENTITIES = 1 << 6;
    public static final int FEATURE_SKIN_PARTS_SYNC = 1 << 7;
    public static final int FEATURE_CLOUD_TIME_SYNC = 1 << 8;
    public static final int FEATURE_CONTAINER_INPUTS = 1 << 9;
    public static final int FEATURE_SPECTATOR_MODE = 1 << 10;
    public static final int FEATURE_BLOCK_MODEL_VISUALS = 1 << 11;
    public static final int FEATURE_DROP_ALL_ITEMS = 1 << 12;
    public static final int FEATURE_MODERN_TRAPDOOR_PLACEMENT = 1 << 13;
    /** Native block-state visuals use the client model system; ordinary entity/block packets remain the state transport. */
    public static final int FEATURE_BLOCK_MODEL_STATES = 1 << 14;
    /** Versioned public server-directory management over authenticated server-side HTTPS. */
    public static final int FEATURE_SERVER_DIRECTORY = 1 << 15;
    /** Requires canonical data-registry agreement before extension bootstrap completes. */
    public static final int FEATURE_REGISTRY_DATA_FINGERPRINT = 1 << 16;
    /** Server-provided rules GUI, including the first-join consent gate. */
    public static final int FEATURE_SERVER_RULES = 1 << 17;
    /** 26.3 title/action-bar and scoreboard/team state over legacy Packet250. */
    public static final int FEATURE_HUD_SCOREBOARD = 1 << 18;
    /** Lossless DataComponentPatch carrier inside the existing compressed item-NBT field. */
    public static final int FEATURE_ITEM_COMPONENT_ENVELOPE_V1 = 1 << 19;

    public static final String CHANNEL_HELLO = "MCOSE|MOD_HELLO";
    public static final String CHANNEL_HELLO_ACK = "MCOSE|MOD_HELLO_ACK";
    public static final String CHANNEL_REGISTRY_SYNC = "MCOSE|REG_SYNC";
    public static final String CHANNEL_REGISTRY_REQUEST = "MCOSE|REG_REQ";
    public static final String CHANNEL_SKIN_PARTS = "MCOSE|SKINPARTS";
    public static final String CHANNEL_CLOUD_TIME = "MCOSE|CLOUD_TIME";
    public static final String CHANNEL_SPECTATOR = "MCOSE|SPECTATE";
    /** Spectator-menu teleport request. The legacy player list exposes names rather than profile UUIDs. */
    public static final String CHANNEL_SPECTATOR_TELEPORT = "MCOSE|SPTP";
    public static final String CHANNEL_BLOCK_MODEL_VISUAL = "MCOSE|BLOCK_MODEL";

    private ModProtocol() {}

    public static byte[] createHelloAckPayload(int protocolVersion, int negotiatedFeatures) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeInt(protocolVersion);
            if (supportsFeatureBits(protocolVersion)) {
                out.writeInt(negotiatedFeatures);
            }
            out.flush();
            return baos.toByteArray();
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    public static int readHelloVersion(byte[] payload) {
        return readHelloInfo(payload).version;
    }

    public static HelloInfo readHelloInfo(byte[] payload) {
        if (payload == null || payload.length < 4) {
            return new HelloInfo(-1, 0);
        }

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            int version = in.readInt();
            int featureBits = 0;
            if (supportsFeatureBits(version) && in.available() >= 4) {
                featureBits = in.readInt();
            }
            in.close();
            return new HelloInfo(version, featureBits);
        } catch (Throwable ignored) {
            return new HelloInfo(-1, 0);
        }
    }

    public static boolean supportsFeatureBits(int version) {
        return version >= FEATURE_BITS_INTRODUCED_IN;
    }

    public static boolean isSupportedVersion(int version) {
        return version == PROTOCOL_VERSION
                || version == PROTOCOL_VERSION_LEGACY
                || version == PROTOCOL_VERSION_ITEMS
                || version == 3
                || version == PROTOCOL_VERSION_EXPERIMENTAL;
    }

    public static int readRegistryRequestVersion(byte[] payload) {
        RegistryRequestInfo request = readRegistryRequestInfo(payload);
        return request.valid ? request.version : -1;
    }

    public static byte[] createRegistryRequestPayload(int protocolVersion) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(4);
            DataOutputStream out = new DataOutputStream(baos);
            out.writeInt(protocolVersion);
            out.flush();
            return baos.toByteArray();
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    public static byte[] createRegistryRequestPayload(
            int protocolVersion,
            String synchronizedDataFingerprint) {
        try {
            if (!isSha256(synchronizedDataFingerprint)) {
                return new byte[0];
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream(70);
            DataOutputStream out = new DataOutputStream(baos);
            out.writeInt(protocolVersion);
            out.writeUTF(synchronizedDataFingerprint);
            out.flush();
            return baos.toByteArray();
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    /** Strictly decodes either the legacy four-byte request or the negotiated hash shape. */
    public static RegistryRequestInfo readRegistryRequestInfo(byte[] payload) {
        if (payload == null || payload.length < 4) {
            return new RegistryRequestInfo(-1, "", false);
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            int version = in.readInt();
            String fingerprint = in.available() == 0 ? "" : in.readUTF();
            boolean valid = in.available() == 0
                    && (fingerprint.length() == 0 || isSha256(fingerprint));
            in.close();
            return new RegistryRequestInfo(version, fingerprint, valid);
        } catch (Throwable ignored) {
            return new RegistryRequestInfo(-1, "", false);
        }
    }

    /**
     * Applies the negotiated compatibility contract before any registry state
     * is admitted: modern peers must match exactly, while old peers are safe
     * only against the immutable built-in data baseline.
     */
    public static boolean registryRequestMatches(
            RegistryRequestInfo request,
            int expectedProtocolVersion,
            boolean synchronizedDataFingerprintNegotiated,
            String localSynchronizedDataFingerprint) {
        if (request == null || !request.valid || request.version != expectedProtocolVersion
                || !isSha256(localSynchronizedDataFingerprint)) {
            return false;
        }
        if (synchronizedDataFingerprintNegotiated) {
            return request.hasSynchronizedDataFingerprint()
                    && localSynchronizedDataFingerprint.equals(
                            request.synchronizedDataFingerprint);
        }
        return !request.hasSynchronizedDataFingerprint()
                && RegistryDataFingerprint.isBuiltInSynchronizedData(
                        localSynchronizedDataFingerprint);
    }

    public static int resolveServerSupportedFeatures() {
        int features = FEATURE_ITEM_STACK_V2
                | FEATURE_ITEM_COMPONENTS
                | FEATURE_REGIONCORE_ITEMS
                | FEATURE_ENTITY_WIRE_V2
                | FEATURE_ENTITY_DATA_V2
                | FEATURE_REGIONCORE_ENTITIES
                | FEATURE_SKIN_PARTS_SYNC
                | FEATURE_CLOUD_TIME_SYNC
                | FEATURE_CONTAINER_INPUTS
                | FEATURE_SPECTATOR_MODE
                | FEATURE_BLOCK_MODEL_VISUALS
                | FEATURE_DROP_ALL_ITEMS
                | FEATURE_MODERN_TRAPDOOR_PLACEMENT
                | FEATURE_BLOCK_MODEL_STATES
                | FEATURE_SERVER_DIRECTORY
                | FEATURE_REGISTRY_DATA_FINGERPRINT
                | FEATURE_SERVER_RULES
                | FEATURE_HUD_SCOREBOARD
                | FEATURE_ITEM_COMPONENT_ENVELOPE_V1;
        if (net.minecraft.server.ZstdRuntime.isAvailable()) {
            features |= FEATURE_CHUNK_ZSTD;
        }
        return features;
    }

    public static boolean hasRequiredEntityFeatures(int featureBits) {
        int required = FEATURE_ENTITY_WIRE_V2 | FEATURE_ENTITY_DATA_V2;
        return (featureBits & required) == required;
    }

    public static boolean hasRequiredBlockModelVisuals(int featureBits) {
        int required = FEATURE_BLOCK_MODEL_VISUALS | FEATURE_BLOCK_MODEL_STATES;
        return (featureBits & required) == required;
    }

    public static boolean hasItemComponentEnvelope(int featureBits) {
        int required = FEATURE_ITEM_COMPONENTS | FEATURE_ITEM_COMPONENT_ENVELOPE_V1;
        return (featureBits & required) == required;
    }

    public static byte[] createPaintingVisualPayload(int entityId,
                                                      int x,
                                                      int y,
                                                      int z,
                                                      int direction,
                                                      String motive) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(32);
            DataOutputStream out = new DataOutputStream(baos);
            out.writeByte(1);
            out.writeByte(1);
            out.writeInt(entityId);
            PacketLimits.writeUtf(out, motive, net.minecraft.server.EnumArt.z, "painting motive");
            out.writeInt(x);
            out.writeInt(y);
            out.writeInt(z);
            out.writeByte(direction);
            out.flush();
            return baos.toByteArray();
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    public static byte[] createCloudTimePayload(long gameTime) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(8);
            DataOutputStream out = new DataOutputStream(baos);
            out.writeLong(gameTime);
            out.flush();
            return baos.toByteArray();
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    public static long readCloudTimePayload(byte[] payload) {
        if (payload == null || payload.length != 8) {
            return Long.MIN_VALUE;
        }

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            long gameTime = in.readLong();
            in.close();
            return gameTime;
        } catch (Throwable ignored) {
            return Long.MIN_VALUE;
        }
    }

    public static byte[] createSpectatorTargetPayload(int entityId) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(4);
            DataOutputStream out = new DataOutputStream(baos);
            out.writeInt(entityId);
            out.flush();
            return baos.toByteArray();
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    public static int readSpectatorTargetPayload(byte[] payload) {
        if (payload == null || payload.length != 4) {
            return Integer.MIN_VALUE;
        }

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            int entityId = in.readInt();
            in.close();
            return entityId;
        } catch (Throwable ignored) {
            return Integer.MIN_VALUE;
        }
    }

    public static byte[] createSpectatorTeleportPayload(String playerName) {
        if (!isValidLegacyPlayerName(playerName)) {
            return new byte[0];
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(18);
            DataOutputStream out = new DataOutputStream(baos);
            PacketLimits.writeUtf(out, playerName, 16, "spectator player name");
            out.flush();
            return baos.toByteArray();
        } catch (Throwable ignored) {
            return new byte[0];
        }
    }

    public static String readSpectatorTeleportPayload(byte[] payload) {
        if (payload == null || payload.length < 3 || payload.length > 18) {
            return null;
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            String playerName = PacketLimits.readUtf(in, 16, "spectator player name");
            boolean valid = in.available() == 0 && isValidLegacyPlayerName(playerName);
            in.close();
            return valid ? playerName : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isValidLegacyPlayerName(String playerName) {
        if (playerName == null || playerName.length() < 1 || playerName.length() > 16) {
            return false;
        }
        for (int i = 0; i < playerName.length(); ++i) {
            char ch = playerName.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '_') {
                return false;
            }
        }
        return true;
    }

    public static byte[] createRegistrySyncPayload(RegistrySyncSnapshot snapshot) {
        return createRegistrySyncPayload(snapshot, true);
    }

    public static byte[] createRegistrySyncPayload(
            RegistrySyncSnapshot snapshot,
            boolean includeSynchronizedDataFingerprint) {
        if (snapshot == null) {
            return new byte[0];
        }
        return includeSynchronizedDataFingerprint
                ? snapshot.toBytes()
                : snapshot.toLegacyBytes();
    }

    private static boolean isSha256(String value) {
        if (value == null || value.length() != 64) {
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

    public static byte[] createSkinPartsPayload(String username, int modelPartMask) {
		return createSkinPartsPayload(username, modelPartMask, false, false);
	}

	public static byte[] createSkinPartsPayload(String username, int modelPartMask, boolean leftHanded) {
		return createSkinPartsPayload(username, modelPartMask, leftHanded, true);
	}

	private static byte[] createSkinPartsPayload(String username, int modelPartMask, boolean leftHanded, boolean includeMainHand) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            PacketLimits.writeUtf(out, username, PacketLimits.MAX_USERNAME_CHARS, "skin-parts username");
            out.writeByte(modelPartMask & 0x7F);
			if(includeMainHand) {
				out.writeBoolean(leftHanded);
			}
            out.flush();
            return baos.toByteArray();
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    public static SkinPartsInfo readSkinPartsPayload(byte[] payload) {
        if (payload == null || payload.length == 0) {
			return new SkinPartsInfo("", 0x7F, false);
        }

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            String username = PacketLimits.readUtf(in, PacketLimits.MAX_USERNAME_CHARS, "skin-parts username");
            int modelPartMask = in.readByte() & 0x7F;
			boolean leftHanded = in.available() > 0 && in.readBoolean();
			if(in.available() != 0) {
				in.close();
				return new SkinPartsInfo("", 0x7F, false);
			}
            in.close();
			return new SkinPartsInfo(username, modelPartMask, leftHanded);
        } catch (Throwable ignored) {
			return new SkinPartsInfo("", 0x7F, false);
        }
    }

    public static final class HelloInfo {
        public final int version;
        public final int featureBits;

        public HelloInfo(int version, int featureBits) {
            this.version = version;
            this.featureBits = featureBits;
        }
    }

    public static final class RegistryRequestInfo {
        public final int version;
        public final String synchronizedDataFingerprint;
        public final boolean valid;

        public RegistryRequestInfo(
                int version,
                String synchronizedDataFingerprint,
                boolean valid) {
            this.version = version;
            this.synchronizedDataFingerprint = synchronizedDataFingerprint == null
                    ? "" : synchronizedDataFingerprint;
            this.valid = valid;
        }

        public boolean hasSynchronizedDataFingerprint() {
            return isSha256(this.synchronizedDataFingerprint);
        }
    }

    public static final class SkinPartsInfo {
        public final String username;
        public final int modelPartMask;
		public final boolean leftHanded;

        public SkinPartsInfo(String username, int modelPartMask) {
			this(username, modelPartMask, false);
		}

		public SkinPartsInfo(String username, int modelPartMask, boolean leftHanded) {
            this.username = username == null ? "" : username;
            this.modelPartMask = modelPartMask & 0x7F;
			this.leftHanded = leftHanded;
        }
    }
}
