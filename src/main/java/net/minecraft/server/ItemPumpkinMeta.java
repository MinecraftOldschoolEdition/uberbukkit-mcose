package net.minecraft.server;

/**
 * Combined inventory item used by the client: damage 0 places a carved
 * pumpkin, while damage 1 places a plain pumpkin.
 */
public class ItemPumpkinMeta extends Item {

    public ItemPumpkinMeta(int itemId) {
        super(itemId);
        this.a(true);
        this.a("pumpkin");
    }

    public boolean a(ItemStack stack, EntityHuman player, World world, int x, int y, int z, int side) {
        int targetBlockId = stack.getData() == 1 ? Block.PUMPKIN_PLAIN.id : Block.CARVED_PUMPKIN.id;
        return ItemBlock.placeBlock(targetBlockId, this, stack, player, world, x, y, z, side);
    }

    public int filterData(int damage) {
        return 0;
    }

}
