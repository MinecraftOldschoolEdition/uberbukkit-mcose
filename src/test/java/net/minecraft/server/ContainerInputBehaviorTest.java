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
    public void pickupSwapsRatherThanMergesSameItemWithDifferentComponents() throws Exception {
        TestHuman player = player();
        TestContainer container = new TestContainer();
        TestInventory inventory = new TestInventory(1);
        ItemStack slotStack = namedStack(Block.STONE, 2, "slot");
        ItemStack carriedStack = namedStack(Block.STONE, 3, "carried");
        inventory.setItem(0, slotStack);
        container.add(inventory, 0);
        player.inventory.b(carriedStack);

        container.a(0, 0, ContainerInput.PICKUP, player);

        assertSame(carriedStack, inventory.getItem(0));
        assertSame(slotStack, player.inventory.j());
    }

    @Test
    public void pickupUsesResolvedDamageDefaultsAndTombstonesForIdentity() throws Exception {
        TestHuman equivalentPlayer = player();
        TestContainer equivalentContainer = new TestContainer();
        TestInventory equivalentInventory = new TestInventory(1);
        ItemStack pristine = new ItemStack(Item.IRON_PICKAXE, 1, 0);
        ItemStack explicitZero = new ItemStack(Item.IRON_PICKAXE, 1, 0);
        explicitZero.applyComponents(DataComponentPatch.builder()
                .set(DataComponents.DAMAGE, Integer.valueOf(0))
                .build());
        equivalentInventory.setItem(0, pristine);
        equivalentContainer.add(equivalentInventory, 0);
        equivalentPlayer.inventory.b(explicitZero);

        equivalentContainer.a(0, 0, ContainerInput.PICKUP, equivalentPlayer);

        assertSame(pristine, equivalentInventory.getItem(0));
        assertSame(explicitZero, equivalentPlayer.inventory.j());

        TestHuman distinctPlayer = player();
        TestContainer distinctContainer = new TestContainer();
        TestInventory distinctInventory = new TestInventory(1);
        ItemStack otherPristine = new ItemStack(Item.IRON_PICKAXE, 1, 0);
        ItemStack removedDamage = new ItemStack(Item.IRON_PICKAXE, 1, 0);
        removedDamage.applyComponents(DataComponentPatch.builder()
                .remove(DataComponents.DAMAGE)
                .build());
        distinctInventory.setItem(0, otherPristine);
        distinctContainer.add(distinctInventory, 0);
        distinctPlayer.inventory.b(removedDamage);

        distinctContainer.a(0, 0, ContainerInput.PICKUP, distinctPlayer);

        assertSame(removedDamage, distinctInventory.getItem(0));
        assertSame(otherPristine, distinctPlayer.inventory.j());
    }

    @Test
    public void pickupDoesNotMergeStacksAboveRemovedMaxStackFallback() throws Exception {
        TestHuman player = player();
        TestContainer container = new TestContainer();
        TestInventory inventory = new TestInventory(1);
        ItemStack slotted = new ItemStack(Block.STONE, 1, 0);
        slotted.applyComponents(DataComponentPatch.builder()
                .remove(DataComponents.MAX_STACK_SIZE)
                .build());
        ItemStack carried = slotted.cloneItemStack();
        inventory.setItem(0, slotted);
        container.add(inventory, 0);
        player.inventory.b(carried);

        container.a(0, 0, ContainerInput.PICKUP, player);

        assertEquals(1, slotted.getMaxStackSize());
        assertEquals(1, inventory.getItem(0).count);
        assertSame(slotted, inventory.getItem(0));
        assertSame(carried, player.inventory.j());
        assertEquals(1, carried.count);
    }

    @Test
    public void exactStackComparisonIncludesStructuralItemComponents() {
        ItemStack first = namedStack(Block.STONE, 2, "same");
        ItemStack same = namedStack(Block.STONE, 2, "same");
        ItemStack differentPatch = new ItemStack(Block.STONE, 2);
        differentPatch.applyComponents(DataComponentPatch.builder()
                .set(DataComponents.MAX_STACK_SIZE, Integer.valueOf(32))
                .build());

        assertTrue(ItemStack.equals(first, same));
        assertFalse(ItemStack.equals(first, differentPatch));
    }

    @Test
    public void exactStackComparisonUsesResolvedComponentsRatherThanPatchEncoding() {
        ItemStack defaultsOnly = new ItemStack(Block.STONE, 2);
        ItemStack explicitDefault = new ItemStack(Block.STONE, 2);
        explicitDefault.applyComponents(DataComponentPatch.builder()
                .set(DataComponents.MAX_STACK_SIZE, Integer.valueOf(64))
                .build());

        assertTrue(ItemStack.equals(defaultsOnly, explicitDefault));
    }

    @Test
    public void clonedStackComponentsDoNotAliasTheSource() {
        ItemStack source = namedStack(Block.STONE, 2, "source");
        ItemStack copy = source.cloneItemStack();

        assertSame(copy.tag, copy.getComponents().get(DataComponents.CUSTOM_DATA));
        copy.tag.k("display").setString("Name", "copy");

        assertEquals("source", source.tag.k("display").getString("Name"));
        assertEquals("copy", copy.tag.k("display").getString("Name"));
        assertEquals("copy", copy.getComponents().get(DataComponents.CUSTOM_DATA)
                .k("display").getString("Name"));
        assertFalse(ItemStack.equals(source, copy));
    }

    @Test
    public void splitStackComponentsDoNotAliasTheSourceAndRetainOneLegacyView() {
        ItemStack source = namedStack(Block.STONE, 5, "source");
        ItemStack split = source.a(2);

        assertEquals(3, source.count);
        assertEquals(2, split.count);
        assertSame(split.tag, split.getComponents().get(DataComponents.CUSTOM_DATA));
        split.tag.k("display").setString("Name", "split");

        assertEquals("source", source.tag.k("display").getString("Name"));
        assertEquals("split", split.getComponents().get(DataComponents.CUSTOM_DATA)
                .k("display").getString("Name"));
    }

    @Test
    public void closingContainerReturnsCarriedStackToInventoryBeforeDropping() throws Exception {
        TestHuman player = player();
        TestContainer container = new TestContainer();
        ItemStack carried = namedStack(Block.STONE, 5, "cursor");
        player.inventory.b(carried);

        container.a(player);

        assertNull(player.inventory.j());
        assertNull(player.dropped);
        assertNotNull(player.inventory.getItem(0));
        assertEquals(5, player.inventory.getItem(0).count);
        assertEquals("cursor", player.inventory.getItem(0).tag
                .k("display").getString("Name"));
    }

    @Test
    public void closingContainerDropsOnlyTheCarriedRemainderWhenInventoryIsFull() throws Exception {
        TestHuman player = player();
        TestContainer container = new TestContainer();
        for (int i = 0; i < player.inventory.items.length; i++) {
            player.inventory.items[i] = new ItemStack(Block.DIRT, 64);
        }
        player.inventory.items[0] = namedStack(Block.STONE, 63, "same");
        ItemStack carried = namedStack(Block.STONE, 5, "same");
        player.inventory.b(carried);

        container.a(player);

        assertNull(player.inventory.j());
        assertEquals(64, player.inventory.items[0].count);
        assertSame(carried, player.dropped);
        assertEquals(4, player.dropped.count);
        assertEquals("same", player.dropped.tag.k("display").getString("Name"));
    }

    @Test
    public void nbtEqualityAndCopyAreStructuralAndRecursive() {
        NBTTagList firstList = new NBTTagList();
        firstList.a(new NBTTagInt(3));
        firstList.a(new NBTTagInt(7));
        NBTTagCompound first = new NBTTagCompound();
        first.a("bytes", new byte[] {1, 2, 3});
        first.a("list", firstList);

        NBTTagList secondList = new NBTTagList();
        secondList.a(new NBTTagInt(3));
        secondList.a(new NBTTagInt(7));
        NBTTagCompound second = new NBTTagCompound();
        second.a("list", secondList);
        second.a("bytes", new byte[] {1, 2, 3});

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());

        NBTTagCompound copy = (NBTTagCompound) first.copy();
        copy.j("bytes")[0] = 9;
        ((NBTTagInt) copy.l("list").a(0)).a = 11;

        assertEquals(1, first.j("bytes")[0]);
        assertEquals(3, ((NBTTagInt) first.l("list").a(0)).a);
        assertFalse(first.equals(copy));
    }

    @Test
    public void inventoryPickupDoesNotMergeSameItemWithDifferentComponents() throws Exception {
        TestHuman player = playerWithFoodStacking(true);
        player.inventory.setItem(0, namedStack(Block.STONE, 2, "existing"));
        ItemStack incoming = namedStack(Block.STONE, 3, "incoming");

        assertTrue(player.inventory.pickup(incoming));

        assertEquals(2, player.inventory.getItem(0).count);
        assertEquals(3, player.inventory.getItem(1).count);
        assertEquals("incoming", player.inventory.getItem(1).tag.k("display").getString("Name"));
        assertEquals(0, incoming.count);
    }

    @Test
    public void inventoryPickupPreservesAndCopiesTheCompleteComponentPatch() throws Exception {
        TestHuman player = playerWithFoodStacking(true);
        ItemStack incoming = namedStack(Block.STONE, 3, "incoming");
        incoming.applyComponents(DataComponentPatch.builder()
                .set(DataComponents.MAX_STACK_SIZE, Integer.valueOf(32))
                .build());

        assertTrue(player.inventory.pickup(incoming));
        ItemStack stored = player.inventory.getItem(0);

        assertEquals(32, stored.getMaxStackSize());
        assertTrue(ItemStack.isSameItemSameComponents(stored, incoming));
        incoming.tag.k("display").setString("Name", "mutated");
        assertEquals("incoming", stored.tag.k("display").getString("Name"));
    }

    @Test
    public void ordinaryContainersAllow99WhileKeepingSmallerItemAndSlotLimits()
            throws Exception {
        ItemStack componentStack = new ItemStack(Block.STONE, 99, 0);
        componentStack.applyComponents(DataComponentPatch.builder()
                .set(DataComponents.MAX_STACK_SIZE, Integer.valueOf(99))
                .build());

        TestHuman player = player();
        TestContainer playerContainer = new TestContainer();
        playerContainer.add(player.inventory, 0);
        ItemStack playerMoving = componentStack.cloneItemStack();
        playerContainer.merge(playerMoving, 0, 1);
        assertEquals(99, player.inventory.getItem(0).count);
        assertEquals(0, playerMoving.count);

        TileEntityChest chest = new TileEntityChest();
        TestContainer chestContainer = new TestContainer();
        chestContainer.add(chest, 0);
        ItemStack chestMoving = componentStack.cloneItemStack();
        chestContainer.merge(chestMoving, 0, 1);
        assertEquals(99, chest.getItem(0).count);
        assertEquals(0, chestMoving.count);

        TestHuman itemLimitedPlayer = player();
        TestContainer itemLimitedContainer = new TestContainer();
        itemLimitedContainer.add(itemLimitedPlayer.inventory, 0);
        ItemStack ordinaryStone = new ItemStack(Block.STONE, 99, 0);
        itemLimitedContainer.merge(ordinaryStone, 0, 1);
        assertEquals(64, itemLimitedPlayer.inventory.getItem(0).count);
        assertEquals(35, ordinaryStone.count);

        TestHuman slotLimitedPlayer = player();
        TestContainer slotLimitedContainer = new TestContainer();
        slotLimitedContainer.add(new LimitedSlot(slotLimitedPlayer.inventory, 0, 16));
        ItemStack slotMoving = componentStack.cloneItemStack();
        slotLimitedContainer.merge(slotMoving, 0, 1);
        assertEquals(16, slotLimitedPlayer.inventory.getItem(0).count);
        assertEquals(83, slotMoving.count);

        ItemStack food = new ItemStack(Item.PORK, 99, 0);
        food.applyComponents(DataComponentPatch.builder()
                .set(DataComponents.MAX_STACK_SIZE, Integer.valueOf(99))
                .build());
        assertEquals(64, new SlotFurnaceInput(new TileEntityFurnace(), 0, 0, 0)
                .getItemStackLimit(food, null));
    }

    @Test
    public void creativePacketSlotsUseTheWindowZeroContainerMapAndRejectTheResultSlot() throws Exception {
        TestHuman player = player();
        player.defaultContainer = new ContainerPlayer(player.inventory);
        ItemStack originalHotbar = new ItemStack(Block.DIRT, 7);
        player.inventory.setItem(0, originalHotbar);

        assertFalse(NetServerHandler.setCreativeInventorySlot(player, -2, new ItemStack(Block.STONE, 1)));
        assertFalse(NetServerHandler.setCreativeInventorySlot(player, 0, new ItemStack(Block.STONE, 1)));
        assertFalse(NetServerHandler.setCreativeInventorySlot(player, 45, new ItemStack(Block.STONE, 1)));
        assertSame(originalHotbar, player.inventory.getItem(0));

        ItemStack crafting = namedStack(Block.STONE, 2, "crafting");
        assertTrue(NetServerHandler.setCreativeInventorySlot(player, 1, crafting));
        crafting.tag.k("display").setString("Name", "mutated");
        assertEquals("crafting", ((ContainerPlayer) player.defaultContainer).craftInventory
                .getItem(0).tag.k("display").getString("Name"));

        ItemStack armor = new ItemStack(Item.IRON_HELMET, 1);
        assertTrue(NetServerHandler.setCreativeInventorySlot(player, 5, armor));
        assertEquals(Item.IRON_HELMET.id, player.inventory.armor[3].id);

        ItemStack main = new ItemStack(Block.STONE, 3);
        assertTrue(NetServerHandler.setCreativeInventorySlot(player, 9, main));
        assertEquals(3, player.inventory.getItem(9).count);

        ItemStack hotbar = new ItemStack(Block.STONE, 4);
        assertTrue(NetServerHandler.setCreativeInventorySlot(player, 36, hotbar));
        assertEquals(4, player.inventory.getItem(0).count);
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
    public void pickupAllSkipsSameItemWithDifferentComponents() throws Exception {
        TestHuman player = player();
        TestContainer container = new TestContainer();
        TestInventory inventory = new TestInventory(3);
        inventory.setItem(0, namedStack(Block.STONE, 5, "other"));
        inventory.setItem(1, namedStack(Block.STONE, 3, "wanted"));
        for (int i = 0; i < 3; ++i) {
            container.add(inventory, i);
        }
        player.inventory.b(namedStack(Block.STONE, 1, "wanted"));

        container.a(2, 0, ContainerInput.PICKUP_ALL, player);

        assertEquals(4, player.inventory.j().count);
        assertEquals(5, inventory.getItem(0).count);
        assertNull(inventory.getItem(1));
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
    public void quickCraftSkipsSameItemWithDifferentComponents() throws Exception {
        TestHuman player = player();
        TestContainer container = new TestContainer();
        TestInventory inventory = new TestInventory(2);
        inventory.setItem(0, namedStack(Block.STONE, 2, "other"));
        container.add(inventory, 0);
        container.add(inventory, 1);
        player.inventory.b(namedStack(Block.STONE, 4, "wanted"));

        container.a(-999, ContainerInput.getQuickCraftMask(0, 0), ContainerInput.QUICK_CRAFT, player);
        container.a(0, ContainerInput.getQuickCraftMask(1, 0), ContainerInput.QUICK_CRAFT, player);
        container.a(1, ContainerInput.getQuickCraftMask(1, 0), ContainerInput.QUICK_CRAFT, player);
        container.a(-999, ContainerInput.getQuickCraftMask(2, 0), ContainerInput.QUICK_CRAFT, player);

        assertEquals(2, inventory.getItem(0).count);
        assertEquals(4, inventory.getItem(1).count);
        assertNull(player.inventory.j());
    }

    @Test
    public void quickMoveDoesNotMergeSameItemWithDifferentComponents() throws Exception {
        TestContainer container = new TestContainer();
        TestInventory inventory = new TestInventory(2);
        inventory.setItem(0, namedStack(Block.STONE, 2, "other"));
        container.add(inventory, 0);
        container.add(inventory, 1);
        ItemStack moving = namedStack(Block.STONE, 3, "wanted");

        container.merge(moving, 0, 2);

        assertEquals(2, inventory.getItem(0).count);
        assertEquals(3, inventory.getItem(1).count);
        assertEquals("wanted", inventory.getItem(1).tag.k("display").getString("Name"));
        assertEquals(0, moving.count);
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
    public void fullThrowReusesTheOriginalAmountWhenTheSourceRegenerates() throws Exception {
        TestHuman player = player();
        TestContainer container = new TestContainer();
        TestInventory inventory = new TestInventory(1);
        inventory.setItem(0, new ItemStack(Block.STONE, 3));
        RegeneratingSlot slot = new RegeneratingSlot(inventory, 0);
        container.add(slot);

        container.a(0, 1, ContainerInput.THROW, player);

        assertEquals(2, slot.takeCalls);
        assertEquals(3, slot.requestedAmounts[0]);
        assertEquals(3, slot.requestedAmounts[1]);
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

    private static ItemStack namedStack(Block block, int count, String name) {
        ItemStack stack = new ItemStack(block, count);
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", name);
        NBTTagCompound tag = new NBTTagCompound();
        tag.a("display", display);
        stack.setTag(tag);
        return stack;
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

        void merge(ItemStack stack, int start, int end) {
            this.a(stack, start, end, false, (World)null);
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

    private static final class LimitedSlot extends Slot {
        private final int limit;

        LimitedSlot(IInventory inventory, int index, int limit) {
            super(inventory, index, 0, 0);
            this.limit = limit;
        }

        @Override
        public int d() {
            return this.limit;
        }
    }

    private static final class RegeneratingSlot extends Slot {
        final int[] requestedAmounts = new int[2];
        int takeCalls;

        RegeneratingSlot(IInventory inventory, int index) {
            super(inventory, index, 0, 0);
        }

        @Override
        public ItemStack a(int amount) {
            if (this.takeCalls < this.requestedAmounts.length) {
                this.requestedAmounts[this.takeCalls] = amount;
            }
            ++this.takeCalls;
            return super.a(amount);
        }

        @Override
        public void a(ItemStack removed) {
            if (this.takeCalls == 1) {
                this.inventory.setItem(this.index, new ItemStack(Block.STONE, 7));
            } else {
                this.inventory.setItem(this.index, new ItemStack(Block.DIRT, 1));
            }
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
