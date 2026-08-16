package net.minecraft.server;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class NetworkTrustBoundarySecurityTest {

    @Test
    public void serverboundTabCompleteRejectsResponseEntriesBeforeReadingThem() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeUTF("/help");
        output.writeInt(1);
        output.writeUTF("attacker-controlled completion");
        output.close();

        Packet203TabComplete packet = new Packet203TabComplete();
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        try {
            packet.a(input);
            fail("Expected serverbound response entries to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("response entries"));
        }

        assertTrue("completion body must not be read", input.available() > 0);
    }

    @Test
    public void serverboundTabCompleteAcceptsRequestShape() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeUTF("/help");
        output.writeInt(0);
        output.close();

        Packet203TabComplete packet = new Packet203TabComplete();
        packet.a(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

        assertEquals("/help", packet.text);
        assertEquals(0, packet.completions.length);
    }

    @Test
    public void mapLockRequiresExactPayloadShape() {
        // Match the server bootstrap order so ItemBlock registrations exist
        // before the Item static initializer builds achievements/statistics.
        int ignoredBootstrapId = Block.STONE.id;
        Packet131 request = new Packet131((short) Item.MAP.id, 42, new byte[] {2});

        assertTrue(NetServerHandler.isValidMapLockRequest(request));

        request.c = new byte[] {2, 0};
        assertFalse(NetServerHandler.isValidMapLockRequest(request));
        request.c = new byte[] {2};

        request.a = (short) Item.STICK.id;
        assertFalse(NetServerHandler.isValidMapLockRequest(request));
        request.a = (short) Item.MAP.id;
        request.b = -1;
        assertFalse(NetServerHandler.isValidMapLockRequest(request));
    }

    @Test
    public void mapLockAuthorizationIsShortLivedAndOneShot() {
        NetServerHandler.MapLockAuthorization authorization =
                new NetServerHandler.MapLockAuthorization(10000L);

        authorization.grant(42, 1000L);
        assertTrue(authorization.consume(42, 11000L));
        assertFalse(authorization.consume(42, 11000L));

        authorization.grant(42, 1000L);
        assertFalse(authorization.consume(43, 1001L));
        assertFalse("a wrong request must consume the grant", authorization.consume(42, 1002L));

        authorization.grant(42, 1000L);
        assertFalse(authorization.consume(42, 11001L));
    }

    @Test
    public void signEditIsOwnedAndConsumedOnce() {
        TileEntitySign sign = new TileEntitySign();

        assertFalse(sign.finishEditing("Alice"));
        sign.beginEditing("Alice");
        assertFalse(sign.finishEditing("Bob"));
        assertTrue(sign.finishEditing("alice"));
        assertFalse(sign.finishEditing("Alice"));
    }

    @Test
    public void signEditRequiresNearbyFinitePlayerPosition() {
        assertTrue(NetServerHandler.isSignEditInRange(0.5D, 64.5D, 0.5D, 0, 64, 0));
        assertFalse(NetServerHandler.isSignEditInRange(9.0D, 64.5D, 0.5D, 0, 64, 0));
        assertFalse(NetServerHandler.isSignEditInRange(Double.NaN, 64.5D, 0.5D, 0, 64, 0));
    }

    @Test
    public void signLinesAreRevalidatedAfterPluginMutation() {
        assertEquals("hello", NetServerHandler.sanitizeSignLine("hello"));
        assertEquals("\u00A7aGreen", NetServerHandler.sanitizeSignLine("\u00A7aGreen"));
        assertEquals("!?", NetServerHandler.sanitizeSignLine("0123456789abcdef"));
        assertEquals("!?", NetServerHandler.sanitizeSignLine("bad\u0000line"));
        assertEquals("!?", NetServerHandler.sanitizeSignLine("\u00A7\u0000"));
        assertEquals("!?", NetServerHandler.sanitizeSignLine("\u00A7z"));
        assertEquals("!?", NetServerHandler.sanitizeSignLine("trailing\u00A7"));
        assertEquals("!?", NetServerHandler.sanitizeSignLine(null));
    }

    @Test
    public void legacyDropRequestRequiresOneRegisteredItemAndLegacyProtocol() {
        int stoneId = Block.STONE.id;
        Packet21PickupSpawn request = new Packet21PickupSpawn();
        request.h = stoneId;
        request.i = 1;

        assertTrue(NetServerHandler.isValidLegacyDropRequest(request, 6));
        assertFalse(NetServerHandler.isValidLegacyDropRequest(request, 14));

        request.i = -1;
        assertFalse(NetServerHandler.isValidLegacyDropRequest(request, 6));
        request.i = 1;
        request.h = Item.byId.length;
        assertFalse(NetServerHandler.isValidLegacyDropRequest(request, 6));
    }

    @Test
    public void dropAllDigActionRequiresNegotiatedSupport() {
        Packet14BlockDig request = new Packet14BlockDig();
        request.e = Packet14BlockDig.STATUS_DROP_ALL_ITEMS;

        assertTrue(NetServerHandler.isValidDropAllItemsRequest(request, true));
        assertFalse(NetServerHandler.isValidDropAllItemsRequest(request, false));

        request.e = Packet14BlockDig.STATUS_DROP_ITEM;
        assertFalse(NetServerHandler.isValidDropAllItemsRequest(request, true));
    }

    @Test
    public void hotbarIndexExcludesFirstMainInventorySlot() {
        assertTrue(NetServerHandler.isValidHotbarIndex(0));
        assertTrue(NetServerHandler.isValidHotbarIndex(8));
        assertFalse(NetServerHandler.isValidHotbarIndex(9));
        assertFalse(NetServerHandler.isValidHotbarIndex(-1));
    }

    @Test
    public void chatRoomActionsRequireNegotiationAndAreRateLimited() {
        Packet66ChatRoomAction action = new Packet66ChatRoomAction(
                Packet66ChatRoomAction.ACTION_REFRESH, "", "");
        assertFalse(NetServerHandler.isValidChatRoomAction(action, false));
        assertTrue(NetServerHandler.isValidChatRoomAction(action, true));

        action.action = 100;
        assertFalse(NetServerHandler.isValidChatRoomAction(action, true));

        NetServerHandler.FixedWindowRateLimiter limiter =
                new NetServerHandler.FixedWindowRateLimiter(2000L, 4);
        assertTrue(limiter.tryAcquire(1000L));
        assertTrue(limiter.tryAcquire(1001L));
        assertTrue(limiter.tryAcquire(1002L));
        assertTrue(limiter.tryAcquire(1003L));
        assertFalse(limiter.tryAcquire(1004L));
        assertTrue(limiter.tryAcquire(3000L));
        assertTrue("clock rollback should safely start a new window", limiter.tryAcquire(2999L));
    }

    @Test
    public void autocompleteLimiterDropsRequestsBeyondSharedTransportBudget() {
        NetServerHandler.FixedWindowRateLimiter sharedLimiter =
                new NetServerHandler.FixedWindowRateLimiter(2000L, 4);

        // The handler uses this same instance for both Packet203 and the MCOSE
        // autocomplete channel, so alternating transports still reaches five.
        assertTrue(sharedLimiter.tryAcquire(1000L));
        assertTrue(sharedLimiter.tryAcquire(1001L));
        assertTrue(sharedLimiter.tryAcquire(1002L));
        assertTrue(sharedLimiter.tryAcquire(1003L));
        assertFalse(sharedLimiter.tryAcquire(1004L));
    }

    @Test
    public void handshakeAndRegistryAmplifiersAreOneShotPerConnection() {
        NetServerHandler.OneShotGate hello = new NetServerHandler.OneShotGate();
        NetServerHandler.OneShotGate registry = new NetServerHandler.OneShotGate();

        assertTrue(hello.tryAcquire());
        assertFalse(hello.tryAcquire());
        assertTrue(registry.tryAcquire());
        assertFalse(registry.tryAcquire());
    }

    @Test
    public void unchangedSkinPartMasksDoNotBroadcast() {
        assertFalse(NetServerHandler.isSkinPartMaskChange(0x7F, 0xFF));
        assertFalse(NetServerHandler.isSkinPartMaskChange(0x01, 0x81));
        assertTrue(NetServerHandler.isSkinPartMaskChange(0x01, 0x02));
		assertFalse(NetServerHandler.isSkinCustomizationChange(0x7F, false, 0xFF, false));
		assertTrue(NetServerHandler.isSkinCustomizationChange(0x7F, false, 0xFF, true));
    }
}
