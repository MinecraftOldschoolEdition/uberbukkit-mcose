package net.minecraft.server;

public class ItemRecord extends Item {

    public final String a;
    private final int shiftedIndex; // Store the shifted index for packet 1005

    protected ItemRecord(int i, String s) {
        super(i);
        this.shiftedIndex = i; // Store the shifted index (e.g., 2000, not 2256)
        this.a = s;
        this.maxStackSize = 1;
    }

    // Public getter for record name (needed for client-side playback)
    public String getRecordName() {
        return this.a;
    }

    public boolean a(ItemStack itemstack, EntityHuman entityhuman, World world, int i, int j, int k, int l) {
        if (world.getTypeId(i, j, k) == Block.JUKEBOX.id && world.getData(i, j, k) == 0) {
            if (world.isStatic) {
                return true;
            } else {
                ((BlockJukeBox) Block.JUKEBOX).f(world, i, j, k, this.id);
                // Send shifted index (not full ID) - client expects shifted index (e.g., 2000) not full ID (e.g., 2256)
                world.a((EntityHuman) null, 1005, i, j, k, this.shiftedIndex);
                --itemstack.count;
                return true;
            }
        } else {
            return false;
        }
    }
}
