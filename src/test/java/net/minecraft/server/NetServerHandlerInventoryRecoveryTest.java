package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

public class NetServerHandlerInventoryRecoveryTest {
    @BeforeClass
    public static void initializeBlocksBeforeContainers() {
        assertTrue(Block.STONE != null);
    }

    @Test
    public void packet102ValidationEnforcesNegotiationSlotsButtonsAndQuickCraftShape() throws Exception {
        CapturingHandler handler = (CapturingHandler) unsafe().allocateInstance(CapturingHandler.class);
        setBooleanField(handler, "modProtocolNegotiated", true);
        setIntField(handler, "negotiatedModFeatures", net.minecraft.server.network.ModProtocol.FEATURE_CONTAINER_INPUTS);

        assertTrue(handler.isValidInventoryClick(click(0, 0, false), 10));
        assertFalse(handler.isValidInventoryClick(click(0, 2, false), 10));
        assertFalse(handler.isValidInventoryClick(click(-2, 0, false), 10));
        assertFalse(handler.isValidInventoryClick(click(10, 0, false), 10));

        assertTrue(handler.isValidInventoryClick(extendedClick(0, ContainerInput.CLONE, 2), 10));
        assertFalse(handler.isValidInventoryClick(extendedClick(0, ContainerInput.CLONE, 0), 10));

        assertTrue(handler.isValidInventoryClick(extendedClick(-999, ContainerInput.QUICK_CRAFT,
                ContainerInput.getQuickCraftMask(0, 0)), 10));
        assertFalse(handler.isValidInventoryClick(extendedClick(0, ContainerInput.QUICK_CRAFT,
                ContainerInput.getQuickCraftMask(0, 0)), 10));
        assertTrue(handler.isValidInventoryClick(extendedClick(0, ContainerInput.QUICK_CRAFT,
                ContainerInput.getQuickCraftMask(1, 0)), 10));
        assertFalse(handler.isValidInventoryClick(extendedClick(-999, ContainerInput.QUICK_CRAFT,
                ContainerInput.getQuickCraftMask(1, 0)), 10));
        assertTrue(handler.isValidInventoryClick(extendedClick(-999, ContainerInput.QUICK_CRAFT,
                ContainerInput.getQuickCraftMask(2, 0)), 10));
        assertFalse(handler.isValidInventoryClick(extendedClick(-999, ContainerInput.QUICK_CRAFT,
                ContainerInput.getQuickCraftMask(2, 3)), 10));

        setBooleanField(handler, "modProtocolNegotiated", false);
        assertFalse(handler.isValidInventoryClick(extendedClick(0, ContainerInput.SWAP, 0), 10));
    }

    @Test
    public void rejectedClickFalseAcksDisablesAndRestoresSlotsAndCursor() throws Exception {
        assertTrue(Block.STONE != null);
        CapturingHandler handler = (CapturingHandler) unsafe().allocateInstance(CapturingHandler.class);
        handler.packets = new ArrayList<Packet>();
        handler.networkManager = (NetworkManager) unsafe().allocateInstance(NetworkManager.class);
        handler.networkManager.pvn = 14;
        setField(handler, "n", new HashMap());
        setIntField(handler, "primedInventorySlot", 3);
        setIntField(handler, "primedInventoryWindowId", 7);
        setLongField(handler, "primedInventoryClickAt", 123L);

        EntityPlayer player = (EntityPlayer) unsafe().allocateInstance(EntityPlayer.class);
        player.inventory = new InventoryPlayer(player);
        player.netServerHandler = handler;
        handler.player = player;

        TestInventory inventory = new TestInventory(2);
        ItemStack first = namedStack(2, "first");
        ItemStack second = namedStack(3, "second");
        inventory.setItem(0, first);
        inventory.setItem(1, second);
        TestContainer container = new TestContainer();
        container.windowId = 7;
        container.add(inventory, 0);
        container.add(inventory, 1);
        ItemStack cursor = namedStack(4, "cursor");
        player.inventory.b(cursor);

        Packet102WindowClick click = new Packet102WindowClick();
        click.a = 7;
        click.d = 42;
        handler.rejectInventoryClick(click, container);

        assertEquals(3, handler.packets.size());
        Packet106Transaction transaction = (Packet106Transaction) handler.packets.get(0);
        assertEquals(7, transaction.a);
        assertEquals(42, transaction.b);
        assertFalse(transaction.c);

        Packet104WindowItems contents = (Packet104WindowItems) handler.packets.get(1);
        assertEquals(7, contents.a);
        assertEquals(2, contents.b.length);
        assertTrue(ItemStack.equals(first, contents.b[0]));
        assertTrue(ItemStack.equals(second, contents.b[1]));
        assertNotSame(first, contents.b[0]);
        assertNotSame(second, contents.b[1]);
        first.tag.k("display").setString("Name", "mutated-after-snapshot");
        assertEquals("first", contents.b[0].getComponents().get(DataComponents.CUSTOM_DATA)
                .k("display").getString("Name"));

        Packet103SetSlot carried = (Packet103SetSlot) handler.packets.get(2);
        assertEquals(-1, carried.a);
        assertEquals(-1, carried.b);
        assertTrue(ItemStack.equals(cursor, carried.c));
        assertNotSame(cursor, carried.c);

        assertFalse(container.c(player));
        assertEquals(Short.valueOf((short) 42), pendingTransactions(handler).get(Integer.valueOf(7)));
        assertEquals(-1, getIntField(handler, "primedInventorySlot"));
        assertEquals(-1, getIntField(handler, "primedInventoryWindowId"));
        assertEquals(0L, getLongField(handler, "primedInventoryClickAt"));
    }

