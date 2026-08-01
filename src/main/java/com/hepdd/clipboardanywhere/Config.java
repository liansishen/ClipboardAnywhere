package com.hepdd.clipboardanywhere;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static final double DEFAULT_OPACITY = 0.5D;
    public static final double MIN_OPACITY = 0.10D;
    public static final double MAX_OPACITY = 1.0D;
    private static Configuration configuration;
    public static String horizontalPositionMode = "right";
    public static String verticalPositionMode = "top";
    public static int horizontalOffset = 8;
    public static int verticalOffset = 36;
    public static double horizontalPosition = 1.0D;
    public static double verticalPosition;
    public static double scale = 1.0D;
    public static double backgroundOpacity = DEFAULT_OPACITY;
    public static double textOpacity = DEFAULT_OPACITY;
    public static boolean collapsed;
    private static boolean legacyPositionPending;
    private static int legacyAnchorRight = -1;
    private static int legacyAnchorTop = 36;

    public static void synchronizeConfiguration(File configFile) {
        configuration = new Configuration(configFile);
        configuration.load();
        boolean hasSavedPosition = configuration.getCategory("overlay")
            .containsKey("horizontalAnchor");
        boolean hasLegacyPosition = configuration.getCategory("overlay")
            .containsKey("anchorRight")
            || configuration.getCategory("overlay")
                .containsKey("anchorTop");
        if (hasSavedPosition || !hasLegacyPosition) {
            horizontalPositionMode = normalizeHorizontalMode(
                configuration.get("overlay", "horizontalAnchor", "right")
                    .getString());
            verticalPositionMode = normalizeVerticalMode(
                configuration.get("overlay", "verticalAnchor", "top")
                    .getString());
            horizontalOffset = Math.max(
                0,
                configuration.get("overlay", "horizontalOffset", 8)
                    .getInt(8));
            verticalOffset = Math.max(
                0,
                configuration.get("overlay", "verticalOffset", 36)
                    .getInt(36));
            horizontalPosition = clamp(
                configuration.get("overlay", "horizontalPosition", 1.0D)
                    .getDouble(1.0D),
                0.0D,
                1.0D);
            verticalPosition = clamp(
                configuration.get("overlay", "verticalPosition", 0.0D)
                    .getDouble(0.0D),
                0.0D,
                1.0D);
            legacyPositionPending = false;
        } else {
            legacyAnchorRight = configuration.get("overlay", "anchorRight", -1)
                .getInt(-1);
            legacyAnchorTop = configuration.get("overlay", "anchorTop", 36)
                .getInt(36);
            legacyPositionPending = true;
        }
        scale = clamp(
            configuration.get("overlay", "scale", 1.0D)
                .getDouble(1.0D),
            0.5D,
            2.0D);
        double legacyOpacity = configuration.getCategory("overlay")
            .containsKey("opacity")
                ? configuration.get("overlay", "opacity", DEFAULT_OPACITY)
                    .getDouble(DEFAULT_OPACITY)
                : DEFAULT_OPACITY;
        backgroundOpacity = clamp(
            configuration.get("overlay", "backgroundOpacity", legacyOpacity)
                .getDouble(legacyOpacity),
            MIN_OPACITY,
            MAX_OPACITY);
        textOpacity = clamp(
            configuration.get("overlay", "textOpacity", legacyOpacity)
                .getDouble(legacyOpacity),
            MIN_OPACITY,
            MAX_OPACITY);
        configuration.getCategory("overlay")
            .remove("opacity");
        configuration.get("overlay", "backgroundOpacity", DEFAULT_OPACITY)
            .set(backgroundOpacity);
        configuration.get("overlay", "textOpacity", DEFAULT_OPACITY)
            .set(textOpacity);
        collapsed = configuration.get("overlay", "collapsed", false)
            .getBoolean(false);

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static void saveLayout(String newHorizontalMode, String newVerticalMode, int newHorizontalOffset,
        int newVerticalOffset, double newHorizontalPosition, double newVerticalPosition, double newScale,
        double newBackgroundOpacity, double newTextOpacity) {
        horizontalPositionMode = normalizeHorizontalMode(newHorizontalMode);
        verticalPositionMode = normalizeVerticalMode(newVerticalMode);
        horizontalOffset = Math.max(0, newHorizontalOffset);
        verticalOffset = Math.max(0, newVerticalOffset);
        horizontalPosition = clamp(newHorizontalPosition, 0.0D, 1.0D);
        verticalPosition = clamp(newVerticalPosition, 0.0D, 1.0D);
        scale = clamp(newScale, 0.5D, 2.0D);
        backgroundOpacity = clamp(newBackgroundOpacity, MIN_OPACITY, MAX_OPACITY);
        textOpacity = clamp(newTextOpacity, MIN_OPACITY, MAX_OPACITY);
        legacyPositionPending = false;
        configuration.getCategory("overlay")
            .remove("anchorRight");
        configuration.getCategory("overlay")
            .remove("anchorTop");
        configuration.get("overlay", "horizontalAnchor", "right")
            .set(horizontalPositionMode);
        configuration.get("overlay", "verticalAnchor", "top")
            .set(verticalPositionMode);
        configuration.get("overlay", "horizontalOffset", 8)
            .set(horizontalOffset);
        configuration.get("overlay", "verticalOffset", 36)
            .set(verticalOffset);
        configuration.get("overlay", "horizontalPosition", 1.0D)
            .set(horizontalPosition);
        configuration.get("overlay", "verticalPosition", 0.0D)
            .set(verticalPosition);
        configuration.get("overlay", "scale", 1.0D)
            .set(scale);
        configuration.getCategory("overlay")
            .remove("opacity");
        configuration.get("overlay", "backgroundOpacity", DEFAULT_OPACITY)
            .set(backgroundOpacity);
        configuration.get("overlay", "textOpacity", DEFAULT_OPACITY)
            .set(textOpacity);
        configuration.save();
    }

    public static boolean hasLegacyPosition() {
        return legacyPositionPending;
    }

    public static int getLegacyAnchorRight() {
        return legacyAnchorRight;
    }

    public static int getLegacyAnchorTop() {
        return legacyAnchorTop;
    }

    public static void setCollapsed(boolean value) {
        collapsed = value;
        configuration.get("overlay", "collapsed", false)
            .set(value);
        configuration.save();
    }

    private static String normalizeHorizontalMode(String value) {
        if ("left".equalsIgnoreCase(value)) return "left";
        if ("relative".equalsIgnoreCase(value)) return "relative";
        return "right";
    }

    private static String normalizeVerticalMode(String value) {
        if ("bottom".equalsIgnoreCase(value)) return "bottom";
        if ("relative".equalsIgnoreCase(value)) return "relative";
        return "top";
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
