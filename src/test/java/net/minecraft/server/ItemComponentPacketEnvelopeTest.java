package net.minecraft.server;

import net.minecraft.server.network.ModProtocol;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ItemComponentPacketEnvelopeTest {
    private static final ResourceLocation UNKNOWN_VALUE =
            new ResourceLocation("example", "opaque_value");
    private static final ResourceLocation UNKNOWN_REMOVAL =
            new ResourceLocation("example", "removed_value");

    @BeforeClass
    public static void initializeItems() {
        assertNotNull(Block.STONE);
    }

    @Test
    public void allInventoryPacketsRoundTripTheCompleteComponentPatch() throws Exception {
        ItemStack expected = componentStack();

        Packet102WindowClick click = new Packet102WindowClick();
        click.a = 2;
        click.b = 7;
        click.c = 0;
        click.d = 11;
        click.e = expected;
        click.f = false;
        assertCompleteEnvelope(roundTrip(click, new Packet102WindowClick(), true).e, expected);

        Packet103SetSlot slot = new Packet103SetSlot(2, 7, expected);
        assertCompleteEnvelope(roundTrip(slot, new Packet103SetSlot(), true).c, expected);

        Packet104WindowItems items = new Packet104WindowItems();
        items.a = 2;
        items.b = new ItemStack[] { expected };
        assertCompleteEnvelope(roundTrip(items, new Packet104WindowItems(), true).b[0], expected);

        Packet107CreativeSetSlot creative = new Packet107CreativeSetSlot(9, expected);
        assertCompleteEnvelope(roundTrip(creative, new Packet107CreativeSetSlot(), true).itemStack, expected);
    }

    @Test
    public void disabledEnvelopeLeavesLegacyTagRawAndDropsNoReservedUserField() throws Exception {
        ItemStack stack = new ItemStack(Block.STONE, 1, 0);
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setString("marker", "legacy");
        legacy.setString(PacketItemStackCodec.ITEM_COMPONENTS_TAG, "ordinary user data");
        stack.setTag(legacy);
        stack.applyComponents(DataComponentPatch.builder()
                .set(DataComponents.MAX_STACK_SIZE, Integer.valueOf(12))
                .build());

        Packet103SetSlot decoded = roundTrip(
                new Packet103SetSlot(0, 3, stack),
                new Packet103SetSlot(),
                false);

        assertNotNull(decoded.c.getTag());
        assertEquals("legacy", decoded.c.getTag().getString("marker"));
        assertEquals("ordinary user data",
                decoded.c.getTag().getString(PacketItemStackCodec.ITEM_COMPONENTS_TAG));
        assertNull(decoded.c.getComponents().get(DataComponents.MAX_STACK_SIZE));
        assertEquals(Integer.valueOf(64),
                decoded.c.getPatchedComponents().get(DataComponents.MAX_STACK_SIZE));
    }

    @Test
    public void enabledReaderAcceptsLegacyPacketAlreadyInFlightBeforeTheAck() throws Exception {
        ItemStack stack = new ItemStack(Block.STONE, 2, 0);
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setString("marker", "in flight");
        stack.setTag(legacy);

        Packet103SetSlot outgoing = new Packet103SetSlot(0, 4, stack);
        outgoing.pvn = 14;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PacketCodecContext.Scope ignored =
                     PacketCodecContext.overrideItemComponents(Boolean.FALSE)) {
            outgoing.a(new DataOutputStream(bytes));
        }

        Packet103SetSlot incoming = new Packet103SetSlot();
        incoming.pvn = 14;
        try (PacketCodecContext.Scope ignored =
                     PacketCodecContext.overrideItemComponents(Boolean.TRUE)) {
            incoming.a(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        }

        assertEquals("in flight", incoming.c.getTag().getString("marker"));
        assertFalse(incoming.c.getTag().hasKey(PacketItemStackCodec.ITEM_COMPONENTS_TAG));
    }

    @Test
    public void negotiatedWireTagKeepsLegacyDataAndCarriesAVersionedPatchEnvelope()
            throws Exception {
        ItemStack stack = componentStack();
        Packet103SetSlot packet = new Packet103SetSlot(0, 3, stack);
        packet.pvn = 14;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PacketCodecContext.Scope ignored =
                     PacketCodecContext.overrideItemComponents(Boolean.TRUE)) {
            packet.a(new DataOutputStream(bytes));
        }

        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(0, input.readByte());
        assertEquals(3, input.readShort());
        assertEquals(Block.STONE.id, input.readShort());
        assertEquals(3, input.readByte());
        assertEquals(0, input.readShort());
        NBTTagCompound wireTag = PacketLimits.readCompressedNBT(
                input,
                PacketLimits.MAX_ITEM_NBT_BYTES,
                "item NBT");
        assertEquals(0, input.available());
        assertEquals("legacy shadow", wireTag.getString("marker"));
        assertTrue(wireTag.hasKey(PacketItemStackCodec.ITEM_COMPONENTS_TAG));
        NBTBase rawCarrier = wireTag.b(PacketItemStackCodec.ITEM_COMPONENTS_TAG);
        assertTrue(rawCarrier instanceof NBTTagCompound);
        NBTTagCompound envelope = (NBTTagCompound) rawCarrier;
        assertEquals(PacketItemStackCodec.ITEM_COMPONENTS_ENVELOPE_VERSION,
                envelope.e("version"));
        assertTrue(envelope.b("patch") instanceof NBTTagCompound);
        assertEquals(stack.getComponents(),
                DataComponentPatch.fromNbt((NBTTagCompound) envelope.b("patch")));
    }

    @Test
    public void negotiatedReaderRejectsMalformedMissingAndUnknownVersionEnvelopes()
            throws Exception {
        NBTTagCompound wrongCarrierType = new NBTTagCompound();
        wrongCarrierType.setString(PacketItemStackCodec.ITEM_COMPONENTS_TAG, "not-a-compound");
        assertNegotiatedTagRejected(wrongCarrierType);

        NBTTagCompound missingPatchEnvelope = new NBTTagCompound();
        missingPatchEnvelope.a("version", PacketItemStackCodec.ITEM_COMPONENTS_ENVELOPE_VERSION);
        NBTTagCompound missingPatch = new NBTTagCompound();
        missingPatch.a(PacketItemStackCodec.ITEM_COMPONENTS_TAG, missingPatchEnvelope);
        assertNegotiatedTagRejected(missingPatch);

        NBTTagCompound unknownVersionEnvelope = new NBTTagCompound();
        unknownVersionEnvelope.a("version", 2);
        unknownVersionEnvelope.a("patch", new NBTTagCompound());
        NBTTagCompound unknownVersion = new NBTTagCompound();
        unknownVersion.a(PacketItemStackCodec.ITEM_COMPONENTS_TAG, unknownVersionEnvelope);
        assertNegotiatedTagRejected(unknownVersion);
    }

    @Test
    public void negotiatedComponentLimitCanRaiseTheLegacyItemCount() throws Exception {
        ItemStack stack = new ItemStack(Block.STONE, 80, 0);
        stack.applyComponents(DataComponentPatch.builder()
                .set(DataComponents.MAX_STACK_SIZE, Integer.valueOf(99))
                .build());

        Packet107CreativeSetSlot decoded = roundTrip(
                new Packet107CreativeSetSlot(36, stack),
                new Packet107CreativeSetSlot(),
                true);
        assertEquals(80, decoded.itemStack.count);
        assertEquals(99, decoded.itemStack.getNetworkMaxStackSize());
    }

    @Test
    public void negotiatedComponentLimitRejectsAnOversizedCountAfterDecode()
            throws Exception {
        ItemStack stack = new ItemStack(Block.STONE, 17, 0);
        stack.applyComponents(DataComponentPatch.builder()
                .set(DataComponents.MAX_STACK_SIZE, Integer.valueOf(16))
                .build());

        try {
            roundTrip(
                    new Packet107CreativeSetSlot(36, stack),
                    new Packet107CreativeSetSlot(),
                    true);
            fail("component maximum was not enforced after negotiated decode");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("component maximum"));
        }
    }

    @Test
    public void negotiatedEnvelopeRejectsMaxStackSizeOutsideOneThrough99()
            throws Exception {
        assertNegotiatedPatchRejected(DataComponentPatch.builder()
                .set(DataComponents.MAX_STACK_SIZE, Integer.valueOf(0))
                .build(), "MAX_STACK_SIZE");
        assertNegotiatedPatchRejected(DataComponentPatch.builder()
                .set(DataComponents.MAX_STACK_SIZE, Integer.valueOf(100))
                .build(), "MAX_STACK_SIZE");
    }

    @Test
    public void negotiatedEnvelopeRejectsNonPositiveMaxDamage() throws Exception {
        assertNegotiatedPatchRejected(DataComponentPatch.builder()
                .set(DataComponents.MAX_DAMAGE, Integer.valueOf(0))
                .build(), "MAX_DAMAGE");
        assertNegotiatedPatchRejected(DataComponentPatch.builder()
                .set(DataComponents.MAX_DAMAGE, Integer.valueOf(-1))
                .build(), "MAX_DAMAGE");
    }

    @Test
    public void negotiatedEnvelopeRejectsNegativeDamage() throws Exception {
        assertNegotiatedPatchRejected(DataComponentPatch.builder()
                .set(DataComponents.DAMAGE, Integer.valueOf(-1))
                .build(), "DAMAGE");
    }

    @Test
    public void negotiatedEnvelopeRejectsDamageableAndStackableComponentsTogether()
            throws Exception {
        assertNegotiatedPatchRejected(DataComponentPatch.builder()
                .set(DataComponents.MAX_DAMAGE, Integer.valueOf(10))
                .set(DataComponents.DAMAGE, Integer.valueOf(0))
                .build(), "damageable and stackable");
    }

    @Test
    public void negotiatedEnvelopeRejectsCountAboveRemovedMaxStackFallback()
            throws Exception {
        ItemStack stack = new ItemStack(Item.APPLE, 2, 0);
        stack.applyComponents(DataComponentPatch.builder()
                .remove(DataComponents.MAX_STACK_SIZE)
                .build());

        try (PacketCodecContext.Scope ignored =
                     PacketCodecContext.overrideItemComponents(Boolean.TRUE)) {
            NBTTagCompound wireTag = PacketItemStackCodec.encodeTag(stack);
            try {
                PacketItemStackCodec.decode(Item.APPLE.id, 2, 0, wireTag);
                fail("inbound count above removed MAX_STACK_SIZE fallback was accepted");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("component maximum 1"));
            }
        }
    }

    @Test
    public void negotiatedEnvelopeRejectsOutboundCountAboveEffectiveMaximum()
            throws Exception {
        ItemStack stack = new ItemStack(Item.APPLE, 2, 0);
        stack.applyComponents(DataComponentPatch.builder()
                .remove(DataComponents.MAX_STACK_SIZE)
                .build());

        Packet103SetSlot packet = new Packet103SetSlot(0, 0, stack);
        packet.pvn = 14;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PacketCodecContext.Scope ignored =
                     PacketCodecContext.overrideItemComponents(Boolean.TRUE)) {
            try {
                packet.a(new DataOutputStream(bytes));
                fail("outbound count above effective MAX_STACK_SIZE was serialized");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("component maximum 1"));
            }
        }
    }

    @Test
    public void creativePacketRejectsInvalidIdsInsteadOfTurningThemIntoSlotClears()
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeShort(9);
        output.writeShort(Short.MAX_VALUE);
        output.writeByte(1);
        output.writeShort(0);
        output.close();

        Packet107CreativeSetSlot packet = new Packet107CreativeSetSlot();
        packet.pvn = 14;
        try {
            packet.a(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            fail("invalid creative item id was interpreted as an empty stack");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("creative item id"));
        }
    }

    @Test
    public void unknownComponentSetAndRemovalConflictsKeepTheLastOperation() {
        DataComponentPatch removed = DataComponentPatch.builder()
                .setUnknown(UNKNOWN_VALUE, new NBTTagString("first"))
                .removeUnknown(UNKNOWN_VALUE)
                .build();
        assertFalse(removed.getUnknownComponents().containsKey(UNKNOWN_VALUE));
        assertTrue(removed.getUnknownRemovedComponents().contains(UNKNOWN_VALUE));

        DataComponentPatch restored = DataComponentPatch.merge(
                removed,
                DataComponentPatch.builder()
                        .setUnknown(UNKNOWN_VALUE, new NBTTagString("second"))
                        .build());
        assertEquals(new NBTTagString("second"),
                restored.getUnknownComponents().get(UNKNOWN_VALUE));
        assertFalse(restored.getUnknownRemovedComponents().contains(UNKNOWN_VALUE));

        DataComponentPatch otherRemoval = DataComponentPatch.builder()
                .removeUnknown(UNKNOWN_REMOVAL)
                .build();
        assertFalse(new PatchedDataComponentMap(DataComponentMap.EMPTY, removed).equals(
                new PatchedDataComponentMap(DataComponentMap.EMPTY, otherRemoval)));
    }

    @Test
    public void envelopeRequiresBothSemanticAndWireFeatureBitsInEachDirection() throws Exception {
        int requiredFeatures = ModProtocol.FEATURE_ENTITY_WIRE_V2
                | ModProtocol.FEATURE_ENTITY_DATA_V2
                | ModProtocol.FEATURE_BLOCK_MODEL_VISUALS
                | ModProtocol.FEATURE_BLOCK_MODEL_STATES;
        int itemOnly = requiredFeatures | ModProtocol.FEATURE_ITEM_COMPONENTS;
        int envelopeFeatures = itemOnly | ModProtocol.FEATURE_ITEM_COMPONENT_ENVELOPE_V1;

        assertTrue((ModProtocol.resolveServerSupportedFeatures()
                & ModProtocol.FEATURE_ITEM_COMPONENT_ENVELOPE_V1) != 0);
        assertFalse(ModProtocol.hasItemComponentEnvelope(itemOnly));
        assertFalse(ModProtocol.hasItemComponentEnvelope(
                ModProtocol.FEATURE_ITEM_COMPONENT_ENVELOPE_V1));
        assertTrue(ModProtocol.hasItemComponentEnvelope(envelopeFeatures));

        NetworkManager oldPeer = bareNetworkManager();
        oldPeer.pvn = 14;
        oldPeer.updateItemComponentCodecAfterRead(hello(ModProtocol.CHANNEL_HELLO, itemOnly));
        assertFalse(oldPeer.isItemComponentsReadEnabled());
        assertFalse(oldPeer.hasItemComponentsWriteBarrier());

        NetworkManager preNbtPeer = bareNetworkManager();
        preNbtPeer.pvn = 13;
        preNbtPeer.updateItemComponentCodecAfterRead(
                hello(ModProtocol.CHANNEL_HELLO, envelopeFeatures));
        assertFalse(preNbtPeer.isItemComponentsReadEnabled());
        assertFalse(preNbtPeer.hasItemComponentsWriteBarrier());

        NetworkManager manager = bareNetworkManager();
        manager.pvn = 14;
        manager.updateItemComponentCodecAfterRead(
                hello(ModProtocol.CHANNEL_HELLO, envelopeFeatures));
        assertTrue(manager.isItemComponentsReadEnabled());
        assertTrue(manager.hasItemComponentsWriteBarrier());
        assertFalse(manager.isItemComponentsWriteEnabled());
        assertTrue(manager.blocksItemComponentPacketForWrite(new Packet103SetSlot()));
        assertTrue(manager.blocksItemComponentPacketForWrite(new Packet104WindowItems()));

        manager.updateItemComponentCodecAfterWrite(
                hello(ModProtocol.CHANNEL_HELLO_ACK, itemOnly));
        assertFalse(manager.isItemComponentsWriteEnabled());
        assertTrue(manager.hasItemComponentsWriteBarrier());

        manager.updateItemComponentCodecAfterWrite(
                hello(ModProtocol.CHANNEL_HELLO_ACK, envelopeFeatures));
        assertTrue(manager.isItemComponentsWriteEnabled());
        assertFalse(manager.hasItemComponentsWriteBarrier());
        assertFalse(manager.blocksItemComponentPacketForWrite(new Packet103SetSlot()));
    }

    @Test
    public void malformedNegativeNbtLengthIsRejected() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(0);
        out.writeShort(0);
        out.writeShort(Block.STONE.id);
        out.writeByte(1);
        out.writeShort(0);
        out.writeShort(-2);
        out.close();

        Packet103SetSlot packet = new Packet103SetSlot();
        packet.pvn = 14;
        try {
            packet.a(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("length"));
            return;
        }
        throw new AssertionError("negative item NBT length was accepted");
    }

    @Test
    public void windowItemsRejectsMoreThan256Entries() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(0);
        out.writeShort(257);
        out.close();

        Packet104WindowItems packet = new Packet104WindowItems();
        packet.pvn = 14;
        try {
            packet.a(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Too many window items"));
            return;
        }
        throw new AssertionError("oversized window item count was accepted");
    }

    @Test
    public void windowItemsSharesOnePacketWideNbtQuota() throws Exception {
        NBTTagCompound largeTag = new NBTTagCompound();
        largeTag.a("payload", new byte[9000]);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(0);
        out.writeShort(256);
        for (int i = 0; i < 256; i++) {
            out.writeShort(Block.STONE.id);
            out.writeByte(1);
            out.writeShort(0);
            PacketLimits.writeCompressedNBT(
                    out,
                    largeTag,
                    PacketLimits.MAX_ITEM_NBT_BYTES,
                    "item NBT");
        }
        out.close();

        Packet104WindowItems packet = new Packet104WindowItems();
        packet.pvn = 14;
        try {
            packet.a(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("NBT data exceeded"));
            return;
        }
        throw new AssertionError("aggregate packet NBT quota was reset per slot");
    }

    @Test
    public void negotiatedEnvelopeSerializationFailureIsNotDowngradedToNullNbt() throws Exception {
        byte[] incompressible = new byte[40000];
        new Random(12345L).nextBytes(incompressible);
        ItemStack stack = new ItemStack(Block.STONE, 1, 0);
        stack.applyComponents(DataComponentPatch.builder()
                .setUnknown(new ResourceLocation("example", "oversized"),
                        new NBTTagByteArray(incompressible))
                .build());

        Packet103SetSlot packet = new Packet103SetSlot(0, 0, stack);
        packet.pvn = 14;
        try (PacketCodecContext.Scope ignored =
                     PacketCodecContext.overrideItemComponents(Boolean.TRUE)) {
            packet.a(new DataOutputStream(new ByteArrayOutputStream()));
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("too large"));
            return;
        }
        throw new AssertionError("negotiated envelope failure was silently downgraded");
    }

    private static ItemStack componentStack() {
        ItemStack stack = new ItemStack(Block.STONE, 3, 0);
        NBTTagCompound customData = new NBTTagCompound();
        customData.setString("marker", "legacy shadow");
        stack.setTag(customData);
        stack.applyComponents(DataComponentPatch.builder()
                .set(DataComponents.MAX_STACK_SIZE, Integer.valueOf(12))
                .remove(DataComponents.MAX_DAMAGE)
                .setUnknown(UNKNOWN_VALUE, new NBTTagString("opaque"))
                .removeUnknown(UNKNOWN_REMOVAL)
                .build());
        return stack;
    }

    private static void assertCompleteEnvelope(ItemStack actual, ItemStack expected) {
        assertNotNull(actual);
        assertEquals(expected.getComponents(), actual.getComponents());
        assertEquals(Integer.valueOf(12), actual.getComponents().get(DataComponents.MAX_STACK_SIZE));
        assertTrue(actual.getComponents().getRemovedTypes().contains(DataComponents.MAX_DAMAGE));
        assertEquals(new NBTTagString("opaque"),
                actual.getComponents().getUnknownComponents().get(UNKNOWN_VALUE));
        assertTrue(actual.getComponents().getUnknownRemovedComponents().contains(UNKNOWN_REMOVAL));
        assertEquals("legacy shadow", actual.getTag().getString("marker"));
        assertFalse(actual.getTag().hasKey(PacketItemStackCodec.ITEM_COMPONENTS_TAG));
    }

    private static void assertNegotiatedTagRejected(NBTTagCompound wireTag) throws Exception {
        try (PacketCodecContext.Scope ignored =
                     PacketCodecContext.overrideItemComponents(Boolean.TRUE)) {
            PacketItemStackCodec.decode(Block.STONE.id, 1, 0, wireTag);
            fail("malformed negotiated item component envelope was accepted");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("component envelope"));
        }
    }

    private static void assertNegotiatedPatchRejected(
            DataComponentPatch patch,
            String expectedMessage) throws Exception {
        ItemStack stack = new ItemStack(Block.STONE, 1, 0);
        stack.applyComponents(patch);
        try {
            roundTrip(
                    new Packet103SetSlot(0, 0, stack),
                    new Packet103SetSlot(),
                    true);
            fail("invalid negotiated item component patch was accepted");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains(expectedMessage));
        }
    }

    private static <T extends Packet> T roundTrip(T outgoing, T incoming, boolean envelope)
            throws Exception {
        outgoing.pvn = 14;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PacketCodecContext.Scope ignored = PacketCodecContext.overrideItemComponents(
                Boolean.valueOf(envelope))) {
            outgoing.a(new DataOutputStream(bytes));
        }

        incoming.pvn = 14;
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        try (PacketCodecContext.Scope ignored = PacketCodecContext.overrideItemComponents(
                Boolean.valueOf(envelope))) {
            incoming.a(in);
        }
        assertEquals(0, in.available());
        return incoming;
    }

    private static Packet250CustomPayload hello(String channel, int features) {
        return new Packet250CustomPayload(
                channel,
                ModProtocol.createHelloAckPayload(ModProtocol.PROTOCOL_VERSION, features));
    }

    private static NetworkManager bareNetworkManager() throws Exception {
        return (NetworkManager) unsafe().allocateInstance(NetworkManager.class);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
