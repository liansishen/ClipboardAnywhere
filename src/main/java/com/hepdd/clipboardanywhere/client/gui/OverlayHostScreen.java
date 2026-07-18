package com.hepdd.clipboardanywhere.client.gui;

import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.hepdd.clipboardanywhere.client.ClientEventHandler;

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
    public void initGui() {
        boolean exposed = exposeDelegate();
        try {
            delegate.setWorldAndResolution(mc, width, height);
        } finally {
            restoreHost(exposed);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        boolean exposed = exposeDelegate();
        try {
            delegate.drawScreen(mouseX, mouseY, partialTicks);
        } finally {
            restoreHost(exposed);
        }
        if (mc.currentScreen == this) ClipboardOverlay.INSTANCE.render(width, height, mouseX, mouseY, true);
    }

    @Override
    public void updateScreen() {
        boolean exposed = exposeDelegate();
        try {
            delegate.updateScreen();
        } finally {
            restoreHost(exposed);
        }
    }

    @Override
    public void handleMouseInput() {
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        boolean handled = false;
        if (Mouse.getEventDWheel() != 0) {
            handled = ClipboardOverlay.INSTANCE.mouseScrolled(Mouse.getEventDWheel());
        }
        int eventButton = Mouse.getEventButton();
        if (!handled && eventButton >= 0) {
            handled = Mouse.getEventButtonState()
                ? ClipboardOverlay.INSTANCE.mouseClicked(width, height, mouseX, mouseY, eventButton)
                : ClipboardOverlay.INSTANCE.mouseReleased(width, height, eventButton);
        } else if (!handled && Mouse.isButtonDown(0)) {
            handled = ClipboardOverlay.INSTANCE.mouseDragged(width, height, mouseX, mouseY, 0);
        }
        if (!handled) {
            boolean exposed = exposeDelegate();
            try {
                delegate.handleMouseInput();
            } finally {
                restoreHost(exposed);
            }
        }
    }

    @Override
    public void handleKeyboardInput() {
        if (Keyboard.getEventKeyState()) {
            int keyCode = Keyboard.getEventKey();
            if (ClipboardOverlay.INSTANCE.keyTyped(Keyboard.getEventCharacter(), keyCode)) return;
            if (!ClientEventHandler.hasTextInputFocus(delegate) && ClipboardOverlay.INSTANCE.handleShortcut(keyCode))
                return;
        }
        boolean exposed = exposeDelegate();
        try {
            delegate.handleKeyboardInput();
        } finally {
            restoreHost(exposed);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return delegate.doesGuiPauseGame();
    }

    @Override
    public void onGuiClosed() {
        if (!restored) delegate.onGuiClosed();
    }

    private boolean exposeDelegate() {
        if (mc.currentScreen != this) return false;
        mc.currentScreen = delegate;
        return true;
    }

    private void restoreHost(boolean exposed) {
        if (exposed && mc.currentScreen == delegate) mc.currentScreen = this;
    }
}
