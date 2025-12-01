package net.minecraft.server;

import java.util.List;

/**
 * Represents a map placed on a wall or floor, similar to a painting.
 */
public class EntityMapHanging extends Entity {

    private int tickCounter;
    /** Direction the map faces: 0=south, 1=west, 2=north, 3=east */
    public int direction;
    /** Block position the map is attached to */
    public int xPosition;
    public int yPosition;
    public int zPosition;
    /** The map item damage value (map ID) */
    public int mapId;

    public EntityMapHanging(World world) {
        super(world);
        this.tickCounter = 0;
        this.direction = 0;
        this.height = 0.0F;
        this.b(0.5F, 0.5F);
    }

    /**
     * Create a map hanging entity at the specified position with given direction and map ID.
     */
    public EntityMapHanging(World world, int x, int y, int z, int dir, int mapId) {
        this(world);
        this.xPosition = x;
        this.yPosition = y;
        this.zPosition = z;
        this.mapId = mapId;
        this.setDirection(dir);
    }

    protected void b() {
        // Entity init - nothing to do
    }

    /**
     * Set the direction and update position/bounding box accordingly.
     * Direction: 0=south, 1=west, 2=north, 3=east
     */
    public void setDirection(int dir) {
        this.direction = dir;
        this.lastYaw = this.yaw = (float)(dir * 90);

        // Size for 1x1 map (16 pixels = 1 block)
        float sizeX = 16.0F;
        float sizeY = 16.0F;

        float var2 = sizeX;
        float var3 = sizeY;
        float var4 = sizeX;

        // Wall-mounted: thin in one horizontal direction
        if (dir == 0 || dir == 2) {
            var4 = 0.5F;
        } else {
            var2 = 0.5F;
        }

        var2 /= 32.0F;
        var3 /= 32.0F;
        var4 /= 32.0F;

        float posXf = (float)this.xPosition + 0.5F;
        float posYf = (float)this.yPosition + 0.5F;
        float posZf = (float)this.zPosition + 0.5F;

        float offset = 9.0F / 16.0F;

        // Wall mounting offsets - position map in front of the block face
        if (dir == 0) {
            posZf -= offset;  // Map on north side of block, facing south
        } else if (dir == 1) {
            posXf -= offset;  // Map on west side of block, facing east
        } else if (dir == 2) {
            posZf += offset;  // Map on south side of block, facing north
        } else if (dir == 3) {
            posXf += offset;  // Map on east side of block, facing west
        }

        this.setPosition((double)posXf, (double)posYf, (double)posZf);
        float var9 = -(0.1F / 16.0F);
        this.boundingBox.c(
            (double)(posXf - var2 - var9),
            (double)(posYf - var3 - var9),
            (double)(posZf - var4 - var9),
            (double)(posXf + var2 + var9),
            (double)(posYf + var3 + var9),
            (double)(posZf + var4 + var9)
        );
    }

    public void m_() {
        // Check validity every 100 ticks
        if (this.tickCounter++ == 100 && !this.world.isStatic) {
            this.tickCounter = 0;
            if (!this.isValidPosition()) {
                this.die();
                // Drop the map item with the same map ID
                this.world.addEntity(new EntityItem(this.world, this.locX, this.locY, this.locZ, 
                    new ItemStack(Item.MAP, 1, this.mapId)));
            }
        }
    }

    /**
     * Check if this map can hang at its current position.
     */
    public boolean isValidPosition() {
        // Check for collisions with blocks
        if (this.world.getEntities(this, this.boundingBox).size() > 0) {
            return false;
        }

        // Check that the block we're attached to is solid
        Material mat = this.world.getMaterial(this.xPosition, this.yPosition, this.zPosition);
        if (!mat.isBuildable()) {
            return false;
        }

        // Check for other hanging entities in the same space
        List list = this.world.b((Entity) this, this.boundingBox);
        for (int i = 0; i < list.size(); i++) {
            Object ent = list.get(i);
            if (ent instanceof EntityPainting || ent instanceof EntityMapHanging) {
                return false;
            }
        }

        return true;
    }

    public boolean l_() {
        return true;
    }

    /**
     * Called when a player right-clicks on this map.
     * Returns false to let the client handle showing the lock confirmation GUI.
     * The actual locking is done via Packet131 when the player confirms.
     */
    public boolean a(EntityHuman entityhuman) {
        // Don't auto-lock - let the client show the confirmation GUI
        // The lock will be processed via Packet131 when the player confirms
        return false;
    }

    /**
     * When attacked, drop the map item with its ID preserved.
     */
    public boolean damageEntity(Entity entity, int damage) {
        if (!this.dead && !this.world.isStatic) {
            this.die();
            this.af();
            this.world.addEntity(new EntityItem(this.world, this.locX, this.locY, this.locZ, 
                new ItemStack(Item.MAP, 1, this.mapId)));
        }
        return true;
    }

    public void b(NBTTagCompound nbt) {
        nbt.a("Dir", (byte)this.direction);
        // Save as integer for extended map ID support
        nbt.a("MapId", this.mapId);
        nbt.a("TileX", this.xPosition);
        nbt.a("TileY", this.yPosition);
        nbt.a("TileZ", this.zPosition);
    }

    public void a(NBTTagCompound nbt) {
        this.direction = nbt.c("Dir");
        // Read MapId - support both old short format and new int format
        if (nbt.hasKey("MapId")) {
            this.mapId = nbt.e("MapId");
            // If the value is 0 but we have a short key, use short value for old saves
            if (this.mapId == 0 && nbt.d("MapId") != 0) {
                this.mapId = nbt.d("MapId") & 0xFFFF;
            }
        }
        this.xPosition = nbt.e("TileX");
        this.yPosition = nbt.e("TileY");
        this.zPosition = nbt.e("TileZ");
        this.setDirection(this.direction);
    }

    public void a(double d0, double d1, double d2) {
        if (!this.world.isStatic && d0 * d0 + d1 * d1 + d2 * d2 > 0.0D) {
            this.die();
            this.world.addEntity(new EntityItem(this.world, this.locX, this.locY, this.locZ, 
                new ItemStack(Item.MAP, 1, this.mapId)));
        }
    }

    public void b(double d0, double d1, double d2) {
        if (!this.world.isStatic && d0 * d0 + d1 * d1 + d2 * d2 > 0.0D) {
            this.die();
            this.world.addEntity(new EntityItem(this.world, this.locX, this.locY, this.locZ, 
                new ItemStack(Item.MAP, 1, this.mapId)));
        }
    }
}

