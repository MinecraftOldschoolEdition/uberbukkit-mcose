package net.minecraft.server.registry;

/** Publishes complete registry generations with one volatile pointer swap. */
public final class RegistryRuntime {
    private static volatile RegistryRuntimeSnapshot current = emptySnapshot();
    private static long generation;

    private RegistryRuntime() {}

    public static RegistryRuntimeSnapshot current() { return current; }

    public static synchronized RegistryRuntimeSnapshot captureAndPublish() {
        LayeredRegistryAccess<RegistryLayer> candidate =
                new LayeredRegistryAccess<RegistryLayer>(RegistryLayer.orderedValues())
                        .replaceFrom(
                                RegistryLayer.STATIC,
                                RegistryRuntimeAccessFactory.captureStatic(),
                                RegistryRuntimeAccessFactory.captureWorld(),
                                RegistryAccess.EMPTY,
                                RegistryRuntimeAccessFactory.captureReloadable());
        RegistryRuntimeSnapshot published =
                new RegistryRuntimeSnapshot(++generation, candidate);
        current = published;
        return published;
    }

    static synchronized void resetForTests() {
        generation = 0L;
        current = emptySnapshot();
    }

    private static RegistryRuntimeSnapshot emptySnapshot() {
        return new RegistryRuntimeSnapshot(0L,
                new LayeredRegistryAccess<RegistryLayer>(RegistryLayer.orderedValues()));
    }
}
