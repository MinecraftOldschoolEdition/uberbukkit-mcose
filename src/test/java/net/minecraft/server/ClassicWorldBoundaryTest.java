package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ClassicWorldBoundaryTest {
    @Test
    public void ordinaryWorldsAndBoxesInsideClassicBoundsHaveNoVirtualCollision() {
        List collisions = new ArrayList();
        AxisAlignedBB inside = AxisAlignedBB.a(0.3D, 70.0D, 0.3D, 255.7D, 72.0D, 255.7D);

        ClassicWorldBoundary.addCollisionBoxes(false, inside, collisions);
        ClassicWorldBoundary.addCollisionBoxes(true, inside, collisions);

        assertTrue(collisions.isEmpty());
        assertFalse(ClassicWorldBoundary.intersectsBoundary(inside));
    }

    @Test
    public void wallsStopMovementAtAllFourClassicEdgesAtAnyHeight() {
        this.assertXWall(-0.1D, 0.6D, 0.3D, 0.9D, -1.0D, -0.3D, 0.0D);
        this.assertXWall(255.4D, 256.1D, 255.1D, 255.7D, 1.0D, 0.3D, 256.0D);
        this.assertZWall(-0.1D, 0.6D, 0.3D, 0.9D, -1.0D, -0.3D, 0.0D);
        this.assertZWall(255.4D, 256.1D, 255.1D, 255.7D, 1.0D, 0.3D, 256.0D);
    }

    @Test
    public void virtualBedrockExistsBelowTheLevelRatherThanInsideItsEdgeColumns() {
        List collisions = new ArrayList();
        ClassicWorldBoundary.addCollisionBoxes(
                true,
                AxisAlignedBB.a(10.0D, -0.2D, 10.0D, 10.6D, 0.6D, 10.6D),
                collisions
        );

        assertEquals(1, collisions.size());
        AxisAlignedBB floor = (AxisAlignedBB) collisions.get(0);
        AxisAlignedBB entity = AxisAlignedBB.a(10.0D, 0.3D, 10.0D, 10.6D, 2.1D, 10.6D);
        assertEquals(-0.3D, floor.b(entity, -1.0D), 0.0000001D);
        assertTrue(ClassicWorldBoundary.intersectsBoundary(
                AxisAlignedBB.a(10.0D, -0.1D, 10.0D, 10.6D, 0.5D, 10.6D)
        ));
    }

    private void assertXWall(double queryMinX,
                             double queryMaxX,
                             double entityMinX,
                             double entityMaxX,
                             double requestedOffset,
                             double expectedOffset,
                             double wallCoordinate) {
        List collisions = new ArrayList();
        ClassicWorldBoundary.addCollisionBoxes(
                true,
                AxisAlignedBB.a(queryMinX, 1000.0D, 10.0D, queryMaxX, 1002.0D, 10.6D),
                collisions
        );

        assertEquals(1, collisions.size());
        AxisAlignedBB wall = (AxisAlignedBB) collisions.get(0);
        AxisAlignedBB entity = AxisAlignedBB.a(entityMinX, 1000.0D, 10.0D, entityMaxX, 1002.0D, 10.6D);
        assertEquals(expectedOffset, wall.a(entity, requestedOffset), 0.0000001D);
        assertTrue(wall.a == wallCoordinate || wall.d == wallCoordinate);
    }

    private void assertZWall(double queryMinZ,
                             double queryMaxZ,
                             double entityMinZ,
                             double entityMaxZ,
                             double requestedOffset,
                             double expectedOffset,
                             double wallCoordinate) {
        List collisions = new ArrayList();
        ClassicWorldBoundary.addCollisionBoxes(
                true,
                AxisAlignedBB.a(10.0D, 1000.0D, queryMinZ, 10.6D, 1002.0D, queryMaxZ),
                collisions
        );

        assertEquals(1, collisions.size());
        AxisAlignedBB wall = (AxisAlignedBB) collisions.get(0);
        AxisAlignedBB entity = AxisAlignedBB.a(10.0D, 1000.0D, entityMinZ, 10.6D, 1002.0D, entityMaxZ);
        assertEquals(expectedOffset, wall.c(entity, requestedOffset), 0.0000001D);
        assertTrue(wall.c == wallCoordinate || wall.f == wallCoordinate);
    }
}
