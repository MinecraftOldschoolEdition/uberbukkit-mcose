package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.google.gson.JsonParser;
import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

public class SmeltingRecipeCookingTimeTest {
    private static final ResourceLocation KEY =
            new ResourceLocation("minecraft", "smelting/test_iron_ore");

    @BeforeClass
    public static void initializeLegacyRegistries() {
        assertNotNull(Block.STONE);
        assertNotNull(Item.IRON_INGOT);
    }

    @Test
    public void omittedCookingTimeRetainsExactBetaDefault() {
        SmeltingRecipe recipe = decode("");
        assertEquals(200, recipe.getCookingTime());
    }

    @Test
    public void explicitCookingTimeIsOwnedByDecodedRecipe() {
        assertEquals(73, decode(",\"cookingtime\":73").getCookingTime());
        assertEquals(32767,
                decode(",\"cookingtime\":32767").getCookingTime());
    }

    @Test
    public void malformedCookingTimeFailsBeforePublication() {
        assertRejected("0");
        assertRejected("-1");
        assertRejected("1.5");
        assertRejected("\"200\"");
        assertRejected("32768");
    }

    private static SmeltingRecipe decode(String extra) {
        return RecipeCodec.decodeSmelting(
                KEY,
                JsonParser.parseString("{"
                        + "\"type\":\"minecraft:smelting\","
                        + "\"ingredient\":{\"id\":\"minecraft:iron_ore\","
                        + "\"legacy_metadata\":-1},"
                        + "\"result\":{\"id\":\"minecraft:iron_ingot\","
                        + "\"count\":1,\"legacy_metadata\":0}"
                        + extra + "}").getAsJsonObject());
    }

    private static void assertRejected(String value) {
        try {
            decode(",\"cookingtime\":" + value);
            fail("Expected cookingtime " + value + " to be rejected");
        } catch (IllegalArgumentException expected) {
            // Strict preparation is the publication rollback boundary.
        }
    }
}
