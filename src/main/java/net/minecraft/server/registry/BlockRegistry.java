package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.Holder;
import net.minecraft.server.IdMap;
import net.minecraft.server.MappedRegistry;
import net.minecraft.server.util.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Namespaced block registry with legacy-id compatibility and alias support.
 */
public final class BlockRegistry {
    private static final Map<ResourceLocation, Block> byKey = new HashMap<ResourceLocation, Block>();
    private static final Map<Block, ResourceLocation> keyOf = new IdentityHashMap<Block, ResourceLocation>();
    private static final MappedRegistry<Block> runtimeRegistry = new MappedRegistry<Block>();
    private static final List<Listener> listeners = new ArrayList<Listener>();
    private static boolean scanned = false;

    public interface Listener {
        void onRegistered(ResourceLocation key, Block block);
    }

    private BlockRegistry() {}

    public static synchronized void register(ResourceLocation key, Block block, int legacyId) {
        if (key == null || block == null) return;
        Block existing = byKey.get(key);
        if (existing != null && existing != block) {
            return;
        }
        byKey.put(key, block);
        if (!keyOf.containsKey(block)) {
            keyOf.put(block, key);
        }
        runtimeRegistry.registerIfAbsent(key, block, legacyId);
        try { Registries.BLOCK.registerIfAbsent(key, block); } catch (Throwable ignored) {}
        for (int i = 0; i < listeners.size(); i++) {
            try { listeners.get(i).onRegistered(key, block); } catch (Throwable ignored) {}
        }
    }

    public static synchronized void registerAlias(ResourceLocation alias, Block block) {
        if (alias == null || block == null) return;
        Block existing = byKey.get(alias);
        if (existing == null) {
            byKey.put(alias, block);
            try { Registries.BLOCK.registerIfAbsent(alias, block); } catch (Throwable ignored) {}
        }
    }

    public static Block get(ResourceLocation key) {
        ensureScanned();
        return byKey.get(key);
    }

    public static Block getByIdentifier(String any) {
        String normalized = normalizeInputIdentifier(any);
        if (normalized == null) return null;
        return get(new ResourceLocation(normalized));
    }

    public static ResourceLocation getKey(Block block) {
        ensureScanned();
        return keyOf.get(block);
    }

    public static Holder<Block> getHolder(ResourceLocation key) {
        ensureScanned();
        return runtimeRegistry.getHolder(key);
    }

    public static Holder<Block> getHolder(Block block) {
        ensureScanned();
        return runtimeRegistry.getHolder(block);
    }

    public static Holder<Block> getHolderByRuntimeId(int runtimeId) {
        ensureScanned();
        return runtimeRegistry.holderById(runtimeId);
    }

    public static IdMap<Block> idMap() {
        ensureScanned();
        return runtimeRegistry;
    }

    public static MappedRegistry<Block> registry() {
        ensureScanned();
        return runtimeRegistry;
    }

    public static int getRuntimeId(Block block) {
        ensureScanned();
        return runtimeRegistry.getId(block);
    }

    public static Block getByRuntimeId(int runtimeId) {
        ensureScanned();
        return runtimeRegistry.byId(runtimeId);
    }

    public static Block getByLegacyId(int legacyId) {
        if (legacyId < 0 || legacyId >= Block.byId.length) return null;
        return Block.byId[legacyId];
    }

    public static int getLegacyId(Block block) {
        return block == null ? -1 : block.id;
    }

    public static Collection<Block> values() {
        ensureScanned();
        return Collections.unmodifiableCollection(byKey.values());
    }

    public static Set<ResourceLocation> keys() {
        ensureScanned();
        return Collections.unmodifiableSet(byKey.keySet());
    }

    public static Collection<ResourceLocation> primaryKeys() {
        ensureScanned();
        return Collections.unmodifiableCollection(keyOf.values());
    }

    public static Collection<ResourceLocation> displayKeys() {
        ensureScanned();
        LinkedHashSet<ResourceLocation> display = new LinkedHashSet<ResourceLocation>();
        display.addAll(keyOf.values());
        display.addAll(byKey.keySet());
        return display;
    }

