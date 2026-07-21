package net.minecraft.server;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public class ChunkRegionLoader implements IChunkLoader {

    private static final String OVERSIZED_MARKER = "UberBukkitOversized";
    private static final String OVERSIZED_DIRECTORY = ".oversized";
    private final File a;

    public ChunkRegionLoader(File file1) {
        this.a = file1;
    }

    public Chunk a(World world, int i, int j) throws IOException {
        DataInputStream datainputstream = RegionFileCache.c(this.a, i, j);

        if (datainputstream != null) {
            NBTTagCompound nbttagcompound = CompressedStreamTools.a((DataInput) datainputstream);
            this.mergeOversizedData(nbttagcompound, i, j);

            if (!nbttagcompound.hasKey("Level")) {
                System.out.println("Chunk file at " + i + "," + j + " is missing level data, skipping");
                return null;
            }

            NBTTagCompound level = nbttagcompound.k("Level");
            if (!level.hasKey("Blocks") && !BlockStateCodec.hasStateData(level)) {
                System.out.println("Chunk file at " + i + "," + j + " is missing block data, skipping");
                return null;
            } else {
                Chunk chunk = ChunkLoader.a(world, level);

                if (!chunk.a(i, j)) {
                    System.out.println("Chunk file at " + i + "," + j + " is in the wrong location; relocating. (Expected " + i + ", " + j + ", got " + chunk.x + ", " + chunk.z + ")");
                    nbttagcompound.a("xPos", i);
                    nbttagcompound.a("zPos", j);
                    chunk = ChunkLoader.a(world, level);
                }

                chunk.h();
                return chunk;
            }
        } else {
            return null;
        }
    }

    public void a(World world, Chunk chunk) throws IOException {
        world.k();

        NBTTagCompound nbttagcompound = new NBTTagCompound();
        NBTTagCompound nbttagcompound1 = new NBTTagCompound();

        nbttagcompound.a("Level", (NBTBase) nbttagcompound1);
        ChunkLoader.a(chunk, world, nbttagcompound1);

        try {
            this.writeRegionChunk(nbttagcompound, chunk.x, chunk.z);
            if (this.hasOversizedFiles(chunk.x, chunk.z, null)) {
                RegionFileCache.flushRegion(this.a, chunk.x, chunk.z);
                this.deleteOversizedFiles(chunk.x, chunk.z, null);
            }
        } catch (RegionFile.ChunkTooLargeException tooLarge) {
            this.writeOversizedChunk(nbttagcompound, nbttagcompound1, chunk.x, chunk.z, tooLarge);
        }

        WorldData worlddata = world.q();
        worlddata.b(worlddata.g() + (long) RegionFileCache.b(this.a, chunk.x, chunk.z));
    }

    public void b(World world, Chunk chunk) {
    }

    public void a() {
    }

    public void b() {
    }

    private void writeRegionChunk(NBTTagCompound root, int chunkX, int chunkZ) throws IOException {
        // Serialize completely before touching the region file. Closing the old
        // streaming output after a serialization failure committed the partial
        // buffer and could replace the last good copy of a chunk.
        ByteArrayOutputStream serializedBytes = new ByteArrayOutputStream(65536);
        DataOutputStream serialized = new DataOutputStream(serializedBytes);
        CompressedStreamTools.a(root, (DataOutput) serialized);
        serialized.flush();
        byte[] bytes = serializedBytes.toByteArray();
        RegionFileCache.writeUncompressed(this.a, chunkX, chunkZ, bytes, bytes.length);
    }

    private void writeOversizedChunk(NBTTagCompound root, NBTTagCompound level, int chunkX, int chunkZ, RegionFile.ChunkTooLargeException originalFailure) throws IOException {
        NBTTagList entities = level.l("Entities");
        NBTTagList tileEntities = level.l("TileEntities");
        if (entities.c() == 0 && tileEntities.c() == 0) {
            throw originalFailure;
        }

        String generation = Long.toHexString(System.nanoTime()) + "-" + Integer.toHexString(System.identityHashCode(root));
        NBTTagCompound oversizedRoot = new NBTTagCompound();
        NBTTagCompound oversizedLevel = new NBTTagCompound();
        oversizedLevel.a("Entities", (NBTBase) entities);
        oversizedLevel.a("TileEntities", (NBTBase) tileEntities);
        oversizedRoot.a("Level", (NBTBase) oversizedLevel);

        File sidecar = this.writeOversizedSidecar(oversizedRoot, chunkX, chunkZ, generation);
        level.a("Entities", (NBTBase) new NBTTagList());
        level.a("TileEntities", (NBTBase) new NBTTagList());
        level.setString(OVERSIZED_MARKER, generation);

        try {
            this.writeRegionChunk(root, chunkX, chunkZ);
            // The new generation marker must be durable before any sidecar used
            // by the previously durable marker can be removed.
            RegionFileCache.flushRegion(this.a, chunkX, chunkZ);
        } catch (IOException retryFailure) {
            Files.deleteIfExists(sidecar.toPath());
            retryFailure.addSuppressed(originalFailure);
            throw retryFailure;
        }

        this.deleteOversizedFiles(chunkX, chunkZ, sidecar.getName());
        MinecraftServer.log.fine("Saved oversized chunk [" + chunkX + "," + chunkZ + "] using sidecar " + sidecar.getName());
    }

    private void mergeOversizedData(NBTTagCompound root, int chunkX, int chunkZ) throws IOException {
        if (root == null || !root.hasKey("Level")) {
            return;
        }

        NBTTagCompound level = root.k("Level");
        NBTBase marker = level.b(OVERSIZED_MARKER);
        if (!(marker instanceof NBTTagString)) {
            return;
        }

        String generation = ((NBTTagString) marker).a;
        if (generation == null || generation.length() == 0 || !generation.matches("[0-9a-fA-F-]+")) {
            throw new IOException("Invalid oversized chunk generation for [" + chunkX + "," + chunkZ + "]");
        }

        File sidecar = this.getOversizedFile(chunkX, chunkZ, generation);
        if (!sidecar.isFile()) {
            throw new IOException("Missing oversized chunk sidecar for [" + chunkX + "," + chunkZ + "]: " + sidecar.getName());
        }

        FileInputStream input = new FileInputStream(sidecar);
        NBTTagCompound oversizedRoot;
        try {
            oversizedRoot = CompressedStreamTools.a((InputStream) input);
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {
            }
        }

        NBTTagCompound oversizedLevel = oversizedRoot.k("Level");
        mergeList(level, oversizedLevel, "Entities");
        mergeList(level, oversizedLevel, "TileEntities");
    }

    private static void mergeList(NBTTagCompound destination, NBTTagCompound source, String key) {
        NBTTagList destinationList = destination.l(key);
        NBTTagList sourceList = source.l(key);
        for (int i = 0; i < sourceList.c(); ++i) {
            destinationList.a(sourceList.a(i));
        }
        destination.a(key, (NBTBase) destinationList);
    }

    private File writeOversizedSidecar(NBTTagCompound root, int chunkX, int chunkZ, String generation) throws IOException {
        File directory = this.getOversizedDirectory();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Unable to create oversized chunk directory " + directory);
        }

        File target = this.getOversizedFile(chunkX, chunkZ, generation);
        File temporary = new File(directory, target.getName() + ".tmp");
        try {
            FileOutputStream output = new FileOutputStream(temporary);
            CompressedStreamTools.a(root, (OutputStream) output);

            RandomAccessFile syncFile = new RandomAccessFile(temporary, "rw");
            try {
                syncFile.getFD().sync();
            } finally {
                syncFile.close();
            }

            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            this.syncSidecarDirectory(directory);
            return target;
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }

    private void deleteOversizedFiles(int chunkX, int chunkZ, String keepName) throws IOException {
        File directory = this.getOversizedDirectory();
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        String prefix = "c." + chunkX + "." + chunkZ + ".";
        for (int i = 0; i < files.length; ++i) {
            File file = files[i];
            if (file.getName().startsWith(prefix) && file.getName().endsWith(".nbt") && !file.getName().equals(keepName)) {
                Files.deleteIfExists(file.toPath());
            }
        }
    }

    private boolean hasOversizedFiles(int chunkX, int chunkZ, String keepName) {
        File[] files = this.getOversizedDirectory().listFiles();
        if (files == null) {
            return false;
        }

        String prefix = "c." + chunkX + "." + chunkZ + ".";
        for (int i = 0; i < files.length; ++i) {
            String name = files[i].getName();
            if (name.startsWith(prefix) && name.endsWith(".nbt") && !name.equals(keepName)) {
                return true;
            }
        }
        return false;
    }

    private File getOversizedDirectory() {
        return new File(this.a, OVERSIZED_DIRECTORY);
    }

    private void syncSidecarDirectory(File directory) throws IOException {
        FileChannel channel = null;
        try {
            channel = FileChannel.open(directory.toPath(), StandardOpenOption.READ);
            channel.force(true);
        } catch (UnsupportedOperationException unsupported) {
            // Directory fsync is not exposed by every Java/filesystem pair.
        } catch (IOException failure) {
            // Unix-like filesystems support directory channels and need this
            // checkpoint before the region marker can safely become durable.
            if (File.separatorChar == '/') {
                throw failure;
            }
        } finally {
            if (channel != null) {
                channel.close();
            }
        }
    }

    private File getOversizedFile(int chunkX, int chunkZ, String generation) {
        return new File(this.getOversizedDirectory(), "c." + chunkX + "." + chunkZ + "." + generation + ".nbt");
    }
}
