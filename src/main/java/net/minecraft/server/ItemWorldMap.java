package net.minecraft.server;

// CraftBukkit start

import org.bukkit.Bukkit;
import org.bukkit.event.server.MapInitializeEvent;
// CraftBukkit end

public class ItemWorldMap extends ItemWorldMapBase {

    protected ItemWorldMap(int i) {
        super(i);
        this.c(1);
    }

    /**
     * Called when the player right-clicks a block with a map.
     * Places the map on the block face if valid.
     */
    public boolean a(ItemStack itemstack, EntityHuman entityhuman, World world, int x, int y, int z, int face) {
        if (face == 0 || face == 1) {
            return false;
        }

        int wallFace = face;
        if (entityhuman != null) {
            int expectedFace = expectedHorizontalFace(entityhuman, x, z);
            if (isOppositeHorizontalFace(wallFace, expectedFace)) {
                wallFace = expectedFace;
            }
        }

        int direction = wallDirectionFromFace(wallFace);
        if (direction < 0) {
            return false;
        }

        int mapId = itemstack.getData();
        EntityMapHanging mapHanging = new EntityMapHanging(world, x, y, z, direction, mapId);

        if (mapHanging.isValidPosition()) {
            if (!world.isStatic) {
                world.addEntity(mapHanging);
            }
            --itemstack.count;
            return true;
        }

        return false;
    }

    private static int wallDirectionFromFace(int face) {
        // Match ItemPainting mapping:
        // face 2 -> dir 0, face 4 -> dir 1, face 3 -> dir 2, face 5 -> dir 3
        if (face == 2) return 0;
        if (face == 4) return 1;
        if (face == 3) return 2;
        if (face == 5) return 3;
        return -1;
    }

    private static int expectedHorizontalFace(EntityHuman entityhuman, int x, int z) {
        double dx = entityhuman.locX - ((double) x + 0.5D);
        double dz = entityhuman.locZ - ((double) z + 0.5D);

        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0.0D ? 5 : 4;
        }

