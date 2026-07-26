package com.hepdd.clipboardanywhere.client;

import com.cleanroommc.modularui.api.event.KeyboardInputEvent;
import com.cleanroommc.modularui.api.event.MouseInputEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Input bridge loaded only when ModularUI2 is installed. */
public final class ModularUiInputCompat {

    public static final ModularUiInputCompat INSTANCE = new ModularUiInputCompat();

    private ModularUiInputCompat() {}

    @SubscribeEvent
    public void onMouseInput(MouseInputEvent.Pre event) {
        if (ClientEventHandler.handleGuiMouseInput(event.gui)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onKeyboardInput(KeyboardInputEvent.Pre event) {
        if (ClientEventHandler.handleGuiKeyboardInput(event.gui)) event.setCanceled(true);
    }
}
