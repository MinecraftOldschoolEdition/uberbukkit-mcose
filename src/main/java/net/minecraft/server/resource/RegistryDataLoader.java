package net.minecraft.server.resource;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import net.minecraft.server.util.ResourceLocation;

/**
 * Decodes built-in registry entries from the modern
 * {@code data/<namespace>} resource tree.
 *
 * <p>The legacy engine still owns registry bootstrap order, numeric protocol
 * ids, and save compatibility. Registry values themselves can now be supplied
 * as data and validated before they become visible to gameplay systems.</p>
 */
public final class RegistryDataLoader {
    public interface Decoder<T> {
        T decode(ResourceLocation key, JsonObject json) throws Exception;
    }

    /** Decoder for registries whose root JSON value is not necessarily an object. */
    public interface ValueDecoder<T> {
        T decode(ResourceLocation key, JsonElement json) throws Exception;
    }

    /** Optional registry lookup used to honor required/optional direct tag values. */
    public interface TagValueResolver {
        boolean contains(ResourceLocation key);
    }

    public interface ResourceProvider {
        InputStream open(String resourcePath) throws IOException;
    }

    public interface RegistryResource {
        String sourceId();

        InputStream open() throws IOException;
    }

    /** Supplies matching resources in low-to-high server-data priority order. */
    public interface LayeredResourceProvider extends ResourceProvider {
        RegistryResource getResource(String resourcePath) throws IOException;

        List<RegistryResource> getResourceStack(String resourcePath) throws IOException;

        /**
         * Lists normalized resource paths below {@code resourceRoot}.
         * Implementations must return paths independent of layer priority;
         * callers resolve each path through {@link #getResourceStack(String)}.
         */
        default List<String> listResources(String resourceRoot) throws IOException {
            return Collections.emptyList();
        }
    }

    private static final ResourceProvider BUILT_IN_PROVIDER = new BuiltInResourceProvider();

    private RegistryDataLoader() {}

    /** Creates an immutable vanilla-plus-configured startup server-data view. */
    public static LayeredResourceProvider createLayeredProvider(File configuredRoot) {
        return new BuiltInResourceProvider(configuredRoot);
    }

    /** Explicit packaged source hook used by isolated loader verification. */
    public static LayeredResourceProvider createLayeredProvider(
            File configuredRoot,
            URL vanillaCodeSource) {
        return new BuiltInResourceProvider(configuredRoot, vanillaCodeSource);
    }

    public static <T> Map<ResourceLocation, T> loadRequired(
            String registryDirectory,
            Iterable<ResourceLocation> keys,
            Decoder<T> decoder) {
        return loadRequired(registryDirectory, keys, BUILT_IN_PROVIDER, decoder);
    }

    public static <T> Map<ResourceLocation, T> loadRequired(
            String registryDirectory,
            Iterable<ResourceLocation> keys,
            ResourceProvider provider,
            Decoder<T> decoder) {
        validateDirectory(registryDirectory);
        if (keys == null || provider == null || decoder == null) {
            throw new IllegalArgumentException("Registry data loader arguments cannot be null");
        }

        LinkedHashMap<ResourceLocation, T> loaded = new LinkedHashMap<ResourceLocation, T>();
        StringBuilder failures = new StringBuilder();
        for (ResourceLocation key : keys) {
            if (key == null) {
                appendFailure(failures, "<null>", "registry key cannot be null");
                continue;
            }
            try {
                validateResourceLocation(key, "registry key");
            } catch (Throwable failure) {
                appendFailure(failures, key.toString(), message(failure));
                continue;
            }
            if (loaded.containsKey(key)) {
                appendFailure(failures, key.toString(), "duplicate registry key");
                continue;
            }

            String resourcePath = toResourcePath(registryDirectory, key);
            InputStream stream = null;
            Reader reader = null;
            String sourceId = null;
            try {
                RegistryResource resource = getResource(provider, resourcePath);
                if (resource == null) {
                    throw new IOException("missing " + resourcePath);
                }
                sourceId = resource.sourceId();
                stream = resource.open();
                if (stream == null) {
                    throw new IOException("resource returned no stream");
                }
                reader = new InputStreamReader(stream, "UTF-8");
                T value = decoder.decode(key, parseObjectStrict(reader));
                if (value == null) {
                    throw new IllegalArgumentException("decoder returned null");
                }
                loaded.put(key, value);
            } catch (Throwable failure) {
                String source = sourceId == null ? "unresolved" : sourceId;
                appendFailure(failures, key.toString(), "[" + source + "] " + message(failure));
            } finally {
                closeQuietly(reader);
                if (reader == null) closeQuietly(stream);
            }
        }

        if (failures.length() > 0) {
            throw new IllegalStateException(
                    "Failed to load data registry '" + registryDirectory + "':\n" + failures);
        }
        if (loaded.isEmpty()) {
            throw new IllegalStateException("Data registry '" + registryDirectory + "' cannot be empty");
        }
        return Collections.unmodifiableMap(loaded);
    }

    public static <T> Map<ResourceLocation, T> loadRequiredValues(
            String registryDirectory,
            Iterable<ResourceLocation> keys,
            ValueDecoder<T> decoder) {
        return loadRequiredValues(registryDirectory, keys, BUILT_IN_PROVIDER, decoder);
    }

