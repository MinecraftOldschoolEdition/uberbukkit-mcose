package net.minecraft.server.registry;

/** One immutable, published generation of the layered registry graph. */
public final class RegistryRuntimeSnapshot {
    private final long generation;
    private final LayeredRegistryAccess<RegistryLayer> layers;

    RegistryRuntimeSnapshot(long generation, LayeredRegistryAccess<RegistryLayer> layers) {
        this.generation = generation;
        this.layers = layers;
    }

    public long getGeneration() { return this.generation; }
    public LayeredRegistryAccess<RegistryLayer> getLayers() { return this.layers; }
    public RegistryAccess worldAccess() {
        return this.layers.getAccessForLoading(RegistryLayer.RELOADABLE);
    }
    public RegistryAccess reloadableAccess() {
        return this.layers.getLayer(RegistryLayer.RELOADABLE);
    }
}
