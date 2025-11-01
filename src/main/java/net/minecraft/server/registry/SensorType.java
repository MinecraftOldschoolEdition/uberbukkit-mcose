package net.minecraft.server.registry;

/**
 * Describes a type of sensor available to entity AI.
 */
public final class SensorType {
    private final String description;

    public SensorType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}


