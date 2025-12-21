package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Packet130UpdateSign extends Packet {

    public int x;
    public int y;
    public int z;
    public String[] lines;
    public int color = 0x000000; // MCOSE: text color (default black)

    public Packet130UpdateSign() {
        this.k = true;
    }

    public Packet130UpdateSign(int i, int j, int k, String[] astring) {
        this.k = true;
        this.x = i;
        this.y = j;
        this.z = k;
        this.lines = astring;
    }

    public void a(DataInputStream datainputstream) throws IOException {
        this.x = datainputstream.readInt();
        this.y = datainputstream.readShort();
        this.z = datainputstream.readInt();
        this.lines = new String[4];

        for (int i = 0; i < 4; ++i) {
            // uberbukkit
            if (this.pvn >= 11) {
                this.lines[i] = a(datainputstream, 15);
            } else {
                this.lines[i] = datainputstream.readUTF();
            }
        }
        
        // MCOSE: Sign color is NOT sent over Packet130UpdateSign
        // Color is synced separately via MC|SignDye custom payload
    }

    public void a(DataOutputStream dataoutputstream) throws IOException {
        dataoutputstream.writeInt(this.x);
        dataoutputstream.writeShort(this.y);
        dataoutputstream.writeInt(this.z);

        for (int i = 0; i < 4; ++i) {
            // uberbukkit
            if (this.pvn >= 11) {
                a(this.lines[i], dataoutputstream);
            } else {
                dataoutputstream.writeUTF(this.lines[i]);
            }
        }
        
        // MCOSE: Sign color is NOT sent over Packet130UpdateSign
        // Color is synced separately via MC|SignDye custom payload
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        int i = 0;

        for (int j = 0; j < 4; ++j) {
            i += this.lines[j].length();
        }

        return i;
    }

    @Override
    public Packet clone() {
        Packet130UpdateSign packet = new Packet130UpdateSign(this.x, this.y, this.z, this.lines);
        packet.color = this.color;
        return packet;
    }
}
