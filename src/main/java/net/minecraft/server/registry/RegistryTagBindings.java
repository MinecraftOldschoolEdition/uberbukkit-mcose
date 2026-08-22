package net.minecraft.server.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.util.ResourceLocation;

/**
 * One immutable generation of resolved registry tags.
 *
 * <p>Values and canonical keys are captured together, so gameplay never does
 * a lazy registry lookup against a different registry generation.</p>
 */
public final class RegistryTagBindings<T> {
    public interface Resolver<T> {
        T get(ResourceLocation key);

        ResourceLocation getKey(T value);
    }

    private final ResourceLocation registryKey;
    private final long registryRevision;
    private final Map<TagKey<T>, List<ResourceLocation>> keysByTag;
    private final Map<TagKey<T>, List<T>> valuesByTag;
    private final Map<TagKey<T>, Set<T>> membershipByTag;

    private RegistryTagBindings(
            ResourceLocation registryKey,
            long registryRevision,
            Map<TagKey<T>, List<ResourceLocation>> keysByTag,
            Map<TagKey<T>, List<T>> valuesByTag,
            Map<TagKey<T>, Set<T>> membershipByTag) {
        this.registryKey = registryKey;
        this.registryRevision = registryRevision;
        this.keysByTag = Collections.unmodifiableMap(keysByTag);
        this.valuesByTag = Collections.unmodifiableMap(valuesByTag);
        this.membershipByTag = Collections.unmodifiableMap(membershipByTag);
    }

    public static <T> RegistryTagBindings<T> create(
            ResourceLocation registryKey,
            long registryRevision,
            Map<ResourceLocation, List<ResourceLocation>> resolvedTags,
            Resolver<T> resolver) {
        if (registryKey == null || resolvedTags == null || resolver == null) {
            throw new IllegalArgumentException(
                    "Registry tag binding arguments cannot be null");
        }

        LinkedHashMap<TagKey<T>, List<ResourceLocation>> keysByTag =
                new LinkedHashMap<TagKey<T>, List<ResourceLocation>>();
        LinkedHashMap<TagKey<T>, List<T>> valuesByTag =
                new LinkedHashMap<TagKey<T>, List<T>>();
        LinkedHashMap<TagKey<T>, Set<T>> membershipByTag =
                new LinkedHashMap<TagKey<T>, Set<T>>();

        for (Map.Entry<ResourceLocation, List<ResourceLocation>> tagEntry
                : resolvedTags.entrySet()) {
            ResourceLocation location = tagEntry.getKey();
            List<ResourceLocation> requestedKeys = tagEntry.getValue();
            if (location == null || requestedKeys == null) {
                throw new IllegalArgumentException(
                        "Registry tag generation contains a null tag");
            }
            TagKey<T> tag = TagKey.create(registryKey, location);
            ArrayList<ResourceLocation> canonicalKeys =
                    new ArrayList<ResourceLocation>(requestedKeys.size());
            ArrayList<T> values = new ArrayList<T>(requestedKeys.size());
            IdentityHashMap<T, Boolean> identitySeen =
                    new IdentityHashMap<T, Boolean>();
            for (int i = 0; i < requestedKeys.size(); i++) {
                ResourceLocation requested = requestedKeys.get(i);
                T value = requested == null ? null : resolver.get(requested);
                if (value == null) {
                    throw new IllegalArgumentException(
                            "Tag " + location + " has missing registry value " + requested);
                }
                ResourceLocation canonical = resolver.getKey(value);
                if (canonical == null) {
                    throw new IllegalArgumentException(
                            "Tag " + location + " value " + requested
                                    + " has no canonical registry key");
                }
                if (!canonical.equals(requested)) {
                    throw new IllegalArgumentException(
                            "Tag " + location + " value " + requested
                                    + " is an alias for canonical key " + canonical);
                }
                if (identitySeen.put(value, Boolean.TRUE) == null) {
                    canonicalKeys.add(canonical);
                    values.add(value);
                }
            }

            List<ResourceLocation> immutableKeys = Collections.unmodifiableList(
                    new ArrayList<ResourceLocation>(canonicalKeys));
            List<T> immutableValues = Collections.unmodifiableList(
                    new ArrayList<T>(values));
            Set<T> identityMembership = Collections.newSetFromMap(
                    new IdentityHashMap<T, Boolean>());
            identityMembership.addAll(values);
            keysByTag.put(tag, immutableKeys);
            valuesByTag.put(tag, immutableValues);
            membershipByTag.put(tag,
                    Collections.unmodifiableSet(identityMembership));
        }

        return new RegistryTagBindings<T>(registryKey, registryRevision,
                keysByTag, valuesByTag, membershipByTag);
    }

    public static <T> RegistryTagBindings<T> empty(
            ResourceLocation registryKey,
            long registryRevision) {
        if (registryKey == null) {
            throw new IllegalArgumentException("Registry key cannot be null");
        }
        return new RegistryTagBindings<T>(registryKey, registryRevision,
                new LinkedHashMap<TagKey<T>, List<ResourceLocation>>(),
                new LinkedHashMap<TagKey<T>, List<T>>(),
                new LinkedHashMap<TagKey<T>, Set<T>>());
    }

    public ResourceLocation registryKey() {
        return this.registryKey;
    }

    public long registryRevision() {
        return this.registryRevision;
    }

    public boolean isForRevision(long revision) {
        return this.registryRevision == revision;
    }

    public Set<TagKey<T>> tagKeys() {
        return this.keysByTag.keySet();
    }

    public boolean contains(TagKey<T> tag, T value) {
        requireOwnedTag(tag);
        Set<T> membership = this.membershipByTag.get(tag);
        return membership != null && membership.contains(value);
    }

    public List<T> values(TagKey<T> tag) {
        requireOwnedTag(tag);
        List<T> values = this.valuesByTag.get(tag);
        return values == null ? Collections.<T>emptyList() : values;
    }

    public List<ResourceLocation> valueKeys(TagKey<T> tag) {
        requireOwnedTag(tag);
        List<ResourceLocation> keys = this.keysByTag.get(tag);
        return keys == null
                ? Collections.<ResourceLocation>emptyList() : keys;
    }

    private void requireOwnedTag(TagKey<T> tag) {
        if (tag == null || !this.registryKey.equals(tag.registry())) {
            throw new IllegalArgumentException(
                    "Tag does not belong to registry " + this.registryKey + ": " + tag);
        }
    }
}
