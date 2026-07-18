package com.hepdd.clipboardanywhere;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    private static Configuration configuration;
    public static int anchorRight = -1;
    public static int anchorTop = 8;
    public static double scale = 1.0D;
    public static double opacity = 0.85D;
    public static boolean collapsed;

    public static void synchronizeConfiguration(File configFile) {
        configuration = new Configuration(configFile);
        configuration.load();
        anchorRight = configuration.get("overlay", "anchorRight", -1)
            .getInt(-1);
        anchorTop = configuration.get("overlay", "anchorTop", 8)
            .getInt(8);
        scale = clamp(
            configuration.get("overlay", "scale", 1.0D)
                .getDouble(1.0D),
            0.5D,
            2.0D);
        opacity = clamp(
            configuration.get("overlay", "opacity", 0.85D)
                .getDouble(0.85D),
            0.25D,
            1.0D);
        collapsed = configuration.get("overlay", "collapsed", false)
            .getBoolean(false);

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static void saveLayout(int right, int top, double newScale, double newOpacity) {
        anchorRight = right;
        anchorTop = top;
        scale = clamp(newScale, 0.5D, 2.0D);
        opacity = clamp(newOpacity, 0.25D, 1.0D);
        configuration.get("overlay", "anchorRight", -1)
            .set(anchorRight);
        configuration.get("overlay", "anchorTop", 8)
            .set(anchorTop);
        configuration.get("overlay", "scale", 1.0D)
            .set(scale);
        configuration.get("overlay", "opacity", 0.85D)
            .set(opacity);
        configuration.save();
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
