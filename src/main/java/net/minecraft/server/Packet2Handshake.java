package net.minecraft.server;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Packet2Handshake extends Packet {

    public String a;
    public boolean pvn11;

    public Packet2Handshake() {
    }

    public Packet2Handshake(String s, boolean pvn11) {
        this.a = s;
        this.pvn11 = pvn11;
    }

    public void a(DataInputStream datainputstream) throws IOException {
        // uberbukkit -- read the packet in a custom way to allow joining with vastly different PVNs

        int declaredLength = datainputstream.readUnsignedShort();
        if (declaredLength > PacketLimits.MAX_HANDSHAKE_CHARS) {
            throw new IOException("Handshake string is too long (" + declaredLength + " > " + PacketLimits.MAX_HANDSHAKE_CHARS + ")");
        }

        byte[] firstHalf = new byte[declaredLength];
        datainputstream.readFully(firstHalf);

        this.pvn11 = looksLikeWideHandshake(firstHalf);
        if (this.pvn11) {
            byte[] wide = new byte[declaredLength * 2];
            System.arraycopy(firstHalf, 0, wide, 0, firstHalf.length);
            datainputstream.readFully(wide, declaredLength, declaredLength);
            this.a = decodeWideString(wide);
        } else {
            this.a = decodeModifiedUtf(declaredLength, firstHalf);
        }
    }

    public void a(DataOutputStream dataoutputstream) throws IOException {
        // uberbukkit
        if (this.pvn11) {
            a(this.a, dataoutputstream);
        } else {
            dataoutputstream.writeUTF(this.a);
        }
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        return 4 + this.a.length() + 4;
    }

    private static boolean looksLikeWideHandshake(byte[] firstHalf) {
        if (firstHalf.length < 2) {
            return false;
        }

        int pairs = firstHalf.length / 2;
        int zeroHighBytes = 0;
        int printableLowBytes = 0;
        for (int i = 0; i < pairs; ++i) {
            int high = firstHalf[i * 2] & 255;
            int low = firstHalf[i * 2 + 1] & 255;
            if (high == 0) {
                ++zeroHighBytes;
            }
            if (low >= 32 && low < 127) {
                ++printableLowBytes;
            }
        }

        return zeroHighBytes == pairs && printableLowBytes > 0;
    }

    private static String decodeWideString(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length / 2);
        for (int i = 0; i + 1 < bytes.length; i += 2) {
            char value = (char) (((bytes[i] & 255) << 8) | (bytes[i + 1] & 255));
            if (value != 0) {
                builder.append(value);
            }
        }
        return builder.toString();
    }

    private static String decodeModifiedUtf(int length, byte[] bytes) throws IOException {
        byte[] encoded = new byte[length + 2];
        encoded[0] = (byte) (length >>> 8);
        encoded[1] = (byte) length;
        System.arraycopy(bytes, 0, encoded, 2, bytes.length);
        return new DataInputStream(new ByteArrayInputStream(encoded)).readUTF();
    }
}
