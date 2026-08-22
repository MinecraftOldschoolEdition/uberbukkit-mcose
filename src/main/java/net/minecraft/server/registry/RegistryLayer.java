package net.minecraft.server.registry;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Registry ownership layers matching the 26.3 world-loading lifecycle. */
public enum RegistryLayer {
    STATIC,
    WORLD,
    DIMENSIONS,
    RELOADABLE;

    private static final List<RegistryLayer> ORDER =
            Collections.unmodifiableList(Arrays.asList(values()));

    public static List<RegistryLayer> orderedValues() { return ORDER; }
}
