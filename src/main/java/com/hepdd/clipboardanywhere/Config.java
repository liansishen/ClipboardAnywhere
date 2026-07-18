package com.hepdd.clipboardanywhere;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static final double DEFAULT_OPACITY = 0.5D;
    public static final double MIN_OPACITY = 0.05D;
    public static final double MAX_OPACITY = 1.0D;
    private static Configuration configuration;
    public static boolean anchorFromRight = true;
    public static boolean anchorFromBottom;
    public static int horizontalOffset = 8;
    public static int verticalOffset = 36;
    public static double scale = 1.0D;
    public static double opacity = DEFAULT_OPACITY;
    public static boolean collapsed;
    private static boolean legacyPositionPending;
    private static int legacyAnchorRight = -1;
    private static int legacyAnchorTop = 36;

    public static void synchronizeConfiguration(File configFile) {
        configuration = new Configuration(configFile);
        configuration.load();
        boolean hasRelativePosition = configuration.getCategory("overlay")
            .containsKey("horizontalAnchor");
        boolean hasLegacyPosition = configuration.getCategory("overlay")
            .containsKey("anchorRight")
            || configuration.getCategory("overlay")
                .containsKey("anchorTop");
        if (hasRelativePosition || !hasLegacyPosition) {
            anchorFromRight = "right".equalsIgnoreCase(
                configuration.get("overlay", "horizontalAnchor", "right")
                    .getString());
            anchorFromBottom = "bottom".equalsIgnoreCase(
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
        opacity = clamp(
            configuration.get("overlay", "opacity", DEFAULT_OPACITY)
                .getDouble(DEFAULT_OPACITY),
            MIN_OPACITY,
            MAX_OPACITY);
        collapsed = configuration.get("overlay", "collapsed", false)
            .getBoolean(false);

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static void saveLayout(boolean fromRight, boolean fromBottom, int newHorizontalOffset, int newVerticalOffset,
        double newScale, double newOpacity) {
        anchorFromRight = fromRight;
        anchorFromBottom = fromBottom;
        horizontalOffset = Math.max(0, newHorizontalOffset);
        verticalOffset = Math.max(0, newVerticalOffset);
        scale = clamp(newScale, 0.5D, 2.0D);
        opacity = clamp(newOpacity, MIN_OPACITY, MAX_OPACITY);
        legacyPositionPending = false;
        configuration.getCategory("overlay")
            .remove("anchorRight");
        configuration.getCategory("overlay")
            .remove("anchorTop");
        configuration.get("overlay", "horizontalAnchor", "right")
            .set(anchorFromRight ? "right" : "left");
        configuration.get("overlay", "verticalAnchor", "top")
            .set(anchorFromBottom ? "bottom" : "top");
        configuration.get("overlay", "horizontalOffset", 8)
            .set(horizontalOffset);
        configuration.get("overlay", "verticalOffset", 36)
            .set(verticalOffset);
        configuration.get("overlay", "scale", 1.0D)
            .set(scale);
        configuration.get("overlay", "opacity", DEFAULT_OPACITY)
            .set(opacity);
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

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
