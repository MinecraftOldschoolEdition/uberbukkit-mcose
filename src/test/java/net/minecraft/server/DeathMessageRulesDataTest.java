package net.minecraft.server;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;
import org.junit.Test;
import sun.misc.Unsafe;

public class DeathMessageRulesDataTest {
    private static final ResourceLocation DROWN =
            new ResourceLocation("minecraft", "drown");

    @Test
    public void builtInsSelectTheClient263TemplatesAndItemPolicies() {
        assertEquals(15, DeathMessageRules.size());
        assertTrue(DeathMessageRules.keys().containsAll(
                DamageTypes.builtInTypes()));

        DeathMessage playerItem = DeathMessageRules
                .get(DamageTypes.PLAYER).ordinary
                .create("Victim", "Attacker", "Diamond Sword", false);
        assertEquals("death.attack.player.item",
                playerItem.getTranslationKey());
        assertEquals(3, playerItem.getArguments().length);

        DeathMessage ordinaryMobItem = DeathMessageRules
                .get(DamageTypes.MOB).ordinary
                .create("Victim", "Zombie", "Iron Sword", false);
        assertEquals("death.attack.mob",
                ordinaryMobItem.getTranslationKey());

        DeathMessage namedMobItem = DeathMessageRules
                .get(DamageTypes.MOB).ordinary
                .create("Victim", "Zombie", "Needle", true);
        assertEquals("death.attack.mob.item",
                namedMobItem.getTranslationKey());

        DeathMessage arrowWithoutActor = DeathMessageRules
                .get(DamageTypes.ARROW).ordinary
                .create("Victim", null, null, false);
        assertEquals("death.attack.generic",
                arrowWithoutActor.getTranslationKey());

        DeathMessage fallAssist = DeathMessageRules
                .get(DamageTypes.FALL).fallVariant
                .create("Victim", "Spleefer", null, false);
        assertEquals("death.fell.assist", fallAssist.getTranslationKey());
    }

    @Test
    public void serverDeathFormattingConsumesTheAnyItemPolicy() throws Exception {
        StatisticList.a();
        TestHuman victim = testHuman("Victim");
        TestHuman attacker = testHuman("Attacker");
        attacker.inventory.items[0] = new ItemStack(Item.DIAMOND_SWORD);

        DeathDamageSource source = source(
                DeathDamageType.PLAYER, attacker, attacker, null);
        assertEquals(
                "\u00A7fVictim was slain by Attacker using Diamond Sword",
                DeathMessageHelper.format(victim, source, null));
    }

    @Test
    public void ruleDecoderRejectsUnknownFieldsAndMismatchedFallMetadata() {
        try {
            DeathMessageRule.decode(new JsonParser().parse(
                    "{\"default_key\":\"death.attack.generic\","
                            + "\"typo\":true}").getAsJsonObject());
            fail("unknown rule fields must fail startup validation");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("unknown field"));
        }

