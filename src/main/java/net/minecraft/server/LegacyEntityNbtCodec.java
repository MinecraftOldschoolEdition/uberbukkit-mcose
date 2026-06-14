package net.minecraft.server;

import net.minecraft.server.registry.EntityTypeRegistry;
import net.minecraft.server.util.ResourceLocation;

/**
 * Ensures legacy entity fields remain available as a projection for old loaders/callers.
 */
public final class LegacyEntityNbtCodec {
    private LegacyEntityNbtCodec() {}

    public static boolean ensureLegacyShadowFields(NBTTagCompound entityTag) {
        if (entityTag == null) {
            return false;
        }
        boolean changed = false;
        changed |= ensureCoreLegacyFields(entityTag);

        String modernType = entityTag.getString("entity_type");
        if (modernType == null || modernType.length() == 0) {
            return changed;
        }

        String normalized = EntityTypeRegistry.normalizeInputIdentifier(modernType);
        if (normalized == null) {
            return changed;
        }

        String legacyName = EntityTypeRegistry.getLegacyName(new ResourceLocation(normalized));
        if (legacyName == null || legacyName.length() == 0) {
            return changed;
        }

        if (!entityTag.hasKey("id")) {
            entityTag.setString("id", legacyName);
            changed = true;
        }
        return changed;
    }

    private static boolean ensureCoreLegacyFields(NBTTagCompound entityTag) {
        if (!entityTag.hasKey("entity_data")) {
            return false;
        }

        NBTTagCompound entityData = entityTag.k("entity_data");
        if (!entityTag.hasKey("CustomName") && entityData.hasKey("custom_name")) {
            entityTag.setString("CustomName", entityData.getString("custom_name"));
            return true;
        }
        return false;
    }
}
