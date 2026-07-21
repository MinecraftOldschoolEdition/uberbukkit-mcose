package net.minecraft.server;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NBTTagList extends NBTBase {

    private static final long ELEMENT_HEAP_OVERHEAD_BYTES = 32L;

    private List a = new ArrayList();
    private byte b;

    public NBTTagList() {
    }

    void a(DataOutput dataoutput) throws IOException {
        if (this.a.size() > 0) {
            this.b = ((NBTBase) this.a.get(0)).a();
        } else {
            this.b = 1;
        }

        dataoutput.writeByte(this.b);
        dataoutput.writeInt(this.a.size());

        for (int i = 0; i < this.a.size(); ++i) {
            ((NBTBase) this.a.get(i)).a(dataoutput);
        }
    }

    void a(DataInput datainput, NBTReadLimiter limiter) throws IOException {
        limiter.account(5L);
        this.b = datainput.readByte();
        int i = datainput.readInt();
        if (i < 0) {
            throw new IOException("Negative TAG_List length: " + i);
        }
        if (i > 0) {
            if (this.b == 0) {
                throw new IOException("Non-empty TAG_List cannot contain TAG_End elements");
            }
            if (NBTBase.a(this.b) == null) {
                throw new IOException("Invalid TAG_List element type: " + this.b);
            }

            // Paper/Spigot-style structural accounting. Primitive payload bytes alone
            // do not represent the heap cost of allocating one object per list entry.
            limiter.account((long) i * ELEMENT_HEAP_OVERHEAD_BYTES);
        }

        this.a = new ArrayList();

        for (int j = 0; j < i; ++j) {
            NBTBase nbtbase = NBTBase.a(this.b);

            limiter.enterTag();
            try {
                nbtbase.a(datainput, limiter);
            } finally {
                limiter.exitTag();
            }
            this.a.add(nbtbase);
        }
    }

    public byte a() {
        return (byte) 9;
    }

    public String toString() {
        return "" + this.a.size() + " entries of type " + NBTBase.b(this.b);
    }

    public void a(NBTBase nbtbase) {
        this.b = nbtbase.a();
        this.a.add(nbtbase);
    }

    public NBTBase a(int i) {
        return (NBTBase) this.a.get(i);
    }

    public int c() {
        return this.a.size();
    }
}
