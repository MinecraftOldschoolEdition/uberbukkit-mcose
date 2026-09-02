package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** ItemStack payload bridge that keeps the Beta packet shape intact. */
final class PacketItemStackCodec {
    static final String ITEM_COMPONENTS_TAG = "__mcose_components";
    static final int ITEM_COMPONENTS_ENVELOPE_VERSION = 1;

    private PacketItemStackCodec() {}

    static ItemStack decode(
            int legacyId,
            int count,
            int damage,
            NBTTagCompound wireTag) throws IOException {
        DataComponentPatch componentPatch = null;
        NBTTagCompound legacyTag = wireTag;

        if (PacketCodecContext.usesItemComponents()
                && legacyTag != null
                && legacyTag.hasKey(ITEM_COMPONENTS_TAG)) {
            NBTBase carrier = legacyTag.b(ITEM_COMPONENTS_TAG);
            if (!(carrier instanceof NBTTagCompound)) {
                throw new IOException("Invalid item component envelope tag");
            }
            NBTTagCompound envelope = (NBTTagCompound) carrier;
            NBTBase version = envelope.b("version");
            NBTBase patch = envelope.b("patch");
            if (!(version instanceof NBTTagInt)
                    || ((NBTTagInt) version).a != ITEM_COMPONENTS_ENVELOPE_VERSION
                    || !(patch instanceof NBTTagCompound)) {
                throw new IOException("Unsupported item component envelope");
            }
            try {
                componentPatch = DataComponentPatch.fromNbt((NBTTagCompound) patch);
            } catch (RuntimeException malformedPatch) {
                throw new IOException("Invalid item component patch", malformedPatch);
            }
            legacyTag.remove(ITEM_COMPONENTS_TAG);
            if (legacyTag.c().isEmpty()) {
                legacyTag = null;
            }
        }

        ItemStack stack = LegacyItemStackCodec.decode(legacyId, count, damage, legacyTag);
        if (stack != null && componentPatch != null && !componentPatch.isEmpty()) {
            stack.applyComponents(componentPatch);
        }
        if (PacketCodecContext.usesItemComponents() && stack != null) {
            validateNegotiatedStack(stack, count);
        }
        return stack;
    }

    private static void validateNegotiatedStack(ItemStack stack, int count) throws IOException {
        validateNegotiatedComponents(stack);
        int maximum = stack.getNetworkMaxStackSize();
        if (count > maximum) {
            throw new IOException("Invalid item stack count: " + count
                    + " (component maximum " + maximum + ")");
        }
    }

    private static void validateNegotiatedComponents(ItemStack stack) throws IOException {
        Integer maxStackSize = stack.getPatchedComponents().get(DataComponents.MAX_STACK_SIZE);
        if (maxStackSize != null
                && (maxStackSize.intValue() < 1 || maxStackSize.intValue() > 99)) {
            throw new IOException("Invalid MAX_STACK_SIZE component: " + maxStackSize);
        }
        Integer maxDamage = stack.getPatchedComponents().get(DataComponents.MAX_DAMAGE);
        if (maxDamage != null && maxDamage.intValue() <= 0) {
            throw new IOException("Invalid MAX_DAMAGE component: " + maxDamage);
        }
        Integer damage = stack.getPatchedComponents().get(DataComponents.DAMAGE);
        if (damage != null && damage.intValue() < 0) {
            throw new IOException("Invalid DAMAGE component: " + damage);
        }
        if (maxDamage != null && stack.getMaxStackSize() > 1) {
            throw new IOException("Item cannot be both damageable and stackable");
        }
    }

    static NBTTagCompound encodeTag(ItemStack stack) {
        if (stack == null) {
            return null;
        }

        NBTTagCompound wireTag = stack.hasTag()
                ? (NBTTagCompound) stack.getTag().copy()
                : null;
        if (PacketCodecContext.usesItemComponents()) {
            DataComponentPatch patch = stack.getComponents();
            if (patch != null && !patch.isEmpty()) {
                if (wireTag == null) {
                    wireTag = new NBTTagCompound();
                }
                NBTTagCompound envelope = new NBTTagCompound();
                envelope.a("version", ITEM_COMPONENTS_ENVELOPE_VERSION);
                envelope.a("patch", patch.toNbt());
                wireTag.a(ITEM_COMPONENTS_TAG, envelope);
            }
        }
        return wireTag;
    }

    static NBTTagCompound readTag(DataInputStream input, int pvn) throws IOException {
        return readTag(input, pvn, NBTReadLimiter.packet());
    }

    static NBTTagCompound readTag(
            DataInputStream input,
            int pvn,
            NBTReadLimiter limiter) throws IOException {
        return pvn >= 14
                ? PacketLimits.readCompressedNBT(
                        input,
                        PacketLimits.MAX_ITEM_NBT_BYTES,
                        "item NBT",
                        limiter)
                : null;
    }

    static void writeTag(DataOutputStream output, ItemStack stack, int pvn) throws IOException {
        if (pvn < 14) {
            return;
        }

        if (PacketCodecContext.usesItemComponents() && stack != null) {
            validateNegotiatedStack(stack, stack.count);
        }
        NBTTagCompound wireTag = encodeTag(stack);
        if (PacketCodecContext.usesItemComponents()) {
            // Once the lossless envelope is negotiated, failure must terminate the
            // connection rather than silently replacing authoritative components.
            PacketLimits.writeCompressedNBT(
                    output,
                    wireTag,
                    PacketLimits.MAX_ITEM_NBT_BYTES,
                    "item NBT");
            return;
        }

        try {
            PacketLimits.writeCompressedNBT(
                    output,
                    wireTag,
                    PacketLimits.MAX_ITEM_NBT_BYTES,
                    "item NBT");
        } catch (Exception ignored) {
            output.writeShort(-1);
        }
    }
}
