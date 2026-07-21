package net.minecraft.server;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
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
