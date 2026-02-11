package net.minecraft.server.mod;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Minimal mod discovery and initialization from the /mods directory.
 */
public final class ModLoader {
    private static final Pattern FIELD_PATTERN = Pattern.compile("\\\"([a-zA-Z0-9_\\-]+)\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
    private static final Pattern DEP_PATTERN = Pattern.compile("\\\"dependencies\\\"\\s*:\\s*\\[(.*?)\\]");
    private static boolean initialized = false;
    private static final List<ModContainer> loadedMods = new ArrayList<ModContainer>();

    private ModLoader() {}

    public static synchronized void initialize(File serverDir) {
        if (initialized) {
            return;
        }
        initialized = true;

        File modsDir = new File(serverDir, "mods");
        if (!modsDir.exists() || !modsDir.isDirectory()) {
            return;
        }

        File[] files = modsDir.listFiles();
        if (files == null) {
            return;
        }

        for (int i = 0; i < files.length; i++) {
            File candidate = files[i];
            try {
                if (candidate.isDirectory()) {
                    loadDirectoryMod(candidate);
                } else if (candidate.isFile() && candidate.getName().endsWith(".jar")) {
                    loadJarMod(candidate);
                }
            } catch (Throwable t) {
                System.err.println("[ModLoader] Failed loading " + candidate.getName() + ": " + t.getMessage());
            }
        }
    }

    public static synchronized List<ModContainer> getLoadedMods() {
        return Collections.unmodifiableList(new ArrayList<ModContainer>(loadedMods));
    }

    private static void loadDirectoryMod(File dir) throws Exception {
        File modJson = new File(dir, "mod.json");
        if (!modJson.exists()) {
            return;
        }

        InputStream in = new FileInputStream(modJson);
        try {
            ModMetadata metadata = parseMetadata(readAll(in));
            if (metadata == null) {
                return;
            }

            URLClassLoader cl = new URLClassLoader(new URL[]{dir.toURI().toURL()}, ModLoader.class.getClassLoader());
            initializeContainer(metadata, cl);
        } finally {
            in.close();
        }
    }

    private static void loadJarMod(File jarFile) throws Exception {
        JarFile jar = new JarFile(jarFile);
        try {
            JarEntry modJson = jar.getJarEntry("mod.json");
            if (modJson == null) {
                return;
            }

            InputStream in = jar.getInputStream(modJson);
            ModMetadata metadata;
            try {
                metadata = parseMetadata(readAll(in));
            } finally {
                in.close();
            }
            if (metadata == null) {
                return;
            }

            URLClassLoader cl = new URLClassLoader(new URL[]{jarFile.toURI().toURL()}, ModLoader.class.getClassLoader());
            initializeContainer(metadata, cl);
        } finally {
            jar.close();
        }
    }

    private static void initializeContainer(ModMetadata metadata, ClassLoader classLoader) throws Exception {
        Class<?> entry = Class.forName(metadata.getEntrypoint(), true, classLoader);
        Object instance = entry.newInstance();
        if (!(instance instanceof ModInitializer)) {
            throw new IllegalStateException("Entrypoint does not implement ModInitializer: " + metadata.getEntrypoint());
        }

        ModInitializer initializer = (ModInitializer) instance;
        ModContext context = new ModContext(metadata);
        initializer.initialize(context);
        loadedMods.add(new ModContainer(metadata, initializer));

        System.out.println("[ModLoader] Initialized mod " + metadata.getId() + "@" + metadata.getVersion());
    }

    private static ModMetadata parseMetadata(String json) {
        if (json == null) {
            return null;
        }

        String id = null;
        String name = null;
        String version = null;
        String entrypoint = null;

        Matcher matcher = FIELD_PATTERN.matcher(json);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            if ("id".equals(key)) {
                id = value;
            } else if ("name".equals(key)) {
                name = value;
            } else if ("version".equals(key)) {
                version = value;
            } else if ("entrypoint".equals(key)) {
                entrypoint = value;
            }
        }

        if (id == null || entrypoint == null) {
            return null;
        }
        if (name == null) {
            name = id;
        }
        if (version == null) {
            version = "0.0.0";
        }

        List<String> dependencies = new ArrayList<String>();
        Matcher depsMatcher = DEP_PATTERN.matcher(json);
        if (depsMatcher.find()) {
            String depsBody = depsMatcher.group(1);
            Matcher depToken = Pattern.compile("\\\"([^\\\"]+)\\\"").matcher(depsBody);
            while (depToken.find()) {
                dependencies.add(depToken.group(1));
            }
        }

        return new ModMetadata(id, name, version, entrypoint, dependencies);
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), "UTF-8");
    }
}
