package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

public class ContainerInputBehaviorTest {
    @BeforeClass
    public static void initializeBlocksBeforeItems() {
        assertNotNull(Block.STONE);
    }

    @Test
    public void hotbarSwapIsAtomicAndUsesTheRequestedHotbarIndex() throws Exception {
        TestHuman player = player();
        TestContainer container = new TestContainer();
        TestInventory chest = new TestInventory(1);
        ItemStack chestStack = new ItemStack(Block.STONE, 12);
        ItemStack hotbarStack = new ItemStack(Block.DIRT, 7);
        chest.setItem(0, chestStack);
        player.inventory.setItem(4, hotbarStack);
        container.add(chest, 0);

        container.a(0, 4, ContainerInput.SWAP, player);

        assertSame(hotbarStack, chest.getItem(0));
        assertSame(chestStack, player.inventory.getItem(4));
        assertNull(player.inventory.j());
    }

    @Test
    public void pickupAllConsumesPartialStacksBeforeFullStacks() throws Exception {
        TestHuman player = player();
        TestContainer container = new TestContainer();
        TestInventory inventory = new TestInventory(4);
        inventory.setItem(0, new ItemStack(Block.STONE, 64));
        inventory.setItem(1, new ItemStack(Block.STONE, 5));
        inventory.setItem(2, new ItemStack(Block.STONE, 20));
        for (int i = 0; i < 4; ++i) {
            container.add(inventory, i);
        }
        player.inventory.b(new ItemStack(Block.STONE, 10));

        container.a(3, 0, ContainerInput.PICKUP_ALL, player);

        assertEquals(64, player.inventory.j().count);
        assertNull(inventory.getItem(1));
        assertNull(inventory.getItem(2));
        assertEquals(35, inventory.getItem(0).count);
    }

    @Test
    public void pickupAllExcludesCraftingResultsButIncludesFurnaceResults() throws Exception {
        TestHuman player = player();
        TestContainer container = new TestContainer();
        TestInventory input = new TestInventory(1);
        TestInventory result = new TestInventory(1);

        assertFalse(container.canTakeItemForPickAll(new SlotResult(player, input, result, 0, 0, 0)));
        assertTrue(container.canTakeItemForPickAll(new SlotResult2(player, result, 0, 0, 0)));
    }

    @Test
    public void foodPickupUsesTheCurrentWorldStackingRule() throws Exception {
        TestHuman disabledPlayer = playerWithFoodStacking(false);
        assertTrue(disabledPlayer.inventory.pickup(new ItemStack(Item.APPLE, 4)));
        for (int slot = 0; slot < 4; ++slot) {
            assertEquals(1, disabledPlayer.inventory.getItem(slot).count);
        }

        TestHuman enabledPlayer = playerWithFoodStacking(true);
        assertTrue(enabledPlayer.inventory.pickup(new ItemStack(Item.APPLE, 4)));
        assertEquals(4, enabledPlayer.inventory.getItem(0).count);
        assertNull(enabledPlayer.inventory.getItem(1));
    }

    @Test
    public void furnaceInputStacksFoodTo64RegardlessOfTheWorldRule() throws Exception {
        for (boolean foodStacking : new boolean[] {false, true}) {
            TestHuman player = playerWithFoodStacking(foodStacking);
            TestContainer container = new TestContainer();
            TestInventory furnace = new TestInventory(1);
            container.add(new SlotFurnaceInput(furnace, 0, 0, 0));
            player.inventory.b(new ItemStack(Item.PORK, 64));

            container.a(0, 0, false, player);

            assertEquals(64, furnace.getItem(0).count);
            assertNull(player.inventory.j());
        }
    }

    @Test
    public void furnaceQuickMoveCombinesFoodStacksFromThePlayerInventory() throws Exception {
        for (boolean foodStacking : new boolean[] {false, true}) {
            TestHuman player = playerWithFoodStacking(foodStacking);
            TileEntityFurnace furnace = new TileEntityFurnace();
            ContainerFurnace container = new ContainerFurnace(player.inventory, furnace);
            int sourceCount = foodStacking ? 4 : 1;
            for (int slot = 9; slot < 25; ++slot) {
                player.inventory.setItem(slot, new ItemStack(Item.PORK, sourceCount));
            }

            container.a(3);

            assertEquals(16 * sourceCount, furnace.getItem(0).count);
            for (int slot = 9; slot < 25; ++slot) {
                assertNull(player.inventory.getItem(slot));
            }
        }
    }

