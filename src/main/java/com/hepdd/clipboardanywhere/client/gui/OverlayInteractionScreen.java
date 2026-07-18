package com.hepdd.clipboardanywhere.client.gui;

import net.minecraft.client.gui.GuiScreen;

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
        ClipboardOverlay.INSTANCE.mouseReleased(button);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        ClipboardOverlay.INSTANCE.mouseScrolled(org.lwjgl.input.Mouse.getEventDWheel());
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (ClipboardOverlay.INSTANCE.keyTyped(character, keyCode)) return;
        if (keyCode == 1) mc.displayGuiScreen(null);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
