package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class Packet64Voice extends Packet {
    public static final int MAX_PAYLOAD_SIZE = 8192;

    public int entityId;
    public float maxDistance;
    public String username;
    public byte[] audioData;

    public Packet64Voice() {
        this(0, 0.0F, "", new byte[0]);
    }

    public Packet64Voice(int entityId, byte[] audioData) {
        this(entityId, 0.0F, "", audioData);
    }

    public Packet64Voice(int entityId, float maxDistance, byte[] audioData) {
        this(entityId, maxDistance, "", audioData);
    }

    public Packet64Voice(int entityId, float maxDistance, String username, byte[] audioData) {
        this.entityId = entityId;
        this.maxDistance = maxDistance;
        this.username = username != null ? username : "";
        setAudioData(audioData);
    }

    private void setAudioData(byte[] data) {
        if (data == null || data.length == 0) {
            this.audioData = new byte[0];
            return;
        }
        this.audioData = Arrays.copyOf(data, data.length);
    }

    @Override
    public void a(DataInputStream datainputstream) throws IOException {
        this.entityId = datainputstream.readInt();
        this.maxDistance = datainputstream.readFloat();
        this.username = a(datainputstream, 16);
        int length = datainputstream.readUnsignedShort();
        if (length > MAX_PAYLOAD_SIZE) {
            throw new IOException("Voice payload too large (" + length + " > " + MAX_PAYLOAD_SIZE + ")");
        }

        if (length <= 0) {
            this.audioData = new byte[0];
            return;
        }

        this.audioData = new byte[length];
        datainputstream.readFully(this.audioData);
    }

    @Override
    public void a(DataOutputStream dataoutputstream) throws IOException {
        dataoutputstream.writeInt(this.entityId);
        dataoutputstream.writeFloat(this.maxDistance);
        a(this.username, dataoutputstream);
        int length = this.audioData != null ? this.audioData.length : 0;
        dataoutputstream.writeShort(length);
        if (length > 0) {
            dataoutputstream.write(this.audioData);
        }
    }

    @Override
    public void a(NetHandler nethandler) {
        nethandler.handle64Voice(this);
    }

    @Override
    public int a() {
        return 4 + 4 + 2 + (this.username != null ? this.username.length() * 2 + 2 : 2) + (this.audioData != null ? this.audioData.length : 0);
    }

    public Packet64Voice cloneForForwarding(int entityId, float maxDistance) {
        return cloneForForwarding(entityId, maxDistance, this.username);
    }

    public Packet64Voice cloneForForwarding(int entityId, float maxDistance, String username) {
        return new Packet64Voice(entityId, maxDistance, username, this.audioData != null ? Arrays.copyOf(this.audioData, this.audioData.length) : new byte[0]);
    }
}

