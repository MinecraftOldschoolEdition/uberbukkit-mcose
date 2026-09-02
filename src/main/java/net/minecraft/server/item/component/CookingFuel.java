package net.minecraft.server.item.component;

import net.minecraft.server.registry.NumberProviderRegistryApi;
import net.minecraft.server.registry.number.NumberProvider;
import net.minecraft.server.util.ResourceLocation;

/** Code-backed item component referencing reloadable cooking providers. */
public final class CookingFuel {
    private final ResourceLocation burnTimeKey;
    private final ResourceLocation speedMultiplierKey;

    public CookingFuel(
            ResourceLocation burnTimeKey,
            ResourceLocation speedMultiplierKey) {
        if (burnTimeKey == null || speedMultiplierKey == null) {
            throw new IllegalArgumentException("Cooking fuel provider keys cannot be null");
        }
        this.burnTimeKey = burnTimeKey;
        this.speedMultiplierKey = speedMultiplierKey;
    }

    public ResourceLocation getBurnTimeKey() {
        return this.burnTimeKey;
    }

    public ResourceLocation getSpeedMultiplierKey() {
        return this.speedMultiplierKey;
    }

    public int resolveBurnTimeTicks() {
        NumberProvider provider = NumberProviderRegistryApi.get(this.burnTimeKey);
        if (provider == null) {
            return 0;
        }
        float value = provider.getValue();
        if (Float.isNaN(value) || Float.isInfinite(value)
                || value <= 0.0F || (double)value > (double)Integer.MAX_VALUE) {
            throw new IllegalStateException("Cooking-fuel burn time "
                    + this.burnTimeKey
                    + " must resolve to a positive integer-range value");
        }
        int rounded = Math.round(value);
        if (rounded <= 0) {
            throw new IllegalStateException("Cooking-fuel burn time "
                    + this.burnTimeKey + " rounds below one tick");
        }
        return rounded;
    }

    public float resolveSpeedMultiplier() {
        NumberProvider provider = NumberProviderRegistryApi.get(this.speedMultiplierKey);
        return provider == null ? 1.0F : provider.getValue();
    }

    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CookingFuel)) return false;
        CookingFuel that = (CookingFuel)other;
        return this.burnTimeKey.equals(that.burnTimeKey)
                && this.speedMultiplierKey.equals(that.speedMultiplierKey);
    }

    public int hashCode() {
        return 31 * this.burnTimeKey.hashCode() + this.speedMultiplierKey.hashCode();
    }

    public String toString() {
        return "CookingFuel{" + this.burnTimeKey + ", "
                + this.speedMultiplierKey + "}";
    }
}
