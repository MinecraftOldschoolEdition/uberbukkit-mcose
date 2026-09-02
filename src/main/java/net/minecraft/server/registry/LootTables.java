package net.minecraft.server.registry;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.Block;
import net.minecraft.server.EntityLiving;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;

/**
 * Loads the built-in loot tables from the modern
 * {@code data/<namespace>/loot_table} tree.
 */
public final class LootTables {
    public static final ResourceLocation DUNGEON =
            new ResourceLocation("minecraft", "chests/dungeon");
    public static final ResourceLocation SIMPLE_DUNGEON_LOOT =
            new ResourceLocation("minecraft", "chests/simple_dungeon");
    public static final ResourceLocation MONSTER_DUNGEON_LOOT =
            new ResourceLocation("minecraft", "chests/monster_dungeon");
    public static final ResourceLocation ZOMBIE = entityTable("zombie");
    public static final ResourceLocation SKELETON = entityTable("skeleton");
    public static final ResourceLocation CREEPER = entityTable("creeper");
    public static final ResourceLocation SPIDER = entityTable("spider");
    public static final ResourceLocation PIG = entityTable("pig");
    public static final ResourceLocation COW = entityTable("cow");
    public static final ResourceLocation CHICKEN = entityTable("chicken");
    public static final ResourceLocation SHEEP = entityTable("sheep");
    public static final ResourceLocation SQUID = entityTable("squid");
    public static final ResourceLocation SLIME = entityTable("slime");
    public static final ResourceLocation GHAST = entityTable("ghast");
    public static final ResourceLocation ZOMBIFIED_PIGLIN = entityTable("zombified_piglin");
    public static final ResourceLocation SNOW_GOLEM = entityTable("snow_golem");

    private static final List<ResourceLocation> BUILT_IN_TABLES =
            Collections.unmodifiableList(Arrays.asList(
                    DUNGEON,
                    SIMPLE_DUNGEON_LOOT,
                    MONSTER_DUNGEON_LOOT,
                    ZOMBIE,
                    SKELETON,
                    CREEPER,
                    SPIDER,
                    PIG,
                    COW,
                    CHICKEN,
                    SHEEP,
                    SQUID,
                    SLIME,
                    GHAST,
                    ZOMBIFIED_PIGLIN,
                    SNOW_GOLEM));

    private static volatile boolean initialized;
    private static volatile BlockLootBindings blockLootBindings =
            BlockLootBindings.empty();

    private LootTables() {}

    public static void initialize() {
        if (initialized) return;
        initializeSlow();
    }