    @Test
    public void closingWindowZeroReturnsCursorAndSendsFullAuthoritativeState() throws Exception {
        CapturingHandler handler = (CapturingHandler) unsafe().allocateInstance(CapturingHandler.class);
        handler.packets = new ArrayList<Packet>();
        handler.networkManager = (NetworkManager) unsafe().allocateInstance(NetworkManager.class);
        handler.networkManager.pvn = 14;

        EntityPlayer player = (EntityPlayer) unsafe().allocateInstance(EntityPlayer.class);
        player.inventory = new InventoryPlayer(player);
        player.netServerHandler = handler;
        handler.player = player;
        player.defaultContainer = new ContainerPlayer(player.inventory);
        player.activeContainer = player.defaultContainer;
        player.inventory.b(namedStack(3, "cursor"));

        player.A();

        assertNull(player.inventory.j());
        assertEquals(3, player.inventory.getItem(0).count);
        assertEquals(2, handler.packets.size());
        Packet104WindowItems contents = (Packet104WindowItems) handler.packets.get(0);
        assertEquals(0, contents.a);
        assertTrue(ItemStack.equals(player.inventory.getItem(0), contents.b[36]));
        Packet103SetSlot cursor = (Packet103SetSlot) handler.packets.get(1);
        assertEquals(-1, cursor.a);
        assertEquals(-1, cursor.b);
        assertNull(cursor.c);
    }

    @Test
    public void creativeSlotAcceptsAComponentDefinedStackLimitAbove64() throws Exception {
        CapturingHandler handler = (CapturingHandler) unsafe().allocateInstance(CapturingHandler.class);
        handler.packets = new ArrayList<Packet>();
        handler.networkManager = (NetworkManager) unsafe().allocateInstance(NetworkManager.class);
        handler.networkManager.pvn = 14;

        EntityPlayer player = (EntityPlayer) unsafe().allocateInstance(EntityPlayer.class);
        player.inventory = new InventoryPlayer(player);
        player.netServerHandler = handler;
        player.gameMode = 1;
        player.defaultContainer = new ContainerPlayer(player.inventory);
        player.activeContainer = player.defaultContainer;
        handler.player = player;

        ItemStack stack = new ItemStack(Block.STONE, 80, 0);
        stack.applyComponents(DataComponentPatch.builder()
                .set(DataComponents.MAX_STACK_SIZE, Integer.valueOf(99))
                .build());
        handler.handleCreativeSlot(new Packet107CreativeSetSlot(9, stack));

        assertEquals(80, player.inventory.getItem(9).count);
        assertEquals(99, player.inventory.getItem(9).getMaxStackSize());
        assertEquals(1, handler.packets.size());
        Packet103SetSlot confirmation = (Packet103SetSlot) handler.packets.get(0);
        assertEquals(0, confirmation.a);
        assertEquals(9, confirmation.b);
        assertEquals(80, confirmation.c.count);
    }

