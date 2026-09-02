package net.minecraft.server;

public class BiomeMeta {

    public Class a;
    public int b;
    /** Data-driven successful-spawn stop cap, or zero for legacy fallback. */
    public int c;

    public BiomeMeta(Class oclass, int i) {
        this(oclass, i, 0);
    }

    public BiomeMeta(Class oclass, int i, int j) {
        this.a = oclass;
        this.b = i;
        this.c = j;
    }
}
