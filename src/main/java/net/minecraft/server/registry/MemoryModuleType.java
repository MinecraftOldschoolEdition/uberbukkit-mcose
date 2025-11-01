package net.minecraft.server.registry;

/**
 * Describes a type of memory module an entity can store.
 */
public final class MemoryModuleType {
    private final String description;

    public MemoryModuleType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}