    /**
     * Scalar-capable counterpart to {@link #loadRequired}. The object-only
     * loader deliberately remains strict for its existing registry codecs.
     */
    public static <T> Map<ResourceLocation, T> loadRequiredValues(
            String registryDirectory,
            Iterable<ResourceLocation> keys,
            ResourceProvider provider,
            ValueDecoder<T> decoder) {
        validateDirectory(registryDirectory);
        if (keys == null || provider == null || decoder == null) {
            throw new IllegalArgumentException("Registry data loader arguments cannot be null");
        }

        LinkedHashMap<ResourceLocation, T> loaded = new LinkedHashMap<ResourceLocation, T>();
        StringBuilder failures = new StringBuilder();
        for (ResourceLocation key : keys) {
            if (key == null) {
                appendFailure(failures, "<null>", "registry key cannot be null");
                continue;
            }
            try {
                validateResourceLocation(key, "registry key");
            } catch (Throwable failure) {
                appendFailure(failures, key.toString(), message(failure));
                continue;
            }
            if (loaded.containsKey(key)) {
                appendFailure(failures, key.toString(), "duplicate registry key");
                continue;
            }

            String resourcePath = toResourcePath(registryDirectory, key);
            InputStream stream = null;
            Reader reader = null;
            String sourceId = null;
            try {
                RegistryResource resource = getResource(provider, resourcePath);
                if (resource == null) {
                    throw new IOException("missing " + resourcePath);
                }
                sourceId = resource.sourceId();
                stream = resource.open();
                if (stream == null) {
                    throw new IOException("resource returned no stream");
                }
                reader = new InputStreamReader(stream, "UTF-8");
                T value = decoder.decode(key, parseValueStrict(reader));
                if (value == null) {
                    throw new IllegalArgumentException("decoder returned null");
                }
                loaded.put(key, value);
            } catch (Throwable failure) {
                String source = sourceId == null ? "unresolved" : sourceId;
                appendFailure(failures, key.toString(), "[" + source + "] " + message(failure));
            } finally {
                closeQuietly(reader);
                if (reader == null) closeQuietly(stream);
            }
        }

        if (failures.length() > 0) {
            throw new IllegalStateException(
                    "Failed to load data registry '" + registryDirectory + "':\n" + failures);
        }
        if (loaded.isEmpty()) {
            throw new IllegalStateException("Data registry '" + registryDirectory + "' cannot be empty");
        }
        return Collections.unmodifiableMap(loaded);
    }

    /** Discovers all JSON entries in a registry, ordered by namespaced key. */
    public static List<ResourceLocation> discoverKeys(String registryDirectory) {
        return discoverKeys(registryDirectory, BUILT_IN_PROVIDER);
    }

    public static List<ResourceLocation> discoverKeys(
            String registryDirectory,
            ResourceProvider provider) {
        return discoverKeysAt(registryDirectory, false, provider);
    }

    /** Discovers and decodes a complete registry without a hand-maintained key list. */
    public static <T> Map<ResourceLocation, T> loadAll(
            String registryDirectory,
            Decoder<T> decoder) {
        return loadAll(registryDirectory, BUILT_IN_PROVIDER, decoder);
    }

    public static <T> Map<ResourceLocation, T> loadAll(
            String registryDirectory,
            ResourceProvider provider,
            Decoder<T> decoder) {
        List<ResourceLocation> keys = discoverKeys(registryDirectory, provider);
        ResourceProvider stagedProvider = snapshotRegistryResources(
                registryDirectory, keys, provider);
        return loadRequired(registryDirectory, keys, stagedProvider, decoder);
    }

    public static <T> Map<ResourceLocation, T> loadAllValues(
            String registryDirectory,
            ValueDecoder<T> decoder) {
        return loadAllValues(registryDirectory, BUILT_IN_PROVIDER, decoder);
    }

    public static <T> Map<ResourceLocation, T> loadAllValues(
            String registryDirectory,
            ResourceProvider provider,
            ValueDecoder<T> decoder) {
        List<ResourceLocation> keys = discoverKeys(registryDirectory, provider);
        ResourceProvider stagedProvider = snapshotRegistryResources(
                registryDirectory, keys, provider);
        return loadRequiredValues(registryDirectory, keys, stagedProvider, decoder);
    }

    /** Discovers every tag JSON for a registry, ordered by namespaced tag key. */
    public static List<ResourceLocation> discoverTagKeys(String registryDirectory) {
        return discoverTagKeys(registryDirectory, BUILT_IN_PROVIDER);
    }

    public static List<ResourceLocation> discoverTagKeys(
            String registryDirectory,
            ResourceProvider provider) {
        return discoverKeysAt(registryDirectory, true, provider);
    }

    /** Resolves a complete immutable tag generation, including empty tags. */
    public static Map<ResourceLocation, List<ResourceLocation>> loadAllTags(
            String registryDirectory) {
        return loadAllTags(registryDirectory, BUILT_IN_PROVIDER, null);
    }

    public static Map<ResourceLocation, List<ResourceLocation>> loadAllTags(
            String registryDirectory,
            ResourceProvider provider) {
        return loadAllTags(registryDirectory, provider, null);
    }

    public static Map<ResourceLocation, List<ResourceLocation>> loadAllTags(
            String registryDirectory,
            TagValueResolver valueResolver) {
        return loadAllTags(registryDirectory, BUILT_IN_PROVIDER, valueResolver);
    }

