package net.minecraft.server.registry.number;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.server.util.ResourceLocation;

/** Canonical 26.3 cooking number-provider keys used by legacy content. */
public final class NumberProviders {
    public static final ResourceLocation COOKING_DEFAULT_SPEED_MULTIPLIER =
            key("cooking/speed_default");
    public static final ResourceLocation COOKING_TIME_COAL =
            key("cooking/time_coal");
    public static final ResourceLocation COOKING_TIME_COAL_BLOCK =
            key("cooking/time_coal_block");
    public static final ResourceLocation COOKING_TIME_DRY_PLANTS =
            key("cooking/time_dry_plants");
    public static final ResourceLocation COOKING_TIME_LAVA_BUCKET =
            key("cooking/time_lava_bucket");
    public static final ResourceLocation COOKING_TIME_WOOD_BLOCKS =
            key("cooking/time_wood_blocks");
    public static final ResourceLocation COOKING_TIME_WOOD_ITEMS_EXTRA_SMALL =
            key("cooking/time_wood_items_extra_small");

    private static final List<ResourceLocation> KEYS = Collections.unmodifiableList(
            Arrays.asList(
                    COOKING_DEFAULT_SPEED_MULTIPLIER,
                    COOKING_TIME_COAL,
                    COOKING_TIME_COAL_BLOCK,
                    COOKING_TIME_DRY_PLANTS,
                    COOKING_TIME_LAVA_BUCKET,
                    COOKING_TIME_WOOD_BLOCKS,
                    COOKING_TIME_WOOD_ITEMS_EXTRA_SMALL));

    private NumberProviders() {}

    public static List<ResourceLocation> keys() {
        return KEYS;
    }

    private static ResourceLocation key(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
