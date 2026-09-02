package net.minecraft.server.registry;

import com.google.gson.JsonObject;
import net.minecraft.server.MapGenBase;
import net.minecraft.server.util.ResourceLocation;

/** Code-backed carver kind; configured instances are loaded from data packs. */
public final class CarverType {
    public interface ConfigurationCodec {
        ConfiguredCarverDefinition decode(
                ResourceLocation id,
                ResourceLocation type,
                JsonObject root);
    }

    public interface Factory {
        MapGenBase create(ConfiguredCarverDefinition definition);
    }

    public final String description;
    private final ConfigurationCodec codec;
    private final Factory factory;

    /** Compatibility constructor for third-party registry placeholders. */
    public CarverType(String description) {
        this(description, null, null);
    }

    public CarverType(
            String description,
            ConfigurationCodec codec,
            Factory factory) {
        if (description == null) {
            throw new IllegalArgumentException(
                    "Carver type description cannot be null");
        }
        this.description = description;
        this.codec = codec;
        this.factory = factory;
    }

    public ConfiguredCarverDefinition decode(
            ResourceLocation id,
            ResourceLocation type,
            JsonObject root) {
        if (this.codec == null) {
            throw new IllegalStateException(
                    "Carver type " + type + " has no configuration codec");
        }
        return this.codec.decode(id, type, root);
    }

    public MapGenBase create(ConfiguredCarverDefinition definition) {
        if (this.factory == null) {
            throw new IllegalStateException(
                    "Carver type " + definition.getType() + " has no factory");
        }
        MapGenBase generated = this.factory.create(definition);
        if (generated == null) {
            throw new IllegalStateException(
                    "Carver factory returned null for " + definition.getId());
        }
        return generated;
    }
}


