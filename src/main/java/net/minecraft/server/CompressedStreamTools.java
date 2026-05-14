package net.minecraft.server;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CompressedStreamTools {

    public CompressedStreamTools() {
    }

    public static NBTTagCompound a(InputStream inputstream) throws IOException {
        DataInputStream datainputstream = new DataInputStream(new GZIPInputStream(inputstream));

        NBTTagCompound nbttagcompound;

        try {
            nbttagcompound = a((DataInput) datainputstream, NBTReadLimiter.world());
        } finally {
            datainputstream.close();
        }

        return nbttagcompound;
    }

    public static void a(NBTTagCompound nbttagcompound, OutputStream outputstream) throws IOException {
        DataOutputStream dataoutputstream = new DataOutputStream(new GZIPOutputStream(outputstream));

        try {
            a(nbttagcompound, (DataOutput) dataoutputstream);
        } finally {
            dataoutputstream.close();
        }
    }

    public static NBTTagCompound a(DataInput datainput) throws IOException {
        return a(datainput, NBTReadLimiter.world());
    }

    public static NBTTagCompound a(DataInput datainput, NBTReadLimiter limiter) throws IOException {
        NBTBase nbtbase = NBTBase.b(datainput, limiter);

        if (nbtbase instanceof NBTTagCompound) {
            return (NBTTagCompound) nbtbase;
        } else {
            throw new IOException("Root tag must be a named compound tag");
        }
    }

    public static void a(NBTTagCompound nbttagcompound, DataOutput dataoutput) throws IOException {
        NBTBase.a(nbttagcompound, dataoutput);
    }
}
