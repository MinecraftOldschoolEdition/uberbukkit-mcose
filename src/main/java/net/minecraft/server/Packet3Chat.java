package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Packet3Chat extends Packet {
    public static final int MAX_CHAT_LENGTH = 256;

    public String message;

    public Packet3Chat() {
    }

    public Packet3Chat(String s) {
        if (s != null && s.length() > MAX_CHAT_LENGTH) {
            s = s.substring(0, MAX_CHAT_LENGTH);
        }
        this.message = s;
    }

    public void a(DataInputStream datainputstream) throws IOException { // CraftBukkit
        // uberbukkit
        if (this.pvn >= 11) {
            this.message = a(datainputstream, MAX_CHAT_LENGTH);
        } else {
            this.message = PacketLimits.readUtf(datainputstream, MAX_CHAT_LENGTH, "chat message");
        }
    }

    public void a(DataOutputStream dataoutputstream) throws IOException { // CraftBukkit
        // uberbukkit
        if (this.pvn >= 11) {
            a(this.message, dataoutputstream);
        } else {
            PacketLimits.writeUtf(dataoutputstream, this.message, MAX_CHAT_LENGTH, "chat message");
        }
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        return this.message.length();
    }
}
