package com.hepdd.clipboardanywhere.client;

import net.minecraft.client.gui.GuiScreen;

import com.cleanroommc.modularui.api.IMuiScreen;
import com.cleanroommc.modularui.api.event.KeyboardInputEvent;
import com.cleanroommc.modularui.api.event.MouseInputEvent;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.widgets.textfield.BaseTextFieldWidget;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Input bridge loaded only when ModularUI2 is installed. */
public final class ModularUiInputCompat {

    public static final ModularUiInputCompat INSTANCE = new ModularUiInputCompat();

    private ModularUiInputCompat() {}

    public static boolean hasTextInputFocus(GuiScreen screen) {
        if (!(screen instanceof IMuiScreen)) return false;
        ModularScreen modularScreen = ((IMuiScreen) screen).getScreen();
        if (modularScreen == null) return false;
        ModularGuiContext context = modularScreen.getContext();
        return context != null && context.isFocused()
            && context.getFocusedWidget()
                .getElement() instanceof BaseTextFieldWidget;
    }

    @SubscribeEvent
    public void onMouseInput(MouseInputEvent.Pre event) {
        if (ClientEventHandler.handleGuiMouseInput(event.gui)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onKeyboardInput(KeyboardInputEvent.Pre event) {
        if (ClientEventHandler.handleGuiKeyboardInput(event.gui)) event.setCanceled(true);
    }
}