    @Test
    public void anyNegativeCreativeSlotDropsWithoutTargetingInventory() throws Exception {
        CapturingHandler handler = (CapturingHandler) unsafe().allocateInstance(CapturingHandler.class);
        handler.packets = new ArrayList<Packet>();

        DroppingPlayer player = (DroppingPlayer) unsafe().allocateInstance(DroppingPlayer.class);
        player.inventory = new InventoryPlayer(player);
        player.netServerHandler = handler;
        player.gameMode = 1;
        player.defaultContainer = new ContainerPlayer(player.inventory);
        player.activeContainer = player.defaultContainer;
        handler.player = player;

        ((Slot) player.defaultContainer.e.get(1)).c(namedStack(1, "craft"));
        ((Slot) player.defaultContainer.e.get(5)).c(namedStack(1, "armor"));
        ((Slot) player.defaultContainer.e.get(9)).c(namedStack(2, "main"));
        ((Slot) player.defaultContainer.e.get(36)).c(namedStack(3, "hotbar"));
        ItemStack[] before = new ItemStack[player.defaultContainer.e.size()];
        for (int slot = 0; slot < before.length; ++slot) {
            before[slot] = ((Slot) player.defaultContainer.e.get(slot)).getItem();
        }

        ItemStack requestedDrop = namedStack(7, "negative-slot-drop");
        handler.handleCreativeSlot(new Packet107CreativeSetSlot(-2, requestedDrop));

        assertNotNull(player.dropped);
        assertTrue(ItemStack.equals(requestedDrop, player.dropped));
        assertNotSame(requestedDrop, player.dropped);
        assertTrue(player.randomMotion);
        assertEquals(0, handler.packets.size());
        for (int slot = 0; slot < before.length; ++slot) {
            assertSame("Creative drop changed inventory slot " + slot,
                    before[slot], ((Slot) player.defaultContainer.e.get(slot)).getItem());
        }
    }

    @Test
    public void staleClosePacketCannotCloseANewerActiveContainer() throws Exception {
        CapturingHandler handler = (CapturingHandler) unsafe().allocateInstance(CapturingHandler.class);
        handler.packets = new ArrayList<Packet>();
        handler.networkManager = (NetworkManager) unsafe().allocateInstance(NetworkManager.class);
        handler.networkManager.pvn = 14;

        EntityPlayer player = (EntityPlayer) unsafe().allocateInstance(EntityPlayer.class);
        player.inventory = new InventoryPlayer(player);
        player.netServerHandler = handler;
        player.setServerRulesAccepted(true);
        player.defaultContainer = new ContainerPlayer(player.inventory);
        TestContainer newerContainer = new TestContainer();
        newerContainer.windowId = 8;
        player.activeContainer = newerContainer;
        ItemStack cursor = namedStack(3, "newer-menu-cursor");
        player.inventory.b(cursor);
        handler.player = player;

        handler.a(new Packet101CloseWindow(7));

        assertSame(newerContainer, player.activeContainer);
        assertSame(cursor, player.inventory.j());
        assertEquals(0, handler.packets.size());
    }

    private static ItemStack namedStack(int count, String name) {
        ItemStack stack = new ItemStack(Block.STONE, count);
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", name);
        NBTTagCompound tag = new NBTTagCompound();
        tag.a("display", display);
        stack.setTag(tag);
        return stack;
    }

    private static Packet102WindowClick click(int slot, int button, boolean quickMove) {
        Packet102WindowClick packet = new Packet102WindowClick();
        packet.b = slot;
        packet.c = button;
        packet.f = quickMove;
        return packet;
    }

    private static Packet102WindowClick extendedClick(int slot, int input, int button) {
        return click(slot, (byte) ContainerInput.encodeButton(input, button), false);
    }

    private static Map pendingTransactions(NetServerHandler handler) throws Exception {
        Field field = NetServerHandler.class.getDeclaredField("n");
        field.setAccessible(true);
        return (Map) field.get(handler);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = NetServerHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setIntField(Object target, String name, int value) throws Exception {
        Field field = NetServerHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static void setBooleanField(Object target, String name, boolean value) throws Exception {
        Field field = NetServerHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static int getIntField(Object target, String name) throws Exception {
        Field field = NetServerHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static void setLongField(Object target, String name, long value) throws Exception {
        Field field = NetServerHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setLong(target, value);
    }

    private static long getLongField(Object target, String name) throws Exception {
        Field field = NetServerHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getLong(target);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final class CapturingHandler extends NetServerHandler {
        List<Packet> packets;

        private CapturingHandler() {
            super(null, null, null);
        }

        @Override
        public void sendPacket(Packet packet) {
            this.packets.add(packet);
        }
    }

    private static final class DroppingPlayer extends EntityPlayer {
        ItemStack dropped;
        boolean randomMotion;

        private DroppingPlayer() {
            super(null, null, null, null, 0);
        }

        @Override
        public void a(ItemStack stack, boolean randomMotion) {
            this.dropped = stack;
            this.randomMotion = randomMotion;
        }
    }

    private static final class TestContainer extends Container {
        void add(IInventory inventory, int index) {
            this.a(new Slot(inventory, index, 0, 0));
        }

        public boolean b(EntityHuman player) {
            return true;
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
            if (stack == null || stack.count <= amount) {
                this.items[index] = null;
                return stack;
            }
            return stack.a(amount);
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