    private static synchronized void initializeSlow() {
        if (initialized) return;

        BuiltInBlockCapture builtInCapture = captureBuiltInBlocks();
        List<BuiltInBlock> builtInBlocks = builtInCapture.blocks;

        Map<ResourceLocation, LootTable> decoded = RegistryDataLoader.loadAll(
                "loot_table",
                new RegistryDataLoader.Decoder<LootTable>() {
                    public LootTable decode(ResourceLocation key, JsonObject json) {
                        return LootTableCodec.decode(key, json);
                    }
                });
        for (int i = 0; i < BUILT_IN_TABLES.size(); i++) {
            ResourceLocation key = BUILT_IN_TABLES.get(i);
            if (!decoded.containsKey(key)) {
                throw new IllegalStateException("Missing required built-in loot table: " + key);
            }
            if (key.getPath().startsWith("entities/")
                    && !(decoded.get(key) instanceof EntityLootTable)) {
                throw new IllegalStateException(
                        "Entity loot table has the wrong decoded type: " + key);
            }
        }

        IdentityHashMap<Block, BlockLootTable> blockTables =
                new IdentityHashMap<Block, BlockLootTable>();
        IdentityHashMap<Block, ResourceLocation> canonicalBlockKeys =
                new IdentityHashMap<Block, ResourceLocation>();
        ArrayList<ResourceLocation> requiredTables =
                new ArrayList<ResourceLocation>(BUILT_IN_TABLES);
        for (int i = 0; i < builtInBlocks.size(); i++) {
            BuiltInBlock builtIn = builtInBlocks.get(i);
            ResourceLocation tableKey = blockLootKey(builtIn.key);
            LootTable table = decoded.get(tableKey);
            if (!(table instanceof BlockLootTable)) {
                throw new IllegalStateException(
                        "Missing required built-in block loot table: " + tableKey);
            }
            blockTables.put(builtIn.block, (BlockLootTable)table);
            canonicalBlockKeys.put(builtIn.block, builtIn.key);
            requiredTables.add(tableKey);
        }

        final BlockLootBindings stagedBindings = new BlockLootBindings(
                blockTables,
                canonicalBlockKeys,
                requiredTables);
        final Map<ResourceLocation, LootTable> stagedTables = decoded;
        final boolean[] tablesPublished = new boolean[] {false};
        boolean stableBlockGeneration = BlockRegistry.publishIfRevision(
                builtInCapture.blockRegistryRevision,
                new Runnable() {
                    public void run() {
                        tablesPublished[0] =
                                LootTableRegistryApi.publishAtomic(stagedTables);
                        if (tablesPublished[0]) {
                            blockLootBindings = stagedBindings;
                        }
                    }
                });
        if (!stableBlockGeneration || !tablesPublished[0]) {
            throw new IllegalStateException(
                    "Built-in loot tables could not be published atomically "
                            + "against their captured block generation");
        }
        initialized = true;
    }

    public static LootTable get(ResourceLocation id) {
        initialize();
        return LootTableRegistryApi.get(id);
    }

    public static LootTable getByIdentifier(String any) {
        initialize();
        return LootTableRegistryApi.getByIdentifier(any);
    }

    public static ResourceLocation getKey(LootTable value) {
        initialize();
        return LootTableRegistryApi.getKey(value);
    }

    public static Set<ResourceLocation> keys() {
        initialize();
        return LootTableRegistryApi.keys();
    }

    public static Collection<LootTable> values() {
        initialize();
        return LootTableRegistryApi.values();
    }

    public static int size() {
        initialize();
        return LootTableRegistryApi.size();
    }

    public static List<ResourceLocation> builtInTableKeys() {
        initialize();
        return blockLootBindings.requiredTableKeys;
    }

    /** Returns the table bound to this exact declared vanilla block identity. */
    public static BlockLootTable getBlockLootTable(Block block) {
        if (block == null) return null;
        initialize();
        return blockLootBindings.tables.get(block);
    }

    /** True only for identities declared by {@link VanillaRegistryKeys}. */
    public static boolean isBuiltInBlock(Block block) {
        if (block == null) return false;
        initialize();
        return blockLootBindings.canonicalKeys.containsKey(block);
    }

    /** Returns the captured canonical key for an exact vanilla identity. */
    public static ResourceLocation getBuiltInBlockKey(Block block) {
        if (block == null) return null;
        initialize();
        return blockLootBindings.canonicalKeys.get(block);
    }

    public static ResourceLocation entityLootKey(EntityLiving entity) {
        if (entity == null) return null;
        ResourceLocation entityKey = EntityTypeRegistry.getKey(entity.getClass());
        if (entityKey == null) return null;
        return new ResourceLocation(
                entityKey.getNamespace(),
                "entities/" + entityKey.getPath());
    }

    public static EntityLootTable getEntityLootTable(EntityLiving entity) {
        ResourceLocation key = entityLootKey(entity);
        if (key == null) return null;
        LootTable table = get(key);
        return table instanceof EntityLootTable ? (EntityLootTable)table : null;
    }

    public static String normalizeInputIdentifier(String any) {
        return LootTableRegistryApi.normalizeInputIdentifier(any);
    }

    public static String canonicalizeIdentifier(String any) {
        return LootTableRegistryApi.canonicalizeIdentifier(any);
    }

