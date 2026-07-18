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
}
