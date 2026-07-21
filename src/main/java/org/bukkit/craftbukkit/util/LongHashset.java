package org.bukkit.craftbukkit.util;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import static org.bukkit.craftbukkit.util.Java15Compat.Arrays_copyOf;

public class LongHashset extends LongHash {
    private static final int INITIAL_BUCKET_CAPACITY = 4;

    long[][][] values = new long[256][][];
    int[][] bucketSizes = new int[256][];
    int count = 0;
    ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    ReadLock rl = rwl.readLock();
    WriteLock wl = rwl.writeLock();

    public boolean isEmpty() {
        rl.lock();
        try {
            return this.count == 0;
        } finally {
            rl.unlock();
        }
    }

    public void add(int msw, int lsw) {
        add(toLong(msw, lsw));
    }

    public void add(long key) {
        wl.lock();
        try {
            int mainIdx = (int) (key & 255);
            long outer[][] = this.values[mainIdx];
            int[] sizes = this.bucketSizes[mainIdx];
            if (outer == null) {
                this.values[mainIdx] = outer = new long[256][];
                this.bucketSizes[mainIdx] = sizes = new int[256];
            } else if (sizes == null) {
                this.bucketSizes[mainIdx] = sizes = new int[256];
            }

            int outerIdx = (int) ((key >> 32) & 255);
            long inner[] = outer[outerIdx];
            int size = sizes[outerIdx];

            if (inner == null) {
                outer[outerIdx] = inner = new long[INITIAL_BUCKET_CAPACITY];
            } else {
                for (int i = 0; i < size; i++) {
                    if (inner[i] == key) return;
                }
            }

            if (size == inner.length) {
                outer[outerIdx] = inner = Arrays_copyOf(inner, inner.length << 1);
            }

            inner[size] = key;
            sizes[outerIdx] = size + 1;
            this.count++;
        } finally {
            wl.unlock();
        }
    }

    public boolean containsKey(long key) {
        rl.lock();
        try {
            long[][] outer = this.values[(int) (key & 255)];
            if (outer == null) return false;

            int outerIdx = (int) ((key >> 32) & 255);
            int[] sizes = this.bucketSizes[(int) (key & 255)];
            if (sizes == null) return false;

            long[] inner = outer[outerIdx];
            if (inner == null) return false;

            int size = sizes[outerIdx];
            for (int i = 0; i < size; i++) {
                if (inner[i] == key) return true;
            }
            return false;
        } finally {
            rl.unlock();
        }
    }

    public void remove(long key) {
        wl.lock();
        try {
            int mainIdx = (int) (key & 255);
            long[][] outer = this.values[mainIdx];
            if (outer == null) return;

            int[] sizes = this.bucketSizes[mainIdx];
            if (sizes == null) return;

            int outerIdx = (int) ((key >> 32) & 255);
            long[] inner = outer[outerIdx];
            if (inner == null) return;

            int size = sizes[outerIdx];
            int max = size - 1;
            for (int i = 0; i < size; i++) {
                if (inner[i] == key) {
                    this.count--;
                    if (i != max) {
                        inner[i] = inner[max];
                    }

                    inner[max] = 0L;
                    sizes[outerIdx] = max;
                    if (max == 0) {
                        outer[outerIdx] = null;
                    }
                    return;
                }
            }
        } finally {
            wl.unlock();
        }
    }

    public long popFirst() {
        wl.lock();
        try {
            for (int mainIdx = 0; mainIdx < this.values.length; mainIdx++) {
                long[][] outer = this.values[mainIdx];
                if (outer == null) continue;

                int[] sizes = this.bucketSizes[mainIdx];
                if (sizes == null) continue;

                for (int i = 0; i < outer.length; i++) {
                    long[] inner = outer[i];
                    int size = sizes[i];
                    if (inner == null || size == 0) continue;

                    this.count--;
                    int max = size - 1;
                    long ret = inner[max];
                    inner[max] = 0L;
                    sizes[i] = max;
                    if (max == 0) {
                        outer[i] = null;
                    }

                    return ret;
                }
            }
        } finally {
            wl.unlock();
        }
        return 0;
    }

    public long[] keys() {
        int index = 0;
        rl.lock();
        try {
            long[] ret = new long[this.count];
            for (int mainIdx = 0; mainIdx < this.values.length; mainIdx++) {
                long[][] outer = this.values[mainIdx];
                if (outer == null) continue;

                int[] sizes = this.bucketSizes[mainIdx];
                if (sizes == null) continue;

                for (int outerIdx = 0; outerIdx < outer.length; outerIdx++) {
                    long[] inner = outer[outerIdx];
                    if (inner == null) continue;

                    int size = sizes[outerIdx];
                    for (int i = 0; i < size; i++) {
                        ret[index++] = inner[i];
                    }
                }
            }
            return ret;
        } finally {
            rl.unlock();
        }
    }
}
