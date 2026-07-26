package net.minecraft.server;

import com.legacyminecraft.poseidon.util.SessionAPI;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.PublicKey;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LoginSecurityTest {

	@Test
    public void aesCfb8TransportMatchesMinecraftWireVector() throws Exception {
		byte[] keyBytes = new byte[] {
			0, 1, 2, 3, 4, 5, 6, 7,
			8, 9, 10, 11, 12, 13, 14, 15
		};
		SecretKey key = new SecretKeySpec(keyBytes, "AES");
		byte[] plaintext = new byte[] {1, 0, 14, 0, 4, 116, 0, 101, 0, 115, 0, 116};

		Cipher encrypt = NetworkManager.createStreamCipher(Cipher.ENCRYPT_MODE, key);
		Cipher decrypt = NetworkManager.createStreamCipher(Cipher.DECRYPT_MODE, key);
		byte[] ciphertext = encrypt.doFinal(plaintext);
		assertArrayEquals(new byte[] {
			0x0b, 0x54, 0x46, (byte)0x8f, 0x72, (byte)0xba,
			(byte)0xf1, 0x60, 0x3a, (byte)0xa1, 0x3f, (byte)0xde
		}, ciphertext);
		assertArrayEquals(plaintext, decrypt.doFinal(ciphertext));
	}

    @Test
    public void rejectsPlayerDataPathCharactersBeforeConfigurableValidation() {
        assertNotNull(NetLoginHandler.validateHandshakeUsername("../operator"));
        assertNotNull(NetLoginHandler.validateHandshakeUsername("..\\operator"));
        assertNotNull(NetLoginHandler.validateHandshakeUsername("C:operator"));
        assertNotNull(NetLoginHandler.validateHandshakeUsername("op\u0000name"));
    }

    @Test
    public void serverIdMatchesSignedMinecraftSha1Format() throws Exception {
        byte[] keyBytes = new byte[] {
                0, 1, 2, 3, 4, 5, 6, 7,
                8, 9, 10, 11, 12, 13, 14, 15
        };
        final byte[] publicKeyBytes = new byte[] {
                16, 17, 18, 19, 20, 21, 22, 23,
                24, 25, 26, 27, 28, 29, 30, 31,
                32, 33, 34, 35, 36, 37, 38, 39,
                40, 41, 42, 43, 44, 45, 46, 47
        };
        PublicKey publicKey = new PublicKey() {
            public String getAlgorithm() {
                return "RSA";
            }

            public String getFormat() {
                return "X.509";
            }

            public byte[] getEncoded() {
                return publicKeyBytes.clone();
            }
        };

        assertEquals(
                "-2080dc4e9f18a46451a15e19d4bc5a5cb5d9fed9",
                com.legacyminecraft.poseidon.util.CryptoHelper.generateServerId(
                        "",
                        publicKey,
                        new SecretKeySpec(keyBytes, "AES")));
    }

    @Test
    public void rejectsUnexpectedPacketBeforeParsingItsBody() throws Exception {
        byte[] packet = new byte[] {(byte) 203, 0, 8, 'a', 0, 'b', 0, 'c', 0, 'd'};
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(packet));

        try {
            Packet.a(input, true, 0, true);
            fail("Expected login-phase packet rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("not allowed before login"));
        }

        assertTrue("packet body should not have been parsed", input.available() == packet.length - 1);
    }

    @Test
    public void acceptsOnlyMatchingNamedProfileWithValidUuid() {
        SessionAPI.ModernSessionResponse response = new SessionAPI.ModernSessionResponse(
                200,
                "PlayerName",
                "853c80ef3c3749fdaa49938b674adae6",
                "");

        assertEquals(
                UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6"),
                ThreadLoginVerifier.validatedProfileUuid("playername", response));
        assertNull(ThreadLoginVerifier.validatedProfileUuid("SomeoneElse", response));
        assertNull(ThreadLoginVerifier.validatedProfileUuid(
                "PlayerName",
                new SessionAPI.ModernSessionResponse(200, "PlayerName", "bad-uuid", "")));
    }

    @Test
    public void definitiveNoContentDoesNotDisableIpBinding() {
        assertFalse(ThreadLoginVerifier.shouldTryNoIpFallback(
                new SessionAPI.ModernSessionResponse(204, "", "", "")));
        assertTrue(ThreadLoginVerifier.shouldTryNoIpFallback(
                new SessionAPI.ModernSessionResponse(503, "", "", "")));
    }

    @Test
    public void clientIpIsOptionalUnlessProxyProtectionIsEnabled() {
        assertNull(ThreadLoginVerifier.sessionLookupIp("203.0.113.7", false));
        assertEquals("203.0.113.7", ThreadLoginVerifier.sessionLookupIp("203.0.113.7", true));
        assertEquals("127.0.0.1", ThreadLoginVerifier.sessionLookupIp("127.0.0.1", true));
    }

    @Test
    public void reportsAuthenticationOutageSeparatelyFromInvalidSession() {
        assertEquals(
                "Authentication servers are unavailable. Please try again later.",
                ThreadLoginVerifier.sessionFailureMessage(true, false));
        assertEquals(
                "Failed to verify username!",
                ThreadLoginVerifier.sessionFailureMessage(false, true));
        assertEquals(
                "Failed to verify username!",
                ThreadLoginVerifier.sessionFailureMessage(true, true));
    }

    @Test
    public void reevaluatesLoginPhaseAfterBlockingForPacketId() throws Exception {
        final AtomicBoolean loginPhase = new AtomicBoolean(true);
        InputStream transitioningInput = new ByteArrayInputStream(new byte[] {10, 1}) {
            @Override
            public synchronized int read() {
                int value = super.read();
                if (value == 10) {
                    loginPhase.set(false);
                }
                return value;
            }
        };

        Packet packet = Packet.a(
                new DataInputStream(transitioningInput),
                true,
                14,
                new Packet.LoginPhaseState() {
                    @Override
                    public boolean isLoginPhase() {
                        return loginPhase.get();
                    }
                });

        assertTrue("first gameplay packet should survive a completed login transition", packet instanceof Packet10Flying);
    }
}
