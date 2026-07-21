package net.minecraft.server;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FriendsVerificationSecurityTest {

    @Test
    public void acceptsOnlyCanonicalRawOrDashedUuids() {
        String raw = "123e4567e89b12d3a456426614174000";
        assertEquals(raw, FriendsVerificationHandler.normalizeUuid(raw.toUpperCase()));
        assertEquals(raw, FriendsVerificationHandler.normalizeUuid(
                "123e4567-e89b-12d3-a456-426614174000"));

        assertNull(FriendsVerificationHandler.normalizeUuid(""));
        assertNull(FriendsVerificationHandler.normalizeUuid("not-a-uuid"));
        assertNull(FriendsVerificationHandler.normalizeUuid(
                "123e4567e-89b-12d3-a456-426614174000"));
        assertNull(FriendsVerificationHandler.normalizeUuid(
                "123e4567-e89b-12d3-a456-42661417400z"));
    }

    @Test
    public void acceptsOnlyCanonicalEd25519ProofSizes() {
        String signature = Base64.getEncoder().encodeToString(new byte[64]);
        String publicKey = Base64.getEncoder().encodeToString(new byte[32]);

        assertEquals(88, signature.length());
        assertEquals(44, publicKey.length());
        assertTrue(FriendsVerificationHandler.isValidClaimProof(signature, publicKey));
        assertFalse(FriendsVerificationHandler.isValidClaimProof(
                signature.substring(0, signature.length() - 1), publicKey));
        assertFalse(FriendsVerificationHandler.isValidClaimProof(signature, publicKey + "A"));
        assertFalse(FriendsVerificationHandler.isValidClaimProof(
                repeat('!', PacketLimits.MAX_SIGNATURE_CHARS), publicKey));
    }

    @Test
    public void packetStringBoundsMatchEd25519WireShapes() throws Exception {
        assertEquals(36, PacketLimits.MAX_UUID_CHARS);
        assertEquals(88, PacketLimits.MAX_SIGNATURE_CHARS);
        assertEquals(44, PacketLimits.MAX_PUBLIC_KEY_CHARS);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeUTF(repeat('A', PacketLimits.MAX_SIGNATURE_CHARS + 1));
        output.close();

        try {
            PacketLimits.readUtf(
                    new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),
                    PacketLimits.MAX_SIGNATURE_CHARS,
                    "friend signature");
            fail("Expected oversized friend proof to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("too long"));
        }
    }

    @Test
    public void claimCapAllowsReplacementButNotUnboundedGrowth() {
        Map<String, Object> claims = new HashMap<String, Object>();
        for (int i = 0; i < FriendsVerificationHandler.MAX_CLAIMS_PER_PLAYER; ++i) {
            claims.put(String.format("%032x", i), Boolean.TRUE);
        }

        String existing = String.format("%032x", 0);
        String additional = String.format("%032x", FriendsVerificationHandler.MAX_CLAIMS_PER_PLAYER);
        assertTrue(FriendsVerificationHandler.canStoreClaim(claims, existing));
        assertFalse(FriendsVerificationHandler.canStoreClaim(claims, additional));
    }

    @Test
    public void staleAndImplausiblyFutureClaimsExpire() {
        long now = 1_000_000_000L;
        assertFalse(FriendsVerificationHandler.isClaimExpired(now, now));
        assertFalse(FriendsVerificationHandler.isClaimExpired(
                now - FriendsVerificationHandler.CLAIM_TTL_MS, now));
        assertTrue(FriendsVerificationHandler.isClaimExpired(
                now - FriendsVerificationHandler.CLAIM_TTL_MS - 1L, now));
        assertTrue(FriendsVerificationHandler.isClaimExpired(0L, now));
        assertTrue(FriendsVerificationHandler.isClaimExpired(now + 6L * 60L * 1000L, now));
    }

    @Test
    public void onlyFriendRequestChannelsEnterRateLimitedHandler() {
        assertTrue(FriendsVerificationHandler.isClientRequestChannel(
                FriendsVerificationHandler.CHANNEL_QUERY));
        assertTrue(FriendsVerificationHandler.isClientRequestChannel(
                FriendsVerificationHandler.CHANNEL_VERIFY));
        assertTrue(FriendsVerificationHandler.isClientRequestChannel(
                FriendsVerificationHandler.CHANNEL_CLAIM));
        assertTrue(FriendsVerificationHandler.isClientRequestChannel(
                FriendsVerificationHandler.CHANNEL_CHECK));
        assertFalse(FriendsVerificationHandler.isClientRequestChannel(
                FriendsVerificationHandler.CHANNEL_CONFIRM));
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; ++i) {
            result.append(value);
        }
        return result.toString();
    }
}
