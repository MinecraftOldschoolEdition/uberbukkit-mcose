package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import net.minecraft.server.DamageEffects;
import net.minecraft.server.DamageScaling;
import net.minecraft.server.DamageType;
import net.minecraft.server.DamageTypeCodec;
import net.minecraft.server.DamageTypes;
import net.minecraft.server.DeathMessageType;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

public class DamageTypeDataTest {
    @BeforeClass
    public static void initializeRegistry() {
        DamageTypeRegistryBootstrap.initialize();
    }

    @Test
    public void builtInsCarryTheExact263MetadataProjection() {
        assertEquals(15, DamageTypeRegistryBootstrap.builtInKeys().size());
        assertEquals("player_attack", DamageTypes.PLAYER.getPath());
        assertEquals("mob_attack", DamageTypes.MOB.getPath());
        assertType(DamageTypes.GENERIC, "generic",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                0.0F, DamageEffects.HURT, DeathMessageType.DEFAULT);
        assertType(DamageTypes.PLAYER, "player",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                0.1F, DamageEffects.HURT, DeathMessageType.DEFAULT);
        assertType(DamageTypes.MOB, "mob",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                0.1F, DamageEffects.HURT, DeathMessageType.DEFAULT);
        assertType(DamageTypes.ARROW, "arrow",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                0.1F, DamageEffects.HURT, DeathMessageType.DEFAULT);
        assertType(DamageTypes.FIREBALL, "fireball",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                0.1F, DamageEffects.BURNING, DeathMessageType.DEFAULT);
        assertType(DamageTypes.EXPLOSION, "explosion",
                DamageScaling.ALWAYS,
                0.1F, DamageEffects.HURT, DeathMessageType.DEFAULT);
        assertType(DamageTypes.IN_FIRE, "inFire",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                0.1F, DamageEffects.BURNING, DeathMessageType.DEFAULT);
        assertType(DamageTypes.ON_FIRE, "onFire",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                0.0F, DamageEffects.BURNING, DeathMessageType.DEFAULT);
        assertType(DamageTypes.LAVA, "lava",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                0.1F, DamageEffects.BURNING, DeathMessageType.DEFAULT);
        assertType(DamageTypes.IN_WALL, "inWall",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                0.0F, DamageEffects.HURT, DeathMessageType.DEFAULT);
        assertType(DamageTypes.DROWN, "drown",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                0.0F, DamageEffects.DROWNING, DeathMessageType.DEFAULT);
        assertType(DamageTypes.FALL, "fall",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                0.0F, DamageEffects.HURT,
                DeathMessageType.FALL_VARIANTS);
        assertType(DamageTypes.OUT_OF_WORLD, "outOfWorld",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                0.0F, DamageEffects.HURT, DeathMessageType.DEFAULT);
        assertType(DamageTypes.CACTUS, "cactus",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                0.1F, DamageEffects.HURT, DeathMessageType.DEFAULT);
        assertType(DamageTypes.LIGHTNING_BOLT, "lightningBolt",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                0.1F, DamageEffects.HURT, DeathMessageType.DEFAULT);
    }

    @Test
    public void strictCodecDefaultsLike263AndRejectsUnknownFields() {
        JsonObject minimal = new JsonObject();
        minimal.addProperty("message_id", "test");
        minimal.addProperty("scaling", "never");
        minimal.addProperty("exhaustion", 0.25D);
        DamageType decoded = DamageTypeCodec.decode(
                new ResourceLocation("example", "test"), minimal);
        assertEquals(DamageEffects.HURT, decoded.getEffects());
        assertEquals(DeathMessageType.DEFAULT,
                decoded.getDeathMessageType());

        JsonObject unknown = copy(minimal);
        unknown.addProperty("typo", true);
        try {
            DamageTypeCodec.decode(
                    new ResourceLocation("example", "bad"), unknown);
            fail("unknown damage-type fields must fail the staged generation");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("unknown field"));
        }

        JsonObject scaling = copy(minimal);
        scaling.addProperty("scaling", "sometimes");
        try {
            DamageTypeCodec.decode(
                    new ResourceLocation("example", "bad_scaling"), scaling);
            fail("unknown scaling values must fail the staged generation");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("scaling"));
        }
    }

    @Test
    public void layeredTypesStageWithoutReplacingLiveDataOnFailure()
            throws Exception {
        Path root = Files.createTempDirectory("mcose-server-damage-types-");
        ResourceLocation acid = new ResourceLocation("example", "acid");
        try {
            write(root, acid, "{\"message_id\":\"acid\","
                    + "\"scaling\":\"never\",\"exhaustion\":0.25}");
            Map<ResourceLocation, DamageType> staged =
                    DamageTypeRegistryBootstrap.loadForTests(
                            RegistryDataLoader.createLayeredProvider(
                                    root.toFile()));
            assertNotNull(staged.get(acid));
            assertEquals(DamageScaling.NEVER,
                    staged.get(acid).getScaling());

            Map<ResourceLocation, DamageType> live =
                    DamageTypeRegistryBootstrap.rawDefinitions();
            DamageType liveFall =
                    DamageTypeRegistryBootstrap.get(DamageTypes.FALL);
            write(root, DamageTypes.FALL,
                    "{\"message_id\":\"fall\","
                            + "\"scaling\":\"invalid\","
                            + "\"exhaustion\":0.0}");
            try {
                DamageTypeRegistryBootstrap.loadForTests(
                        RegistryDataLoader.createLayeredProvider(
                                root.toFile()));
                fail("malformed staged damage type must fail atomically");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("minecraft:fall"));
            }
            assertSame(live, DamageTypeRegistryBootstrap.rawDefinitions());
            assertSame(liveFall,
                    DamageTypeRegistryBootstrap.get(DamageTypes.FALL));
        } finally {
            deleteTree(root);
        }
    }

    private static void assertType(
            ResourceLocation key,
            String messageId,
            DamageScaling scaling,
            float exhaustion,
            DamageEffects effects,
            DeathMessageType deathMessageType) {
        DamageType value = DamageTypeRegistryBootstrap.get(key);
        assertNotNull(key.toString(), value);
        assertSame(value, DamageTypeRegistryApi.get(key));
        assertSame(value, Registries.DAMAGE_TYPE.get(key));
        assertEquals(messageId, value.getMessageId());
        assertEquals(scaling, value.getScaling());
        assertEquals(exhaustion, value.getExhaustion(), 0.0F);
        assertEquals(effects, value.getEffects());
        assertEquals(deathMessageType, value.getDeathMessageType());
    }

    private static JsonObject copy(JsonObject source) {
        return new com.google.gson.JsonParser().parse(source.toString())
                .getAsJsonObject();
    }

    private static void write(
            Path root, ResourceLocation key, String json) throws Exception {
        Path file = root.resolve("data").resolve(key.getNamespace())
                .resolve("damage_type").resolve(key.getPath() + ".json");
        Files.createDirectories(file.getParent());
        Files.write(file, json.getBytes(StandardCharsets.UTF_8));
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
}
