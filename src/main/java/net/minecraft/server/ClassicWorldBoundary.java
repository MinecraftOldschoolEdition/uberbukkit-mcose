package net.minecraft.server;

import java.util.List;

/**
 * Collision-only walls around the 256x256 playable area, plus the virtual
 * bedrock floor below Y=0, matching c0.30's out-of-bounds collision rules.
 * These surfaces contain no generated blocks and do not alter terrain or light.
 */
public final class ClassicWorldBoundary {
    public static final double MIN_COORDINATE = 0.0D;
    public static final double MAX_COORDINATE = 256.0D;
    public static final double MIN_HEIGHT = 0.0D;
    private static final double COLLISION_LIMIT = 32000000.0D;

    private ClassicWorldBoundary() {
    }

    public static boolean intersectsBoundary(AxisAlignedBB box) {
        return box != null
                && (box.a < MIN_COORDINATE
                    || box.d > MAX_COORDINATE
                    || box.b < MIN_HEIGHT
                    || box.c < MIN_COORDINATE
                    || box.f > MAX_COORDINATE);
    }

    public static void addCollisionBoxes(boolean classicWorld, AxisAlignedBB query, List collisions) {
        if (!classicWorld || query == null || collisions == null) {
            return;
        }

        if (query.a < MIN_COORDINATE) {
            collisions.add(AxisAlignedBB.b(
                    -COLLISION_LIMIT, -COLLISION_LIMIT, -COLLISION_LIMIT,
                    MIN_COORDINATE, COLLISION_LIMIT, COLLISION_LIMIT
            ));
        }
        if (query.d > MAX_COORDINATE) {
            collisions.add(AxisAlignedBB.b(
                    MAX_COORDINATE, -COLLISION_LIMIT, -COLLISION_LIMIT,
                    COLLISION_LIMIT, COLLISION_LIMIT, COLLISION_LIMIT
            ));
        }
        if (query.c < MIN_COORDINATE) {
            collisions.add(AxisAlignedBB.b(
                    -COLLISION_LIMIT, -COLLISION_LIMIT, -COLLISION_LIMIT,
                    COLLISION_LIMIT, COLLISION_LIMIT, MIN_COORDINATE
            ));
        }
        if (query.f > MAX_COORDINATE) {
            collisions.add(AxisAlignedBB.b(
                    -COLLISION_LIMIT, -COLLISION_LIMIT, MAX_COORDINATE,
                    COLLISION_LIMIT, COLLISION_LIMIT, COLLISION_LIMIT
            ));
        }
        if (query.b < MIN_HEIGHT) {
            collisions.add(AxisAlignedBB.b(
                    -COLLISION_LIMIT, -COLLISION_LIMIT, -COLLISION_LIMIT,
                    COLLISION_LIMIT, MIN_HEIGHT, COLLISION_LIMIT
            ));
        }
    }
}
