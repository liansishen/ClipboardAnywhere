package com.hepdd.clipboardanywhere.client.gui;

public final class OverlayGeometry {

    public static final int LOGICAL_WIDTH = 190;
    public static final int HEADER_HEIGHT = 18;
    public static final int TITLE_HEIGHT = 16;
    public static final int ROW_HEIGHT = 14;
    public static final int FOOTER_HEIGHT = 18;
    public static final int LOGICAL_HEIGHT = HEADER_HEIGHT + TITLE_HEIGHT + ROW_HEIGHT * 9 + FOOTER_HEIGHT;
    public static final int COLLAPSED_SIZE = 10;

    private int anchorRight;
    private int top;
    private double scale;

    public OverlayGeometry(int anchorRight, int top, double scale, int screenWidth, int screenHeight) {
        this.anchorRight = anchorRight < 0 ? screenWidth - 8 : anchorRight;
        this.top = top;
        this.scale = clamp(scale, 0.5D, 2.0D);
        clampToScreen(screenWidth, screenHeight);
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

    public int getLeft() {
        return anchorRight - getRenderedWidth();
    }

    public int getRenderedWidth() {
        return (int) Math.ceil(LOGICAL_WIDTH * scale);
    }

    public int getRenderedHeight() {
        return (int) Math.ceil(LOGICAL_HEIGHT * scale);
    }

    public int getCollapsedLeft() {
        return anchorRight - COLLAPSED_SIZE;
    }

    public int toLogicalX(int screenX) {
        return (int) Math.floor((screenX - getLeft()) / scale);
    }

    public int toLogicalY(int screenY) {
        return (int) Math.floor((screenY - top) / scale);
    }

    public void setPosition(int anchorRight, int top, int screenWidth, int screenHeight) {
        this.anchorRight = anchorRight;
        this.top = top;
        clampToScreen(screenWidth, screenHeight);
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
                .min(screenWidth / (double) LOGICAL_WIDTH, screenHeight / (double) LOGICAL_HEIGHT);
            scale = clamp(Math.min(scale, fittingScale), 0.5D, 2.0D);
        }
        int renderedWidth = Math.min(screenWidth, getRenderedWidth());
        int renderedHeight = Math.min(screenHeight, getRenderedHeight());
        anchorRight = clamp(anchorRight, renderedWidth, screenWidth);
        top = clamp(top, 0, Math.max(0, screenHeight - renderedHeight));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
