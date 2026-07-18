package com.hepdd.clipboardanywhere.client.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OverlayGeometryTest {

    @Test
    public void usesCompactExpandedWidth() {
        OverlayGeometry geometry = new OverlayGeometry(200, 10, 1.0D, 320, 300);

        assertEquals(127, OverlayGeometry.LOGICAL_WIDTH);
        assertEquals(127, geometry.getRenderedWidth());
    }

    @Test
    public void clampsPositionAndScaleInsideScreen() {
        OverlayGeometry geometry = new OverlayGeometry(900, -40, 2.0D, 320, 240);

        assertTrue(geometry.getScale() < 2.0D);
        assertEquals(320, geometry.getAnchorRight());
        assertEquals(0, geometry.getTop());
        assertTrue(geometry.getLeft() >= 0);
        assertTrue(geometry.getTop() + geometry.getRenderedHeight() <= 240);
    }

    @Test
    public void resizesProportionallyFromTopLeft() {
        OverlayGeometry geometry = new OverlayGeometry(250, 20, 1.0D, 400, 300);
        int left = geometry.getLeft();

        geometry.resizeFromTopLeft(left, 20, 1.25D, 400, 300);

        assertEquals(1.25D, geometry.getScale(), 0.0001D);
        assertEquals(left, geometry.getLeft());
        assertEquals(20, geometry.getTop());
    }

    @Test
    public void collapsedIconFollowsOverlayScale() {
        OverlayGeometry geometry = new OverlayGeometry(300, 12, 1.75D, 400, 400);

        assertEquals(18, geometry.getCollapsedRenderedSize());
        assertEquals(geometry.getCollapsedRenderedSize(), geometry.getAnchorRight() - geometry.getCollapsedLeft());
    }

    @Test
    public void collapsedIconCanMoveAcrossTheWholeScreen() {
        OverlayGeometry geometry = new OverlayGeometry(1, 500, 2.0D, 320, 240, true);

        assertEquals(20, geometry.getAnchorRight());
        assertEquals(220, geometry.getTop());
        assertTrue(geometry.containsCollapsed(0, 220));
        assertTrue(geometry.containsCollapsed(19, 239));
        assertTrue(!geometry.containsCollapsed(20, 239));
    }

    @Test
    public void resolvesPositionFromEachScreenEdge() {
        OverlayGeometry topRight = OverlayGeometry.fromEdges(true, false, 8, 12, 1.0D, 400, 300, false);
        OverlayGeometry bottomLeft = OverlayGeometry.fromEdges(false, true, 15, 9, 1.0D, 400, 300, false);

        assertEquals(400 - 8, topRight.getAnchorRight());
        assertEquals(12, topRight.getTop());
        assertEquals(15, bottomLeft.getLeft());
        assertEquals(300 - 9, bottomLeft.getTop() + bottomLeft.getRenderedHeight());
    }

    @Test
    public void convertsDraggedPositionToNearestEdges() {
        OverlayGeometry geometry = new OverlayGeometry(390, 250, 1.0D, 400, 450);

        OverlayGeometry.EdgePosition position = geometry.toEdgePosition(400, 450, false);

        assertTrue(position.isFromRight());
        assertTrue(position.isFromBottom());
        assertEquals(10, position.getHorizontalOffset());
        assertEquals(22, position.getVerticalOffset());
    }

    @Test
    public void expandedGeometryAlwaysReservesHeaderSpace() {
        OverlayGeometry geometry = OverlayGeometry.fromEdges(true, true, 8, 10, 1.0D, 400, 400, false);

        assertEquals(OverlayGeometry.LOGICAL_HEIGHT, geometry.getLogicalHeight());
        assertEquals(400 - 10, geometry.getTop() + geometry.getRenderedHeight());
    }

    @Test
    public void relativeOffsetsSurviveResolutionChanges() {
        OverlayGeometry first = OverlayGeometry.fromEdges(true, false, 7, 21, 1.0D, 400, 300, false);
        OverlayGeometry resized = OverlayGeometry.fromEdges(true, false, 7, 21, 1.0D, 700, 500, false);

        assertEquals(first.getAnchorRight() + 300, resized.getAnchorRight());
        assertEquals(first.getTop(), resized.getTop());
    }

    @Test
    public void taskStateOnlyUsesCheckboxHitbox() {
        int taskStart = OverlayGeometry.HEADER_HEIGHT + OverlayGeometry.TITLE_HEIGHT;

        assertEquals(0, OverlayGeometry.checkboxRowAt(6, taskStart + 3, taskStart, 9));
        assertEquals(8, OverlayGeometry.checkboxRowAt(14, taskStart + OverlayGeometry.ROW_HEIGHT * 8, taskStart, 9));
        assertEquals(-1, OverlayGeometry.checkboxRowAt(20, taskStart + 3, taskStart, 9));
        assertEquals(-1, OverlayGeometry.checkboxRowAt(6, taskStart - 1, taskStart, 9));
    }

    @Test
    public void opacityMultipliesForegroundAlpha() {
        assertEquals(0x7FABCDEF, OverlayGeometry.multiplyAlpha(0xFFABCDEF, 127));
        assertEquals(0x3FABCDEF, OverlayGeometry.multiplyAlpha(0x80ABCDEF, 127));
        assertEquals(0x00ABCDEF, OverlayGeometry.multiplyAlpha(0xFFABCDEF, 0));
    }
}
