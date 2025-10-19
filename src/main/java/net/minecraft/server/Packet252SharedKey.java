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
        this.sharedSecret = b(datainputstream);
        this.verifyToken = b(datainputstream);
    }

    public void a(DataOutputStream dataoutputstream) throws IOException {
        a(this.sharedSecret, dataoutputstream);
        a(this.verifyToken, dataoutputstream);
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        return 2 + this.sharedSecret.length + 2 + this.verifyToken.length;
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


