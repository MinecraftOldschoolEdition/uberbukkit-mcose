package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Custom payload packet for plugin channel communication.
 * Used for custom data like Herobrine fog control.
 */
public class Packet250CustomPayload extends Packet {

    public String channel;
    public byte[] data;

    public Packet250CustomPayload() {}

    public Packet250CustomPayload(String channel, byte[] data) {
        this.channel = channel;
        this.data = data;
    }

    @Override
    public void a(DataInputStream datainputstream) throws IOException {
        this.channel = datainputstream.readUTF();
        int length = datainputstream.readShort();
        if (length > 0 && length < 32767) {
            this.data = new byte[length];
            datainputstream.readFully(this.data);
        }
    }

    @Override
    public void a(DataOutputStream dataoutputstream) throws IOException {
        dataoutputstream.writeUTF(this.channel);
        if (this.data != null) {
            dataoutputstream.writeShort(this.data.length);
            dataoutputstream.write(this.data);
        } else {
            dataoutputstream.writeShort(0);
        }
    }

    @Override
    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    @Override
    public int a() {
        return 2 + this.channel.length() * 2 + 2 + (this.data != null ? this.data.length : 0);
    }
}

