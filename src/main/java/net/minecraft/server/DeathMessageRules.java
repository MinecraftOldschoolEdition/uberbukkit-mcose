package net.minecraft.server;

import com.google.gson.JsonObject;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.registry.DamageTypeRegistryBootstrap;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;

/** Atomic startup snapshot of all data-driven death-message rules. */
public final class DeathMessageRules {
    private static volatile Map<ResourceLocation, DeathMessageRule> rules;

    private DeathMessageRules() {}

    /** Validates and publishes the complete rule generation before gameplay. */
    public static synchronized void initialize() {
        if (rules != null) return;
        Map<ResourceLocation, DeathMessageRule> prepared = load(null);
        rules = prepared;
        System.out.println("[DeathMessageRules] Loaded "
                + prepared.size() + " death-message rules");
    }

    static DeathMessageRule get(ResourceLocation type) {
        Map<ResourceLocation, DeathMessageRule> snapshot = snapshot();
        DeathMessageRule rule = snapshot.get(type);
        return rule == null ? snapshot.get(DamageTypes.GENERIC) : rule;
    }

    static DeathMessageRule get(String type) {
        return get(DamageTypes.canonicalizeLegacy(type == null
                ? DamageTypes.GENERIC : new ResourceLocation(type)));
    }

    public static Set<ResourceLocation> keys() {
        return snapshot().keySet();
    }

    public static int size() {
        return snapshot().size();
    }

    /** Writes the gameplay-consumed projection used by registry compatibility. */
    public static void writeCanonicalData(DataOutputStream out) throws Exception {
        writeCanonicalData(out, snapshot());
    }

    private static Map<ResourceLocation, DeathMessageRule> snapshot() {
        initialize();
        return rules;
    }

    static Map<ResourceLocation, DeathMessageRule> loadForTests(
            RegistryDataLoader.ResourceProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException(
                    "Resource provider cannot be null");
        }
        return load(provider);
    }

    private static Map<ResourceLocation, DeathMessageRule> load(
            RegistryDataLoader.ResourceProvider provider) {
        DamageTypeRegistryBootstrap.initialize();
        RegistryDataLoader.Decoder<DeathMessageRule> decoder =
                new RegistryDataLoader.Decoder<DeathMessageRule>() {
                    public DeathMessageRule decode(
                            ResourceLocation key, JsonObject json) {
                        return DeathMessageRule.decode(json);
                    }
                };
        Map<ResourceLocation, DeathMessageRule> decoded = provider == null
                ? RegistryDataLoader.loadAll("death_message", decoder)
                : RegistryDataLoader.loadAll(
                        "death_message", provider, decoder);
        LinkedHashMap<ResourceLocation, DeathMessageRule> byType =
                new LinkedHashMap<ResourceLocation, DeathMessageRule>();
        for (Map.Entry<ResourceLocation, DeathMessageRule> entry
                : decoded.entrySet()) {
            ResourceLocation type = entry.getKey();
            if (type.getPath().indexOf('/') >= 0) {
                throw new IllegalStateException(
                        "Death-message keys must be top-level damage types: "
                                + entry.getKey());
            }
            DamageType definition = DamageTypeRegistryBootstrap.get(type);
            if (definition == null) {
                throw new IllegalStateException(
                        "Death-message rule has no damage type: " + type);
            }
            try {
                entry.getValue().validateFor(definition);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException(
                        "Invalid death-message rule " + type + ": "
                                + invalid.getMessage(), invalid);
            }
            if (byType.put(type, entry.getValue()) != null) {
                throw new IllegalStateException(
                        "Duplicate death-message damage type: " + type);
            }
        }
        for (ResourceLocation required : DamageTypes.builtInTypes()) {
            if (!byType.containsKey(required)) {
                throw new IllegalStateException(
                        "Missing required death-message rule " + required);
            }
        }
        return Collections.unmodifiableMap(byType);
    }

    static void writeCanonicalData(
            DataOutputStream out,
            Map<ResourceLocation, DeathMessageRule> snapshot) throws Exception {
        if (out == null || snapshot == null) {
            throw new IllegalArgumentException(
                    "Canonical death-message output and snapshot are required");
        }
        List<ResourceLocation> keys =
                new ArrayList<ResourceLocation>(snapshot.keySet());
        Collections.sort(keys, new Comparator<ResourceLocation>() {
            public int compare(
                    ResourceLocation left, ResourceLocation right) {
                int path = left.getPath().compareTo(right.getPath());
                return path != 0 ? path
                        : left.getNamespace().compareTo(right.getNamespace());
            }
        });

        out.writeInt(keys.size());
        for (int i = 0; i < keys.size(); ++i) {
            ResourceLocation key = keys.get(i);
            DeathMessageRule rule = snapshot.get(key);
            DamageType damageType = DamageTypeRegistryBootstrap.get(key);
            if (rule == null || damageType == null
                    || damageType.getDeathMessageType() == null) {
                throw new IllegalStateException(
                        "Incomplete canonical death-message rule " + key);
            }
            out.writeUTF(key.toString());
            out.writeUTF(damageType.getDeathMessageType().getSerializedName());
            writeTemplate(out, rule.ordinary);
            out.writeBoolean(rule.fallVariant != null);
            if (rule.fallVariant != null) {
                writeTemplate(out, rule.fallVariant);
            }
            out.writeInt(rule.provenanceRules.size());
            for (int ruleIndex = 0;
                    ruleIndex < rule.provenanceRules.size(); ++ruleIndex) {
                DeathMessageRule.ProvenanceRule provenance =
                        rule.provenanceRules.get(ruleIndex);
                out.writeUTF(provenance.action);
                out.writeInt(provenance.maxAgeTicks);
                out.writeLong(normalizedDoubleBits(
                        provenance.horizontalRadius));
                out.writeLong(normalizedDoubleBits(
                        provenance.verticalRadius));
                out.writeUTF(provenance.verticalRelation);
                writeTemplate(out, provenance.template);
            }
        }
    }

    private static void writeTemplate(
            DataOutputStream out,
            DeathMessageRule.Template template) throws Exception {
        if (template == null || template.defaultKey == null
                || template.itemPolicy == null) {
            throw new IllegalStateException(
                    "Incomplete canonical death-message template");
        }
        out.writeUTF(template.defaultKey);
        writeNullableUtf(out, template.actorKey);
        writeNullableUtf(out, template.itemKey);
        out.writeUTF(template.itemPolicy.name().toLowerCase(Locale.ROOT));
    }

    private static void writeNullableUtf(
            DataOutputStream out, String value) throws Exception {
        out.writeBoolean(value != null);
        if (value != null) out.writeUTF(value);
    }

    private static long normalizedDoubleBits(double value) {
        return Double.doubleToLongBits(value == 0.0D ? 0.0D : value);
    }
}
