package net.minecraft.server;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * One-way upgrader that rewrites item-bearing world NBT to McRegion2 stack format.
 */
public final class McRegion2WorldUpgrader {
    private McRegion2WorldUpgrader() {}

    public static void upgradeWorldToMcRegion2(File worldDir, Logger logger) {
        if (worldDir == null || !worldDir.exists() || !worldDir.isDirectory()) {
            return;
        }

        int worldVersion = readWorldVersion(worldDir);
        if (worldVersion >= WorldSaveVersions.MCREGION_2) {
            return;
        }

        Logger log = logger == null ? MinecraftServer.log : logger;
        log.info("[McRegion2] Upgrading world '" + worldDir.getName() + "' from "
                + WorldSaveVersions.nameOf(worldVersion) + " to McRegion 2...");

        int changedDatFiles = 0;
        int changedChunks = 0;

        changedDatFiles += rewriteDatIfPresent(new File(worldDir, "level.dat"));
        changedDatFiles += rewriteDatIfPresent(new File(worldDir, "level.dat_old"));

        File playersDir = new File(worldDir, "players");
        File[] playerFiles = playersDir.listFiles();
        if (playerFiles != null) {
            for (int i = 0; i < playerFiles.length; i++) {
                File f = playerFiles[i];
                if (f != null && f.isFile() && f.getName().endsWith(".dat")) {
                    changedDatFiles += rewriteDatIfPresent(f);
                }
            }
        }

        File dataDir = new File(worldDir, "data");
        File[] dataFiles = dataDir.listFiles();
        if (dataFiles != null) {
            for (int i = 0; i < dataFiles.length; i++) {
                File f = dataFiles[i];
                if (f != null && f.isFile() && f.getName().endsWith(".dat")) {
                    changedDatFiles += rewriteDatIfPresent(f);
                }
            }
        }

        changedChunks += rewriteRegionFolder(new File(worldDir, "region"), log);
        changedChunks += rewriteRegionFolder(new File(new File(worldDir, "DIM-1"), "region"), log);

        updateLevelVersion(worldDir, WorldSaveVersions.MCREGION_2);

        log.info("[McRegion2] Upgrade complete for '" + worldDir.getName()
                + "' (datFiles=" + changedDatFiles
                + ", chunks=" + changedChunks + ").");
    }

    private static int readWorldVersion(File worldDir) {
        NBTTagCompound root = readCompressedNbt(new File(worldDir, "level.dat"));
        if (root == null) {
            root = readCompressedNbt(new File(worldDir, "level.dat_old"));
        }
        if (root == null) {
            return WorldSaveVersions.LEGACY_PRE_MCREGION;
        }
        if (root.hasKey("Data")) {
            return root.k("Data").e("version");
        }
        return root.e("version");
    }

    private static int rewriteDatIfPresent(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return 0;
        }

        NBTTagCompound root = readCompressedNbt(file);
        if (root == null) {
            return 0;
        }

        boolean changed = rewriteNbtTree(root);
        if (!changed) {
            return 0;
        }

