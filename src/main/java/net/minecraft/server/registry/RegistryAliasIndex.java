package net.minecraft.server.registry;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Small helper for alias-to-canonical identifier mapping.
 */
public final class RegistryAliasIndex {
    private final Map<String, String> aliasToCanonical = new HashMap<String, String>();
    private final Map<String, Set<String>> canonicalToAliases = new HashMap<String, Set<String>>();

    public synchronized void addAlias(String canonicalIdentifier, String aliasIdentifier) {
        String canonical = RegistryKeyPolicy.normalizeIdentifier(canonicalIdentifier);
        String alias = RegistryKeyPolicy.normalizeIdentifier(aliasIdentifier);
        if (canonical == null || alias == null) {
            return;
        }

        aliasToCanonical.put(alias, canonical);
        if (!canonical.equals(alias)) {
            aliasToCanonical.put(canonical, canonical);
        }

        Set<String> aliases = canonicalToAliases.get(canonical);
        if (aliases == null) {
            aliases = new LinkedHashSet<String>();
            canonicalToAliases.put(canonical, aliases);
        }
        aliases.add(alias);
    }

    public synchronized String resolve(String anyIdentifier) {
        String normalized = RegistryKeyPolicy.normalizeIdentifier(anyIdentifier);
        if (normalized == null) {
            return null;
        }

        String canonical = aliasToCanonical.get(normalized);
        return canonical != null ? canonical : normalized;
    }

    public synchronized Set<String> aliasesFor(String canonicalIdentifier) {
        String canonical = RegistryKeyPolicy.normalizeIdentifier(canonicalIdentifier);
        if (canonical == null) {
            return Collections.emptySet();
        }

        Set<String> aliases = canonicalToAliases.get(canonical);
        if (aliases == null || aliases.isEmpty()) {
            return Collections.emptySet();
        }

        return Collections.unmodifiableSet(new LinkedHashSet<String>(aliases));
    }
}
