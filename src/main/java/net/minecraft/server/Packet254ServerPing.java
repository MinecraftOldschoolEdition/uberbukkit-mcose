package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Legacy server list ping (0xFE)
 */
public class Packet254ServerPing extends Packet {

    // Whether the client sent the extended ping (0xFE 0x01)
    public boolean extended;

    public void a(DataInputStream datainputstream) throws IOException {
        // Poseidon approach: check if an extra byte is immediately available and read it
        try {
            if (datainputstream.available() > 0) {
                int b = datainputstream.read();
                this.extended = (b == 1);
            }
        } catch (Throwable ignored) {}
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