    public static String normalizeInputIdentifier(String any) {
        if (any == null) return null;
        ensureScanned();
        String normalized = RegistryKeyPolicy.normalizeIdentifier(any);
        if (normalized == null) return null;
        try {
            ResourceLocation key = new ResourceLocation(normalized);
            return byKey.containsKey(key) ? key.toString() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String canonicalizeIdentifier(String any) {
        if (any == null) return null;
        ensureScanned();
        String normalized = normalizeInputIdentifier(any);
        if (normalized == null) return null;
        Block block = byKey.get(new ResourceLocation(normalized));
        if (block == null) return null;
        ResourceLocation key = keyOf.get(block);
        return key != null ? key.toString() : null;
    }

    public static synchronized void bootstrapFromBlocksList() {
        if (!scanned) {
            scanned = true;
        }
        syncFromBlocksList();
    }

    public static void addListener(Listener listener) {
        if (listener == null) return;
        listeners.add(listener);
    }

    public static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public static String keysetFingerprint() {
        ensureScanned();
        return fingerprint(keys());
    }

    public static String canonicalFingerprint() {
        ensureScanned();
        return fingerprint(primaryKeys());
    }

    public static boolean runSanityChecks() {
        ensureScanned();
        String[] required = new String[]{
                "minecraft:cobblestone",
                "minecraft:netherrack",
                "minecraft:glowstone",
                "minecraft:redstone_torch"
        };
        boolean ok = true;
        for (int i = 0; i < required.length; i++) {
            if (normalizeInputIdentifier(required[i]) == null) {
                ok = false;
                System.err.println("[BlockRegistry] Missing critical key: " + required[i]);
            }
        }
        return ok;
    }

    private static void registerCanonicalFor(int legacyId, Block block) {
        String internal = block.l();
        if (internal == null || internal.length() == 0) {
            internal = "unregistered_block";
        }

        String stripped = RegistryKeyPolicy.stripKnownPrefix(internal);
        String rawSnake = RegistryKeyPolicy.toSnakeCase(stripped);
        ResourceLocation canonicalKey = VanillaRegistryKeys.blockKey(block);
        if (canonicalKey == null) {
            String canonicalPath = RegistryKeyPolicy.canonicalizeBlockPath(rawSnake, legacyId);
            canonicalKey = new ResourceLocation(RegistryKeyPolicy.DEFAULT_NAMESPACE, canonicalPath);
        }

        Block conflict = byKey.get(canonicalKey);
        if (conflict != null && conflict != block) {
            String collisionKey = RegistryKeyPolicy.collisionCompatibilitySuffix(canonicalKey.getPath(), legacyId);
            canonicalKey = new ResourceLocation(RegistryKeyPolicy.DEFAULT_NAMESPACE, collisionKey);
        }

        register(canonicalKey, block, legacyId);
        keyOf.put(block, canonicalKey);

        registerAliasIfFree(rawSnake, block, canonicalKey);

        String materialFirst = RegistryKeyPolicy.materialFirstIfKnown(rawSnake);
        registerAliasIfFree(materialFirst, block, canonicalKey);

        String materialLast = RegistryKeyPolicy.materialLastIfKnown(materialFirst);
        registerAliasIfFree(materialLast, block, canonicalKey);

        String altMaterial = RegistryKeyPolicy.materialFirstWithAltSynonyms(rawSnake);
        registerAliasIfFree(altMaterial, block, canonicalKey);

        String[] specialAliases = RegistryKeyPolicy.aliasesForCanonical(canonicalKey.getPath());
        for (int i = 0; i < specialAliases.length; i++) {
            registerAliasIfFree(specialAliases[i], block, canonicalKey);
        }

        registerStateAliases(legacyId, block, canonicalKey);
    }

    private static void registerStateAliases(int legacyId, Block block, ResourceLocation canonicalKey) {
        if (legacyId == 8) {
            registerAliasIfFree("flowing_water", block, canonicalKey);
        } else if (legacyId == 9) {
            registerAliasIfFree("stationary_water", block, canonicalKey);
        } else if (legacyId == 10) {
            registerAliasIfFree("flowing_lava", block, canonicalKey);
        } else if (legacyId == 11) {
            registerAliasIfFree("stationary_lava", block, canonicalKey);
        } else if (legacyId == 62) {
            registerAliasIfFree("furnace_lit", block, canonicalKey);
        } else if (legacyId == 74) {
            registerAliasIfFree("glowing_redstone_ore", block, canonicalKey);
        } else if (legacyId == 76) {
            registerAliasIfFree("lit_redstone_torch", block, canonicalKey);
            registerAliasIfFree("redstone_torch_on", block, canonicalKey);
        } else if (legacyId == 75) {
            registerAliasIfFree("unlit_redstone_torch", block, canonicalKey);
            registerAliasIfFree("lit_redstone_torch_off", block, canonicalKey);
            registerAliasIfFree("redstone_torch_off", block, canonicalKey);
        }
    }

    private static void registerAliasIfFree(String aliasPath, Block block, ResourceLocation canonicalKey) {
        if (aliasPath == null || aliasPath.length() == 0) return;
        try {
            ResourceLocation alias = new ResourceLocation(RegistryKeyPolicy.DEFAULT_NAMESPACE, aliasPath);
            if (alias.equals(canonicalKey)) return;
            if (!byKey.containsKey(alias)) {
                byKey.put(alias, block);
                try { Registries.BLOCK.registerIfAbsent(alias, block); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private static synchronized void ensureScanned() {
        if (!scanned) {
            bootstrapFromBlocksList();
            return;
        }
        syncFromBlocksList();
    }

    private static void syncFromBlocksList() {
        boolean changed = false;
        for (int id = 0; id < Block.byId.length; id++) {
            Block block = Block.byId[id];
            if (block == null) continue;
            if (!keyOf.containsKey(block)) {
                registerCanonicalFor(id, block);
                changed = true;
            }
        }
        if (changed) {
            try { LegacyIdBridge.refresh(); } catch (Throwable ignored) {}
        }
    }

    private static String fingerprint(Collection<ResourceLocation> values) {
        try {
            ArrayList<String> keys = new ArrayList<String>();
            for (ResourceLocation rl : values) {
                keys.add(rl.toString());
            }
            Collections.sort(keys);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < keys.size(); i++) {
                digest.update(keys.get(i).getBytes(StandardCharsets.UTF_8));
                digest.update((byte)'\n');
            }
            byte[] hash = digest.digest();
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (int i = 0; i < hash.length; i++) {
                int v = hash[i] & 0xFF;
                if (v < 16) out.append('0');
                out.append(Integer.toHexString(v));
            }
            return out.toString();
        } catch (Throwable t) {
            return "unavailable";
        }
    }
}
