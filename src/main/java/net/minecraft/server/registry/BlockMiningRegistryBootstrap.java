package net.minecraft.server.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.server.Block;
import net.minecraft.server.BlockStairs;
import net.minecraft.server.Material;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;

/** Bootstrap for mining defaults sourced from 26.3-style block tags. */
public final class BlockMiningRegistryBootstrap {
    private static final BlockMiningRule RULE_AXE_15 =
            new BlockMiningRule(MiningToolType.AXE, false, 1.5F);
    private static final BlockMiningRule RULE_AXE_15_ENFORCED_ALLOW_ANY_DROP =
            new BlockMiningRule(MiningToolType.AXE, true, 1.5F, true);
    private static final BlockMiningRule RULE_PICKAXE_6 =
            new BlockMiningRule(MiningToolType.PICKAXE, false, 6.0F);
    private static final BlockMiningRule RULE_PICKAXE_2 =
            new BlockMiningRule(MiningToolType.PICKAXE, false, 2.0F);
    private static final BlockMiningRule RULE_PICKAXE_175 =
            new BlockMiningRule(MiningToolType.PICKAXE, false, 1.75F);
    private static final BlockMiningRule RULE_SHOVEL_14 =
            new BlockMiningRule(MiningToolType.SHOVEL, false, 1.4F);
    private static final BlockMiningRule RULE_SHOVEL_15 =
            new BlockMiningRule(MiningToolType.SHOVEL, false, 1.5F);

    private static boolean initialized = false;

    private BlockMiningRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;

        final long blockRevision = BlockRegistry.registrationRevision();
        Map<ResourceLocation, List<ResourceLocation>> resolvedTags =
                RegistryDataLoader.loadAllTags(
                        "block",
                        new RegistryDataLoader.TagValueResolver() {
                            public boolean contains(ResourceLocation key) {
                                Block block = BlockRegistry.get(key);
                                ResourceLocation canonical = block == null
                                        ? null : BlockRegistry.getKey(block);
                                return key != null && key.equals(canonical);
                            }
                        });
        RegistryTagBindings<Block> bindings = RegistryTagBindings.create(
                BlockTags.REGISTRY,
                blockRevision,
                resolvedTags,
                new RegistryTagBindings.Resolver<Block>() {
                    public Block get(ResourceLocation key) {
                        return BlockRegistry.get(key);
                    }

                    public ResourceLocation getKey(Block value) {
                        return BlockRegistry.getKey(value);
                    }
                });

        validateRequiredTags(bindings);
        if (BlockRegistry.registrationRevision() != blockRevision) {
            throw new IllegalStateException(
                    "Block registry changed while mining tags were being resolved");
        }

        IdentityHashMap<Block, BlockMiningRule> rules =
                new IdentityHashMap<Block, BlockMiningRule>();
        for (int i = 0; i < Block.byId.length; i++) {
            Block block = Block.byId[i];
            if (block != null) rules.put(block, BlockMiningRule.VANILLA);
        }

        applyTag(rules, bindings, BlockTags.LEGACY_PICKAXE_6, RULE_PICKAXE_6);
        applyTag(rules, bindings, BlockTags.LEGACY_PICKAXE_2, RULE_PICKAXE_2);
        applyTag(rules, bindings, BlockTags.LEGACY_PICKAXE_1_75,
                RULE_PICKAXE_175);
        applyTag(rules, bindings, BlockTags.LEGACY_AXE_1_5, RULE_AXE_15);
        applyTag(rules, bindings, BlockTags.LEGACY_SHOVEL_1_4,
                RULE_SHOVEL_14);
        applyTag(rules, bindings, BlockTags.LEGACY_SHOVEL_1_5,
                RULE_SHOVEL_15);

        // Legacy mods may register stairs before central bootstrap. Preserve
        // the existing material-based extension rule without treating engine
        // class discovery as vanilla data membership.
        for (int i = 0; i < Block.byId.length; i++) {
            Block block = Block.byId[i];
            if (!(block instanceof BlockStairs)) continue;
            if (block == Block.WOOD_STAIRS
                    || block == Block.COBBLESTONE_STAIRS
                    || block == Block.BRICK_STAIRS
                    || block == Block.STONE_BRICK_STAIRS) {
                continue;
            }
            if (block.material == Material.WOOD) {
                if (!bindings.contains(BlockTags.MINEABLE_WITH_AXE, block)) {
                    rules.put(block, RULE_AXE_15);
                }
            } else if (!bindings.contains(BlockTags.MINEABLE_WITH_PICKAXE, block)) {
                rules.put(block, RULE_PICKAXE_175);
            }
        }

