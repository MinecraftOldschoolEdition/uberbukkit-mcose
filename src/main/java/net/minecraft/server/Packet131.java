package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Packet131 extends Packet {

    // Extended format marker: high bit of itemId signals int mapId instead of short
    private static final int EXTENDED_FORMAT_FLAG = 0x8000;

    public short a; // itemId
    public int b;   // mapId - now int to support up to ~2 billion maps
    public byte[] c;

    public Packet131() {
        this.k = true;
    }

    // Legacy constructor for short mapId (backwards compatibility)
    public Packet131(short short1, short short2, byte[] abyte) {
        this.k = true;
        this.a = short1;
        this.b = short2 & 0xFFFF; // Convert to unsigned int
        this.c = abyte;
    }

    // New constructor for int mapId (extended format)
    public Packet131(short itemId, int mapId, byte[] data) {
        this.k = true;
        this.a = itemId;
        this.b = mapId;
        this.c = data;
    }

    public void a(DataInputStream datainputstream) throws IOException {
        short rawItemId = datainputstream.readShort();
        
        // Check if extended format flag is set
        if ((rawItemId & EXTENDED_FORMAT_FLAG) != 0) {
            // Extended format: itemId has high bit set, mapId is int
            this.a = (short)(rawItemId & ~EXTENDED_FORMAT_FLAG);
            this.b = datainputstream.readInt();
        } else {
            // Legacy format: both are shorts
            this.a = rawItemId;
            this.b = datainputstream.readShort() & 0xFFFF;
        }
        
        this.c = new byte[datainputstream.readByte() & 255];
        datainputstream.readFully(this.c);
    }

    public void a(DataOutputStream dataoutputstream) throws IOException {
        // Always write extended format from uberbukkit server
        // Set high bit on itemId to signal int mapId
        dataoutputstream.writeShort(this.a | EXTENDED_FORMAT_FLAG);
        dataoutputstream.writeInt(this.b);
        dataoutputstream.writeByte(this.c.length);
        dataoutputstream.write(this.c);
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        // Size: 2 (itemId with flag) + 4 (int mapId) + 1 (length) + data
        return 7 + this.c.length;
    }
}
