package com.hepdd.clipboardanywhere.client;

import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import committee.nova.mkb.api.IKeyBinding;

/** Uses ModernKeyBinding's modifier-aware API when the optional mod is installed. */
public final class KeyBindingDisplay {

    private KeyBindingDisplay() {}

    public static String getDisplayString(KeyBinding binding) {
        if (binding == null || binding.getKeyCode() == Keyboard.KEY_NONE) return "NONE";
        if (binding instanceof IKeyBinding) return ((IKeyBinding) binding).getDisplayName();
        return GameSettings.getKeyDisplayString(binding.getKeyCode());
    }

    public static boolean isActiveAndMatches(KeyBinding binding, int keyCode) {
        if (binding == null || keyCode == Keyboard.KEY_NONE) return false;
        if (binding instanceof IKeyBinding) return ((IKeyBinding) binding).isActiveAndMatches(keyCode);
        return keyCode == binding.getKeyCode();
    }
}