    @Test
    public void quickCraftEvenlyDistributesAndKeepsRemainder() throws Exception {
        TestHuman player = player();
        TestContainer container = new TestContainer();
        TestInventory inventory = new TestInventory(3);
        for (int i = 0; i < 3; ++i) {
            container.add(inventory, i);
        }
        player.inventory.b(new ItemStack(Block.STONE, 10));

        container.a(-999, ContainerInput.getQuickCraftMask(0, 0), ContainerInput.QUICK_CRAFT, player);
        for (int i = 0; i < 3; ++i) {
            container.a(i, ContainerInput.getQuickCraftMask(1, 0), ContainerInput.QUICK_CRAFT, player);
        }
        container.a(-999, ContainerInput.getQuickCraftMask(2, 0), ContainerInput.QUICK_CRAFT, player);

        assertEquals(3, inventory.getItem(0).count);
        assertEquals(3, inventory.getItem(1).count);
        assertEquals(3, inventory.getItem(2).count);
        assertEquals(1, player.inventory.j().count);
    }

    @Test
    public void rightQuickCraftPlacesOnePerVisitedSlot() throws Exception {
        TestHuman player = player();
        TestContainer container = new TestContainer();
        TestInventory inventory = new TestInventory(3);
        for (int i = 0; i < 3; ++i) {
            container.add(inventory, i);
        }
        player.inventory.b(new ItemStack(Block.STONE, 10));

        container.a(-999, ContainerInput.getQuickCraftMask(0, 1), ContainerInput.QUICK_CRAFT, player);
        for (int i = 0; i < 3; ++i) {
            container.a(i, ContainerInput.getQuickCraftMask(1, 1), ContainerInput.QUICK_CRAFT, player);
        }
        container.a(-999, ContainerInput.getQuickCraftMask(2, 1), ContainerInput.QUICK_CRAFT, player);

        for (int i = 0; i < 3; ++i) {
            assertEquals(1, inventory.getItem(i).count);
        }
        assertEquals(7, player.inventory.j().count);
    }

    @Test
    public void creativeMiddleQuickCraftFillsEveryVisitedSlot() throws Exception {
        TestHuman player = player();
        player.gameMode = 1;
        TestContainer container = new TestContainer();
        TestInventory inventory = new TestInventory(2);
        container.add(inventory, 0);
        container.add(inventory, 1);
        player.inventory.b(new ItemStack(Block.STONE, 64));

        container.a(-999, ContainerInput.getQuickCraftMask(0, 2), ContainerInput.QUICK_CRAFT, player);
        container.a(0, ContainerInput.getQuickCraftMask(1, 2), ContainerInput.QUICK_CRAFT, player);
        container.a(1, ContainerInput.getQuickCraftMask(1, 2), ContainerInput.QUICK_CRAFT, player);
        container.a(-999, ContainerInput.getQuickCraftMask(2, 2), ContainerInput.QUICK_CRAFT, player);

        assertEquals(64, inventory.getItem(0).count);
        assertEquals(64, inventory.getItem(1).count);
        assertNull(player.inventory.j());
    }

    @Test
    public void cloneRequiresCreativeAndFillsTheCursorStack() throws Exception {
        TestHuman player = player();
        TestContainer container = new TestContainer();
        TestInventory inventory = new TestInventory(1);
        inventory.setItem(0, new ItemStack(Block.DIRT, 1));
        container.add(inventory, 0);

        assertNull(container.a(0, 2, ContainerInput.CLONE, player));
        assertNull(player.inventory.j());

        player.gameMode = 1;
        container.a(0, 2, ContainerInput.CLONE, player);
        assertEquals(Block.DIRT.id, player.inventory.j().id);
        assertEquals(player.inventory.j().getMaxStackSize(), player.inventory.j().count);
    }

    @Test
    public void throwRemovesOneItemWithoutUsingTheCursor() throws Exception {
        TestHuman player = player();
        TestContainer container = new TestContainer();
        TestInventory inventory = new TestInventory(1);
        inventory.setItem(0, new ItemStack(Block.STONE, 5));
        container.add(inventory, 0);

        container.a(0, 0, ContainerInput.THROW, player);

        assertEquals(4, inventory.getItem(0).count);
        assertNotNull(player.dropped);
        assertEquals(1, player.dropped.count);
        assertNull(player.inventory.j());
    }

    @Test
    public void atomicActionsRespectReadOnlySlots() throws Exception {
        TestHuman player = player();
        TestContainer container = new TestContainer();
        TestInventory inventory = new TestInventory(1);
        ItemStack protectedStack = new ItemStack(Block.STONE, 5);
        inventory.setItem(0, protectedStack);
        container.add(new ReadOnlySlot(inventory, 0));

        container.a(0, 2, ContainerInput.SWAP, player);
        container.a(0, 1, ContainerInput.THROW, player);

        assertSame(protectedStack, inventory.getItem(0));
        assertNull(player.inventory.getItem(2));
        assertNull(player.dropped);
    }

