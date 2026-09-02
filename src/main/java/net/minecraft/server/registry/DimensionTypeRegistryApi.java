package net.minecraft.server.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.util.ResourceLocation;

/**
 * Typed dimension metadata plus the legacy provider-class/numeric-id bridge.
 *
 * <p>The modern registry value is {@link DimensionTypeDefinition}. Provider
 * classes remain in a separate bridge so data can never reflectively replace
 * the legacy engine implementation.</p>
 */
public final class DimensionTypeRegistryApi {
    private static final SimpleRegistry<Class<?>> PROVIDERS =
            new SimpleRegistry<Class<?>>();
    private static final RegistryAliasIndex ALIASES = new RegistryAliasIndex();
    private static volatile Map<Integer, ResourceLocation> dimensionIds =
            Collections.emptyMap();

    static {
        ALIASES.addAlias("minecraft:the_nether", "minecraft:nether");
    }

    private DimensionTypeRegistryApi() {}

    /** Legacy extension hook. It registers a provider bridge, not data metadata. */
    public static boolean register(ResourceLocation key, Class<?> value) {
        return RegistryApiSupport.register(PROVIDERS, canonicalProviderKey(key), value);
    }

    public static synchronized boolean registerDimensionId(
            int dimensionId, ResourceLocation key, Class<?> value) {
        ResourceLocation canonical = canonicalProviderKey(key);
        if (canonical == null || value == null) return false;
        ResourceLocation existingId = dimensionIds.get(Integer.valueOf(dimensionId));
        if (existingId != null && !existingId.equals(canonical)) return false;
        Class<?> existingProvider = PROVIDERS.get(canonical);
        if (existingProvider != null && existingProvider != value) return false;
        // All failure conditions are checked before the provider publication so
        // a numeric-id collision cannot leave a partially registered bridge.
        if (!register(canonical, value)) return false;
        LinkedHashMap<Integer, ResourceLocation> copy =
                new LinkedHashMap<Integer, ResourceLocation>(dimensionIds);
        copy.put(Integer.valueOf(dimensionId), canonical);
        dimensionIds = Collections.unmodifiableMap(copy);
        return true;
    }

    static synchronized boolean publishProviderMappingsAtomic(
            Map<ResourceLocation, Class<?>> providers,
            Map<Integer, ResourceLocation> ids) {
        if (providers == null || ids == null) return false;
        LinkedHashMap<ResourceLocation, Class<?>> canonicalProviders =
                new LinkedHashMap<ResourceLocation, Class<?>>();
        for (Map.Entry<ResourceLocation, Class<?>> entry : providers.entrySet()) {
            ResourceLocation canonical = canonicalProviderKey(entry.getKey());
            if (canonical == null || entry.getValue() == null
                    || canonicalProviders.put(canonical, entry.getValue()) != null) {
                return false;
            }
        }
        LinkedHashMap<Integer, ResourceLocation> canonicalIds =
                new LinkedHashMap<Integer, ResourceLocation>(dimensionIds);
        for (Map.Entry<Integer, ResourceLocation> entry : ids.entrySet()) {
            ResourceLocation canonical = canonicalProviderKey(entry.getValue());
            ResourceLocation existing = canonicalIds.get(entry.getKey());
            if (entry.getKey() == null || canonical == null
                    || (PROVIDERS.get(canonical) == null
                            && !canonicalProviders.containsKey(canonical))
                    || (existing != null && !existing.equals(canonical))) {
                return false;
            }
            canonicalIds.put(entry.getKey(), canonical);
        }
        // Merge built-ins atomically so a mod that used the legacy extension
        // hook before central bootstrap is not discarded.
        if (!PROVIDERS.registerAllAtomic(canonicalProviders)) return false;
        dimensionIds = Collections.unmodifiableMap(canonicalIds);
        return true;
    }

    public static Class<?> get(ResourceLocation key) {
        ResourceLocation canonical = canonicalProviderKey(key);
        return canonical == null ? null : RegistryApiSupport.get(PROVIDERS, canonical);
    }

    public static Class<?> getByIdentifier(String any) {
        ResourceLocation key = resolveProviderIdentifier(any);
        return key == null ? null : RegistryApiSupport.get(PROVIDERS, key);
    }

    public static Class<?> getByDimensionId(int dimensionId) {
        ResourceLocation key = dimensionIds.get(Integer.valueOf(dimensionId));
        return key == null ? null : RegistryApiSupport.get(PROVIDERS, key);
    }

    public static ResourceLocation getKeyByDimensionId(int dimensionId) {
        return dimensionIds.get(Integer.valueOf(dimensionId));
    }

    public static Integer getDimensionId(String any) {
        ResourceLocation key = resolveProviderIdentifier(any);
        if (key == null) return null;
        return findDimensionId(key);
    }

    public static Integer getDimensionId(Class<?> value) {
        ResourceLocation key = getKey(value);
        return key == null ? null : findDimensionId(key);
    }

    private static Integer findDimensionId(ResourceLocation key) {
        for (Map.Entry<Integer, ResourceLocation> entry : dimensionIds.entrySet()) {
            if (key.equals(entry.getValue())) return entry.getKey();
        }
        return null;
    }

    public static ResourceLocation getKey(Class<?> value) {
        return RegistryApiSupport.getKey(PROVIDERS, value);
    }

    /** Legacy provider keys. */
    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(PROVIDERS);
    }

    /** Legacy provider classes. */
    public static Collection<Class<?>> values() {
        return RegistryApiSupport.values(PROVIDERS);
    }

    public static int size() {
        return RegistryApiSupport.size(PROVIDERS);
    }

    public static String normalizeInputIdentifier(String any) {
        ResourceLocation key = resolveProviderIdentifier(any);
        return key == null ? null : key.toString();
    }

    public static String canonicalizeIdentifier(String any) {
        ResourceLocation key = resolveProviderIdentifier(any);
        if (key == null) return null;
        Class<?> value = RegistryApiSupport.get(PROVIDERS, key);
        ResourceLocation canonical = value == null ? null : getKey(value);
        return canonical == null ? null : canonical.toString();
    }

    public static boolean registerDefinition(
            ResourceLocation key, DimensionTypeDefinition definition) {
        return RegistryApiSupport.register(Registries.DIMENSION_TYPE, key, definition);
    }

    public static DimensionTypeDefinition getDefinition(ResourceLocation key) {
        ResourceLocation canonical = canonicalProviderKey(key);
        return canonical == null
                ? null : RegistryApiSupport.get(Registries.DIMENSION_TYPE, canonical);
    }

    public static DimensionTypeDefinition getDefinitionByIdentifier(String any) {
        String resolved = ALIASES.resolve(any);
        return resolved == null ? null : RegistryApiSupport.getByIdentifier(
                Registries.DIMENSION_TYPE, resolved);
    }

    public static Set<ResourceLocation> definitionKeys() {
        return RegistryApiSupport.keys(Registries.DIMENSION_TYPE);
    }

    public static Collection<DimensionTypeDefinition> definitions() {
        return RegistryApiSupport.values(Registries.DIMENSION_TYPE);
    }

    private static ResourceLocation resolveProviderIdentifier(String any) {
        String resolved = ALIASES.resolve(any);
        return resolved == null
                ? null : RegistryApiSupport.resolveIdentifier(PROVIDERS, resolved);
    }

    private static ResourceLocation canonicalProviderKey(ResourceLocation key) {
        if (key == null) return null;
        String resolved = ALIASES.resolve(key.toString());
        return resolved == null ? null : new ResourceLocation(resolved);
    }
}
