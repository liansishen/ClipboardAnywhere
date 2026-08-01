package com.hepdd.clipboardanywhere.client.gui;

import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;

import com.hepdd.clipboardanywhere.client.ClientKeyBindings;

public final class OverlayInteractionScreen extends GuiScreen {

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ClipboardOverlay.INSTANCE.render(width, height, mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        ClipboardOverlay.INSTANCE.mouseClicked(width, height, mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long elapsedTime) {
        ClipboardOverlay.INSTANCE.mouseDragged(width, height, mouseX, mouseY, button);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        ClipboardOverlay.INSTANCE.mouseReleased(width, height, button);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        ClipboardOverlay.INSTANCE.mouseScrolled(org.lwjgl.input.Mouse.getEventDWheel());
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (ClipboardOverlay.INSTANCE.keyTyped(character, keyCode)) return;
        if (ClipboardOverlay.INSTANCE.handleShortcut(keyCode)) return;
        if (keyCode != Keyboard.KEY_NONE && keyCode == ClientKeyBindings.TOGGLE_INTERACTION.getKeyCode()) {
            mc.displayGuiScreen(null);
            return;
        }
        if (keyCode == 1) mc.displayGuiScreen(null);
    }

    @Override
    public void onGuiClosed() {
        ClipboardOverlay.INSTANCE.resetTransientState();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
