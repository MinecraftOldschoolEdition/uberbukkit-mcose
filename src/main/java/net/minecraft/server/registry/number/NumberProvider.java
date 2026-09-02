package net.minecraft.server.registry.number;

import net.minecraft.server.util.ResourceLocation;

/** A reloadable source of a numeric gameplay value. */
public interface NumberProvider {
    ResourceLocation getType();

    float getValue();
}
