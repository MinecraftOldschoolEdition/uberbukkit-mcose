package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.PublicKey;

/**
 * Encryption request packet for modern authentication
 * Server -> Client only
 */
public class Packet253EncryptionRequest extends Packet {
    public String serverId = "";
    public byte[] publicKey = new byte[0];
    public byte[] verifyToken = new byte[0];

    public Packet253EncryptionRequest() {
    }

    public Packet253EncryptionRequest(String serverId, PublicKey publicKey, byte[] verifyToken) {
        this.serverId = serverId;
        this.publicKey = publicKey.getEncoded();
        this.verifyToken = verifyToken;
    }

    public void a(DataInputStream datainputstream) throws IOException {
        this.serverId = a(datainputstream, 20);
        this.publicKey = b(datainputstream);
        this.verifyToken = b(datainputstream);
    }

    public void a(DataOutputStream dataoutputstream) throws IOException {
        a(this.serverId, dataoutputstream);
        a(this.publicKey, dataoutputstream);
        a(this.verifyToken, dataoutputstream);
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        return 2 + this.serverId.length() * 2 + 2 + this.publicKey.length + 2 + this.verifyToken.length;
    }

    private static byte[] b(DataInputStream datainputstream) throws IOException {
        short length = datainputstream.readShort();
        if (length < 0) {
            throw new IOException("Invalid array length");
        }
        byte[] array = new byte[length];
        datainputstream.readFully(array);
        return array;
    }

    private static void a(byte[] array, DataOutputStream dataoutputstream) throws IOException {
        dataoutputstream.writeShort(array.length);
        dataoutputstream.write(array);
    }
}


