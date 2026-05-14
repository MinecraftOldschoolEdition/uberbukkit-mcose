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
        this.channel = PacketLimits.readUtf(datainputstream, PacketLimits.MAX_CUSTOM_CHANNEL_CHARS, "custom payload channel");
        this.data = PacketLimits.readUnsignedShortByteArray(datainputstream, PacketLimits.MAX_CUSTOM_PAYLOAD_BYTES, "custom payload");
    }

    @Override
    public void a(DataOutputStream dataoutputstream) throws IOException {
        PacketLimits.writeUtf(dataoutputstream, this.channel, PacketLimits.MAX_CUSTOM_CHANNEL_CHARS, "custom payload channel");
        PacketLimits.writeUnsignedShortByteArray(dataoutputstream, this.data, PacketLimits.MAX_CUSTOM_PAYLOAD_BYTES, "custom payload");
    }

    @Override
    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    @Override
    public int a() {
        return 2 + (this.channel != null ? this.channel.length() * 2 : 0) + 2 + (this.data != null ? this.data.length : 0);
    }
}