    @Test
    public void playerInventoryQuickMoveEquipsArmor() throws Exception {
        TestHuman player = player();
        ItemStack helmet = new ItemStack(Item.IRON_HELMET, 1);
        player.inventory.setItem(9, helmet);
        ContainerPlayer container = new ContainerPlayer(player.inventory);

        container.a(9, 0, ContainerInput.QUICK_MOVE, player);

        assertNotNull(player.inventory.armor[3]);
        assertEquals(helmet.id, player.inventory.armor[3].id);
        assertNull(player.inventory.getItem(9));
    }

    @Test
    public void workbenchQuickMoveUsesSnapshotGridMergeOrder() throws Exception {
        TestHuman player = player();
        ItemStack stone = new ItemStack(Block.STONE, 12);
        player.inventory.setItem(9, stone);
        ContainerWorkbench container = new ContainerWorkbench(player.inventory, (World)null, 0, 0, 0);

        container.a(10, 0, ContainerInput.QUICK_MOVE, player);

        assertNotNull(container.craftInventory.getItem(0));
        assertEquals(12, container.craftInventory.getItem(0).count);
        assertNull(container.craftInventory.getItem(1));
    }

    @Test
    public void rightClickArmorSwapsTheHeldAndWornSlots() throws Exception {
        TestHuman player = player();
        ItemStack helmet = new ItemStack(Item.IRON_HELMET, 1);
        player.inventory.setItem(player.inventory.itemInHandIndex, helmet);

        ItemStack returned = Item.IRON_HELMET.a(helmet, (World)null, player);

        assertSame(helmet, returned);
        assertSame(helmet, player.inventory.armor[3]);
        assertNull(player.inventory.getItemInHand());
    }

    @Test
    public void rightClickArmorReturnsPreviouslyEquippedArmorToTheHand() throws Exception {
        TestHuman player = player();
        ItemStack newHelmet = new ItemStack(Item.IRON_HELMET, 1);
        ItemStack oldHelmet = new ItemStack(Item.LEATHER_HELMET, 1);
        player.inventory.setItem(player.inventory.itemInHandIndex, newHelmet);
        player.inventory.armor[3] = oldHelmet;

        Item.IRON_HELMET.a(newHelmet, (World)null, player);

        assertSame(newHelmet, player.inventory.armor[3]);
        assertSame(oldHelmet, player.inventory.getItemInHand());
    }

    private static TestHuman player() throws Exception {
        TestHuman player = (TestHuman)unsafe().allocateInstance(TestHuman.class);
        player.inventory = new InventoryPlayer(player);
        player.gameMode = 0;
        return player;
    }

    private static TestHuman playerWithFoodStacking(boolean enabled) throws Exception {
        TestHuman player = player();
        World world = (World)unsafe().allocateInstance(World.class);
        world.worldData = new WorldData(1234L, "food-stacking-test");
        world.worldData.setToggleFoodStacking(enabled);
        player.world = world;
        return player;
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe)field.get(null);
    }

    private static final class TestHuman extends EntityHuman {
        ItemStack dropped;

        private TestHuman() {
            super(null);
        }

        public void b(ItemStack stack) {
            this.dropped = stack;
        }
    }

    private static final class TestContainer extends Container {
        void add(IInventory inventory, int index) {
            this.a(new Slot(inventory, index, 0, 0));
        }

        void add(Slot slot) {
            this.a(slot);
        }

        public boolean b(EntityHuman player) {
            return true;
        }
    }

    private static final class ReadOnlySlot extends Slot {
        ReadOnlySlot(IInventory inventory, int index) {
            super(inventory, index, 0, 0);
        }

        public boolean isAllowed(ItemStack stack) {
            return false;
        }

        public boolean canTakeStack() {
            return false;
        }
    }

    private static final class TestInventory implements IInventory {
        private final ItemStack[] items;

        TestInventory(int size) {
            this.items = new ItemStack[size];
        }

        public int getSize() {
            return this.items.length;
        }

        public ItemStack getItem(int index) {
            return this.items[index];
        }

        public ItemStack splitStack(int index, int amount) {
            ItemStack stack = this.items[index];
            if (stack == null) {
                return null;
            }
            if (stack.count <= amount) {
                this.items[index] = null;
                return stack;
            }
            ItemStack split = stack.a(amount);
            if (stack.count == 0) {
                this.items[index] = null;
            }
            return split;
        }

        public void setItem(int index, ItemStack stack) {
            this.items[index] = stack;
        }

        public String getName() {
            return "test";
        }

        public int getMaxStackSize() {
            return 64;
        }

        public void update() {
        }

        public boolean a_(EntityHuman player) {
            return true;
        }

        public ItemStack[] getContents() {
            return this.items;
        }
    }
}
