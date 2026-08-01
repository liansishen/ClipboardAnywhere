package com.hepdd.clipboardanywhere.client.gui;

public final class OverlayGeometry {

    public static final int LOGICAL_WIDTH = 127;
    public static final int HEADER_HEIGHT = 18;
    public static final int ROW_HEIGHT = 14;
    public static final int FOOTER_HEIGHT = 18;
    public static final int LOGICAL_HEIGHT = HEADER_HEIGHT + ROW_HEIGHT * 9 + FOOTER_HEIGHT;
    public static final int COLLAPSED_SIZE = 8;
    private static final int EDGE_SNAP_DISTANCE = 16;

    private int anchorRight;
    private int top;
    private double scale;

    public OverlayGeometry(int anchorRight, int top, double scale, int screenWidth, int screenHeight) {
        this(anchorRight, top, scale, screenWidth, screenHeight, false);
    }

    public OverlayGeometry(int anchorRight, int top, double scale, int screenWidth, int screenHeight,
        boolean collapsed) {
        this.anchorRight = anchorRight < 0 ? screenWidth - 8 : anchorRight;
        this.top = top;
        this.scale = clamp(scale, 0.5D, 2.0D);
        if (collapsed) clampCollapsedToScreen(screenWidth, screenHeight);
        else clampToScreen(screenWidth, screenHeight);
    }

    public static OverlayGeometry fromPosition(String horizontalMode, String verticalMode, int horizontalOffset,
        int verticalOffset, double horizontalPosition, double verticalPosition, double scale, int screenWidth,
        int screenHeight, boolean collapsed) {
        OverlayGeometry geometry = new OverlayGeometry(screenWidth, 0, scale, screenWidth, screenHeight, collapsed);
        int width = collapsed ? geometry.getCollapsedRenderedSize() : geometry.getRenderedWidth();
        int height = collapsed ? geometry.getCollapsedRenderedSize() : geometry.getRenderedHeight();
        int horizontalRange = Math.max(0, screenWidth - width);
        int verticalRange = Math.max(0, screenHeight - height);
        int left;
        if ("left".equals(horizontalMode)) left = Math.max(0, horizontalOffset);
        else if ("right".equals(horizontalMode)) left = screenWidth - Math.max(0, horizontalOffset) - width;
        else left = (int) Math.round(clamp(horizontalPosition, 0.0D, 1.0D) * horizontalRange);
        int top;
        if ("top".equals(verticalMode)) top = Math.max(0, verticalOffset);
        else if ("bottom".equals(verticalMode)) top = screenHeight - Math.max(0, verticalOffset) - height;
        else top = (int) Math.round(clamp(verticalPosition, 0.0D, 1.0D) * verticalRange);
        int right = left + width;
        if (collapsed) geometry.setCollapsedPosition(right, top, screenWidth, screenHeight);
        else geometry.setPosition(right, top, screenWidth, screenHeight);
        return geometry;
    }

    public int getAnchorRight() {
        return anchorRight;
    }

    public int getTop() {
        return top;
    }

    public double getScale() {
        return scale;
    }

    public int getLogicalHeight() {
        return LOGICAL_HEIGHT;
    }

    public int getLeft() {
        return anchorRight - getRenderedWidth();
    }

    public int getRenderedWidth() {
        return (int) Math.ceil(LOGICAL_WIDTH * scale);
    }

    public int getRenderedHeight() {
        return (int) Math.ceil(getLogicalHeight() * scale);
    }

    public int getCollapsedLeft() {
        return anchorRight - getCollapsedRenderedSize();
    }

    public int getCollapsedRenderedSize() {
        return COLLAPSED_SIZE;
    }

    public boolean containsCollapsed(int screenX, int screenY) {
        int size = getCollapsedRenderedSize();
        return screenX >= getCollapsedLeft() && screenX < anchorRight && screenY >= top && screenY < top + size;
    }

    public int toLogicalX(int screenX) {
        return (int) Math.floor((screenX - getLeft()) / scale);
    }

    public int toLogicalY(int screenY) {
        return (int) Math.floor((screenY - top) / scale);
    }

    public static int checkboxRowAt(int logicalX, int logicalY, int taskStart, int taskCount) {
        if (logicalX < 4 || logicalX >= 16 || logicalY < taskStart || logicalY >= taskStart + ROW_HEIGHT * taskCount)
            return -1;
        return (logicalY - taskStart) / ROW_HEIGHT;
    }

    public static int multiplyAlpha(int color, int opacityAlpha) {
        int alpha = (color >>> 24) * clamp(opacityAlpha, 0, 255) / 255;
        return alpha << 24 | color & 0x00FFFFFF;
    }

    public static int layoutOpacitySliderAt(int logicalY, int taskCount) {
        int backgroundTop = HEADER_HEIGHT + ROW_HEIGHT * (taskCount - 1);
        int footerTop = LOGICAL_HEIGHT - FOOTER_HEIGHT;
        if (logicalY >= backgroundTop && logicalY < footerTop) return 0;
        if (logicalY >= footerTop && logicalY < LOGICAL_HEIGHT) return 1;
        return -1;
    }

