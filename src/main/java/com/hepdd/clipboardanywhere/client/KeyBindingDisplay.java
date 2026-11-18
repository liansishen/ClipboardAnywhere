package com.hepdd.clipboardanywhere.client;

import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.common.Loader;

/** Uses ModernKeyBinding's modifier-aware API when the optional mod is installed. */
public final class KeyBindingDisplay {

    private static final boolean MKB_LOADED = isMkbLoaded();

    private KeyBindingDisplay() {}

    public static String getDisplayString(KeyBinding binding) {
        if (binding == null || binding.getKeyCode() == Keyboard.KEY_NONE) return "NONE";
        if (MKB_LOADED) {
            String display = MkbBridge.getDisplayString(binding);
            if (display != null) return display;
        }
        return GameSettings.getKeyDisplayString(binding.getKeyCode());
    }

    public static boolean isActiveAndMatches(KeyBinding binding, int keyCode) {
        if (binding == null || keyCode == Keyboard.KEY_NONE || binding.getKeyCode() == Keyboard.KEY_NONE) {
            return false;
        }
        if (MKB_LOADED) {
            Boolean matched = MkbBridge.isActiveAndMatches(binding, keyCode);
            if (matched != null) return matched.booleanValue();
        }
        return keyCode == binding.getKeyCode();
    }

    private static boolean isMkbLoaded() {
        try {
            return Loader.isModLoaded("mkb");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class MkbBridge {

        private static volatile boolean broken;

        private MkbBridge() {}

        static String getDisplayString(KeyBinding binding) {
            if (broken) return null;
            try {
                if (binding instanceof committee.nova.mkb.api.IKeyBinding) {
                    return ((committee.nova.mkb.api.IKeyBinding) binding).getDisplayName();
                }
            } catch (LinkageError error) {
                broken = true;
            }
            return null;
        }

        static Boolean isActiveAndMatches(KeyBinding binding, int keyCode) {
            if (broken) return null;
            try {
                if (binding instanceof committee.nova.mkb.api.IKeyBinding) {
                    return Boolean.valueOf(((committee.nova.mkb.api.IKeyBinding) binding).isActiveAndMatches(keyCode));
                }
            } catch (LinkageError error) {
                broken = true;
            }
            return null;
        }
    }
}
