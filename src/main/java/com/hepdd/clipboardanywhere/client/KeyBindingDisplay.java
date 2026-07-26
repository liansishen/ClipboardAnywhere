package com.hepdd.clipboardanywhere.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;

/** Displays optional modifier-aware key bindings without requiring the providing mod at runtime. */
public final class KeyBindingDisplay {

    private static final Method GET_DISPLAY_NAME = findMethod("getDisplayName");
    private static final Method GET_KEY_MODIFIER_NAME = findMethod("getKeyModifierName");

    private KeyBindingDisplay() {}

    public static String getDisplayString(KeyBinding binding) {
        if (binding == null) return "";
        String display = invokeDisplayMethod(GET_DISPLAY_NAME, binding);
        if (display == null) display = invokeDisplayMethod(GET_KEY_MODIFIER_NAME, binding);
        if (display != null) return display;
        return GameSettings.getKeyDisplayString(binding.getKeyCode());
    }

    private static Method findMethod(String name) {
        try {
            return KeyBinding.class.getMethod(name);
        } catch (NoSuchMethodException | SecurityException ignored) {
            return null;
        }
    }

    private static String invokeDisplayMethod(Method method, KeyBinding binding) {
        if (method == null) return null;
        try {
            Object value = method.invoke(binding);
            if (value instanceof String && !((String) value).isEmpty()) return (String) value;
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            // Fall back to the vanilla key name when the optional API is unavailable or fails.
        }
        return null;
    }
}