    public void setPosition(int anchorRight, int top, int screenWidth, int screenHeight) {
        this.anchorRight = anchorRight;
        this.top = top;
        clampToScreen(screenWidth, screenHeight);
    }

    public void setCollapsedPosition(int anchorRight, int top, int screenWidth, int screenHeight) {
        this.anchorRight = anchorRight;
        this.top = top;
        clampCollapsedToScreen(screenWidth, screenHeight);
    }

    public void setScale(double scale, int screenWidth, int screenHeight) {
        this.scale = clamp(scale, 0.5D, 2.0D);
        clampToScreen(screenWidth, screenHeight);
    }

    public void resizeFromTopLeft(int left, int top, double scale, int screenWidth, int screenHeight) {
        this.scale = clamp(scale, 0.5D, 2.0D);
        this.anchorRight = left + getRenderedWidth();
        this.top = top;
        clampToScreen(screenWidth, screenHeight);
    }

    public void clampToScreen(int screenWidth, int screenHeight) {
        if (screenWidth > 0 && screenHeight > 0) {
            double fittingScale = Math
                .min(screenWidth / (double) LOGICAL_WIDTH, screenHeight / (double) getLogicalHeight());
            scale = clamp(Math.min(scale, fittingScale), 0.5D, 2.0D);
        }
        int renderedWidth = Math.min(screenWidth, getRenderedWidth());
        int renderedHeight = Math.min(screenHeight, getRenderedHeight());
        anchorRight = clamp(anchorRight, renderedWidth, screenWidth);
        top = clamp(top, 0, Math.max(0, screenHeight - renderedHeight));
    }

    public void clampCollapsedToScreen(int screenWidth, int screenHeight) {
        int renderedSize = Math.min(Math.min(screenWidth, screenHeight), getCollapsedRenderedSize());
        anchorRight = clamp(anchorRight, renderedSize, screenWidth);
        top = clamp(top, 0, Math.max(0, screenHeight - renderedSize));
    }

    public SavedPosition toSavedPosition(int screenWidth, int screenHeight, boolean collapsed) {
        int width = collapsed ? getCollapsedRenderedSize() : getRenderedWidth();
        int height = collapsed ? getCollapsedRenderedSize() : getRenderedHeight();
        int left = collapsed ? getCollapsedLeft() : getLeft();
        int leftOffset = Math.max(0, left);
        int rightOffset = Math.max(0, screenWidth - left - width);
        int topOffset = Math.max(0, top);
        int bottomOffset = Math.max(0, screenHeight - top - height);
        int horizontalRange = Math.max(0, screenWidth - width);
        int verticalRange = Math.max(0, screenHeight - height);
        String horizontalMode;
        int horizontalOffset;
        boolean nearLeft = leftOffset <= EDGE_SNAP_DISTANCE;
        boolean nearRight = rightOffset <= EDGE_SNAP_DISTANCE;
        if (nearLeft && !nearRight) {
            horizontalMode = "left";
            horizontalOffset = leftOffset;
        } else if (nearRight && !nearLeft) {
            horizontalMode = "right";
            horizontalOffset = rightOffset;
        } else {
            horizontalMode = "relative";
            horizontalOffset = 0;
        }
        String verticalMode;
        int verticalOffset;
        boolean nearTop = topOffset <= EDGE_SNAP_DISTANCE;
        boolean nearBottom = bottomOffset <= EDGE_SNAP_DISTANCE;
        if (nearTop && !nearBottom) {
            verticalMode = "top";
            verticalOffset = topOffset;
        } else if (nearBottom && !nearTop) {
            verticalMode = "bottom";
            verticalOffset = bottomOffset;
        } else {
            verticalMode = "relative";
            verticalOffset = 0;
        }
        double horizontalPosition = horizontalRange == 0 ? 0.0D : clamp(left / (double) horizontalRange, 0.0D, 1.0D);
        double verticalPosition = verticalRange == 0 ? 0.0D : clamp(top / (double) verticalRange, 0.0D, 1.0D);
        return new SavedPosition(
            horizontalMode,
            verticalMode,
            horizontalOffset,
            verticalOffset,
            horizontalPosition,
            verticalPosition);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class SavedPosition {

        private final String horizontalMode;
        private final String verticalMode;
        private final int horizontalOffset;
        private final int verticalOffset;
        private final double horizontalPosition;
        private final double verticalPosition;

        private SavedPosition(String horizontalMode, String verticalMode, int horizontalOffset, int verticalOffset,
            double horizontalPosition, double verticalPosition) {
            this.horizontalMode = horizontalMode;
            this.verticalMode = verticalMode;
            this.horizontalOffset = horizontalOffset;
            this.verticalOffset = verticalOffset;
            this.horizontalPosition = horizontalPosition;
            this.verticalPosition = verticalPosition;
        }

        public String getHorizontalMode() {
            return horizontalMode;
        }

        public String getVerticalMode() {
            return verticalMode;
        }

        public int getHorizontalOffset() {
            return horizontalOffset;
        }

        public int getVerticalOffset() {
            return verticalOffset;
        }

        public double getHorizontalPosition() {
            return horizontalPosition;
        }

        public double getVerticalPosition() {
            return verticalPosition;
        }
    }
}
