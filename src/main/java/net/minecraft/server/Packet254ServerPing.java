package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Legacy server list ping (0xFE)
 */
public class Packet254ServerPing extends Packet {

    private static final String PING_HOST_CHANNEL = "MC|PingHost";

    // Whether the client sent the extended ping (0xFE 0x01).
    public boolean extended;
    public boolean pingHost;
    public boolean valid = true;
    public int protocolVersion = -1;
    public String host;
    public int port = -1;

    public void a(DataInputStream datainputstream) throws IOException {
        // A bare 0xFE has no body and must remain valid for vanilla Beta clients.
        if (datainputstream.available() <= 0) {
            return;
        }

        if (datainputstream.readUnsignedByte() != 1) {
            this.valid = false;
            return;
        }
        this.extended = true;

        // 1.4-1.5 use only 0xFE 0x01. The 1.6/1.22 legacy fallback adds
        // a complete MC|PingHost custom payload after it.
        if (datainputstream.available() <= 0) {
            return;
        }
        if (datainputstream.readUnsignedByte() != 250) {
            this.valid = false;
            return;
        }

        String channel = Packet.a(datainputstream, 32);
        if (!PING_HOST_CHANNEL.equals(channel)) {
            this.valid = false;
            return;
        }

        int payloadSize = datainputstream.readUnsignedShort();
        if (payloadSize < 7 || payloadSize > 1024) {
            this.valid = false;
            return;
        }
        byte[] payload = new byte[payloadSize];
        datainputstream.readFully(payload);

        DataInputStream payloadInput = new DataInputStream(new java.io.ByteArrayInputStream(payload));
        this.protocolVersion = payloadInput.readUnsignedByte();
        this.host = Packet.a(payloadInput, 255);
        this.port = payloadInput.readInt();
        this.pingHost = this.protocolVersion >= 73
                && this.host.length() > 0
                && this.port >= 0
                && this.port <= 65535
                && payloadInput.available() == 0;
        this.valid = this.pingHost;
    }

    public void a(DataOutputStream dataoutputstream) throws IOException {
        // No payload
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        return 0;
    }
}

