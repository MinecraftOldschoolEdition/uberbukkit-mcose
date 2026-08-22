package net.minecraft.server.registry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.EnumArt;
import net.minecraft.server.PaintingVariant;
import net.minecraft.server.util.ResourceLocation;

/** Modern data-backed painting variant registry. */
public final class PaintingVariantRegistryApi {
    private PaintingVariantRegistryApi() {}

    public static boolean publishAtomic(Map<ResourceLocation, PaintingVariant> staged) {
        return Registries.PAINTING_VARIANT.registerAllAtomic(staged);
    }

    public static void freeze() {
        Registries.PAINTING_VARIANT.freeze();
    }

    public static PaintingVariant get(ResourceLocation key) {
        PaintingVariantRegistryBootstrap.initialize();
        return RegistryApiSupport.get(Registries.PAINTING_VARIANT, key);
    }

    public static PaintingVariant getByIdentifier(String any) {
        PaintingVariantRegistryBootstrap.initialize();
        return RegistryApiSupport.getByIdentifier(Registries.PAINTING_VARIANT, any);
    }

    public static PaintingVariant getByLegacyArt(EnumArt art) {
        PaintingVariantRegistryBootstrap.initialize();
        return PaintingVariantRegistryBootstrap.getByLegacyArt(art);
    }

    public static PaintingVariant getByLegacyTitle(String title) {
        PaintingVariantRegistryBootstrap.initialize();
        return PaintingVariantRegistryBootstrap.getByLegacyTitle(title);
    }

    public static ResourceLocation getKey(PaintingVariant value) {
        PaintingVariantRegistryBootstrap.initialize();
        return RegistryApiSupport.getKey(Registries.PAINTING_VARIANT, value);
    }

    public static Set<ResourceLocation> keys() {
        PaintingVariantRegistryBootstrap.initialize();
        return RegistryApiSupport.keys(Registries.PAINTING_VARIANT);
    }

    public static Collection<PaintingVariant> values() {
        PaintingVariantRegistryBootstrap.initialize();
        return RegistryApiSupport.values(Registries.PAINTING_VARIANT);
    }

    public static int size() {
        PaintingVariantRegistryBootstrap.initialize();
        return RegistryApiSupport.size(Registries.PAINTING_VARIANT);
    }

    public static boolean isFrozen() {
        PaintingVariantRegistryBootstrap.initialize();
        return Registries.PAINTING_VARIANT.isFrozen();
    }

    /** Fingerprint includes order because it controls legacy seeded selection. */
    public static String fingerprint() {
        PaintingVariantRegistryBootstrap.initialize();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (PaintingVariant variant : Registries.PAINTING_VARIANT.values()) {
                ResourceLocation key = Registries.PAINTING_VARIANT.getKey(variant);
                String line = key + "|" + variant.getWidth() + "|" + variant.getHeight()
                        + "|" + variant.getAssetId() + "|" + variant.getLegacyTitle() + "\n";
                digest.update(line.getBytes(StandardCharsets.UTF_8));
            }
            byte[] hash = digest.digest();
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (int i = 0; i < hash.length; i++) {
                int value = hash[i] & 255;
                if (value < 16) out.append('0');
                out.append(Integer.toHexString(value));
            }
            return out.toString();
        } catch (Throwable failure) {
            throw new IllegalStateException("Could not fingerprint painting variants", failure);
        }
    }
}
