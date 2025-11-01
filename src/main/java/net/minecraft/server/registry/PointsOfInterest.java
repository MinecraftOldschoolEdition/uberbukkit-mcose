package net.minecraft.server.registry;

import net.minecraft.server.World;
import net.minecraft.server.util.ResourceLocation;

public final class PointsOfInterest {
    private PointsOfInterest() {}

    public static boolean isMatch(String path, int blockId) {
        try {
            PointOfInterestType t = Registries.POINT_OF_INTEREST_TYPE.get(new ResourceLocation("minecraft", path));
            if (t == null) return false;
            int[] ids = t.getBlockIds();
            for (int i = 0; i < ids.length; i++) if (ids[i] == blockId) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    public static int[] getIdsFor(String path) {
        try {
            PointOfInterestType t = Registries.POINT_OF_INTEREST_TYPE.get(new ResourceLocation("minecraft", path));
            return t != null ? t.getBlockIds() : new int[0];
        } catch (Throwable ignored) { return new int[0]; }
    }

    /** Find nearest matching block within a cubic radius, returns packed x,y,z or Integer.MIN_VALUE if none. */
    public static int findNearest(World world, int x, int y, int z, int radius, String path) {
        int best = Integer.MIN_VALUE;
        double bestDist2 = Double.MAX_VALUE;
        int[] ids = getIdsFor(path);
        if (ids.length == 0) return best;
        int minX = x - radius, maxX = x + radius;
        int minZ = z - radius, maxZ = z + radius;
        for (int xi = minX; xi <= maxX; xi++) {
            double dx2 = (xi + 0.5D - x) * (xi + 0.5D - x);
            for (int zi = minZ; zi <= maxZ; zi++) {
                double dz2 = (zi + 0.5D - z) * (zi + 0.5D - z);
                for (int yi = 127; yi >= 0; yi--) {
                    int id = world.getTypeId(xi, yi, zi);
                    for (int k = 0; k < ids.length; k++) {
                        if (ids[k] == id) {
                            double dy2 = (yi + 0.5D - y) * (yi + 0.5D - y);
                            double d2 = dx2 + dy2 + dz2;
                            if (d2 < bestDist2) {
                                bestDist2 = d2;
                                best = pack(xi, yi, zi);
                            }
                            break;
                        }
                    }
                }
            }
        }
        return best;
    }

    public static int pack(int x, int y, int z) { return (x & 0x3FFFFF) | ((y & 0xFF) << 22) | ((z & 0x3FFFFF) << 30); }
}