        writeCompressedNbtAtomic(file, root);
        return 1;
    }

    private static int rewriteRegionFolder(File regionDir, Logger logger) {
        if (regionDir == null || !regionDir.exists() || !regionDir.isDirectory()) {
            return 0;
        }

        File[] files = regionDir.listFiles();
        if (files == null) {
            return 0;
        }

        int changedChunks = 0;
        File worldDir = regionDir.getParentFile();
        Map<Long, byte[]> chunkBlockCache = new HashMap<Long, byte[]>();

        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file == null || !file.isFile() || !file.getName().endsWith(".mcr")) {
                continue;
            }
            int[] regionCoords = parseRegionCoordinates(file.getName());
            if (regionCoords == null) {
                continue;
            }
            int regionX = regionCoords[0];
            int regionZ = regionCoords[1];

            RegionFile regionFile = null;
            try {
                regionFile = new RegionFile(file);
                for (int x = 0; x < 32; x++) {
                    for (int z = 0; z < 32; z++) {
                        if (!regionFile.c(x, z)) {
                            continue;
                        }

                        DataInputStream in = regionFile.a(x, z);
                        if (in == null) {
                            continue;
                        }

                        NBTTagCompound chunkNbt;
                        try {
                            chunkNbt = CompressedStreamTools.a((DataInput) in);
                        } finally {
                            in.close();
                        }

                        int chunkX = regionX * 32 + x;
                        int chunkZ = regionZ * 32 + z;

                        boolean changed = rewriteChestMetadataFromLegacyOrientation(worldDir, chunkX, chunkZ, chunkNbt, chunkBlockCache);
                        if (rewriteChunkBlockStates(chunkNbt)) {
                            changed = true;
                        }
                        if (rewriteNbtTree(chunkNbt)) {
                            changed = true;
                        }
                        if (!changed) {
                            continue;
                        }

                        DataOutputStream out = regionFile.b(x, z);
                        try {
                            CompressedStreamTools.a(chunkNbt, (DataOutput) out);
                        } finally {
                            out.close();
                        }
                        changedChunks++;
                    }
                }
            } catch (Throwable t) {
                throw new RuntimeException("[McRegion2] Failed rewriting region file: " + file.getAbsolutePath(), t);
            } finally {
                if (regionFile != null) {
                    try {
                        regionFile.b();
                    } catch (IOException ignored) {}
                }
            }
        }

        if (changedChunks > 0) {
            logger.info("[McRegion2] Rewrote " + changedChunks + " chunks in " + regionDir.getAbsolutePath());
        }

        RegionFileCache.a();
        return changedChunks;
    }

    private static boolean rewriteChestMetadataFromLegacyOrientation(
            File worldDir,
            int chunkX,
            int chunkZ,
            NBTTagCompound chunkNbt,
            Map<Long, byte[]> chunkBlockCache) {
        if (chunkNbt == null || !chunkNbt.hasKey("Level")) {
            return false;
        }

        NBTTagCompound level = chunkNbt.k("Level");
        if (!level.hasKey("Blocks") || !level.hasKey("Data")) {
            return false;
        }

        byte[] blocks = level.j("Blocks");
        byte[] data = level.j("Data");
        if (blocks == null || data == null || blocks.length < 32768 || data.length < 16384) {
            return false;
        }

        chunkBlockCache.put(Long.valueOf(chunkKey(chunkX, chunkZ)), blocks);
        boolean changed = false;

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int y = 0; y < 128; y++) {
                    int index = (localX << 11) | (localZ << 7) | y;
                    int blockId = blocks[index] & 255;
                    if (blockId != Block.CHEST.id) {
                        continue;
                    }

                    int currentMeta = getNibble(data, index);
                    if (isValidChestFacing(currentMeta)) {
                        continue;
                    }

                    int worldX = chunkX * 16 + localX;
                    int worldZ = chunkZ * 16 + localZ;
                    int inferred = inferLegacyChestFacing(worldDir, worldX, y, worldZ, chunkBlockCache);
                    if (!isValidChestFacing(inferred) || inferred == currentMeta) {
                        continue;
                    }

                    setNibble(data, index, inferred);
                    changed = true;
                }
            }
        }

        if (changed) {
            level.a("Data", data);
            chunkNbt.a("Level", level);
        }

        return changed;
    }

    private static int inferLegacyChestFacing(File worldDir, int x, int y, int z, Map<Long, byte[]> chunkBlockCache) {
        int north = getBlockIdAt(worldDir, x, y, z - 1, chunkBlockCache);
        int south = getBlockIdAt(worldDir, x, y, z + 1, chunkBlockCache);
        int west = getBlockIdAt(worldDir, x - 1, y, z, chunkBlockCache);
        int east = getBlockIdAt(worldDir, x + 1, y, z, chunkBlockCache);

        boolean westChest = west == Block.CHEST.id;
        boolean eastChest = east == Block.CHEST.id;
        boolean northChest = north == Block.CHEST.id;
        boolean southChest = south == Block.CHEST.id;

        if (!westChest && !eastChest && !northChest && !southChest) {
            int facing = 3;
            if (isSolidForLegacyChestOrientation(north) && !isSolidForLegacyChestOrientation(south)) {
                facing = 3;
            }
            if (isSolidForLegacyChestOrientation(south) && !isSolidForLegacyChestOrientation(north)) {
                facing = 2;
            }
            if (isSolidForLegacyChestOrientation(west) && !isSolidForLegacyChestOrientation(east)) {
                facing = 5;
            }
            if (isSolidForLegacyChestOrientation(east) && !isSolidForLegacyChestOrientation(west)) {
                facing = 4;
            }
            return facing;
        }

        if (westChest || eastChest) {
            int pairX = westChest ? x - 1 : x + 1;
            int pairNorth = getBlockIdAt(worldDir, pairX, y, z - 1, chunkBlockCache);
            int pairSouth = getBlockIdAt(worldDir, pairX, y, z + 1, chunkBlockCache);
            int facing = 3;
            if ((isSolidForLegacyChestOrientation(north) || isSolidForLegacyChestOrientation(pairNorth))
                    && !isSolidForLegacyChestOrientation(south)
                    && !isSolidForLegacyChestOrientation(pairSouth)) {
                facing = 3;
            }
            if ((isSolidForLegacyChestOrientation(south) || isSolidForLegacyChestOrientation(pairSouth))
                    && !isSolidForLegacyChestOrientation(north)
                    && !isSolidForLegacyChestOrientation(pairNorth)) {
                facing = 2;
            }
            return facing;
        }

        int pairZ = northChest ? z - 1 : z + 1;
        int pairWest = getBlockIdAt(worldDir, x - 1, y, pairZ, chunkBlockCache);
        int pairEast = getBlockIdAt(worldDir, x + 1, y, pairZ, chunkBlockCache);
        int facing = 5;
        if ((isSolidForLegacyChestOrientation(west) || isSolidForLegacyChestOrientation(pairWest))
                && !isSolidForLegacyChestOrientation(east)
                && !isSolidForLegacyChestOrientation(pairEast)) {
            facing = 5;
        }
        if ((isSolidForLegacyChestOrientation(east) || isSolidForLegacyChestOrientation(pairEast))
                && !isSolidForLegacyChestOrientation(west)
                && !isSolidForLegacyChestOrientation(pairWest)) {
            facing = 4;
        }
        return facing;
    }

    private static int getBlockIdAt(File worldDir, int x, int y, int z, Map<Long, byte[]> chunkBlockCache) {
        if (y < 0 || y >= 128) {
            return 0;
        }

        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        byte[] blocks = loadChunkBlocks(worldDir, chunkX, chunkZ, chunkBlockCache);
        if (blocks == null || blocks.length < 32768) {
            return 0;
        }

        int localX = x & 15;
        int localZ = z & 15;
        int index = (localX << 11) | (localZ << 7) | y;
        return blocks[index] & 255;
    }

    private static byte[] loadChunkBlocks(File worldDir, int chunkX, int chunkZ, Map<Long, byte[]> chunkBlockCache) {
        Long key = Long.valueOf(chunkKey(chunkX, chunkZ));
        if (chunkBlockCache.containsKey(key)) {
            return chunkBlockCache.get(key);
        }

        DataInputStream in = RegionFileCache.c(worldDir, chunkX, chunkZ);
        if (in == null) {
            chunkBlockCache.put(key, null);
            return null;
        }

        NBTTagCompound root;
        try {
            root = CompressedStreamTools.a((DataInput) in);
        } catch (IOException e) {
            chunkBlockCache.put(key, null);
            return null;
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {}
        }

        if (root == null || !root.hasKey("Level")) {
            chunkBlockCache.put(key, null);
            return null;
        }

        NBTTagCompound level = root.k("Level");
        if (!level.hasKey("Blocks")) {
            chunkBlockCache.put(key, null);
            return null;
        }

        byte[] blocks = level.j("Blocks");
        if (blocks == null || blocks.length < 32768) {
            chunkBlockCache.put(key, null);
            return null;
        }

        chunkBlockCache.put(key, blocks);
        return blocks;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long)chunkX & 4294967295L) << 32 | (long)chunkZ & 4294967295L;
    }

    private static int getNibble(byte[] data, int index) {
        int nibbleIndex = index >> 1;
        int value = data[nibbleIndex] & 255;
        return (index & 1) == 0 ? value & 15 : value >> 4 & 15;
    }

    private static void setNibble(byte[] data, int index, int nibble) {
        int nibbleIndex = index >> 1;
        int current = data[nibbleIndex] & 255;
        int value = nibble & 15;
        if ((index & 1) == 0) {
            current = current & 240 | value;
        } else {
            current = current & 15 | value << 4;
        }
        data[nibbleIndex] = (byte)current;
    }

    private static boolean isValidChestFacing(int metadata) {
        return metadata == 2 || metadata == 3 || metadata == 4 || metadata == 5;
    }

    private static boolean isSolidForLegacyChestOrientation(int blockId) {
        if (blockId <= 0 || blockId >= Block.o.length || !Block.o[blockId]) {
            return false;
        }
        if (blockId == Block.FURNACE.id || blockId == Block.BURNING_FURNACE.id) {
            return false;
        }
        if (blockId == Block.WORKBENCH.id) {
            return false;
        }
        if (blockId == Block.JUKEBOX.id) {
            return false;
        }
        if (blockId == Block.PUMPKIN.id || blockId == Block.JACK_O_LANTERN.id) {
            return false;
        }
        if (blockId == Block.DISPENSER.id) {
            return false;
        }
        if (blockId == Block.NOTE_BLOCK.id) {
            return false;
        }
        return true;
    }

    private static int[] parseRegionCoordinates(String fileName) {
        if (fileName == null || !fileName.startsWith("r.") || !fileName.endsWith(".mcr")) {
            return null;
        }

        String[] parts = fileName.split("\\.");
        if (parts.length != 4) {
            return null;
        }

        try {
            return new int[] { Integer.parseInt(parts[1]), Integer.parseInt(parts[2]) };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean rewriteNbtTree(NBTBase tag) {
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            boolean changed = tryUpgradeItemCompound(compound);
            if (ModernEntityNbtCodec.rewriteEntityCompound(compound)) {
                changed = true;
            }
            if (LegacyEntityNbtCodec.ensureLegacyShadowFields(compound)) {
                changed = true;
            }

            Collection values = compound.c();
            for (Object obj : values) {
                if (obj instanceof NBTBase) {
                    if (rewriteNbtTree((NBTBase) obj)) {
                        changed = true;
                    }
                }
            }
            return changed;
        }

        if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag;
            boolean changed = false;
            for (int i = 0; i < list.c(); i++) {
                NBTBase child = list.a(i);
                if (child != null && rewriteNbtTree(child)) {
                    changed = true;
                }
            }
            return changed;
        }

        return false;
    }

    private static boolean rewriteChunkBlockStates(NBTTagCompound chunkNbt) {
        if (chunkNbt == null || !chunkNbt.hasKey("Level")) {
            return false;
        }

        NBTTagCompound level = chunkNbt.k("Level");
        boolean changed = false;

        if (level.hasKey("Blocks") && level.hasKey("Data")) {
            byte[] blocks = level.j("Blocks");
            byte[] data = level.j("Data");
            if (blocks != null && data != null && blocks.length >= 32768 && data.length >= 16384) {
                if (BlockStateCodec.writeStateData(level, blocks, data)) {
                    changed = true;
                }
            }
        }

        if (BlockStateCodec.hasStateData(level)) {
            if (level.hasKey("Blocks")) {
                level.remove("Blocks");
                changed = true;
            }
            if (level.hasKey("Data")) {
                level.remove("Data");
                changed = true;
            }
            chunkNbt.a("Level", level);
        }

        return changed;
    }

    private static boolean tryUpgradeItemCompound(NBTTagCompound compound) {
        if (!looksLikeItemCompound(compound)) {
            return false;
        }

        ItemStack stack = ItemStack.parse(compound);
        if (stack == null) {
            return false;
        }

        ModernItemStackCodec.write(stack, compound);
        return true;
    }

    private static boolean looksLikeItemCompound(NBTTagCompound compound) {
        if (compound == null) {
            return false;
        }

        boolean legacyShape = compound.hasKey("Count") && (compound.hasKey("id") || compound.hasKey("name"));
        boolean modernShape = compound.hasKey("item") && (compound.hasKey("count") || compound.hasKey("Count"));
        return legacyShape || modernShape;
    }

    private static void updateLevelVersion(File worldDir, int version) {
        updateLevelVersionFile(new File(worldDir, "level.dat"), version);
        updateLevelVersionFile(new File(worldDir, "level.dat_old"), version);
    }

    private static void updateLevelVersionFile(File file, int version) {
        if (file == null || !file.exists() || !file.isFile()) {
            return;
        }

        NBTTagCompound root = readCompressedNbt(file);
        if (root == null) {
            return;
        }

        if (root.hasKey("Data")) {
            NBTTagCompound data = root.k("Data");
            data.a("version", version);
            root.a("Data", data);
        } else {
            root.a("version", version);
        }

        writeCompressedNbtAtomic(file, root);
    }

    private static NBTTagCompound readCompressedNbt(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }

        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            return CompressedStreamTools.a(in);
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private static void writeCompressedNbtAtomic(File file, NBTTagCompound root) {
        File tmp = new File(file.getParentFile(), file.getName() + ".mcregion2.tmp");
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(tmp);
            CompressedStreamTools.a(root, out);
        } catch (Throwable t) {
            throw new RuntimeException("[McRegion2] Failed writing temp file: " + tmp.getAbsolutePath(), t);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {}
            }
        }

        if (file.exists() && !file.delete()) {
            throw new RuntimeException("[McRegion2] Failed replacing file: " + file.getAbsolutePath());
        }
        if (!tmp.renameTo(file)) {
            throw new RuntimeException("[McRegion2] Failed moving temp file into place: " + file.getAbsolutePath());
        }
    }
}
