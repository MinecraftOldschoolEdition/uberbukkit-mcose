package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

/**
 * Server-side STAT registry bootstrap. The dedicated server in this codebase
 * does not expose client StatBase classes, so this bootstrap registers only
 * well-known GUID placeholders when available in the future. For now, it is a
 * no-op to keep client/server parity (the registry still exists on the server).
 */
public final class StatRegistryBootstrap {
    private StatRegistryBootstrap() {}

    public static void initialize() {
        // Intentionally empty: stats are client-driven in this project.
        // The registry is created in Registries; entries can be registered
        // by plugins or future code via Registries.STAT.register(...).
        Registries.REGISTRIES.registerIfAbsent(new ResourceLocation("minecraft","stat"), Registries.STAT);
    }
}


