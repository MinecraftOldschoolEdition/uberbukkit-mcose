package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Packet19EntityAction extends Packet {

    public static final int START_SPRINTING = 6;
    public static final int STOP_SPRINTING = 7;
    public static final int RELEASE_CHARGED_BOW = 8;

    public int a;
    public int animation;

    public Packet19EntityAction() {
    }

    public void a(DataInputStream datainputstream) throws IOException {
        this.a = datainputstream.readInt();
        this.animation = datainputstream.readByte();
    }

    public void a(DataOutputStream dataoutputstream) throws IOException {
        dataoutputstream.writeInt(this.a);
        dataoutputstream.writeByte(this.animation);
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        return 5;
    }
}
