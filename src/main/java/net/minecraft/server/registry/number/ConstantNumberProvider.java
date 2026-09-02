package net.minecraft.server.registry.number;

import net.minecraft.server.util.ResourceLocation;

/** Inline/typed constant number provider supported by the legacy bridge. */
public final class ConstantNumberProvider implements NumberProvider {
    public static final ResourceLocation TYPE =
            new ResourceLocation("minecraft", "constant");

    private final float value;

    public ConstantNumberProvider(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException("Constant number provider must be finite");
        }
        this.value = value;
    }

    public ResourceLocation getType() {
        return TYPE;
    }

    public float getValue() {
        return this.value;
    }
}
