package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.MappedRegistry;

/**
 * 1.22-style entry point for built-in runtime registries.
 *
 * <p>Legacy numeric IDs remain the disk and vanilla-wire compatibility layer;
 * server systems and new backports should prefer these holder registries.</p>
 */
public final class BuiltInRegistries {
    public static final MappedRegistry<Block> BLOCK = BlockRegistry.registry();
    public static final MappedRegistry<Item> ITEM = ItemRegistry.registry();

    private BuiltInRegistries() {}

    public static void bootstrap() {
        BlockRegistry.keys();
        ItemRegistry.keys();
    }
}
