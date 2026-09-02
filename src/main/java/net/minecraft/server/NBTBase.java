package net.minecraft.server;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

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

    /** Returns a structural copy so inventory and packet snapshots never share mutable NBT. */
    public NBTBase copy() {
        NBTBase copy;
        switch (this.a()) {
            case 0:
                copy = new NBTTagEnd();
                break;
            case 1:
                copy = new NBTTagByte(((NBTTagByte) this).a);
                break;
            case 2:
                copy = new NBTTagShort(((NBTTagShort) this).a);
                break;
            case 3:
                copy = new NBTTagInt(((NBTTagInt) this).a);
                break;
            case 4:
                copy = new NBTTagLong(((NBTTagLong) this).a);
                break;
            case 5:
                copy = new NBTTagFloat(((NBTTagFloat) this).a);
                break;
            case 6:
                copy = new NBTTagDouble(((NBTTagDouble) this).a);
                break;
            case 7:
                byte[] bytes = ((NBTTagByteArray) this).a;
                copy = new NBTTagByteArray(bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length));
                break;
            case 8:
                copy = new NBTTagString(((NBTTagString) this).a);
                break;
            case 9:
                NBTTagList sourceList = (NBTTagList) this;
                NBTTagList copiedList = new NBTTagList();
                for (int index = 0; index < sourceList.c(); ++index) {
                    copiedList.a(sourceList.a(index).copy());
                }
                copy = copiedList;
                break;
            case 10:
                NBTTagCompound copiedCompound = new NBTTagCompound();
                Collection entries = ((NBTTagCompound) this).c();
                for (Object value : entries) {
                    NBTBase child = (NBTBase) value;
                    copiedCompound.a(child.b(), child.copy());
                }
                copy = copiedCompound;
                break;
            default:
                throw new IllegalStateException("Unknown NBT tag type " + this.a());
        }
        return copy.a(this.b());
    }

    @Override
    public final boolean equals(Object other) {
        return this == other || other instanceof NBTBase && valuesEqual(this, (NBTBase) other);
    }

    @Override
    public final int hashCode() {
        return valueHash(this);
    }

    private static boolean valuesEqual(NBTBase first, NBTBase second) {
        if (first == null || second == null || first.a() != second.a()) {
            return first == second;
        }
        switch (first.a()) {
            case 0:
                return true;
            case 1:
                return ((NBTTagByte) first).a == ((NBTTagByte) second).a;
            case 2:
                return ((NBTTagShort) first).a == ((NBTTagShort) second).a;
            case 3:
                return ((NBTTagInt) first).a == ((NBTTagInt) second).a;
            case 4:
                return ((NBTTagLong) first).a == ((NBTTagLong) second).a;
            case 5:
                return Float.floatToIntBits(((NBTTagFloat) first).a)
                        == Float.floatToIntBits(((NBTTagFloat) second).a);
            case 6:
                return Double.doubleToLongBits(((NBTTagDouble) first).a)
                        == Double.doubleToLongBits(((NBTTagDouble) second).a);
            case 7:
                return Arrays.equals(((NBTTagByteArray) first).a, ((NBTTagByteArray) second).a);
            case 8:
                String firstString = ((NBTTagString) first).a;
                String secondString = ((NBTTagString) second).a;
                return firstString == null ? secondString == null : firstString.equals(secondString);
            case 9:
                NBTTagList firstList = (NBTTagList) first;
                NBTTagList secondList = (NBTTagList) second;
                if (firstList.c() != secondList.c()) {
                    return false;
                }
                for (int index = 0; index < firstList.c(); ++index) {
                    if (!valuesEqual(firstList.a(index), secondList.a(index))) {
                        return false;
                    }
                }
                return true;
            case 10:
                NBTTagCompound firstCompound = (NBTTagCompound) first;
                NBTTagCompound secondCompound = (NBTTagCompound) second;
                Collection firstEntries = firstCompound.c();
                Collection secondEntries = secondCompound.c();
                if (firstEntries.size() != secondEntries.size()) {
                    return false;
                }
                for (Object value : firstEntries) {
                    NBTBase firstChild = (NBTBase) value;
                    if (!valuesEqual(firstChild, secondCompound.b(firstChild.b()))) {
                        return false;
                    }
                }
                return true;
            default:
                return false;
        }
    }

    private static int valueHash(NBTBase tag) {
        if (tag == null) {
            return 0;
        }
        switch (tag.a()) {
            case 0:
                return 0;
            case 1:
                return ((NBTTagByte) tag).a;
            case 2:
                return ((NBTTagShort) tag).a;
            case 3:
                return ((NBTTagInt) tag).a;
            case 4:
                long longValue = ((NBTTagLong) tag).a;
                return (int) (longValue ^ longValue >>> 32);
            case 5:
                return Float.floatToIntBits(((NBTTagFloat) tag).a);
            case 6:
                long doubleBits = Double.doubleToLongBits(((NBTTagDouble) tag).a);
                return (int) (doubleBits ^ doubleBits >>> 32);
            case 7:
                return Arrays.hashCode(((NBTTagByteArray) tag).a);
            case 8:
                String string = ((NBTTagString) tag).a;
                return string == null ? 0 : string.hashCode();
            case 9:
                NBTTagList list = (NBTTagList) tag;
                int listHash = 1;
                for (int index = 0; index < list.c(); ++index) {
                    listHash = 31 * listHash + valueHash(list.a(index));
                }
                return listHash;
            case 10:
                int compoundHash = 0;
                for (Object value : ((NBTTagCompound) tag).c()) {
                    NBTBase child = (NBTBase) value;
                    compoundHash += child.b().hashCode() ^ valueHash(child);
                }
                return compoundHash;
            default:
                return 0;
        }
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
