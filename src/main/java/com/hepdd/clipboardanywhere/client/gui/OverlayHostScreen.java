package com.hepdd.clipboardanywhere.client.gui;

import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class OverlayHostScreen extends GuiScreen {

    private final GuiScreen delegate;
    private boolean restored;

    public OverlayHostScreen(GuiScreen delegate) {
        this.delegate = delegate;
    }

    public GuiScreen getDelegate() {
        return delegate;
    }

    public void restoreDelegate() {
        restored = true;
        mc.currentScreen = delegate;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        delegate.drawScreen(mouseX, mouseY, partialTicks);
        ClipboardOverlay.INSTANCE.render(width, height, mouseX, mouseY);
    }

    @Override
    public void updateScreen() {
        delegate.updateScreen();
    }

    @Override
    public void handleMouseInput() {
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (ClipboardOverlay.INSTANCE.isLayoutEditing()) {
            int eventButton = Mouse.getEventButton();
            if (eventButton >= 0) {
                if (Mouse.getEventButtonState()) {
                    ClipboardOverlay.INSTANCE.mouseClicked(width, height, mouseX, mouseY, eventButton);
                } else {
                    ClipboardOverlay.INSTANCE.mouseReleased(eventButton);
                }
            } else if (Mouse.isButtonDown(0)) {
                ClipboardOverlay.INSTANCE.mouseDragged(width, height, mouseX, mouseY, 0);
            }
            return;
        }
        boolean handled = false;
        if (Mouse.getEventDWheel() != 0) {
            handled = ClipboardOverlay.INSTANCE.mouseScrolled(Mouse.getEventDWheel());
        }
        if (!handled && Mouse.getEventButtonState()) {
            handled = ClipboardOverlay.INSTANCE.mouseClicked(width, height, mouseX, mouseY, Mouse.getEventButton());
        }
        if (!handled) delegate.handleMouseInput();
    }

    @Override
    public void handleKeyboardInput() {
        if (Keyboard.getEventKeyState()
            && ClipboardOverlay.INSTANCE.keyTyped(Keyboard.getEventCharacter(), Keyboard.getEventKey())) {
            return;
        }
        delegate.handleKeyboardInput();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return delegate.doesGuiPauseGame();
    }

    @Override
    public void onGuiClosed() {
        if (!restored) delegate.onGuiClosed();
    }
}
