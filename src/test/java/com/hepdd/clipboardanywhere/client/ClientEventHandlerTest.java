package com.hepdd.clipboardanywhere.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClientEventHandlerTest {

    @Test
    public void suppressesAngelicaVideoAndConfigScreens() {
        assertTrue(
            ClientEventHandler.isSuppressedScreenClass(
                "me.flashyreese.mods.reeses_sodium_options.client.gui.ReeseSodiumVideoOptionsScreen"));
        assertTrue(ClientEventHandler.isSuppressedScreenClass("me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI"));
        assertTrue(ClientEventHandler.isSuppressedScreenClass("com.gtnewhorizons.angelica.config.AngelicaGuiConfig"));
        assertTrue(ClientEventHandler.isSuppressedScreenClass("jss.notfine.gui.GuiCustomMenu"));
        assertTrue(ClientEventHandler.isSuppressedScreenClass("net.coderbot.iris.gui.screen.ShaderPackScreen"));
    }

    @Test
    public void keepsMachineAndInventoryScreensEligible() {
        assertFalse(
            ClientEventHandler
                .isSuppressedScreenClass("gregtech.api.gui.modularui.GT_UIInfos$MachineUIFactory$MachineGUI"));
        assertFalse(
            ClientEventHandler
                .isSuppressedScreenClass("com.gtnewhorizons.modularui.common.internal.wrapper.ModularGui"));
        assertFalse(ClientEventHandler.isSuppressedScreenClass("net.minecraft.client.gui.inventory.GuiInventory"));
    }
}
