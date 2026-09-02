package net.minecraft.server;

import net.minecraft.server.registry.ItemRegistry;
import net.minecraft.server.util.ResourceLocation;

/** Immutable registry-backed template that creates a fresh mutable stack. */
public final class ItemStackTemplate {
    private final Item item;
    private final int count;
    private final int legacyMetadata;

    public ItemStackTemplate(Item item) {
        this(item, 1, 0);
    }

    public ItemStackTemplate(Item item, int count, int legacyMetadata) {
        if (item == null) {
            throw new IllegalArgumentException("Template item cannot be null");
        }
        if (count < 1 || count > 99) {
            throw new IllegalArgumentException(
                    "Template count must be between 1 and 99");
        }
        if (legacyMetadata < 0 || legacyMetadata > 32767) {
            throw new IllegalArgumentException(
                    "Template legacy metadata must be between 0 and 32767");
        }
        this.item = item;
        this.count = count;
        this.legacyMetadata = legacyMetadata;
    }

    public Item getItem() {
        return this.item;
    }

    public ResourceLocation getItemKey() {
        return ItemRegistry.getKey(this.item);
    }

    public int getCount() {
        return this.count;
    }

    public int getLegacyMetadata() {
        return this.legacyMetadata;
    }

    public ItemStack create() {
        return new ItemStack(this.item, this.count, this.legacyMetadata);
    }

    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ItemStackTemplate)) return false;
        ItemStackTemplate that = (ItemStackTemplate)other;
        return this.item == that.item
                && this.count == that.count
                && this.legacyMetadata == that.legacyMetadata;
    }

    public int hashCode() {
        int result = System.identityHashCode(this.item);
        result = 31 * result + this.count;
        result = 31 * result + this.legacyMetadata;
        return result;
    }
}
