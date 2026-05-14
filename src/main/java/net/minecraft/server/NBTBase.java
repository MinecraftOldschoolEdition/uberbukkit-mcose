package net.minecraft.server;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public abstract class NBTBase {

    private String a = null;

    public NBTBase() {
    }

    abstract void a(DataOutput dataoutput) throws IOException;

    void a(DataInput datainput) throws IOException {
        this.a(datainput, NBTReadLimiter.packet());
    }

    abstract void a(DataInput datainput, NBTReadLimiter limiter) throws IOException;

    public abstract byte a();

    public String b() {
        return this.a == null ? "" : this.a;
    }

    public NBTBase a(String s) {
        this.a = s;
        return this;
    }

    public static NBTBase b(DataInput datainput) throws IOException {
        return b(datainput, NBTReadLimiter.packet());
    }

    public static NBTBase b(DataInput datainput, NBTReadLimiter limiter) throws IOException {
        if (limiter == null) {
            limiter = NBTReadLimiter.packet();
        }

        limiter.enterTag();
        try {
            limiter.account(1L);
            byte b0 = datainput.readByte();

            if (b0 == 0) {
                return new NBTTagEnd();
            } else {
                NBTBase nbtbase = a(b0);
                if (nbtbase == null) {
                    throw new IOException("Invalid NBT tag id: " + b0);
                }

                nbtbase.a = limiter.readUTF(datainput, "TAG_Name");
                nbtbase.a(datainput, limiter);
                return nbtbase;
            }
        } finally {
            limiter.exitTag();
        }
    }

    public static void a(NBTBase nbtbase, DataOutput dataoutput) throws IOException {
        dataoutput.writeByte(nbtbase.a());
        if (nbtbase.a() != 0) {
            dataoutput.writeUTF(nbtbase.b());
            nbtbase.a(dataoutput);
        }
    }

    public static NBTBase a(byte b0) {
        switch (b0) {
            case 0:
                return new NBTTagEnd();

            case 1:
                return new NBTTagByte();

            case 2:
                return new NBTTagShort();

            case 3:
                return new NBTTagInt();

            case 4:
                return new NBTTagLong();

            case 5:
                return new NBTTagFloat();

            case 6:
                return new NBTTagDouble();

            case 7:
                return new NBTTagByteArray();

            case 8:
                return new NBTTagString();

            case 9:
                return new NBTTagList();

            case 10:
                return new NBTTagCompound();

            default:
                return null;
        }
    }

    public static String b(byte b0) {
        switch (b0) {
            case 0:
                return "TAG_End";

            case 1:
                return "TAG_Byte";

            case 2:
                return "TAG_Short";

            case 3:
                return "TAG_Int";

            case 4:
                return "TAG_Long";

            case 5:
                return "TAG_Float";

            case 6:
                return "TAG_Double";

            case 7:
                return "TAG_Byte_Array";

            case 8:
                return "TAG_String";

            case 9:
                return "TAG_List";

            case 10:
                return "TAG_Compound";

            default:
                return "UNKNOWN";
        }
    }
}
