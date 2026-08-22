package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

/** A namespaced tag identity tied to one concrete registry. */
public final class TagKey<T> {
    private final ResourceLocation registry;
    private final ResourceLocation location;

    private TagKey(ResourceLocation registry, ResourceLocation location) {
        if (registry == null || location == null) {
            throw new IllegalArgumentException("Tag registry and location cannot be null");
        }
        this.registry = registry;
        this.location = location;
    }

    public static <T> TagKey<T> create(
            ResourceLocation registry,
            ResourceLocation location) {
        return new TagKey<T>(registry, location);
    }

    public ResourceLocation registry() {
        return this.registry;
    }

    public ResourceLocation location() {
        return this.location;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TagKey)) return false;
        TagKey<?> key = (TagKey<?>)other;
        return this.registry.equals(key.registry)
                && this.location.equals(key.location);
    }

    @Override
    public int hashCode() {
        return 31 * this.registry.hashCode() + this.location.hashCode();
    }

    @Override
    public String toString() {
        return "TagKey[" + this.registry + " / " + this.location + "]";
    }
}