    private static ResourceLocation entityTable(String path) {
        return new ResourceLocation("minecraft", "entities/" + path);
    }

    private static BuiltInBlockCapture captureBuiltInBlocks() {
        BlockRegistry.bootstrapFromBlocksList();
        long blockRegistryRevision = BlockRegistry.getRegistrationRevision();
        IdentityHashMap<Block, ResourceLocation> seen =
                new IdentityHashMap<Block, ResourceLocation>();
        ArrayList<BuiltInBlock> result = new ArrayList<BuiltInBlock>();
        for (int legacyId = 0; legacyId < Block.byId.length; legacyId++) {
            Block block = Block.byId[legacyId];
            if (block == null) continue;

            ResourceLocation key = VanillaRegistryKeys.blockKey(block);
            if (key == null) {
                // Blocks introduced by plugins or mods retain their legacy
                // virtual drop hooks unless they opt into a future binding.
                continue;
            }

            ResourceLocation primaryKey = BlockRegistry.getKey(block);
            Block canonicalBlock = BlockRegistry.get(key);
            if (!key.equals(primaryKey) || canonicalBlock != block) {
                throw new IllegalStateException(
                        "Vanilla block key is not canonically bound: " + key);
            }
            ResourceLocation previous = seen.put(block, key);
            if (previous != null) {
                throw new IllegalStateException(
                        "Vanilla block has multiple declared keys: "
                                + previous + " and " + key);
            }
            result.add(new BuiltInBlock(key, block));
        }
        Collections.sort(result, new Comparator<BuiltInBlock>() {
            public int compare(BuiltInBlock left, BuiltInBlock right) {
                return left.key.toString().compareTo(right.key.toString());
            }
        });
        if (blockRegistryRevision != BlockRegistry.getRegistrationRevision()) {
            throw new IllegalStateException(
                    "Block registry changed while vanilla block loot keys "
                            + "were captured");
        }
        return new BuiltInBlockCapture(blockRegistryRevision, result);
    }

    private static ResourceLocation blockLootKey(ResourceLocation blockKey) {
        return new ResourceLocation(
                blockKey.getNamespace(),
                "blocks/" + blockKey.getPath());
    }

    private static final class BuiltInBlock {
        final ResourceLocation key;
        final Block block;

        BuiltInBlock(ResourceLocation key, Block block) {
            this.key = key;
            this.block = block;
        }
    }

    private static final class BuiltInBlockCapture {
        final long blockRegistryRevision;
        final List<BuiltInBlock> blocks;

        BuiltInBlockCapture(long blockRegistryRevision, List<BuiltInBlock> blocks) {
            this.blockRegistryRevision = blockRegistryRevision;
            this.blocks = Collections.unmodifiableList(
                    new ArrayList<BuiltInBlock>(blocks));
        }
    }

    private static final class BlockLootBindings {
        final IdentityHashMap<Block, BlockLootTable> tables;
        final IdentityHashMap<Block, ResourceLocation> canonicalKeys;
        final List<ResourceLocation> requiredTableKeys;

        BlockLootBindings(
                IdentityHashMap<Block, BlockLootTable> tables,
                IdentityHashMap<Block, ResourceLocation> canonicalKeys,
                List<ResourceLocation> requiredTableKeys) {
            this.tables = new IdentityHashMap<Block, BlockLootTable>(tables);
            this.canonicalKeys =
                    new IdentityHashMap<Block, ResourceLocation>(canonicalKeys);
            this.requiredTableKeys = Collections.unmodifiableList(
                    new ArrayList<ResourceLocation>(requiredTableKeys));
        }

        static BlockLootBindings empty() {
            return new BlockLootBindings(
                    new IdentityHashMap<Block, BlockLootTable>(),
                    new IdentityHashMap<Block, ResourceLocation>(),
                    Collections.<ResourceLocation>emptyList());
        }
    }
}
