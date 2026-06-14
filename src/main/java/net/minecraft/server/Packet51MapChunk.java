package net.minecraft.server;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class Packet51MapChunk extends Packet {

    public int a;
    public int b;
    public int c;
    public int d;
    public int e;
    public int f;
    public byte[] g;
    public int h; // CraftBukkit - private -> public
    public byte[] rawData; // CraftBukkit

    public Packet51MapChunk() {
        this.k = true;
    }

    // CraftBukkit start
    public Packet51MapChunk(int i, int j, int k, int l, int i1, int j1, World world) {
        this(i, j, k, l, i1, j1, world.getMultiChunkData(i, j, k, l, i1, j1));
    }

    public Packet51MapChunk(int i, int j, int k, int l, int i1, int j1, byte[] data) {
        // CraftBukkit end
        this.k = true;
        this.a = i;
        this.b = j;
        this.c = k;
        this.d = l;
        this.e = i1;
        this.f = j1;
        /* CraftBukkit - Moved compression into its own method.
        byte[] abyte = data; // CraftBukkit - uses data from above constructor
        Deflater deflater = new Deflater(-1);

        try {
            deflater.setInput(abyte);
            deflater.finish();
            this.g = new byte[l * i1 * j1 * 5 / 2];
            this.h = deflater.deflate(this.g);
        } finally {
            deflater.end();
        }*/
        this.rawData = data; // CraftBukkit
    }

    public void a(DataInputStream datainputstream) throws IOException { // CraftBukkit - throws IOEXception
        this.a = datainputstream.readInt();
        this.b = datainputstream.readShort();
        this.c = datainputstream.readInt();
        this.d = datainputstream.read() + 1;
        this.e = datainputstream.read() + 1;
        this.f = datainputstream.read() + 1;
        this.h = datainputstream.readInt();
        byte[] abyte = new byte[this.h];

        datainputstream.readFully(abyte);
        int expectedSize = this.d * this.e * this.f * 5 / 2;
        this.g = new byte[expectedSize];
        Inflater inflater = new Inflater();

        inflater.setInput(abyte);

        try {
            int inflated = inflater.inflate(this.g);
            if (inflated != expectedSize || !inflater.finished()) {
                throw new IOException("Bad compressed chunk data length: " + inflated + "/" + expectedSize);
            }
        } catch (DataFormatException dataformatexception) {
            throw new IOException("Bad compressed data format");
        } finally {
            inflater.end();
        }
    }

    public void a(DataOutputStream dataoutputstream) throws IOException { // CraftBukkit - throws IOException
        this.ensureCompressed();
        dataoutputstream.writeInt(this.a);
        dataoutputstream.writeShort(this.b);
        dataoutputstream.writeInt(this.c);
        dataoutputstream.write(this.d - 1);
        dataoutputstream.write(this.e - 1);
        dataoutputstream.write(this.f - 1);
        dataoutputstream.writeInt(this.h);
        dataoutputstream.write(this.g, 0, this.h);
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        return 17 + this.h;
    }

    private void ensureCompressed() throws IOException {
        if (this.g != null && this.h > 0) {
            return;
        }
        if (this.rawData == null) {
            throw new IOException("Chunk packet missing raw data for compression");
        }

        this.g = deflateChunkData(this.rawData);
        this.h = this.g.length;
    }

    public static byte[] deflateChunkData(byte[] rawData) throws IOException {
        if (rawData == null) {
            throw new IOException("Cannot compress null chunk data");
        }

        Deflater deflater = new Deflater();
        try {
            deflater.setInput(rawData);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, rawData.length / 2));
            byte[] buffer = new byte[8192];
            int emptyWrites = 0;
            while (!deflater.finished()) {
                int written = deflater.deflate(buffer);
                if (written > 0) {
                    out.write(buffer, 0, written);
                    emptyWrites = 0;
                } else if (++emptyWrites > 2) {
                    throw new IOException("Deflate stalled while compressing chunk data");
                }
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    public Packet clone() {
        Packet51MapChunk clone = new Packet51MapChunk();
        clone.a = this.a;
        clone.b = this.b;
        clone.c = this.c;
        clone.d = this.d;
        clone.e = this.e;
        clone.f = this.f;
        clone.h = this.h;
        clone.k = this.k;
        if (this.g != null) {
            clone.g = new byte[this.g.length];
            System.arraycopy(this.g, 0, clone.g, 0, this.g.length);
        }
        if (this.rawData != null) {
            clone.rawData = new byte[this.rawData.length];
            System.arraycopy(this.rawData, 0, clone.rawData, 0, this.rawData.length);
        }
        return clone;
    }
}
