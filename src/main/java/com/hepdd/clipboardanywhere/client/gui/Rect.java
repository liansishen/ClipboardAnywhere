package com.hepdd.clipboardanywhere.client.gui;

public final class Rect {

    public final int x;
    public final int y;
    public final int width;
    public final int height;

    public Rect(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean contains(int pointX, int pointY) {
        return pointX >= x && pointY >= y && pointX < x + width && pointY < y + height;
    }
}
