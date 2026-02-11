package net.minecraft.server.mod;

public final class ModContainer {
    private final ModMetadata metadata;
    private final ModInitializer initializer;

    public ModContainer(ModMetadata metadata, ModInitializer initializer) {
        this.metadata = metadata;
        this.initializer = initializer;
    }

    public ModMetadata getMetadata() { return metadata; }
    public ModInitializer getInitializer() { return initializer; }
}