    public static Map<ResourceLocation, List<ResourceLocation>> loadAllTags(
            String registryDirectory,
            ResourceProvider provider,
            TagValueResolver valueResolver) {
        validateDirectory(registryDirectory);
        if (provider == null) {
            throw new IllegalArgumentException("Resource provider cannot be null");
        }
        List<ResourceLocation> keys = discoverTagKeys(registryDirectory, provider);

        ResourceProvider stagedProvider = snapshotTagResources(
                registryDirectory, keys, provider);

        TagResolver resolver = new TagResolver(
                registryDirectory, stagedProvider, valueResolver);
        LinkedHashMap<ResourceLocation, List<ResourceLocation>> loaded =
                new LinkedHashMap<ResourceLocation, List<ResourceLocation>>();
        StringBuilder failures = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            ResourceLocation key = keys.get(i);
            try {
                loaded.put(key, resolver.resolve(key, true));
            } catch (Throwable failure) {
                appendFailure(failures, key.toString(), message(failure));
            }
        }
        if (failures.length() > 0) {
            throw new IllegalStateException(
                    "Failed to load registry tags '" + registryDirectory + "':\n"
                            + failures);
        }
        return Collections.unmodifiableMap(loaded);
    }

    private static ResourceProvider snapshotTagResources(
            String registryDirectory,
            List<ResourceLocation> keys,
            ResourceProvider provider) {
        if (!(provider instanceof LayeredResourceProvider)) {
            throw new IllegalArgumentException(
                    "Resource provider does not support layered tag snapshots");
        }
        LinkedHashMap<String, List<RegistryResource>> snapshot =
                new LinkedHashMap<String, List<RegistryResource>>();
        try {
            LayeredResourceProvider layered = (LayeredResourceProvider)provider;
            for (int i = 0; i < keys.size(); i++) {
                String path = toTagResourcePath(registryDirectory, keys.get(i));
                List<RegistryResource> resources = layered.getResourceStack(path);
                if (resources == null || resources.isEmpty()) {
                    throw new IOException("listed tag disappeared before snapshot: " + path);
                }
                ArrayList<RegistryResource> staged =
                        new ArrayList<RegistryResource>(resources.size());
                for (int layer = 0; layer < resources.size(); layer++) {
                    RegistryResource resource = resources.get(layer);
                    if (resource == null) {
                        throw new IOException("tag layer " + layer + " is null: " + path);
                    }
                    staged.add(byteResource(resource.sourceId(), readAll(resource)));
                }
                snapshot.put(path, Collections.unmodifiableList(staged));
            }
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Failed to snapshot registry tags '" + registryDirectory
                            + "': " + message(failure), failure);
        }
        return new SnapshotLayeredResourceProvider(snapshot);
    }

    /**
     * Captures the highest-priority bytes for every discovered registry entry.
     * Discovery and decoding therefore observe one immutable startup pack view
     * even when an administrator is editing configured files concurrently.
     */
    private static ResourceProvider snapshotRegistryResources(
            String registryDirectory,
            List<ResourceLocation> keys,
            ResourceProvider provider) {
        if (!(provider instanceof LayeredResourceProvider)) {
            throw new IllegalArgumentException(
                    "Resource provider does not support layered registry snapshots");
        }
        LinkedHashMap<String, List<RegistryResource>> snapshot =
                new LinkedHashMap<String, List<RegistryResource>>();
        try {
            LayeredResourceProvider layered = (LayeredResourceProvider)provider;
            for (int i = 0; i < keys.size(); i++) {
                String path = toResourcePath(registryDirectory, keys.get(i));
                RegistryResource resource = layered.getResource(path);
                if (resource == null) {
                    throw new IOException(
                            "listed registry entry disappeared before snapshot: " + path);
                }
                snapshot.put(path, Collections.singletonList(
                        byteResource(resource.sourceId(), readAll(resource))));
            }
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Failed to snapshot data registry '" + registryDirectory
                            + "': " + message(failure), failure);
        }
        return new SnapshotLayeredResourceProvider(snapshot);
    }

    private static byte[] readAll(RegistryResource resource) throws IOException {
        InputStream input = resource.open();
        if (input == null) throw new IOException("resource returned no stream");
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            closeQuietly(input);
        }
    }

    private static RegistryResource byteResource(
            final String sourceId,
            final byte[] bytes) {
        return new RegistryResource() {
            public String sourceId() {
                return sourceId;
            }

            public InputStream open() {
                return new ByteArrayInputStream(bytes);
            }
        };
    }

    private static final class SnapshotLayeredResourceProvider
            implements LayeredResourceProvider {
        private final Map<String, List<RegistryResource>> resources;

        SnapshotLayeredResourceProvider(
                Map<String, List<RegistryResource>> resources) {
            this.resources = Collections.unmodifiableMap(
                    new LinkedHashMap<String, List<RegistryResource>>(resources));
        }

        public InputStream open(String resourcePath) throws IOException {
            RegistryResource resource = getResource(resourcePath);
            return resource == null ? null : resource.open();
        }

        public RegistryResource getResource(String resourcePath) {
            List<RegistryResource> stack = this.resources.get(resourcePath);
            return stack == null || stack.isEmpty()
                    ? null : stack.get(stack.size() - 1);
        }

        public List<RegistryResource> getResourceStack(String resourcePath) {
            List<RegistryResource> stack = this.resources.get(resourcePath);
            return stack == null
                    ? Collections.<RegistryResource>emptyList() : stack;
        }

        public List<String> listResources(String resourceRoot) {
            ArrayList<String> paths = new ArrayList<String>();
            String prefix = resourceRoot + "/";
            for (String path : this.resources.keySet()) {
                if (path.startsWith(prefix)) paths.add(path);
            }
            return Collections.unmodifiableList(paths);
        }
    }

    private static List<ResourceLocation> discoverKeysAt(
            String registryDirectory,
            boolean tags,
            ResourceProvider provider) {
        validateDirectory(registryDirectory);
        if (!(provider instanceof LayeredResourceProvider)) {
            throw new IllegalArgumentException(
                    "Resource provider does not support deterministic discovery");
        }

        String middle = tags
                ? "/tags/" + registryDirectory + "/"
                : "/" + registryDirectory + "/";
        Set<String> discovered = new HashSet<String>();
        try {
            List<String> listed = ((LayeredResourceProvider)provider)
                    .listResources("data");
            if (listed == null) {
                throw new IOException("resource listing returned null");
            }
            for (int i = 0; i < listed.size(); i++) {
                String path = listed.get(i);
                validateRelativeResourcePath(path);
                if (!path.startsWith("data/") || !path.endsWith(".json")) {
                    continue;
                }
                int namespaceEnd = path.indexOf('/', "data/".length());
                if (namespaceEnd < 0) continue;
                String namespace = path.substring("data/".length(), namespaceEnd);
                String remainder = path.substring(namespaceEnd);
                if (!remainder.startsWith(middle)) continue;
                String valuePath = remainder.substring(
                        middle.length(), remainder.length() - ".json".length());
                validateResourceLocationParts(namespace, valuePath, path);
                String identity = namespace + ":" + valuePath;
                if (!discovered.add(identity)) {
                    throw new IOException(
                            "duplicate discovered registry resource " + identity);
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Failed to discover data registry "
                            + (tags ? "tags " : "") + "'" + registryDirectory
                            + "': " + message(failure), failure);
        }

        ArrayList<ResourceLocation> keys =
                new ArrayList<ResourceLocation>(discovered.size());
        for (String identity : discovered) {
            keys.add(new ResourceLocation(identity));
        }
        Collections.sort(keys, new java.util.Comparator<ResourceLocation>() {
            public int compare(ResourceLocation left, ResourceLocation right) {
                int path = left.getPath().compareTo(right.getPath());
                return path != 0 ? path
                        : left.getNamespace().compareTo(right.getNamespace());
            }
        });
        return Collections.unmodifiableList(keys);
    }

    /** Loads the ordered values of a built-in registry tag. */
    public static List<ResourceLocation> loadRequiredTag(
            String registryDirectory,
            ResourceLocation tagKey) {
        return loadRequiredTag(registryDirectory, tagKey, BUILT_IN_PROVIDER);
    }

    public static List<ResourceLocation> loadRequiredTag(
            String registryDirectory,
            ResourceLocation tagKey,
            TagValueResolver valueResolver) {
        return loadRequiredTag(
                registryDirectory, tagKey, BUILT_IN_PROVIDER, valueResolver);
    }

    public static List<ResourceLocation> loadRequiredTag(
            String registryDirectory,
            ResourceLocation tagKey,
            ResourceProvider provider) {
        return loadRequiredTag(registryDirectory, tagKey, provider, null);
    }

    public static List<ResourceLocation> loadRequiredTag(
            String registryDirectory,
            ResourceLocation tagKey,
            ResourceProvider provider,
            TagValueResolver valueResolver) {
        validateDirectory(registryDirectory);
        if (tagKey == null || provider == null) {
            throw new IllegalArgumentException("Tag key and resource provider cannot be null");
        }
        validateResourceLocation(tagKey, "tag key");
        try {
            List<ResourceLocation> result = new TagResolver(
                    registryDirectory, provider, valueResolver).resolve(tagKey, true);
            return Collections.unmodifiableList(result);
        } catch (Throwable failure) {
            String resourcePath = toTagResourcePath(registryDirectory, tagKey);
            throw new IllegalStateException("Failed to load registry tag '" + tagKey
                    + "' from " + resourcePath + ": " + message(failure), failure);
        }
    }

    private static String toTagResourcePath(
            String registryDirectory,
            ResourceLocation tagKey) {
        validateResourceLocation(tagKey, "tag key");
        String resourcePath = "data/" + tagKey.getNamespace() + "/tags/"
                + registryDirectory + "/" + tagKey.getPath() + ".json";
        requireRelativeResourcePath(resourcePath);
        return resourcePath;
    }

    private static final class TagResolver {
        private final String registryDirectory;
        private final ResourceProvider provider;
        private final TagValueResolver valueResolver;
        private final Map<ResourceLocation, List<ResourceLocation>> resolved =
                new LinkedHashMap<ResourceLocation, List<ResourceLocation>>();
        private final List<ResourceLocation> resolving = new ArrayList<ResourceLocation>();

        TagResolver(
                String registryDirectory,
                ResourceProvider provider,
                TagValueResolver valueResolver) {
            this.registryDirectory = registryDirectory;
            this.provider = provider;
            this.valueResolver = valueResolver;
        }

        List<ResourceLocation> resolve(ResourceLocation tagKey, boolean required)
                throws IOException {
            List<ResourceLocation> cached = this.resolved.get(tagKey);
            if (cached != null) return cached;

            int cycleStart = this.resolving.indexOf(tagKey);
            if (cycleStart >= 0) {
                StringBuilder cycle = new StringBuilder();
                for (int i = cycleStart; i < this.resolving.size(); i++) {
                    if (cycle.length() > 0) cycle.append(" -> ");
                    cycle.append('#').append(this.resolving.get(i));
                }
                cycle.append(" -> #").append(tagKey);
                throw new IllegalArgumentException("cyclic registry tag reference: " + cycle);
            }

            String resourcePath = toTagResourcePath(this.registryDirectory, tagKey);
            List<RegistryResource> resources = getResourceStack(this.provider, resourcePath);
            if (resources.isEmpty()) {
                if (required) throw new IOException("missing " + resourcePath);
                return null;
            }

            this.resolving.add(tagKey);
            try {
                List<TagEntry> entries = loadEntries(resources);
                ArrayList<ResourceLocation> values = new ArrayList<ResourceLocation>();
                Set<ResourceLocation> seen = new HashSet<ResourceLocation>();
                for (int i = 0; i < entries.size(); i++) {
                    TagEntry entry = entries.get(i);
                    if (entry.tag) {
                        List<ResourceLocation> nested = resolve(entry.id, entry.required);
                        if (nested == null) continue;
                        for (int j = 0; j < nested.size(); j++) {
                            ResourceLocation value = nested.get(j);
                            if (seen.add(value)) values.add(value);
                        }
                    } else {
                        if (this.valueResolver != null
                                && !this.valueResolver.contains(entry.id)) {
                            if (entry.required) {
                                throw new IllegalArgumentException(
                                        "missing required tag value " + entry.id);
                            }
                            continue;
                        }
                        if (seen.add(entry.id)) values.add(entry.id);
                    }
                }
                List<ResourceLocation> immutable = Collections.unmodifiableList(values);
                this.resolved.put(tagKey, immutable);
                return immutable;
            } finally {
                this.resolving.remove(this.resolving.size() - 1);
            }
        }

        private List<TagEntry> loadEntries(List<RegistryResource> resources) {
            ArrayList<TagEntry> entries = new ArrayList<TagEntry>();
            for (int layer = 0; layer < resources.size(); layer++) {
                RegistryResource resource = resources.get(layer);
                if (resource == null) {
                    throw new IllegalArgumentException("tag layer " + layer + " is null");
                }
                InputStream stream = null;
                Reader reader = null;
                try {
                    stream = resource.open();
                    if (stream == null) {
                        throw new IOException("resource returned no stream");
                    }
                    reader = new InputStreamReader(stream, "UTF-8");
                    JsonObject parsed = parseObjectStrict(reader);
                    requireOnlyFields(parsed, "registry tag", "replace", "values");
                    if (parsed.has("replace")) {
                        JsonElement replace = parsed.get("replace");
                        if (replace == null || !replace.isJsonPrimitive()
                                || !replace.getAsJsonPrimitive().isBoolean()) {
                            throw new IllegalArgumentException("tag replace must be a boolean");
                        }
                        if (replace.getAsBoolean()) {
                            entries.clear();
                        }
                    }

                    JsonElement valuesElement = parsed.get("values");
                    if (valuesElement == null || !valuesElement.isJsonArray()) {
                        throw new IllegalArgumentException("tag values must be a JSON array");
                    }
                    JsonArray values = valuesElement.getAsJsonArray();
                    for (int i = 0; i < values.size(); i++) {
                        TagEntry entry = TagEntry.decode(values.get(i), i);
                        // Modern tag loading preserves every textual entry in
                        // pack order. Resolved registry identities are de-duped
                        // later, after required/optional validation has run.
                        entries.add(entry);
                    }
                } catch (Throwable failure) {
                    throw new IllegalArgumentException("[" + resource.sourceId() + "] "
                            + message(failure), failure);
                } finally {
                    closeQuietly(reader);
                    if (reader == null) closeQuietly(stream);
                }
            }
            return entries;
        }
    }

    private static final class TagEntry {
        final ResourceLocation id;
        final boolean tag;
        final boolean required;

        TagEntry(ResourceLocation id, boolean tag, boolean required) {
            this.id = id;
            this.tag = tag;
            this.required = required;
        }

        static TagEntry decode(JsonElement raw, int index) {
            String idText;
            boolean required = true;
            if (raw != null && raw.isJsonPrimitive()
                    && raw.getAsJsonPrimitive().isString()) {
                idText = raw.getAsString();
            } else if (raw != null && raw.isJsonObject()) {
                JsonObject object = raw.getAsJsonObject();
                requireOnlyFields(object, "tag value " + index, "id", "required");
                JsonElement id = object.get("id");
                if (id == null || !id.isJsonPrimitive()
                        || !id.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException(
                            "tag value " + index + " id must be a string");
                }
                idText = id.getAsString();
                if (object.has("required")) {
                    JsonElement requiredValue = object.get("required");
                    if (requiredValue == null || !requiredValue.isJsonPrimitive()
                            || !requiredValue.getAsJsonPrimitive().isBoolean()) {
                        throw new IllegalArgumentException(
                                "tag value " + index + " required must be a boolean");
                    }
                    required = requiredValue.getAsBoolean();
                }
            } else {
                throw new IllegalArgumentException(
                        "tag value " + index + " must be a string or object");
            }

            boolean tag = idText.startsWith("#");
            String identifier = tag ? idText.substring(1) : idText;
            if (identifier.length() == 0) {
                throw new IllegalArgumentException(
                        "tag value " + index + " identifier cannot be empty");
            }
            return new TagEntry(parseStrictResourceLocation(
                    identifier, "tag value " + index), tag, required);
        }

        String identity() {
            return (this.tag ? "#" : "") + this.id.toString();
        }
    }

    public static String toResourcePath(String registryDirectory, ResourceLocation key) {
        validateDirectory(registryDirectory);
        if (key == null) {
            throw new IllegalArgumentException("Registry key cannot be null");
        }
        validateResourceLocation(key, "registry key");
        String resourcePath = "data/" + key.getNamespace() + "/"
                + registryDirectory + "/" + key.getPath() + ".json";
        requireRelativeResourcePath(resourcePath);
        return resourcePath;
    }

    private static void validateDirectory(String registryDirectory) {
        if (registryDirectory == null || registryDirectory.length() == 0
                || registryDirectory.indexOf("..") >= 0
                || registryDirectory.charAt(0) == '/') {
            throw new IllegalArgumentException("Invalid registry data directory: " + registryDirectory);
        }
    }

    /** Parses a modern identifier without legacy ResourceLocation normalization. */
    public static ResourceLocation parseIdentifierStrict(
            String identifier,
            String description) {
        return parseStrictResourceLocation(identifier, description);
    }

    private static ResourceLocation parseStrictResourceLocation(
            String identifier,
            String description) {
        if (identifier == null || identifier.length() == 0
                || identifier.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(
                    description + " has invalid identifier '" + identifier + "'");
        }
        int colon = identifier.indexOf(':');
        if (colon != identifier.lastIndexOf(':')) {
            throw new IllegalArgumentException(
                    description + " has invalid identifier '" + identifier + "'");
        }
        String namespace = colon < 0 ? "minecraft" : identifier.substring(0, colon);
        String path = colon < 0 ? identifier : identifier.substring(colon + 1);
        validateResourceLocationParts(namespace, path, description);
        return new ResourceLocation(namespace, path);
    }

    private static void validateResourceLocationParts(
            String namespace,
            String path,
            String description) {
        if (namespace == null || namespace.length() == 0
                || path == null || path.length() == 0) {
            throw new IllegalArgumentException(
                    description + " has an empty resource identifier component");
        }
        for (int i = 0; i < namespace.length(); i++) {
            char c = namespace.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.')) {
                throw new IllegalArgumentException(
                        description + " has invalid resource namespace '" + namespace + "'");
            }
        }
        String[] segments = path.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            if (segments[i].length() == 0 || ".".equals(segments[i])
                    || "..".equals(segments[i])) {
                throw new IllegalArgumentException(
                        description + " has invalid relative resource path '" + path + "'");
            }
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || c == '/')) {
                throw new IllegalArgumentException(
                        description + " has invalid relative resource path '" + path + "'");
            }
        }
    }

    private static void validateResourceLocation(
            ResourceLocation key,
            String description) {
        if (key == null) {
            throw new IllegalArgumentException(description + " cannot be null");
        }
        validateResourceLocationParts(
                key.getNamespace(), key.getPath(), description);
    }

    private static JsonObject parseObjectStrict(Reader reader) {
        JsonElement parsed = parseValueStrict(reader);
        if (parsed == null || !parsed.isJsonObject()) {
            throw new IllegalArgumentException("root must be a JSON object");
        }
        return parsed.getAsJsonObject();
    }

    private static JsonElement parseValueStrict(Reader reader) {
        JsonReader json = new JsonReader(reader);
        json.setLenient(false);
        try {
            JsonElement parsed = readValueStrict(json);
            if (json.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("trailing content after JSON value");
            }
            return parsed;
        } catch (IOException failure) {
            throw new IllegalArgumentException("invalid JSON: " + message(failure), failure);
        }
    }

    private static JsonElement readValueStrict(JsonReader reader) throws IOException {
        JsonToken token = reader.peek();
        switch (token) {
            case BEGIN_OBJECT:
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) {
                        throw new IllegalArgumentException("Duplicate key \"" + name + "\"");
                    }
                    object.add(name, readValueStrict(reader));
                }
                reader.endObject();
                return object;
            case BEGIN_ARRAY:
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) {
                    array.add(readValueStrict(reader));
                }
                reader.endArray();
                return array;
            case STRING:
                return new JsonPrimitive(reader.nextString());
            case NUMBER:
                return new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN:
                return new JsonPrimitive(Boolean.valueOf(reader.nextBoolean()));
            case NULL:
                reader.nextNull();
                return JsonNull.INSTANCE;
            case END_DOCUMENT:
                throw new IllegalArgumentException("root JSON value cannot be missing");
            default:
                throw new IllegalArgumentException("unexpected JSON token " + token);
        }
    }

    private static void requireOnlyFields(
            JsonObject json,
            String description,
            String... allowedFields) {
        Set<String> allowed = new HashSet<String>();
        for (int i = 0; i < allowedFields.length; i++) {
            allowed.add(allowedFields[i]);
        }
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                throw new IllegalArgumentException(description + " contains unsupported field '"
                        + entry.getKey() + "'");
            }
        }
    }

    private static RegistryResource getResource(
            ResourceProvider provider,
            String resourcePath) throws IOException {
        if (provider instanceof LayeredResourceProvider) {
            return ((LayeredResourceProvider)provider).getResource(resourcePath);
        }
        return streamResource("custom", provider.open(resourcePath));
    }

    private static List<RegistryResource> getResourceStack(
            ResourceProvider provider,
            String resourcePath) throws IOException {
        if (provider instanceof LayeredResourceProvider) {
            List<RegistryResource> resources =
                    ((LayeredResourceProvider)provider).getResourceStack(resourcePath);
            return resources == null
                    ? Collections.<RegistryResource>emptyList()
                    : resources;
        }
        RegistryResource resource = streamResource("custom", provider.open(resourcePath));
        return resource == null
                ? Collections.<RegistryResource>emptyList()
                : Collections.singletonList(resource);
    }

    private static RegistryResource streamResource(
            final String sourceId,
            final InputStream stream) {
        if (stream == null) return null;
        return new RegistryResource() {
            public String sourceId() {
                return sourceId;
            }

            public InputStream open() {
                return stream;
            }
        };
    }

    private static void appendFailure(StringBuilder failures, String key, String reason) {
        failures.append(" - ").append(key).append(": ").append(reason).append('\n');
    }

    private static String message(Throwable failure) {
        String value = failure == null ? null : failure.getMessage();
        return value == null || value.length() == 0
                ? (failure == null ? "unknown error" : failure.getClass().getSimpleName())
                : value;
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException ignored) {}
    }

    private static final class BuiltInResourceProvider implements LayeredResourceProvider {
        private final File configuredRoot;
        private final URL vanillaCodeSource;
        private final boolean developmentFallbacks;
        private final List<File> developmentResourceRoots;

        BuiltInResourceProvider() {
            String configured = System.getProperty("mcose.resourcesDir");
            this.configuredRoot = configured == null || configured.length() == 0
                    ? null
                    : new File(configured);
            this.vanillaCodeSource = ownCodeSource();
            this.developmentFallbacks = true;
            this.developmentResourceRoots = developmentRoots(this.vanillaCodeSource);
        }

        BuiltInResourceProvider(File configuredRoot) {
            this.configuredRoot = configuredRoot;
            this.vanillaCodeSource = ownCodeSource();
            this.developmentFallbacks = true;
            this.developmentResourceRoots = developmentRoots(this.vanillaCodeSource);
        }

        BuiltInResourceProvider(File configuredRoot, URL vanillaCodeSource) {
            if (vanillaCodeSource == null) {
                throw new IllegalArgumentException("Vanilla code source cannot be null");
            }
            this.configuredRoot = configuredRoot;
            this.vanillaCodeSource = vanillaCodeSource;
            this.developmentFallbacks = false;
            this.developmentResourceRoots = Collections.emptyList();
        }

        public InputStream open(String resourcePath) throws IOException {
            RegistryResource resource = getResource(resourcePath);
            return resource == null ? null : resource.open();
        }

        public RegistryResource getResource(String resourcePath) throws IOException {
            validateRelativeResourcePath(resourcePath);
            RegistryResource configured = resourceFromRoot(
                    "configured", configuredRoot, resourcePath);
            return configured != null ? configured : getVanillaResource(resourcePath);
        }

        public List<RegistryResource> getResourceStack(String resourcePath) throws IOException {
            validateRelativeResourcePath(resourcePath);
            ArrayList<RegistryResource> layers = new ArrayList<RegistryResource>(2);
            RegistryResource vanilla = getVanillaResource(resourcePath);
            if (vanilla != null) layers.add(vanilla);
            RegistryResource configured = resourceFromRoot(
                    "configured", configuredRoot, resourcePath);
            if (configured != null) layers.add(configured);
            return layers;
        }

        public List<String> listResources(String resourceRoot) throws IOException {
            validateRelativeResourcePath(resourceRoot);
            TreeSet<String> paths = new TreeSet<String>();
            if (this.vanillaCodeSource != null) {
                listCodeSourceResources(this.vanillaCodeSource, resourceRoot, paths);
            }
            if (this.developmentFallbacks) {
                // Gradle separates compiled classes from processed resources;
                // direct IDE launches may use the source resource directory.
                for (int i = 0; i < this.developmentResourceRoots.size(); i++) {
                    listFileRoot(this.developmentResourceRoots.get(i),
                            resourceRoot, paths);
                }
            }
            listFileRoot(this.configuredRoot, resourceRoot, paths);
            return Collections.unmodifiableList(new ArrayList<String>(paths));
        }

        private RegistryResource getVanillaResource(String resourcePath) throws IOException {
            RegistryResource packaged = this.vanillaCodeSource == null
                    ? null : resourceFromCodeSource(
                            this.vanillaCodeSource, resourcePath);
            if (packaged != null || !this.developmentFallbacks) return packaged;
            for (int i = 0; i < this.developmentResourceRoots.size(); i++) {
                RegistryResource fallback = resourceFromRoot(
                        "vanilla", this.developmentResourceRoots.get(i), resourcePath);
                if (fallback != null) return fallback;
            }
            return null;
        }

        private static void listCodeSourceResources(
                URL location,
                String resourceRoot,
                Set<String> output) throws IOException {
            try {
                if (location == null || !"file".equalsIgnoreCase(location.getProtocol())) {
                    throw new IOException("unsupported vanilla code source: " + location);
                }
                File source = new File(location.toURI());
                if (source.isFile() && source.getName().endsWith(".jar")) {
                    JarFile jar = new JarFile(source);
                    try {
                        listJar(jar, resourceRoot, output);
                    } finally {
                        jar.close();
                    }
                } else if (source.isDirectory()) {
                    listFileRoot(source, resourceRoot, output);
                }
            } catch (Exception failure) {
                if (failure instanceof IOException) throw (IOException)failure;
                throw new IOException("could not list packaged registry resources", failure);
            }
        }

        private static RegistryResource resourceFromCodeSource(
                URL location,
                String resourcePath) throws IOException {
            try {
                if (location == null || !"file".equalsIgnoreCase(location.getProtocol())) {
                    throw new IOException("unsupported vanilla code source: " + location);
                }
                File source = new File(location.toURI());
                if (source.isDirectory()) {
                    return resourceFromTrustedRoot(source, resourcePath);
                }
                if (!source.isFile() || !source.getName().endsWith(".jar")) {
                    return null;
                }
                JarFile jar = new JarFile(source);
                try {
                    if (jar.getJarEntry(resourcePath) == null) return null;
                } finally {
                    jar.close();
                }
                URL entryUrl = new URL("jar:" + source.toURI().toURL().toExternalForm()
                        + "!/" + resourcePath);
                return new UrlRegistryResource("vanilla", entryUrl);
            } catch (Exception failure) {
                if (failure instanceof IOException) throw (IOException)failure;
                throw new IOException("could not resolve packaged registry resource "
                        + resourcePath, failure);
            }
        }

        private static RegistryResource resourceFromTrustedRoot(
                File root,
                String resourcePath) throws IOException {
            File canonicalRoot = root.getCanonicalFile();
            File file = new File(canonicalRoot, resourcePath).getCanonicalFile();
            requireWithinRoot(canonicalRoot, file, resourcePath);
            return file.isFile() ? new FileRegistryResource("vanilla", file) : null;
        }

        private static URL ownCodeSource() {
            try {
                if (RegistryDataLoader.class.getProtectionDomain() == null
                        || RegistryDataLoader.class.getProtectionDomain()
                                .getCodeSource() == null) {
                    return null;
                }
                return RegistryDataLoader.class.getProtectionDomain()
                        .getCodeSource().getLocation();
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static List<File> developmentRoots(URL codeSource) {
            ArrayList<File> roots = new ArrayList<File>();
            try {
                if (codeSource == null
                        || !"file".equalsIgnoreCase(codeSource.getProtocol())) {
                    return Collections.emptyList();
                }
                File source = new File(codeSource.toURI()).getCanonicalFile();
                if (!source.isDirectory()) {
                    return Collections.emptyList();
                }
                File cursor = source;
                while (cursor != null && !"build".equals(cursor.getName())) {
                    cursor = cursor.getParentFile();
                }
                if (cursor != null && cursor.getParentFile() != null) {
                    roots.add(new File(cursor, "resources/main"));
                    roots.add(new File(cursor.getParentFile(), "src/main/resources"));
                }
            } catch (Exception ignored) {}
            return Collections.unmodifiableList(roots);
        }

        private static void listJar(
                JarFile jar,
                String resourceRoot,
                Set<String> output) throws IOException {
            String prefix = resourceRoot.endsWith("/")
                    ? resourceRoot : resourceRoot + "/";
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry == null || entry.isDirectory()) continue;
                String name = entry.getName();
                validateRelativeResourcePath(name);
                if (name.startsWith(prefix)) output.add(name);
            }
        }

        private static void listFileRoot(
                File root,
                String resourceRoot,
                Set<String> output) throws IOException {
            if (root == null || !root.exists()) return;
            File canonicalRoot = root.getCanonicalFile();
            File start = new File(canonicalRoot, resourceRoot).getCanonicalFile();
            requireWithinRoot(canonicalRoot, start, resourceRoot);
            if (!start.exists()) return;
            listDirectory(canonicalRoot, start, resourceRoot, output,
                    new HashSet<String>());
        }

        private static void listDirectory(
                File boundary,
                File current,
                String logicalPath,
                Set<String> output,
                Set<String> visitedDirectories) throws IOException {
            File canonicalBoundary = boundary.getCanonicalFile();
            File canonicalCurrent = current.getCanonicalFile();
            requireWithinRoot(canonicalBoundary, canonicalCurrent, logicalPath);
            if (canonicalCurrent.isFile()) {
                validateRelativeResourcePath(logicalPath);
                output.add(logicalPath.replace(File.separatorChar, '/'));
                return;
            }
            if (!canonicalCurrent.isDirectory()
                    || !visitedDirectories.add(canonicalCurrent.getPath())) {
                return;
            }
            File[] children = canonicalCurrent.listFiles();
            if (children == null) {
                throw new IOException("could not list resource directory "
                        + canonicalCurrent);
            }
            java.util.Arrays.sort(children, new java.util.Comparator<File>() {
                public int compare(File left, File right) {
                    return left.getName().compareTo(right.getName());
                }
            });
            for (int i = 0; i < children.length; i++) {
                String childPath = logicalPath + "/" + children[i].getName();
                validateRelativeResourcePath(childPath.replace(File.separatorChar, '/'));
                listDirectory(canonicalBoundary, children[i],
                        childPath.replace(File.separatorChar, '/'), output,
                        visitedDirectories);
            }
        }

        private static void requireWithinRoot(
                File root,
                File file,
                String resourcePath) throws IOException {
            String rootPath = root.getPath();
            String filePath = file.getPath();
            if (!filePath.equals(rootPath)
                    && !filePath.startsWith(rootPath + File.separator)) {
                throw new IOException("resource path escapes configured root: "
                        + resourcePath);
            }
        }

        private RegistryResource resourceFromRoot(
                String sourceId,
                File root,
                String resourcePath) throws IOException {
            if (root == null) return null;
            File canonicalRoot = root.getCanonicalFile();
            File file = new File(canonicalRoot, resourcePath).getCanonicalFile();
            String rootPath = canonicalRoot.getPath();
            String filePath = file.getPath();
            if (!filePath.equals(rootPath)
                    && !filePath.startsWith(rootPath + File.separator)) {
                throw new IOException("resource path escapes " + sourceId + " root: "
                        + resourcePath);
            }
            if (!file.isFile()) return null;
            return new FileRegistryResource(sourceId, file);
        }
    }

    private static void validateRelativeResourcePath(String resourcePath) throws IOException {
        if (resourcePath == null || resourcePath.length() == 0
                || new File(resourcePath).isAbsolute()
                || resourcePath.charAt(0) == '/'
                || resourcePath.indexOf('\\') >= 0) {
            throw new IOException("invalid relative resource path: " + resourcePath);
        }
        String[] segments = resourcePath.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            if (segments[i].length() == 0
                    || ".".equals(segments[i])
                    || "..".equals(segments[i])) {
                throw new IOException("invalid relative resource path: " + resourcePath);
            }
        }
    }

    private static void requireRelativeResourcePath(String resourcePath) {
        try {
            validateRelativeResourcePath(resourcePath);
        } catch (IOException failure) {
            throw new IllegalArgumentException(failure.getMessage(), failure);
        }
    }

    private static final class FileRegistryResource implements RegistryResource {
        private final String sourceId;
        private final File file;

        FileRegistryResource(String sourceId, File file) {
            this.sourceId = sourceId;
            this.file = file;
        }

        public String sourceId() {
            return sourceId;
        }

        public InputStream open() throws IOException {
            return new FileInputStream(file);
        }
    }

    private static final class UrlRegistryResource implements RegistryResource {
        private final String sourceId;
        private final URL url;

        UrlRegistryResource(String sourceId, URL url) {
            this.sourceId = sourceId;
            this.url = url;
        }

        public String sourceId() {
            return sourceId;
        }

        public InputStream open() throws IOException {
            return url.openStream();
        }
    }
}
