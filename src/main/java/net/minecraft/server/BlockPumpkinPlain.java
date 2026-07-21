package net.minecraft.server;

/** Plain, uncarved pumpkin block used by the MCOSE client protocol. */
public class BlockPumpkinPlain extends Block {

    protected BlockPumpkinPlain(int id) {
        super(id, 102, Material.PUMPKIN);
    }

    public int a(int side, int metadata) {
        return side == 0 || side == 1 ? this.textureId : this.textureId + 16;
    }
}
