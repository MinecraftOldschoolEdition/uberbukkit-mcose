package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import net.minecraft.server.Block;
import net.minecraft.server.IBlockAccess;
import net.minecraft.server.Material;
import net.minecraft.server.TileEntity;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

public class BlockMiningTagParityTest {
    @BeforeClass
    public static void bootstrap() {
        BlockRegistryBootstrap.initialize();
        BlockMiningRegistryBootstrap.initialize();
    }

    @Test
    public void builtInTagProfilesAndSynchronizedOrderMatchLegacyOracle() {
        assertEquals(112, BlockMiningRegistryApi.size());
        assertTrue(BlockMiningRegistryApi.areTagBindingsCurrent());

        RegistryTagBindings<Block> tags = BlockMiningRegistryApi.tagBindings();
        assertEquals(33, tags.valueKeys(BlockTags.MINEABLE_WITH_PICKAXE).size());
        assertEquals(17, tags.valueKeys(BlockTags.MINEABLE_WITH_AXE).size());
        assertEquals(9, tags.valueKeys(BlockTags.MINEABLE_WITH_SHOVEL).size());
        assertEquals(1, tags.valueKeys(BlockTags.LEGACY_PICKAXE_6).size());
        assertEquals(7, tags.valueKeys(BlockTags.LEGACY_PICKAXE_2).size());
        assertEquals(25, tags.valueKeys(BlockTags.LEGACY_PICKAXE_1_75).size());
        assertEquals(17, tags.valueKeys(BlockTags.LEGACY_AXE_1_5).size());
        assertEquals(8, tags.valueKeys(BlockTags.LEGACY_SHOVEL_1_4).size());
        assertEquals(1, tags.valueKeys(BlockTags.LEGACY_SHOVEL_1_5).size());
        assertEquals(3, tags.valueKeys(
                BlockTags.CLIENT_AXE_1_5_SUPPLEMENT).size());

        List<TagKey<Block>> expectedOrder = Arrays.asList(
                BlockTags.MINEABLE_WITH_PICKAXE,
                BlockTags.MINEABLE_WITH_AXE,
                BlockTags.MINEABLE_WITH_SHOVEL,
                BlockTags.LEGACY_PICKAXE_6,
                BlockTags.LEGACY_PICKAXE_2,
                BlockTags.LEGACY_PICKAXE_1_75,
                BlockTags.LEGACY_AXE_1_5,
                BlockTags.LEGACY_SHOVEL_1_4,
                BlockTags.LEGACY_SHOVEL_1_5,
                BlockTags.MAINTAINS_FARMLAND,
                BlockTags.PREVENTS_NEARBY_LEAF_DECAY,
                BlockTags.CLIMBABLE,
                BlockTags.ANIMALS_SPAWNABLE_ON);
        assertEquals(expectedOrder, BlockMiningRegistryApi.synchronizedTagKeys());
        assertFalse(BlockMiningRegistryApi.synchronizedTagKeys().contains(
                BlockTags.CLIENT_AXE_1_5_SUPPLEMENT));
    }

    @Test
    public void effectiveBlockRulesMatchExactServerOracle() {
        int nonVanilla = 0;
        int pickaxe = 0;
        int axe = 0;
        int shovel = 0;
        int pickaxeSix = 0;
        int pickaxeTwo = 0;
        int pickaxeOneSeventyFive = 0;
        for (int i = 0; i < Block.byId.length; i++) {
            Block block = Block.byId[i];
            if (block == null) continue;
            BlockMiningRule rule = BlockMiningRegistryApi.get(block);
            if (rule.getPreferredTool() == MiningToolType.NONE) continue;
            nonVanilla++;
            if (rule.getPreferredTool() == MiningToolType.PICKAXE) {
                pickaxe++;
                if (rule.getSpeedMultiplier() == 6.0F) pickaxeSix++;
                else if (rule.getSpeedMultiplier() == 2.0F) pickaxeTwo++;
                else if (rule.getSpeedMultiplier() == 1.75F) {
                    pickaxeOneSeventyFive++;
                }
            } else if (rule.getPreferredTool() == MiningToolType.AXE) {
                axe++;
            } else if (rule.getPreferredTool() == MiningToolType.SHOVEL) {
                shovel++;
            }
            assertFalse(rule.isEnforcePreferredTool());
            assertFalse(rule.allowsDropsWithoutPreferredTool());
        }
        assertEquals(59, nonVanilla);
        assertEquals(33, pickaxe);
        assertEquals(17, axe);
        assertEquals(9, shovel);
        assertEquals(1, pickaxeSix);
        assertEquals(7, pickaxeTwo);
        assertEquals(25, pickaxeOneSeventyFive);

        assertRule(BlockMiningRegistryApi.get(Block.FENCE_GATE_COMPAT),
                MiningToolType.NONE, false, 1.0F, false);
        assertRule(BlockMiningRegistryApi.get(Block.PUMPKIN_PLAIN),
                MiningToolType.NONE, false, 1.0F, false);
        assertRule(BlockMiningRegistryApi.get(Block.CARVED_PUMPKIN),
                MiningToolType.NONE, false, 1.0F, false);
    }

    @Test
    public void slabMetadataExceptionsRemainExact() {
        SingleBlockAccess access = new SingleBlockAccess();
        Block[] slabs = new Block[]{Block.STEP, Block.DOUBLE_STEP};
        int[] stone = new int[]{0, 1, 3, 4, 5};
        for (int i = 0; i < slabs.length; i++) {
            for (int j = 0; j < stone.length; j++) {
                access.set(slabs[i].id, stone[j]);
                assertRule(BlockMiningRegistryApi.get(access, 0, 0, 0),
                        MiningToolType.PICKAXE, false, 1.75F, false);
            }
            access.set(slabs[i].id, 2);
            assertRule(BlockMiningRegistryApi.get(access, 0, 0, 0),
                    MiningToolType.AXE, true, 1.5F, true);
        }
    }

    private static void assertRule(
            BlockMiningRule rule,
            MiningToolType tool,
            boolean enforced,
            float speed,
            boolean allowDrops) {
        assertEquals(tool, rule.getPreferredTool());
        assertEquals(enforced, rule.isEnforcePreferredTool());
        assertEquals(speed, rule.getSpeedMultiplier(), 0.0F);
        assertEquals(allowDrops, rule.allowsDropsWithoutPreferredTool());
    }

    private static final class SingleBlockAccess implements IBlockAccess {
        private int blockId;
        private int metadata;

        void set(int blockId, int metadata) {
            this.blockId = blockId;
            this.metadata = metadata;
        }

        public int getTypeId(int x, int y, int z) {
            return this.blockId;
        }

        public TileEntity getTileEntity(int x, int y, int z) {
            return null;
        }

        public int getData(int x, int y, int z) {
            return this.metadata;
        }

        public Material getMaterial(int x, int y, int z) {
            Block block = Block.byId[this.blockId];
            return block == null ? Material.AIR : block.material;
        }

        public boolean e(int x, int y, int z) {
            return false;
        }
    }
}
