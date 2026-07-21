package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class RegionFileCache {

    private static final String REGION_FILE_CACHE_SIZE_CONFIG = "settings.region-file-cache-size";
    private static final String REGION_FILE_FLUSH_ON_SAVE_CONFIG = "settings.region-file-flush-on-save.enabled";
    private static final int DEFAULT_REGION_FILE_CACHE_SIZE = 256;
    // MCOSE: Use strong references instead of SoftReference to prevent GC from
    // silently collecting RegionFile objects (and their unflushed WAL data)
    // between chunk saves and flush/close calls. With SoftReference, memory
    // pressure during a large save could cause the GC to clear references,
    // meaning RegionFile.b() (close/flush WAL) would be skipped entirely.
    private static final Map<File, RegionFile> a = new LinkedHashMap<File, RegionFile>(16, 0.75F, true);
    private static boolean bulkConversionMode = false;
    private static int configuredCacheSize = -1;

    private RegionFileCache() {
    }

    public static synchronized RegionFile a(File file1, int i, int j) {
        return getRegionFile(file1, i, j, false);
    }

    private static synchronized RegionFile getRegionFile(File file1, int i, int j, boolean existingOnly) {
        File file2 = new File(file1, "region");
        File file3 = new File(file2, "r." + (i >> 5) + "." + (j >> 5) + ".mcr");
        RegionFile regionfile = a.get(file3);

        if (regionfile != null) {
            return regionfile;
        }

        if (!file2.exists()) {
            if (existingOnly) {
                return null;
            }

            file2.mkdirs();
        }

        if (existingOnly && !file3.exists()) {
            return null;
        }

        evictRegionFileIfNeeded();
        regionfile = new RegionFile(file3);
        a.put(file3, regionfile);
        return regionfile;
    }

    public static synchronized void setBulkConversionMode(boolean flag) {
        if (bulkConversionMode == flag) {
            return;
        }

        a();
        bulkConversionMode = flag;
    }

    public static synchronized boolean isBulkConversionMode() {
        return bulkConversionMode;
    }

    public static synchronized void a() {
        Iterator<Map.Entry<File, RegionFile>> iterator = a.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<File, RegionFile> entry = iterator.next();

            try {
                RegionFile regionfile = entry.getValue();

                if (regionfile != null) {
                    regionfile.b();
                }
            } catch (IOException ioexception) {
                ioexception.printStackTrace();
            }
        }

        a.clear();
    }

    public static synchronized void flushOnSave() {
        if (getConfigBoolean(REGION_FILE_FLUSH_ON_SAVE_CONFIG, true)) {
            flush();
        }
    }

    public static synchronized void flush() {
        Iterator<Map.Entry<File, RegionFile>> iterator = a.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<File, RegionFile> entry = iterator.next();

            try {
                RegionFile regionfile = entry.getValue();

                if (regionfile != null) {
                    regionfile.flush();
                }
            } catch (IOException ioexception) {
                ioexception.printStackTrace();
            }
        }
    }

    public static int b(File file1, int i, int j) {
        RegionFile regionfile = a(file1, i, j);

        return regionfile.a();
    }

    public static DataInputStream c(File file1, int i, int j) {
        RegionFile regionfile = getRegionFile(file1, i, j, true);

        return regionfile == null ? null : regionfile.a(i & 31, j & 31);
    }

    public static DataOutputStream d(File file1, int i, int j) {
        RegionFile regionfile = a(file1, i, j);

        return regionfile.b(i & 31, j & 31);
    }

    public static synchronized void writeUncompressed(File file1, int i, int j, byte[] serialized, int length) throws IOException {
        RegionFile regionfile = a(file1, i, j);
        regionfile.writeUncompressed(i & 31, j & 31, serialized, length);
    }

    public static synchronized void flushRegion(File file1, int i, int j) throws IOException {
        RegionFile regionfile = getRegionFile(file1, i, j, true);
        if (regionfile != null) {
            regionfile.flush();
        }
    }

    private static void evictRegionFileIfNeeded() {
        int cacheSize = getConfiguredCacheSize();

        while (a.size() >= cacheSize && !a.isEmpty()) {
            Iterator<Map.Entry<File, RegionFile>> iterator = a.entrySet().iterator();
            Map.Entry<File, RegionFile> entry = iterator.next();
            iterator.remove();
            closeRegionFile(entry.getValue());
        }
    }

    private static void closeRegionFile(RegionFile regionfile) {
        try {
            if (regionfile != null) {
                regionfile.b();
            }
        } catch (IOException ioexception) {
            ioexception.printStackTrace();
        }
    }

    private static int getConfiguredCacheSize() {
        if (configuredCacheSize < 1) {
            configuredCacheSize = Math.max(1, getConfigInt(REGION_FILE_CACHE_SIZE_CONFIG, DEFAULT_REGION_FILE_CACHE_SIZE));
        }

        return configuredCacheSize;
    }

    private static int getConfigInt(String key, int defaultValue) {
        Object value = PoseidonConfig.getInstance().getConfigOption(key, Integer.valueOf(defaultValue));

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static boolean getConfigBoolean(String key, boolean defaultValue) {
        Object value = PoseidonConfig.getInstance().getConfigOption(key, Boolean.valueOf(defaultValue));

        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }

        return Boolean.valueOf(String.valueOf(value)).booleanValue();
    }
}
