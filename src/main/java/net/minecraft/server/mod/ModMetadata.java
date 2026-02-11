package net.minecraft.server.mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModMetadata {
    private final String id;
    private final String name;
    private final String version;
    private final String entrypoint;
    private final List<String> dependencies;

    public ModMetadata(String id, String name, String version, String entrypoint, List<String> dependencies) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.entrypoint = entrypoint;
        this.dependencies = dependencies == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(dependencies));
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getEntrypoint() { return entrypoint; }
    public List<String> getDependencies() { return dependencies; }
}
