package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;

import java.util.HashMap;
import java.util.Map;

final class ChunkEntityLimits {
    private static final String CONFIG_ENABLED = "settings.entity-load-save-limit-per-chunk.enabled";
    private static final String CONFIG_LIMITS = "settings.entity-load-save-limit-per-chunk.limits";
    private static final String DEFAULT_LIMITS = "Item=256,Arrow=128,Snowball=128,Egg=128,FallingSand=64,PrimedTnt=64,Minecart=64,Boat=64";

    private static boolean loaded;
    private static boolean enabled;
    private static Map limits = new HashMap();

    private ChunkEntityLimits() {
    }

    static Map createCounter() {
        return new HashMap();
    }

    static boolean canSave(NBTTagCompound entityTag, Entity entity, Map counts) {
        loadConfig();
        if (!enabled || entity == null) {
            return true;
        }

        String entityId = entityTag == null ? null : entityTag.getString("id");
        if (entityId == null || entityId.length() == 0) {
            entityId = EntityTypes.b(entity);
        }

        return countIfAllowed(entityId, entity.getClass(), counts);
    }

    static boolean canLoad(NBTTagCompound entityTag, Map counts) {
        loadConfig();
        if (!enabled || entityTag == null) {
            return true;
        }

        return countIfAllowed(entityTag.getString("id"), null, counts);
    }

    private static boolean countIfAllowed(String entityId, Class entityClass, Map counts) {
        String countKey = getCountKey(entityId, entityClass);
        int limit = getLimit(entityId, entityClass, countKey);
        if (limit < 0) {
            return true;
        }

        Integer count = (Integer) counts.get(countKey);
        int current = count == null ? 0 : count.intValue();
        if (current >= limit) {
            return false;
        }

        counts.put(countKey, Integer.valueOf(current + 1));
        return true;
    }

    private static int getLimit(String entityId, Class entityClass, String countKey) {
        Integer limit = (Integer) limits.get(countKey);
        if (limit != null) {
            return limit.intValue();
        }

        if (entityId != null) {
            String normalizedEntityId = normalize(entityId);
            limit = (Integer) limits.get(normalizedEntityId);
            if (limit != null) {
                return limit.intValue();
            }

            int namespace = normalizedEntityId.indexOf(':');
            if (namespace >= 0 && namespace < normalizedEntityId.length() - 1) {
                limit = (Integer) limits.get(normalizedEntityId.substring(namespace + 1));
                if (limit != null) {
                    return limit.intValue();
                }
            }
        }

        if (entityClass != null) {
            limit = (Integer) limits.get(normalize(entityClass.getSimpleName()));
            if (limit != null) {
                return limit.intValue();
            }
        }

        return -1;
    }

    private static String getCountKey(String entityId, Class entityClass) {
        String normalized = normalize(entityId);
        if (normalized.length() > 0) {
            return normalized;
        }

        return entityClass == null ? "" : normalize(entityClass.getSimpleName());
    }

    private static void loadConfig() {
        if (loaded) {
            return;
        }

        PoseidonConfig config = PoseidonConfig.getInstance();
        enabled = getConfigBoolean(config, CONFIG_ENABLED, false);
        limits = parseLimits(getConfigString(config, CONFIG_LIMITS, DEFAULT_LIMITS));
        loaded = true;
    }

    private static Map parseLimits(String raw) {
        Map parsed = new HashMap();
        if (raw == null) {
            return parsed;
        }

        String[] entries = raw.split("[,;\\n]");
        for (int i = 0; i < entries.length; ++i) {
            String entry = entries[i] == null ? "" : entries[i].trim();
            if (entry.length() == 0) {
                continue;
            }

            int separator = entry.indexOf('=');
            if (separator < 0) {
                separator = entry.indexOf(':');
            }

            if (separator <= 0 || separator >= entry.length() - 1) {
                continue;
            }

            String key = entry.substring(0, separator).trim();
            String value = entry.substring(separator + 1).trim();
            try {
                addLimit(parsed, key, Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
            }
        }

        return parsed;
    }

    private static void addLimit(Map parsed, String key, int limit) {
        String normalized = normalize(key);
        if (normalized.length() == 0) {
            return;
        }

        putLimit(parsed, normalized, limit);

        int namespace = normalized.indexOf(':');
        if (namespace >= 0 && namespace < normalized.length() - 1) {
            putLimit(parsed, normalized.substring(namespace + 1), limit);
        } else if (!normalized.startsWith("entity")) {
            putLimit(parsed, "entity" + normalized, limit);
        }
    }

    private static void putLimit(Map parsed, String normalized, int limit) {
        parsed.put(normalized, Integer.valueOf(limit));

        if ("primedtnt".equals(normalized) || "entityprimedtnt".equals(normalized) || "entitytntprimed".equals(normalized)) {
            parsed.put("tnt", Integer.valueOf(limit));
            parsed.put("entitytnt", Integer.valueOf(limit));
            parsed.put("entitytntprimed", Integer.valueOf(limit));
        } else if ("tnt".equals(normalized) || "entitytnt".equals(normalized)) {
            parsed.put("primedtnt", Integer.valueOf(limit));
            parsed.put("entityprimedtnt", Integer.valueOf(limit));
            parsed.put("entitytntprimed", Integer.valueOf(limit));
        } else if ("fallingsand".equals(normalized) || "entityfallingsand".equals(normalized)) {
            parsed.put("fallingblock", Integer.valueOf(limit));
            parsed.put("entityfallingblock", Integer.valueOf(limit));
        } else if ("fallingblock".equals(normalized) || "entityfallingblock".equals(normalized)) {
            parsed.put("fallingsand", Integer.valueOf(limit));
            parsed.put("entityfallingsand", Integer.valueOf(limit));
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().toLowerCase().replace("_", "").replace("-", "");
    }

    private static boolean getConfigBoolean(PoseidonConfig config, String key, boolean defaultValue) {
        try {
            Object value = config.getConfigOption(key, Boolean.valueOf(defaultValue));
            if (value instanceof Boolean) {
                return ((Boolean) value).booleanValue();
            }

            return Boolean.valueOf(String.valueOf(value)).booleanValue();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static String getConfigString(PoseidonConfig config, String key, String defaultValue) {
        try {
            Object value = config.getConfigOption(key, defaultValue);
            return value == null ? defaultValue : String.valueOf(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
