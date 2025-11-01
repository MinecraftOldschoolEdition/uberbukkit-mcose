package net.minecraft.server.util;

import java.util.Locale;

public final class ResourceLocation {
    private final String namespace;
    private final String path;

    public ResourceLocation(String full) {
        int idx = full.indexOf(':');
        if (idx >= 0) {
            this.namespace = full.substring(0, idx).toLowerCase(Locale.ROOT);
            this.path = normalize(full.substring(idx + 1));
        } else {
            this.namespace = "minecraft";
            this.path = normalize(full);
        }
    }

    public ResourceLocation(String namespace, String path) {
        this.namespace = (namespace == null || namespace.length() == 0) ? "minecraft" : namespace.toLowerCase(Locale.ROOT);
        this.path = normalize(path);
    }

    private String normalize(String p) {
        if (p == null) return "";
        if (p.startsWith("/")) p = p.substring(1);
        return p.replace('\\', '/');
    }

    public String getNamespace() { return namespace; }
    public String getPath() { return path; }

    public String toString() { return namespace + ":" + path; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceLocation)) return false;
        ResourceLocation other = (ResourceLocation)o;
        return namespace.equals(other.namespace) && path.equals(other.path);
    }
    public int hashCode() { return 31 * namespace.hashCode() + path.hashCode(); }
}