        return dz > 0.0D ? 3 : 2;
    }

    private static boolean isOppositeHorizontalFace(int left, int right) {
        return (left == 2 && right == 3) || (left == 3 && right == 2) || (left == 4 && right == 5) || (left == 5 && right == 4);
    }

    public WorldMap a(ItemStack itemstack, World world) {
        WorldMap worldmap = (WorldMap) world.a(WorldMap.class, "map_" + itemstack.getData());
        if (worldmap == null) {
            itemstack.b(world.b("map"));
            String s = "map_" + itemstack.getData();
            worldmap = new WorldMap(s);
            // Do NOT stamp center/scale/dimension here; defer until first time selected/used
            world.a(s, (WorldMapBase) worldmap);
            // CraftBukkit start
            MapInitializeEvent event = new MapInitializeEvent(worldmap.mapView);
            Bukkit.getServer().getPluginManager().callEvent(event);
            // CraftBukkit end
        }
        return worldmap;
    }

    public void a(World world, Entity entity, WorldMap worldmap) {
        if (((WorldServer) world).dimension == worldmap.map) { // CraftBukkit
            short short1 = 128;
            short short2 = 128;
            int i = 1 << worldmap.e;
            int j = worldmap.b;
            int k = worldmap.c;
            int l = MathHelper.floor(entity.locX - (double) j) / i + short1 / 2;
            int i1 = MathHelper.floor(entity.locZ - (double) k) / i + short2 / 2;
            int j1 = 128 / i;

            if (world.worldProvider.e) {
                j1 /= 2;
            }

            ++worldmap.g;

            for (int k1 = l - j1 + 1; k1 < l + j1; ++k1) {
                if ((k1 & 15) == (worldmap.g & 15)) {
                    int l1 = 255;
                    int i2 = 0;
                    double d0 = 0.0D;

                    for (int j2 = i1 - j1 - 1; j2 < i1 + j1; ++j2) {
                        if (k1 >= 0 && j2 >= -1 && k1 < short1 && j2 < short2) {
                            int k2 = k1 - l;
                            int l2 = j2 - i1;
                            boolean flag = k2 * k2 + l2 * l2 > (j1 - 2) * (j1 - 2);
                            int i3 = (j / i + k1 - short1 / 2) * i;
                            int j3 = (k / i + j2 - short2 / 2) * i;
                            byte b0 = 0;
                            byte b1 = 0;
                            byte b2 = 0;
                            int[] aint = new int[256];
                            Chunk chunk = world.getChunkAtWorldCoords(i3, j3);
                            if (chunk.isEmpty()) continue; // CraftBukkit
                            int k3 = i3 & 15;
                            int l3 = j3 & 15;
                            int i4 = 0;
                            double d1 = 0.0D;
                            int j4;
                            int k4;
                            int l4;
                            int i5;

                            if (world.worldProvider.e) {
                                l4 = i3 + j3 * 231871;
                                l4 = l4 * l4 * 31287121 + l4 * 11;
                                if ((l4 >> 20 & 1) == 0) {
                                    aint[Block.DIRT.id] += 10;
                                } else {
                                    aint[Block.STONE.id] += 10;
                                }

                                d1 = 100.0D;
                            } else {
                                for (l4 = 0; l4 < i; ++l4) {
                                    for (j4 = 0; j4 < i; ++j4) {
                                        k4 = chunk.b(l4 + k3, j4 + l3) + 1;
                                        int j5 = 0;

                                        if (k4 > 1) {
                                            boolean flag1 = false;

                                            do {
                                                flag1 = true;
                                                j5 = chunk.getTypeId(l4 + k3, k4 - 1, j4 + l3);
                                                if (j5 == 0) {
                                                    flag1 = false;
                                                } else if (k4 > 0 && j5 > 0 && Block.byId[j5].material.C == MaterialMapColor.b) {
                                                    flag1 = false;
                                                }

                                                if (!flag1) {
                                                    --k4;
                                                    if (k4 <= 0) break; // CraftBukkit
                                                    j5 = chunk.getTypeId(l4 + k3, k4 - 1, j4 + l3);
                                                }
                                            } while (!flag1);

                                            if (j5 != 0 && Block.byId[j5].material.isLiquid()) {
                                                i5 = k4 - 1;
                                                boolean flag2 = false;

                                                int k5;

                                                do {
                                                    k5 = chunk.getTypeId(l4 + k3, i5--, j4 + l3);
                                                    ++i4;
                                                } while (i5 > 0 && k5 != 0 && Block.byId[k5].material.isLiquid());
                                            }
                                        }

                                        d1 += (double) k4 / (double) (i * i);
                                        ++aint[j5];
                                    }
                                }
                            }

                            i4 /= i * i;
                            int l5 = b0 / (i * i);

                            l5 = b1 / (i * i);
                            l5 = b2 / (i * i);
                            l4 = 0;
                            j4 = 0;

                            for (k4 = 0; k4 < 256; ++k4) {
                                if (aint[k4] > l4) {
                                    j4 = k4;
                                    l4 = aint[k4];
                                }
                            }

                            double d2 = (d1 - d0) * 4.0D / (double) (i + 4) + ((double) (k1 + j2 & 1) - 0.5D) * 0.4D;
                            byte b3 = 1;

                            if (d2 > 0.6D) {
                                b3 = 2;
                            }

                            if (d2 < -0.6D) {
                                b3 = 0;
                            }

                            i5 = 0;
                            if (j4 > 0) {
                                MaterialMapColor materialmapcolor = Block.byId[j4].material.C;

                                if (materialmapcolor == MaterialMapColor.n) {
                                    d2 = (double) i4 * 0.1D + (double) (k1 + j2 & 1) * 0.2D;
                                    b3 = 1;
                                    if (d2 < 0.5D) {
                                        b3 = 2;
                                    }

                                    if (d2 > 0.9D) {
                                        b3 = 0;
                                    }
                                }

                                i5 = materialmapcolor.q;
                            }

                            d0 = d1;
                            if (j2 >= 0 && k2 * k2 + l2 * l2 < j1 * j1 && (!flag || (k1 + j2 & 1) != 0)) {
                                byte b4 = worldmap.f[k1 + j2 * short1];
                                byte b5 = (byte) (i5 * 4 + b3);

                                if (b4 != b5) {
                                    if (l1 > j2) {
                                        l1 = j2;
                                    }

                                    if (i2 < j2) {
                                        i2 = j2;
                                    }

                                    worldmap.f[k1 + j2 * short1] = b5;
                                }
                            }
                        }
                    }

                    if (l1 <= i2) {
                        worldmap.a(k1, l1, i2);
                    }
                }
            }
        }
    }

    public void a(ItemStack itemstack, World world, Entity entity, int i, boolean flag) {
        if (!world.isStatic) {
            if (!flag) {
                // Not selected: do nothing to avoid premature map data stamping
                return;
            }
            WorldMap worldmap = this.a(itemstack, world);
            // First selection: if not stamped yet, stamp center/scale/dimension now
            if (worldmap.e == 0 && worldmap.b == 0 && worldmap.c == 0 && worldmap.map == 0) {
                int playerX, playerZ;
                if (entity instanceof EntityHuman) {
                    EntityHuman entityhuman = (EntityHuman) entity;
                    playerX = MathHelper.floor(entityhuman.locX);
                    playerZ = MathHelper.floor(entityhuman.locZ);
                } else {
                    playerX = world.q().c();
                    playerZ = world.q().e();
                }
                
                // Use scale 3 (1:8) by default - each map covers 1024x1024 blocks
                byte scale = 3;
                
                // Snap to grid so maps tile perfectly when placed side by side
                int[] gridCenter = snapToMapGrid(playerX, playerZ, scale);
                worldmap.b = gridCenter[0];
                worldmap.c = gridCenter[1];
                worldmap.e = scale;
                worldmap.map = (byte) ((WorldServer) world).dimension;
                worldmap.a();
            }
            if (entity instanceof EntityHuman) {
                EntityHuman entityhuman = (EntityHuman) entity;
                worldmap.a(entityhuman, itemstack);
            }
            
            // Don't update terrain data if map is locked
            if (worldmap.locked) {
                return;
            }
            
            this.a(world, entity, worldmap);
        }
    }

    /**
     * Snaps coordinates to a map grid based on scale.
     * This ensures maps tile perfectly when placed side by side.
     * @param x Player X coordinate
     * @param z Player Z coordinate
     * @param scale Map scale (0-4)
     * @return int[] with [centerX, centerZ] for the grid cell
     */
    private static int[] snapToMapGrid(int x, int z, byte scale) {
        // Map size at this scale: 128 pixels * (2^scale) blocks per pixel
        int mapSize = 128 * (1 << scale);
        
        // Find which grid cell the player is in
        int gridX = (int) Math.floor((double) x / mapSize);
        int gridZ = (int) Math.floor((double) z / mapSize);
        
        // Center of that grid cell
        int centerX = gridX * mapSize + mapSize / 2;
        int centerZ = gridZ * mapSize + mapSize / 2;
        
        return new int[] { centerX, centerZ };
    }

    public void c(ItemStack itemstack, World world, EntityHuman entityhuman) {
        // Allocate a new id and register an empty WorldMap; do not stamp dimension yet
        itemstack.b(world.b("map"));
        String s = "map_" + itemstack.getData();
        WorldMap worldmap = new WorldMap(s);
        world.a(s, (WorldMapBase) worldmap);
    }

    public Packet b(ItemStack itemstack, World world, EntityHuman entityhuman) {
        byte[] abyte = this.a(itemstack, world).a(itemstack, world, entityhuman);

        // Use int mapId constructor for extended format support (up to ~2 billion maps)
        return abyte == null ? null : new Packet131((short) Item.MAP.id, itemstack.getData(), abyte);
    }
}
