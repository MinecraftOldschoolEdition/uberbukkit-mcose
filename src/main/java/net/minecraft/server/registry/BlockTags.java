package net.minecraft.server.registry;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.server.Block;
import net.minecraft.server.util.ResourceLocation;

/** 26.3-facing block tag identities used by legacy mining behavior. */
public final class BlockTags {
    public static final ResourceLocation REGISTRY_KEY = minecraft("block");
    public static final ResourceLocation REGISTRY = REGISTRY_KEY;

    public static final TagKey<Block> MINEABLE_WITH_PICKAXE =
            create("mineable/pickaxe");
    public static final TagKey<Block> MINEABLE_WITH_AXE =
            create("mineable/axe");
    public static final TagKey<Block> MINEABLE_WITH_SHOVEL =
            create("mineable/shovel");

    public static final TagKey<Block> LEGACY_PICKAXE_6 =
            create("legacy_mining/pickaxe_6");
    public static final TagKey<Block> LEGACY_PICKAXE_2 =
            create("legacy_mining/pickaxe_2");
    public static final TagKey<Block> LEGACY_PICKAXE_1_75 =
            create("legacy_mining/pickaxe_1_75");
    public static final TagKey<Block> LEGACY_AXE_1_5 =
            create("legacy_mining/axe_1_5");
    public static final TagKey<Block> LEGACY_SHOVEL_1_4 =
            create("legacy_mining/shovel_1_4");
    public static final TagKey<Block> LEGACY_SHOVEL_1_5 =
            create("legacy_mining/shovel_1_5");

    /** Present in the common corpus, but applied only by the legacy client. */
    public static final TagKey<Block> CLIENT_AXE_1_5_SUPPLEMENT =
            create("legacy_mining/client_axe_1_5_supplement");
    public static final TagKey<Block> MAINTAINS_FARMLAND =
            create("maintains_farmland");
    public static final TagKey<Block> PREVENTS_NEARBY_LEAF_DECAY =
            create("prevents_nearby_leaf_decay");
    public static final TagKey<Block> CLIMBABLE =
            create("climbable");
    public static final TagKey<Block> ANIMALS_SPAWNABLE_ON =
            create("animals_spawnable_on");

    private static final List<TagKey<Block>> SYNCHRONIZED_MINING_TAGS =
            Collections.unmodifiableList(Arrays.asList(
                    MINEABLE_WITH_PICKAXE,
                    MINEABLE_WITH_AXE,
                    MINEABLE_WITH_SHOVEL,
                    LEGACY_PICKAXE_6,
                    LEGACY_PICKAXE_2,
                    LEGACY_PICKAXE_1_75,
                    LEGACY_AXE_1_5,
                    LEGACY_SHOVEL_1_4,
                    LEGACY_SHOVEL_1_5,
                    MAINTAINS_FARMLAND,
                    PREVENTS_NEARBY_LEAF_DECAY,
                    CLIMBABLE,
                    ANIMALS_SPAWNABLE_ON));

    private static final List<TagKey<Block>> MINING_PROFILES =
            Collections.unmodifiableList(Arrays.asList(
                    LEGACY_PICKAXE_6,
                    LEGACY_PICKAXE_2,
                    LEGACY_PICKAXE_1_75,
                    LEGACY_AXE_1_5,
                    LEGACY_SHOVEL_1_4,
                    LEGACY_SHOVEL_1_5));

    private BlockTags() {}

    public static List<TagKey<Block>> synchronizedMiningTags() {
        return SYNCHRONIZED_MINING_TAGS;
    }

    public static List<TagKey<Block>> synchronizedTags() {
        return SYNCHRONIZED_MINING_TAGS;
    }

    public static boolean is(Block block, TagKey<Block> tag) {
        return BlockMiningRegistryApi.isInTag(tag, block);
    }

    public static List<TagKey<Block>> miningProfiles() {
        return MINING_PROFILES;
    }

    private static TagKey<Block> create(String path) {
        return TagKey.create(REGISTRY, minecraft(path));
    }

    private static ResourceLocation minecraft(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
