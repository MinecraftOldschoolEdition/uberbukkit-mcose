package net.minecraft.server.network;

import net.minecraft.server.registry.RegistrySyncSnapshot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

/**
 * Versioned mod handshake and registry sync payload helpers.
 */
public final class ModProtocol {
    public static final int PROTOCOL_VERSION = 1;
    public static final String CHANNEL_HELLO = "MCOSE|MOD_HELLO";
    public static final String CHANNEL_HELLO_ACK = "MCOSE|MOD_HELLO_ACK";
    public static final String CHANNEL_REGISTRY_SYNC = "MCOSE|REG_SYNC";
    public static final String CHANNEL_REGISTRY_REQUEST = "MCOSE|REG_REQ";

    private ModProtocol() {}

    public static byte[] createHelloAckPayload() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeInt(PROTOCOL_VERSION);
            out.flush();
            return baos.toByteArray();
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    public static int readHelloVersion(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return -1;
        }

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            int version = in.readInt();
            in.close();
            return version;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    public static int readRegistryRequestVersion(byte[] payload) {
        return readHelloVersion(payload);
    }

    public static byte[] createRegistrySyncPayload(RegistrySyncSnapshot snapshot) {
        if (snapshot == null) {
            return new byte[0];
        }
        return snapshot.toBytes();
    }
}
