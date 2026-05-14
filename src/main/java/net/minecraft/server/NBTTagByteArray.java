package net.minecraft.server;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class NBTTagByteArray extends NBTBase {

    public byte[] a;

    public NBTTagByteArray() {
    }

    public NBTTagByteArray(byte[] abyte) {
        this.a = abyte;
    }

    void a(DataOutput dataoutput) throws IOException {
        dataoutput.writeInt(this.a.length);
        dataoutput.write(this.a);
    }

    void a(DataInput datainput, NBTReadLimiter limiter) throws IOException {
        limiter.account(4L);
        int i = datainput.readInt();
        if (i < 0) {
            throw new IOException("Negative TAG_Byte_Array length: " + i);
        }
        limiter.account((long) i);

        this.a = new byte[i];
        datainput.readFully(this.a);
    }

    public byte a() {
        return (byte) 7;
    }

    public String toString() {
        return "[" + this.a.length + " bytes]";
    }
}
