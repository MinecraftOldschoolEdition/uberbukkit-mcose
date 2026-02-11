package example.sample;

import net.minecraft.server.Block;
import net.minecraft.server.EntityPig;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.event.EventListener;
import net.minecraft.server.event.events.BlockBreakEvent;
import net.minecraft.server.event.events.UseItemEvent;
import net.minecraft.server.mod.ModContext;
import net.minecraft.server.mod.ModInitializer;

/**
 * Minimal example mod for the new mod loader APIs.
 */
public final class SampleModInitializer implements ModInitializer {
    public void initialize(ModContext context) {
        // Register one item, one block, one entity type, and one recipe.
        context.registerItem("sample:demo_item", Item.STICK, Item.STICK.id);
        context.registerBlock("sample:demo_block", Block.DIRT, Block.DIRT.id);
        context.registerEntityType("sample:demo_entity", EntityPig.class);
        context.registerShapelessRecipe("sample:demo_recipe", new ItemStack(Item.APPLE), Item.STICK);

        // Register gameplay event listeners.
        context.getEventBus().subscribe(BlockBreakEvent.class, new EventListener<BlockBreakEvent>() {
            public void handle(BlockBreakEvent event) {
                if (event == null || event.getPlayer() == null) {
                    return;
                }
                System.out.println("[SampleMod] BlockBreakEvent by " + event.getPlayer().name + " at " + event.getX() + "," + event.getY() + "," + event.getZ());
            }
        });

        context.getEventBus().subscribe(UseItemEvent.class, new EventListener<UseItemEvent>() {
            public void handle(UseItemEvent event) {
                if (event == null || event.getPlayer() == null) {
                    return;
                }
                int itemId = event.getStack() == null ? -1 : event.getStack().id;
                System.out.println("[SampleMod] UseItemEvent by " + event.getPlayer().name + " with itemId=" + itemId);
            }
        });
    }
}