        DeathMessageRule missingFall = DeathMessageRule.decode(
                new JsonParser().parse(
                        "{\"default_key\":\"death.attack.fall\"}")
                        .getAsJsonObject());
        try {
            missingFall.validateFor(
                    net.minecraft.server.registry.DamageTypeRegistryBootstrap
                            .get(DamageTypes.FALL));
            fail("fall-variant metadata must require a fall rule");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("fall_variant"));
        }
    }

    @Test
    public void malformedCandidateDoesNotReplacePublishedSnapshot()
            throws Exception {
        DeathMessageRules.initialize();
        byte[] before = canonicalBytes();
        Path root = Files.createTempDirectory(
                "mcose-server-death-message-atomic-");
        try {
            writeRule(root, DamageTypes.FALL,
                    "{\"default_key\":\"death.attack.fall\"}");
            try {
                DeathMessageRules.loadForTests(
                        RegistryDataLoader.createLayeredProvider(
                                root.toFile()));
                fail("Malformed death-message generation must fail before publication");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("minecraft:fall"));
            }
            assertArrayEquals(before, canonicalBytes());
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void canonicalWriterSortsKeysNormalizesSignedZeroAndKeepsRuleOrder()
            throws Exception {
        Path positive = Files.createTempDirectory(
                "mcose-server-death-positive-");
        Path negative = Files.createTempDirectory(
                "mcose-server-death-negative-");
        Path swapped = Files.createTempDirectory(
                "mcose-server-death-swapped-");
        Path changed = Files.createTempDirectory(
                "mcose-server-death-changed-");
        try {
            writeRule(positive, DROWN, drownRule("0.0", false, 1200));
            writeRule(negative, DROWN, drownRule("-0.0", false, 1200));
            writeRule(swapped, DROWN, drownRule("0.0", true, 1200));
            writeRule(changed, DROWN, drownRule("0.0", false, 1201));

            Map<ResourceLocation, DeathMessageRule> positiveRules =
                    load(positive);
            Map<ResourceLocation, DeathMessageRule> negativeRules =
                    reverseInsertionOrder(load(negative));
            byte[] canonical = canonicalBytes(positiveRules);
            assertArrayEquals(canonical, canonicalBytes(negativeRules));
            assertFalse(Arrays.equals(
                    canonical, canonicalBytes(load(swapped))));
            assertFalse(Arrays.equals(
                    canonical, canonicalBytes(load(changed))));
        } finally {
            deleteTree(positive);
            deleteTree(negative);
            deleteTree(swapped);
            deleteTree(changed);
        }
    }

    private static Map<ResourceLocation, DeathMessageRule> load(Path root) {
        return DeathMessageRules.loadForTests(
                RegistryDataLoader.createLayeredProvider(root.toFile()));
    }

    private static Map<ResourceLocation, DeathMessageRule>
            reverseInsertionOrder(
                    Map<ResourceLocation, DeathMessageRule> source) {
        List<Map.Entry<ResourceLocation, DeathMessageRule>> entries =
                new ArrayList<Map.Entry<ResourceLocation, DeathMessageRule>>(
                        source.entrySet());
        Collections.reverse(entries);
        LinkedHashMap<ResourceLocation, DeathMessageRule> reversed =
                new LinkedHashMap<ResourceLocation, DeathMessageRule>();
        for (Map.Entry<ResourceLocation, DeathMessageRule> entry : entries) {
            reversed.put(entry.getKey(), entry.getValue());
        }
        return reversed;
    }

    private static byte[] canonicalBytes() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        DeathMessageRules.writeCanonicalData(out);
        out.flush();
        return bytes.toByteArray();
    }

    private static byte[] canonicalBytes(
            Map<ResourceLocation, DeathMessageRule> rules) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        DeathMessageRules.writeCanonicalData(out, rules);
        out.flush();
        return bytes.toByteArray();
    }

    private static String drownRule(
            String horizontalRadius,
            boolean swapOrder,
            int firstMaxAge) {
        String water = provenance(
                "water_place", firstMaxAge, horizontalRadius, "24.0");
        String current = provenance(
                "current_test", 600, horizontalRadius, "8.0");
        String entries = swapOrder
                ? current + "," + water : water + "," + current;
        return "{"
                + "\"default_key\":\"death.attack.drown\","
                + "\"actor_key\":\"death.attack.drown.player\","
                + "\"item_policy\":\"custom_named\","
                + "\"provenance\":[" + entries + "]}";
    }

    private static String provenance(
            String action,
            int maxAge,
            String horizontalRadius,
            String verticalRadius) {
        return "{"
                + "\"action\":\"" + action + "\","
                + "\"max_age_ticks\":" + maxAge + ","
                + "\"horizontal_radius\":" + horizontalRadius + ","
                + "\"vertical_radius\":" + verticalRadius + ","
                + "\"vertical_relation\":\"any\","
                + "\"default_key\":\"death.attack.drown\","
                + "\"actor_key\":\"death.attack.drown.pvp\","
                + "\"item_key\":\"death.attack.drown.pvp.item\","
                + "\"item_policy\":\"any\"}";
    }

    private static void writeRule(
            Path root, ResourceLocation key, String json) throws Exception {
        Path target = root.resolve("data").resolve(key.getNamespace())
                .resolve("death_message")
                .resolve(key.getPath() + ".json");
        Files.createDirectories(target.getParent());
        Files.write(target, json.getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteTree(Path root) throws Exception {
        if (root == null || !Files.exists(root)) return;
        java.util.stream.Stream<Path> paths = Files.walk(root);
        try {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            });
        } finally {
            paths.close();
        }
    }

    private static TestHuman testHuman(String name) throws Exception {
        TestHuman human = (TestHuman) unsafe().allocateInstance(TestHuman.class);
        human.name = name;
        human.inventory = new InventoryPlayer(human);
        return human;
    }

    private static DeathDamageSource source(
            DeathDamageType type,
            Entity direct,
            Entity causing,
            String causingName) throws Exception {
        Constructor<DeathDamageSource> constructor =
                DeathDamageSource.class.getDeclaredConstructor(
                        DeathDamageType.class,
                        Entity.class,
                        Entity.class,
                        String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(type, direct, causing, causingName);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final class TestHuman extends EntityHuman {
        private TestHuman() {
            super(null);
        }
    }
}
