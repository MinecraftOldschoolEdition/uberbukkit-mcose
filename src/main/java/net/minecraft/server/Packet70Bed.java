package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Packet70Bed extends Packet {

    /** MCOSE extension: reasons 32-39 carry the exact server terrain-type ID. */
    public static final int TERRAIN_TYPE_STATE_BASE = 32;
    public static final int TERRAIN_TYPE_STATE_MAX_ID = 7;
    public static final String[] a = new String[] { "tile.bed.notValid", null, null };
    public int b;

    public Packet70Bed() {
    }

    public Packet70Bed(int i) {
        this.b = i;
    }

    public static int terrainTypeStateReason(int terrainTypeId) {
        if (terrainTypeId < 0 || terrainTypeId > TERRAIN_TYPE_STATE_MAX_ID) {
            terrainTypeId = 0;
        }
        return TERRAIN_TYPE_STATE_BASE + terrainTypeId;
    }

    public void a(DataInputStream datainputstream) throws IOException {
        this.b = datainputstream.readByte();
    }

    public void a(DataOutputStream dataoutputstream) throws IOException {
        dataoutputstream.writeByte(this.b);
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        return 1;
    }
}
