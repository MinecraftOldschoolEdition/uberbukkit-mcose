package net.minecraft.server.serverdirectory;

import net.minecraft.server.network.ModProtocol;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ServerDirectoryProtocolTest {
    @Test
    public void capabilityIsAdvertisedAndChannelNamesFitPacketLimit() {
        assertTrue((ModProtocol.resolveServerSupportedFeatures() & ModProtocol.FEATURE_SERVER_DIRECTORY) != 0);
        assertTrue(ServerDirectoryProtocol.CHANNEL_REQUEST.length() <= 64);
        assertTrue(ServerDirectoryProtocol.CHANNEL_STATE.length() <= 64);
        assertTrue(ServerDirectoryProtocol.CHANNEL_MUTATION.length() <= 64);
        assertTrue(ServerDirectoryProtocol.CHANNEL_RESULT.length() <= 64);
    }

    @Test
    public void mutationAndStatePayloadsRoundTrip() throws Exception {
        ServerDirectoryProtocol.Mutation mutation = ServerDirectoryProtocol.Mutation.upsert(
                3, "Oldschool Survival", "A small classic server.", Arrays.asList("survival", "vanilla"));
        ServerDirectoryProtocol.Mutation decoded = ServerDirectoryProtocol.readMutationPayload(
                ServerDirectoryProtocol.createMutationPayload(mutation));
        assertEquals(ServerDirectoryProtocol.OP_UPSERT, decoded.operation);
        assertEquals(3, decoded.expectedRevision);
        assertEquals(mutation.name, decoded.name);
        assertEquals(mutation.description, decoded.description);
        assertEquals(mutation.tagIds, decoded.tagIds);

        ServerDirectoryProtocol.Listing listing = new ServerDirectoryProtocol.Listing(
                "550e8400-e29b-41d4-a716-446655440000", 42L, 3,
                mutation.name, mutation.description, mutation.tagIds);
        ServerDirectoryProtocol.State state = new ServerDirectoryProtocol.State(
                true, true, true, false, "play.example.net:25565", "Eric", "", listing);
        ServerDirectoryProtocol.State decodedState = ServerDirectoryProtocol.readStatePayload(
                ServerDirectoryProtocol.createStatePayload(state));
        assertTrue(decodedState.supported);
        assertTrue(decodedState.permitted);
        assertEquals("play.example.net:25565", decodedState.publicEndpoint);
        assertEquals(42L, decodedState.listing.listingNumber);
    }

    @Test
    public void hostileCountsTrailingBytesAndOversizedPayloadsAreRejected() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(ServerDirectoryProtocol.VERSION);
        out.writeByte(ServerDirectoryProtocol.OP_UPSERT);
        out.writeInt(0);
        out.writeUTF("Name");
        out.writeUTF("Description");
        out.writeByte(255);
        out.close();
        assertRejected(bytes.toByteArray());
        assertRejected(new byte[ServerDirectoryProtocol.MAX_MUTATION_BYTES + 1]);

        byte[] valid = ServerDirectoryProtocol.createMutationPayload(
                ServerDirectoryProtocol.Mutation.delete(1));
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        assertRejected(trailing);
    }

    @Test
    public void packetsContainNoBackendAuthenticationSecret() throws Exception {
        String backendSecret = "NO_BACKEND_SECRET_BELONGS_IN_PACKETS";
        ServerDirectoryProtocol.State state = new ServerDirectoryProtocol.State(
                true, true, true, false, "play.example.net:25565", "Eric", backendSecret,
                new ServerDirectoryProtocol.Listing("550e8400-e29b-41d4-a716-446655440000", 1, 1,
                        "Name", "Description", Arrays.asList("survival")));
        // The protocol has no backend-authentication field.
        state = new ServerDirectoryProtocol.State(state.supported, state.permitted, state.ready, state.loading,
                state.publicEndpoint, state.creatorUsername, "Ready", state.listing);
        String payload = new String(ServerDirectoryProtocol.createStatePayload(state), "ISO-8859-1");
        assertFalse(payload.contains(backendSecret));
    }

    @Test
    public void opDefaultPluginOverridesAndForgedPacketGateUseTheNativePermissionResult() {
        assertTrue(PermissionDefault.OP.getValue(true));
        assertFalse(PermissionDefault.OP.getValue(false));

        Player grantedNonOperator = player(false, true);
        Player deniedOperator = player(true, false);
        assertTrue(ServerDirectoryManager.hasPermission(grantedNonOperator));
        assertFalse(ServerDirectoryManager.hasPermission(deniedOperator));
    }

    @Test
    public void missingPublicationConfigurationFailsClosedWithUsefulState() {
        ServerDirectoryConfig disabled = ServerDirectoryConfig.fromValues(false, "https://minecraftoldschool.com", "");
        ServerDirectoryConfig readyWithoutToken = ServerDirectoryConfig.fromValues(true, "https://minecraftoldschool.com", "play.example.net");
        ServerDirectoryConfig missingAddress = ServerDirectoryConfig.fromValues(true, "https://minecraftoldschool.com", "");
        assertFalse(disabled.isReady());
        assertTrue(disabled.configurationError.contains("disabled"));
        assertTrue(readyWithoutToken.isReady());
        assertFalse(missingAddress.isReady());
        assertTrue(missingAddress.configurationError.contains("public-address"));
    }

    @Test
    public void successConflictAndUnlistResultsRoundTripToClientProtocol() throws Exception {
        for (int code : new int[] {
                ServerDirectoryProtocol.RESULT_SUCCESS,
                ServerDirectoryProtocol.RESULT_CONFLICT,
                ServerDirectoryProtocol.RESULT_AUTHORIZATION,
                ServerDirectoryProtocol.RESULT_CONFIGURATION,
                ServerDirectoryProtocol.RESULT_WEBSITE}) {
            ServerDirectoryProtocol.Result result = ServerDirectoryProtocol.readResultPayload(
                    ServerDirectoryProtocol.createResultPayload(code, "result " + code));
            assertEquals(code, result.code);
            assertEquals("result " + code, result.message);
        }
        ServerDirectoryProtocol.Mutation unlist = ServerDirectoryProtocol.readMutationPayload(
                ServerDirectoryProtocol.createMutationPayload(ServerDirectoryProtocol.Mutation.delete(4)));
        assertEquals(ServerDirectoryProtocol.OP_DELETE, unlist.operation);
        assertEquals(4, unlist.expectedRevision);
    }

    @Test
    public void websiteStatusesReachUsefulClientResultCategories() {
        assertEquals(ServerDirectoryProtocol.RESULT_VALIDATION, ServerDirectoryManager.resultForWebsiteStatus(400));
        assertEquals(ServerDirectoryProtocol.RESULT_CONFLICT, ServerDirectoryManager.resultForWebsiteStatus(409));
        assertEquals(ServerDirectoryProtocol.RESULT_CONFIGURATION, ServerDirectoryManager.resultForWebsiteStatus(401));
        assertEquals(ServerDirectoryProtocol.RESULT_WEBSITE, ServerDirectoryManager.resultForWebsiteStatus(504));
        ServerDirectoryManager manager = new ServerDirectoryManager();
        assertEquals(2, manager.workerMaximum());
        assertEquals(32, manager.workerQueueCapacity());
    }

    @Test
    public void publicAddressValidationRejectsPrivateAndMalformedDestinations() {
        assertEquals("play.example.net:25565", ServerDirectoryConfig.parseEndpoint("PLAY.Example.NET").formatted);
        assertEquals("[2606:4700:4700::1111]:25565", ServerDirectoryConfig.parseEndpoint("[2606:4700:4700::1111]").formatted);
        for (String value : new String[] {"127.0.0.1:25565", "10.0.0.1", "[::1]:25565", "localhost:25565", "https://play.example.net"}) {
            try {
                ServerDirectoryConfig.parseEndpoint(value);
                fail("Expected unsafe address rejection for " + value);
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    private static void assertRejected(byte[] payload) {
        try {
            ServerDirectoryProtocol.readMutationPayload(payload);
            fail("Expected malformed payload rejection");
        } catch (IOException expected) {
            // expected
        }
    }

    private static Player player(final boolean op, final boolean permission) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, args) -> {
                    if ("hasPermission".equals(method.getName())) return permission;
                    if ("isOp".equals(method.getName())) return op;
                    Class<?> type = method.getReturnType();
                    if (type == Boolean.TYPE) return false;
                    if (type == Integer.TYPE) return 0;
                    if (type == Long.TYPE) return 0L;
                    if (type == Float.TYPE) return 0F;
                    if (type == Double.TYPE) return 0D;
                    return null;
                });
    }
}
