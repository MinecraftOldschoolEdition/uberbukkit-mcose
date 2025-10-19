package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Packet255KickDisconnect extends Packet {

    public String a;

    public Packet255KickDisconnect() {
    }

    public Packet255KickDisconnect(String s) {
        this.a = s;
    }

    public void a(DataInputStream datainputstream) throws IOException {
        // Match Poseidon/vanilla b1.7.3: always use Packet.a string encoding
        this.a = a(datainputstream, 100);
    }

    public void a(DataOutputStream dataoutputstream) throws IOException {
        // Match Poseidon/vanilla b1.7.3: always use Packet.a string encoding
        a(this.a, dataoutputstream);
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        return this.a.length();
    }
}