        // Slab metadata remains a legacy state rule rather than a block tag:
        // metadata 2 is wood even though both block IDs default to stone.
        IdentityHashMap<Block, Map<Integer, BlockMiningRule>> metadataRules =
                new IdentityHashMap<Block, Map<Integer, BlockMiningRule>>();
        stageSlabRules(metadataRules, Block.STEP);
        stageSlabRules(metadataRules, Block.DOUBLE_STEP);

        BlockMiningRegistryApi.publishBootstrap(
                rules, metadataRules, bindings, blockRevision);
        initialized = true;
        System.out.println("[BlockMiningRegistryBootstrap] Registered "
                + BlockMiningRegistryApi.size() + " block mining rules from "
                + bindings.tagKeys().size() + " block tags");
    }

    private static void validateRequiredTags(
            RegistryTagBindings<Block> bindings) {
        requirePresent(bindings, BlockTags.LEGACY_PICKAXE_6);
        requirePresent(bindings, BlockTags.LEGACY_PICKAXE_2);
        requirePresent(bindings, BlockTags.LEGACY_PICKAXE_1_75);
        requirePresent(bindings, BlockTags.LEGACY_AXE_1_5);
        requirePresent(bindings, BlockTags.LEGACY_SHOVEL_1_4);
        requirePresent(bindings, BlockTags.LEGACY_SHOVEL_1_5);

        requireAggregate(bindings, BlockTags.MINEABLE_WITH_PICKAXE,
                BlockTags.LEGACY_PICKAXE_6,
                BlockTags.LEGACY_PICKAXE_2,
                BlockTags.LEGACY_PICKAXE_1_75);
        requireAggregate(bindings, BlockTags.MINEABLE_WITH_AXE,
                BlockTags.LEGACY_AXE_1_5);
        requireAggregate(bindings, BlockTags.MINEABLE_WITH_SHOVEL,
                BlockTags.LEGACY_SHOVEL_1_4,
                BlockTags.LEGACY_SHOVEL_1_5);

        IdentityHashMap<Block, TagKey<Block>> owner =
                new IdentityHashMap<Block, TagKey<Block>>();
        List<TagKey<Block>> profiles = BlockTags.miningProfiles();
        for (int i = 0; i < profiles.size(); i++) {
            TagKey<Block> profile = profiles.get(i);
            List<Block> values = bindings.values(profile);
            for (int j = 0; j < values.size(); j++) {
                TagKey<Block> previous = owner.put(values.get(j), profile);
                if (previous != null) {
                    throw new IllegalStateException("Mining profile overlap: "
                            + previous.location() + " and " + profile.location()
                            + " contain " + BlockRegistry.getKey(values.get(j)));
                }
            }
        }
    }

    private static void requirePresent(
            RegistryTagBindings<Block> bindings,
            TagKey<Block> tag) {
        if (!bindings.tagKeys().contains(tag)) {
            throw new IllegalStateException(
                    "Missing required mining tag " + tag.location()
                            + "; loaded " + bindings.tagKeys());
        }
    }

    @SafeVarargs
    private static void requireAggregate(
            RegistryTagBindings<Block> bindings,
            TagKey<Block> aggregate,
            TagKey<Block>... profiles) {
        LinkedHashSet<ResourceLocation> expected =
                new LinkedHashSet<ResourceLocation>();
        for (int i = 0; i < profiles.length; i++) {
            expected.addAll(bindings.valueKeys(profiles[i]));
        }
        List<ResourceLocation> actual = bindings.valueKeys(aggregate);
        if (!actual.equals(new ArrayList<ResourceLocation>(expected))) {
            throw new IllegalStateException("Mining aggregate "
                    + aggregate.location() + " does not exactly match its profiles");
        }
    }

    private static void applyTag(
            Map<Block, BlockMiningRule> rules,
            RegistryTagBindings<Block> bindings,
            TagKey<Block> tag,
            BlockMiningRule rule) {
        List<Block> blocks = bindings.values(tag);
        for (int i = 0; i < blocks.size(); i++) {
            rules.put(blocks.get(i), rule);
        }
    }

    private static void stageSlabRules(
            Map<Block, Map<Integer, BlockMiningRule>> staged,
            Block slab) {
        HashMap<Integer, BlockMiningRule> rules =
                new HashMap<Integer, BlockMiningRule>();
        int[] stoneLikeMetadata = new int[]{0, 1, 3, 4, 5};
        for (int i = 0; i < stoneLikeMetadata.length; i++) {
            rules.put(Integer.valueOf(stoneLikeMetadata[i]), RULE_PICKAXE_175);
        }
        rules.put(Integer.valueOf(2), RULE_AXE_15_ENFORCED_ALLOW_ANY_DROP);
        staged.put(slab, Collections.unmodifiableMap(rules));
    }
}
