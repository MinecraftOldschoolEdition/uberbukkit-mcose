package net.minecraft.server;

import net.minecraft.server.registry.ItemRegistry;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RegionCoreWorldUpgraderCoverageTest {
    private static final Logger TEST_LOGGER = Logger.getLogger(RegionCoreWorldUpgraderCoverageTest.class.getName());
    private static final IProgressUpdate NO_PROGRESS = new IProgressUpdate() {
        public void a(String message) {}
        public void b(String message) {}
        public void a(int progress) {}
    };

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void initializeRegistries() {
        assertNotNull(Block.STONE);
        assertNotNull(Item.SIGN);
    }

    @After
    public void closeRegionFiles() {
        RegionFileCache.a();
    }

    @Test
    public void conversionKeepsEveryLegacyBlockIdAndMetadataInRegionChunks() throws Exception {
        File world = this.temporaryFolder.newFolder("block-coverage-world");
        NBTTagCompound levelRoot = this.levelRoot();
        levelRoot.k("Data").a("version", WorldSaveVersions.MCREGION_1);
        this.writeCompressed(new File(world, "level.dat"), levelRoot);

        byte[] blocks = new byte[32768];
        byte[] metadata = new byte[16384];
        int position = 0;
        for (int blockId = 0; blockId < Block.byId.length; ++blockId) {
            for (int blockMeta = 0; blockMeta < 16; ++blockMeta) {
                blocks[position] = (byte)blockId;
                setNibble(metadata, position, blockMeta);
                ++position;
            }
        }

        NBTTagCompound chunkRoot = new NBTTagCompound();
        NBTTagCompound chunkLevel = new NBTTagCompound();
        chunkLevel.a("xPos", 0);
        chunkLevel.a("zPos", 0);
        chunkLevel.a("Blocks", blocks);
        chunkLevel.a("Data", metadata);

        NBTTagCompound painting = new NBTTagCompound();
        painting.setString("id", "Painting");
        painting.setString("Motive", "SkullAndRoses");
        painting.a("TileX", 5);
        painting.a("TileY", 70);
        painting.a("TileZ", -3);
        painting.a("Dir", (byte)2);
        NBTTagList entities = new NBTTagList();
        entities.a(painting);
        chunkLevel.a("Entities", entities);

        NBTTagCompound sign = new NBTTagCompound();
        sign.setString("id", "Sign");
        sign.a("x", 7);
        sign.a("y", 65);
        sign.a("z", 9);
        sign.setString("Text1", "beta");
        sign.setString("Text2", "block");
        sign.setString("Text3", "model");
        sign.setString("Text4", "sign");
        NBTTagList tileEntities = new NBTTagList();
        tileEntities.a(sign);
        chunkLevel.a("TileEntities", tileEntities);
        chunkRoot.a("Level", chunkLevel);

        DataOutputStream output = RegionFileCache.d(world, 0, 0);
        assertNotNull(output);
        CompressedStreamTools.a(chunkRoot, (DataOutput)output);
        output.close();
        RegionFileCache.a();

        RegionCoreWorldUpgrader.upgradeWorldToRegionCore(world, TEST_LOGGER);

        DataInputStream input = RegionFileCache.c(world, 0, 0);
        assertNotNull(input);
        NBTTagCompound convertedRoot = CompressedStreamTools.a((DataInput)input);
        input.close();
        NBTTagCompound convertedLevel = convertedRoot.k("Level");
        assertFalse(convertedLevel.hasKey("Blocks"));
        assertFalse(convertedLevel.hasKey("Data"));

        BlockStateCodec.DecodedState decoded = BlockStateCodec.readStateData(convertedLevel);
        assertNotNull(decoded);
        assertFalse(decoded.usedNearestFallback);
        for (int index = 0; index < position; ++index) {
            int blockId = blocks[index] & 255;
            int blockMetadata = index & 15;
            assertEquals("block at converted position " + index,
                    expectedRegionCoreProjectionId(blockId, blockMetadata), decoded.blocks[index] & 255);
        }

        assertTrue(paletteContains(convertedLevel, "minecraft:snow"));
        assertTrue(paletteContains(convertedLevel, "minecraft:snow_block"));
        for (int snowMetadata = 0; snowMetadata < 16; ++snowMetadata) {
            int snowLayerPosition = Block.SNOW.id * 16 + snowMetadata;
            int snowBlockPosition = Block.SNOW_BLOCK.id * 16 + snowMetadata;
            assertEquals(Block.SNOW.id, decoded.blocks[snowLayerPosition] & 255);
            assertEquals(snowMetadata, getNibble(decoded.metadata, snowLayerPosition));
            assertEquals(Block.SNOW_BLOCK.id, decoded.blocks[snowBlockPosition] & 255);
            assertEquals(snowMetadata, getNibble(decoded.metadata, snowBlockPosition));
        }
        assertBlockModelMetadataPreserved(decoded);

        assertBlockModelVisualPayloadsPreserved(convertedLevel);
        assertEquals(WorldSaveVersions.currentWriteVersion(), this.readCompressed(
                new File(world, "level.dat")).k("Data").e("version"));
    }

    @Test
    public void alphaChunkConversionPreservesBlockModelStatesAndVisualPayloads() throws Exception {
        File saves = this.temporaryFolder.newFolder("alpha-block-model-saves");
        File world = new File(saves, "AlphaBlockModelWorld");
        assertTrue(world.mkdirs());
        NBTTagCompound levelRoot = this.levelRoot();
        levelRoot.k("Data").a("version", WorldSaveVersions.LEGACY_PRE_MCREGION);
        this.writeCompressed(new File(world, "level.dat"), levelRoot);

        File legacyChunkDirectory = new File(new File(world, "0"), "0");
        assertTrue(legacyChunkDirectory.mkdirs());
        this.writeCompressed(new File(legacyChunkDirectory, "c.0.0.dat"), blockModelCoverageChunkRoot());

        WorldLoaderServer converter = new WorldLoaderServer(saves);
        assertTrue(converter.convert("AlphaBlockModelWorld", NO_PROGRESS));

        DataInputStream input = RegionFileCache.c(world, 0, 0);
        assertNotNull(input);
        NBTTagCompound convertedRoot = CompressedStreamTools.a((DataInput)input);
        input.close();
        NBTTagCompound convertedLevel = convertedRoot.k("Level");
        BlockStateCodec.DecodedState decoded = BlockStateCodec.readStateData(convertedLevel);
        assertNotNull(decoded);
        assertFalse(decoded.usedNearestFallback);
        assertBlockModelMetadataPreserved(decoded);
        assertBlockModelVisualPayloadsPreserved(convertedLevel);
        assertEquals(WorldSaveVersions.currentWriteVersion(), this.readCompressed(
                new File(world, "level.dat")).k("Data").e("version"));
    }

    private static void assertBlockModelVisualPayloadsPreserved(NBTTagCompound convertedLevel) {
        NBTTagCompound convertedPainting = (NBTTagCompound)convertedLevel.l("Entities").a(0);
        assertEquals("Painting", convertedPainting.getString("id"));
        assertEquals("SkullAndRoses", convertedPainting.getString("Motive"));
        assertEquals(5, convertedPainting.e("TileX"));
        assertEquals(70, convertedPainting.e("TileY"));
        assertEquals(-3, convertedPainting.e("TileZ"));
        assertEquals(2, convertedPainting.c("Dir"));
        NBTTagCompound convertedSign = (NBTTagCompound)convertedLevel.l("TileEntities").a(0);
        assertEquals("Sign", convertedSign.getString("id"));
        assertEquals("beta", convertedSign.getString("Text1"));
        assertEquals("block", convertedSign.getString("Text2"));
        assertEquals("model", convertedSign.getString("Text3"));
        assertEquals("sign", convertedSign.getString("Text4"));
    }

    private static NBTTagCompound blockModelCoverageChunkRoot() {
        byte[] blocks = new byte[32768];
        byte[] metadata = new byte[16384];
        populateMetadataCoverage(blocks, metadata, Block.SIGN_POST.id, range(0, 15));
        populateMetadataCoverage(blocks, metadata, Block.WALL_SIGN.id, new int[]{2, 3, 4, 5});
        populateMetadataCoverage(blocks, metadata, Block.WOODEN_DOOR.id, range(0, 15));
        populateMetadataCoverage(blocks, metadata, Block.IRON_DOOR_BLOCK.id, range(0, 15));
        populateMetadataCoverage(blocks, metadata, Block.BED.id, range(0, 15));
        int[] pistonMetadata = new int[]{0, 1, 2, 3, 4, 5, 8, 9, 10, 11, 12, 13};
        populateMetadataCoverage(blocks, metadata, Block.PISTON.id, pistonMetadata);
        populateMetadataCoverage(blocks, metadata, Block.PISTON_STICKY.id, pistonMetadata);
        populateMetadataCoverage(blocks, metadata, Block.PISTON_EXTENSION.id, pistonMetadata);
        populateMetadataCoverage(blocks, metadata, Block.PISTON_MOVING.id, pistonMetadata);

        NBTTagCompound chunkLevel = new NBTTagCompound();
        chunkLevel.a("xPos", 0);
        chunkLevel.a("zPos", 0);
        chunkLevel.a("Blocks", blocks);
        chunkLevel.a("Data", metadata);

        NBTTagCompound painting = new NBTTagCompound();
        painting.setString("id", "Painting");
        painting.setString("Motive", "SkullAndRoses");
        painting.a("TileX", 5);
        painting.a("TileY", 70);
        painting.a("TileZ", -3);
        painting.a("Dir", (byte)2);
        NBTTagList entities = new NBTTagList();
        entities.a(painting);
        chunkLevel.a("Entities", entities);

        NBTTagCompound sign = new NBTTagCompound();
        sign.setString("id", "Sign");
        sign.a("x", 7);
        sign.a("y", 65);
        sign.a("z", 9);
        sign.setString("Text1", "beta");
        sign.setString("Text2", "block");
        sign.setString("Text3", "model");
        sign.setString("Text4", "sign");
        NBTTagList tileEntities = new NBTTagList();
        tileEntities.a(sign);
        chunkLevel.a("TileEntities", tileEntities);

        NBTTagCompound chunkRoot = new NBTTagCompound();
        chunkRoot.a("Level", chunkLevel);
        return chunkRoot;
    }

    private static void populateMetadataCoverage(byte[] blocks, byte[] metadata, int blockId,
                                                 int[] metadataValues) {
        for (int blockMetadata : metadataValues) {
            int position = blockId * 16 + blockMetadata;
            blocks[position] = (byte)blockId;
            setNibble(metadata, position, blockMetadata);
        }
    }

    private static void assertBlockModelMetadataPreserved(BlockStateCodec.DecodedState decoded) {
        assertMetadataPreserved(decoded, Block.SIGN_POST.id, range(0, 15));
        assertMetadataPreserved(decoded, Block.WALL_SIGN.id, new int[]{2, 3, 4, 5});
        assertMetadataPreserved(decoded, Block.WOODEN_DOOR.id, range(0, 15));
        assertMetadataPreserved(decoded, Block.IRON_DOOR_BLOCK.id, range(0, 15));
        assertMetadataPreserved(decoded, Block.BED.id, range(0, 15));
        int[] pistonMetadata = new int[]{0, 1, 2, 3, 4, 5, 8, 9, 10, 11, 12, 13};
        assertMetadataPreserved(decoded, Block.PISTON.id, pistonMetadata);
        assertMetadataPreserved(decoded, Block.PISTON_STICKY.id, pistonMetadata);
        assertMetadataPreserved(decoded, Block.PISTON_EXTENSION.id, pistonMetadata);
        assertMetadataPreserved(decoded, Block.PISTON_MOVING.id, pistonMetadata);
    }

    private static void assertMetadataPreserved(BlockStateCodec.DecodedState decoded, int blockId,
                                                int[] metadataValues) {
        for (int metadata : metadataValues) {
            int position = blockId * 16 + metadata;
            assertEquals("converted block id " + blockId + ":" + metadata,
                    blockId, decoded.blocks[position] & 255);
            assertEquals("converted block metadata " + blockId + ":" + metadata,
                    metadata, getNibble(decoded.metadata, position));
        }
    }

    private static int[] range(int first, int last) {
        int[] values = new int[last - first + 1];
        for (int index = 0; index < values.length; ++index) {
            values[index] = first + index;
        }
        return values;
    }

    @Test
    public void conversionRewritesEveryPopulatedLegacyItemIdAndMetadata() throws Exception {
        File world = this.temporaryFolder.newFolder("item-coverage-world");
        File data = new File(world, "data");
        assertTrue(data.mkdirs());
        this.writeCompressed(new File(world, "level.dat"), this.levelRoot());

        NBTTagList legacyStacks = new NBTTagList();
        int registeredItems = 0;
        for (int itemId = 0; itemId < Item.byId.length; ++itemId) {
            if (Item.byId[itemId] == null) {
                continue;
            }
            ++registeredItems;
            for (int metadata = 0; metadata < 16; ++metadata) {
                NBTTagCompound stack = new NBTTagCompound();
                stack.a("id", (short)itemId);
                stack.a("Count", (byte)1);
                stack.a("Damage", (short)metadata);
                stack.a("audit_id", itemId);
                stack.a("audit_metadata", metadata);
                legacyStacks.a(stack);
            }
        }
        assertEquals(239, registeredItems);

        NBTTagCompound auditRoot = new NBTTagCompound();
        auditRoot.a("AllLegacyItems", legacyStacks);
        File auditFile = new File(data, "all-legacy-items.dat");
        this.writeCompressed(auditFile, auditRoot);

        RegionCoreWorldUpgrader.upgradeWorldToRegionCore(world, TEST_LOGGER);

        NBTTagList convertedStacks = this.readCompressed(auditFile).l("AllLegacyItems");
        assertEquals(registeredItems * 16, convertedStacks.c());
        for (int index = 0; index < convertedStacks.c(); ++index) {
            NBTTagCompound converted = (NBTTagCompound)convertedStacks.a(index);
            int sourceId = converted.e("audit_id");
            int metadata = converted.e("audit_metadata");
            int expectedId = normalizedInventoryItemId(sourceId);
            String context = "converted legacy item ID " + sourceId + ", metadata " + metadata;

            assertEquals(context + " format", 3, converted.e("mcose_stack_format"));
            assertEquals(context + " key", ItemRegistry.getKey(Item.byId[expectedId]).toString(),
                    converted.getString("item"));
            assertEquals(context + " count", 1, converted.e("count"));
            assertFalse(context + " retained numeric ID", converted.hasKey("id"));
            assertFalse(context + " retained legacy count", converted.hasKey("Count"));
            assertFalse(context + " retained legacy damage", converted.hasKey("Damage"));

            ItemStack decoded = ItemStack.parse(converted);
            assertNotNull(context + " decoded stack", decoded);
            assertEquals(context + " decoded ID", expectedId, decoded.id);
            assertEquals(context + " decoded metadata", metadata, decoded.getItemDamage());
        }
    }

    private NBTTagCompound levelRoot() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound data = new NBTTagCompound();
        data.a("version", WorldSaveVersions.REGIONCORE_1);
        data.setString("LevelName", "RegionCore coverage test");
        data.setLong("RandomSeed", 1L);
        root.a("Data", data);
        return root;
    }

    private static int normalizedInventoryItemId(int itemId) {
        if (itemId == Block.REDSTONE_TORCH_OFF.id) return Block.REDSTONE_TORCH_ON.id;
        if (itemId == Block.SIGN_POST.id || itemId == Block.WALL_SIGN.id) return Item.SIGN.id;
        if (itemId == Block.SUGAR_CANE_BLOCK.id) return Item.SUGAR_CANE.id;
        if (itemId == Block.WOODEN_DOOR.id) return Item.WOOD_DOOR.id;
        if (itemId == Block.IRON_DOOR_BLOCK.id) return Item.IRON_DOOR.id;
        if (itemId == Block.BED.id) return Item.BED.id;
        if (itemId == Block.CAKE_BLOCK.id) return Item.CAKE.id;
        if (itemId == Block.DIODE_OFF.id || itemId == Block.DIODE_ON.id) return Item.DIODE.id;
        return itemId;
    }

    private static int expectedRegionCoreProjectionId(int blockId, int metadata) {
        if (blockId == Block.PUMPKIN.id) {
            return metadata <= 3 ? Block.CARVED_PUMPKIN.id : Block.PUMPKIN_PLAIN.id;
        }
        if (blockId == Block.FENCE_GATE_COMPAT.id) {
            return Block.FENCE_GATE.id;
        }
        if (blockId == Block.RED_MUSHROOM_CAP.id && (metadata == 10 || metadata == 15)) {
            return Block.BROWN_MUSHROOM_CAP.id;
        }
        return blockId;
    }

    private static void setNibble(byte[] values, int index, int value) {
        int byteIndex = index >> 1;
        int previous = values[byteIndex] & 255;
        values[byteIndex] = (index & 1) == 0
                ? (byte)((previous & 240) | (value & 15))
                : (byte)((previous & 15) | ((value & 15) << 4));
    }

    private static int getNibble(byte[] values, int index) {
        int value = values[index >> 1] & 255;
        return (index & 1) == 0 ? value & 15 : value >> 4 & 15;
    }

    private static boolean paletteContains(NBTTagCompound level, String name) {
        NBTTagList palette = level.l(BlockStateCodec.KEY_PALETTE);
        for (int index = 0; index < palette.c(); ++index) {
            NBTBase entry = palette.a(index);
            if (entry instanceof NBTTagCompound && name.equals(((NBTTagCompound)entry).getString("Name"))) {
                return true;
            }
        }
        return false;
    }

    private void writeCompressed(File file, NBTTagCompound root) throws Exception {
        CompressedStreamTools.a(root, new FileOutputStream(file));
    }

    private NBTTagCompound readCompressed(File file) throws Exception {
        return CompressedStreamTools.a(new FileInputStream(file));
    }
}
