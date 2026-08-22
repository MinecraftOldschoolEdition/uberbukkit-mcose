package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.network.ModProtocol;
import org.junit.BeforeClass;
import org.junit.Test;

public class RegistrySyncSnapshotDataFingerprintTest {
    @BeforeClass
    public static void initializeLegacyStaticsInProductionOrder() {
        assertTrue(Block.STONE != null);
        assertTrue(Item.STICK != null);
        RegistryBootstrap.initialize();
    }

    @Test
    public void builtInSemanticFingerprintMatchesTheClientContract() {
        String fingerprint = RegistryDataFingerprint.captureSynchronizedData();
        assertEquals(RegistryDataFingerprint.BUILT_IN_SYNCHRONIZED_DATA, fingerprint);
        assertTrue(RegistryDataFingerprint.isBuiltInSynchronizedData(fingerprint));
    }

    @Test
    public void versionTwoSnapshotRoundTripsAndMatchesLocalData() {
        RegistrySyncSnapshot local = RegistrySyncSnapshot.captureLocal();
        RegistrySyncSnapshot decoded = RegistrySyncSnapshot.fromBytes(local.toBytes());

        assertTrue(decoded.isValid());
        assertEquals(2, decoded.getFormatVersion());
        assertTrue(decoded.hasSynchronizedDataFingerprint());
        assertTrue(decoded.matchesLocalSynchronizedData());
        assertEquals(local.getSynchronizedDataFingerprint(),
                decoded.getSynchronizedDataFingerprint());
        assertEquals(local.getItemIds(), decoded.getItemIds());
        assertEquals(local.getBlockIds(), decoded.getBlockIds());
        assertEquals(local.getEntityIds(), decoded.getEntityIds());
    }

    @Test
    public void nonNegotiatedPeerReceivesStrictVersionOneShape() {
        RegistrySyncSnapshot local = RegistrySyncSnapshot.captureLocal();
        byte[] payload = ModProtocol.createRegistrySyncPayload(local, false);
        RegistrySyncSnapshot decoded = RegistrySyncSnapshot.fromBytes(payload);

        assertTrue(decoded.isValid());
        assertEquals(1, decoded.getFormatVersion());
        assertFalse(decoded.hasSynchronizedDataFingerprint());
        assertEquals(local.getItemIds(), decoded.getItemIds());
        assertEquals(local.getBlockIds(), decoded.getBlockIds());
        assertEquals(local.getEntityIds(), decoded.getEntityIds());
    }

    @Test
    public void malformedDuplicateOversizedAndTrailingSnapshotsAreInvalid() throws Exception {
        byte[] valid = RegistrySyncSnapshot.captureLocal().toBytes();
        assertFalse(RegistrySyncSnapshot.fromBytes(null).isValid());
        assertFalse(RegistrySyncSnapshot.fromBytes(new byte[0]).isValid());
        assertFalse(RegistrySyncSnapshot.fromBytes(
                Arrays.copyOf(valid, valid.length + 1)).isValid());
        assertFalse(RegistrySyncSnapshot.fromBytes(
                Arrays.copyOf(valid, valid.length - 1)).isValid());
        assertFalse(RegistrySyncSnapshot.fromBytes(duplicateKeyPayload()).isValid());
        assertFalse(RegistrySyncSnapshot.fromBytes(oversizedMapPayload()).isValid());
        assertFalse(RegistrySyncSnapshot.fromBytes(uppercaseHashPayload()).isValid());
    }

    @Test
    public void registryRequestNegotiationRejectsMismatchAndPinsLegacyBaseline() {
        String fingerprint = RegistryDataFingerprint.captureSynchronizedData();
        ModProtocol.RegistryRequestInfo negotiated = ModProtocol.readRegistryRequestInfo(
                ModProtocol.createRegistryRequestPayload(
                        ModProtocol.PROTOCOL_VERSION, fingerprint));
        assertTrue(negotiated.valid);
        assertTrue(negotiated.hasSynchronizedDataFingerprint());
        assertTrue(ModProtocol.registryRequestMatches(
                negotiated, ModProtocol.PROTOCOL_VERSION, true, fingerprint));

        String mismatch = fingerprint.charAt(0) == '0'
                ? "1" + fingerprint.substring(1)
                : "0" + fingerprint.substring(1);
        ModProtocol.RegistryRequestInfo wrongHash = ModProtocol.readRegistryRequestInfo(
                ModProtocol.createRegistryRequestPayload(
                        ModProtocol.PROTOCOL_VERSION, mismatch));
        assertFalse(ModProtocol.registryRequestMatches(
                wrongHash, ModProtocol.PROTOCOL_VERSION, true, fingerprint));

        ModProtocol.RegistryRequestInfo legacy = ModProtocol.readRegistryRequestInfo(
                ModProtocol.createRegistryRequestPayload(ModProtocol.PROTOCOL_VERSION));
        assertTrue(legacy.valid);
        assertFalse(legacy.hasSynchronizedDataFingerprint());
        assertTrue(ModProtocol.registryRequestMatches(
                legacy, ModProtocol.PROTOCOL_VERSION, false, fingerprint));
        assertFalse(ModProtocol.registryRequestMatches(
                legacy, ModProtocol.PROTOCOL_VERSION, true, fingerprint));
        assertFalse(ModProtocol.registryRequestMatches(
                negotiated, ModProtocol.PROTOCOL_VERSION, false, fingerprint));
        assertFalse(ModProtocol.registryRequestMatches(
                legacy, ModProtocol.PROTOCOL_VERSION, false, mismatch));
    }

    @Test
    public void registryRequestParserRejectsInvalidHashAndTrailingBytes() {
        String fingerprint = RegistryDataFingerprint.captureSynchronizedData();
        byte[] valid = ModProtocol.createRegistryRequestPayload(
                ModProtocol.PROTOCOL_VERSION, fingerprint);
        assertFalse(ModProtocol.readRegistryRequestInfo(
                Arrays.copyOf(valid, valid.length + 1)).valid);
        assertEquals(-1, ModProtocol.readRegistryRequestVersion(
                Arrays.copyOf(valid, valid.length + 1)));
        assertEquals(0, ModProtocol.createRegistryRequestPayload(
                ModProtocol.PROTOCOL_VERSION, fingerprint.toUpperCase()).length);
        assertTrue((ModProtocol.resolveServerSupportedFeatures()
                & ModProtocol.FEATURE_REGISTRY_DATA_FINGERPRINT) != 0);
    }

    private static byte[] duplicateKeyPayload() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(1);
        out.writeInt(2);
        out.writeUTF("minecraft:stone");
        out.writeInt(1);
        out.writeUTF("minecraft:stone");
        out.writeInt(2);
        out.writeInt(0);
        out.writeInt(0);
        out.flush();
        return bytes.toByteArray();
    }

    private static byte[] oversizedMapPayload() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(1);
        out.writeInt(4097);
        out.flush();
        return bytes.toByteArray();
    }

    private static byte[] uppercaseHashPayload() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(2);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);
        char[] hash = new char[64];
        Arrays.fill(hash, 'A');
        out.writeUTF(new String(hash));
        out.flush();
        return bytes.toByteArray();
    }
}
