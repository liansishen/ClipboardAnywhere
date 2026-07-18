package com.hepdd.clipboardanywhere.client;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;

public final class ClientKeyBindings {

    public static final String CATEGORY = "key.categories.clipboardanywhere";
    public static final KeyBinding PREVIOUS_PAGE = new KeyBinding(
        "key.clipboardanywhere.previous_page",
        Keyboard.KEY_NONE,
        CATEGORY);
    public static final KeyBinding NEXT_PAGE = new KeyBinding(
        "key.clipboardanywhere.next_page",
        Keyboard.KEY_NONE,
        CATEGORY);
    public static final KeyBinding TOGGLE_COLLAPSE = new KeyBinding(
        "key.clipboardanywhere.toggle_collapse",
        Keyboard.KEY_NONE,
        CATEGORY);
    public static final KeyBinding TOGGLE_INTERACTION = new KeyBinding(
        "key.clipboardanywhere.toggle_interaction",
        Keyboard.KEY_NONE,
        CATEGORY);

    private ClientKeyBindings() {}

    public static void register() {
        ClientRegistry.registerKeyBinding(PREVIOUS_PAGE);
        ClientRegistry.registerKeyBinding(NEXT_PAGE);
        ClientRegistry.registerKeyBinding(TOGGLE_COLLAPSE);
        ClientRegistry.registerKeyBinding(TOGGLE_INTERACTION);
    }
}
