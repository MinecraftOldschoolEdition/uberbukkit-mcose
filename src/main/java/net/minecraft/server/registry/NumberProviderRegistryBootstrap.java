package net.minecraft.server.registry;

import com.google.gson.JsonElement;
import java.util.List;
import java.util.Map;
import net.minecraft.server.registry.number.NumberProvider;
import net.minecraft.server.registry.number.NumberProviderCodec;
import net.minecraft.server.registry.number.NumberProviders;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;

/** Loads and atomically reloads the built-in cooking number providers. */
public final class NumberProviderRegistryBootstrap {
    private static boolean initialized;

    private NumberProviderRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        publish(decode(null));
        initialized = true;
        System.out.println("[NumberProviderRegistryBootstrap] Registered "
                + Registries.NUMBER_PROVIDER.keys().size() + " number providers");
    }

    public static synchronized void reload() {
        publish(decode(null));
        initialized = true;
        RegistryRuntime.captureAndPublish();
    }

    public static synchronized void reload(
            RegistryDataLoader.ResourceProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Resource provider cannot be null");
        }
        publish(decode(provider));
        initialized = true;
        RegistryRuntime.captureAndPublish();
    }

    private static Map<ResourceLocation, NumberProvider> decode(
            RegistryDataLoader.ResourceProvider provider) {
        RegistryDataLoader.ValueDecoder<NumberProvider> decoder =
                new RegistryDataLoader.ValueDecoder<NumberProvider>() {
                    public NumberProvider decode(ResourceLocation key, JsonElement json) {
                        return NumberProviderCodec.decode(key, json);
                    }
                };
        Map<ResourceLocation, NumberProvider> decoded = provider == null
                ? RegistryDataLoader.loadAllValues("number_provider", decoder)
                : RegistryDataLoader.loadAllValues(
                        "number_provider", provider, decoder);
        List<ResourceLocation> required = NumberProviders.keys();
        for (int i = 0; i < required.size(); i++) {
            if (!decoded.containsKey(required.get(i))) {
                throw new IllegalStateException(
                        "Missing required number provider " + required.get(i));
            }
        }
        return decoded;
    }

    private static void publish(Map<ResourceLocation, NumberProvider> decoded) {
        if (!NumberProviderRegistryApi.publishReplacementAtomic(decoded)) {
            throw new IllegalStateException(
                    "Number-provider registry could not be replaced atomically");
        }
    }
}
