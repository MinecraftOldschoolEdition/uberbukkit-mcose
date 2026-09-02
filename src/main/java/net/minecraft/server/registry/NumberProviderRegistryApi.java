package net.minecraft.server.registry;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.registry.number.NumberProvider;
import net.minecraft.server.util.ResourceLocation;

/** API surface for the reloadable number-provider registry. */
public final class NumberProviderRegistryApi {
    private NumberProviderRegistryApi() {}

    public static boolean publishReplacementAtomic(
            Map<ResourceLocation, NumberProvider> staged) {
        return Registries.NUMBER_PROVIDER.replaceAllAtomic(staged);
    }

    public static NumberProvider get(ResourceLocation key) {
        NumberProviderRegistryBootstrap.initialize();
        return RegistryApiSupport.get(Registries.NUMBER_PROVIDER, key);
    }

    public static NumberProvider getByIdentifier(String any) {
        NumberProviderRegistryBootstrap.initialize();
        return RegistryApiSupport.getByIdentifier(Registries.NUMBER_PROVIDER, any);
    }

    public static Set<ResourceLocation> keys() {
        NumberProviderRegistryBootstrap.initialize();
        return RegistryApiSupport.keys(Registries.NUMBER_PROVIDER);
    }

    public static Collection<NumberProvider> values() {
        NumberProviderRegistryBootstrap.initialize();
        return RegistryApiSupport.values(Registries.NUMBER_PROVIDER);
    }

    public static int size() {
        NumberProviderRegistryBootstrap.initialize();
        return RegistryApiSupport.size(Registries.NUMBER_PROVIDER);
    }
}
