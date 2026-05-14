package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Packet for shared key exchange in modern authentication
 * Client -> Server only
 */
public class Packet252SharedKey extends Packet {
    public byte[] sharedSecret = new byte[0];
    public byte[] verifyToken = new byte[0];

    public Packet252SharedKey() {
    }

    public Packet252SharedKey(byte[] sharedSecret, byte[] verifyToken) {
        this.sharedSecret = sharedSecret;
        this.verifyToken = verifyToken;
    }

    public void a(DataInputStream datainputstream) throws IOException {
        this.sharedSecret = b(datainputstream, "shared secret");
        this.verifyToken = b(datainputstream, "verify token");
    }

    public void a(DataOutputStream dataoutputstream) throws IOException {
        a(this.sharedSecret, dataoutputstream, "shared secret");
        a(this.verifyToken, dataoutputstream, "verify token");
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        return 2 + this.sharedSecret.length + 2 + this.verifyToken.length;
    }

    private static byte[] b(DataInputStream datainputstream, String fieldName) throws IOException {
        return PacketLimits.readUnsignedShortByteArray(datainputstream, PacketLimits.MAX_AUTH_BYTE_ARRAY_BYTES, fieldName);
    }

    private static void a(byte[] array, DataOutputStream dataoutputstream, String fieldName) throws IOException {
        PacketLimits.writeUnsignedShortByteArray(dataoutputstream, array, PacketLimits.MAX_AUTH_BYTE_ARRAY_BYTES, fieldName);
    }
}

