package com.hepdd.clipboardanywhere.client;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.hepdd.clipboardanywhere.Config;
import com.hepdd.clipboardanywhere.client.ClipboardClientState.Notice;
import com.hepdd.clipboardanywhere.client.gui.ClipboardOverlay;
import com.hepdd.clipboardanywhere.client.gui.OverlayHostScreen;
import com.hepdd.clipboardanywhere.client.gui.OverlayInteractionScreen;
import com.hepdd.clipboardanywhere.model.ClipboardAction;
import com.hepdd.clipboardanywhere.network.NetworkHandler;
import com.hepdd.clipboardanywhere.network.message.C2SRequestState;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class ClientEventHandler {

    public static final ClientEventHandler INSTANCE = new ClientEventHandler();
    private Object lastWorld;
    private int requestDelay;
    private String noticeKey;
    private boolean noticeSuccess;
    private int noticeTicks;

    private ClientEventHandler() {}

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null || minecraft.thePlayer == null) {
            if (lastWorld != null) {
                ClipboardClientState.INSTANCE.clear();
                ClipboardOverlay.INSTANCE.resetTransientState();
            }
            lastWorld = null;
            requestDelay = 0;
            return;
        }
        if (lastWorld != minecraft.theWorld) {
            lastWorld = minecraft.theWorld;
            ClipboardClientState.INSTANCE.clear();
            requestDelay = 5;
        } else if (requestDelay > 0 && --requestDelay == 0) {
            NetworkHandler.sendToServer(new C2SRequestState());
        }

        updateHostScreen(minecraft);
        updateNotice();
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        ClipboardOverlay overlay = ClipboardOverlay.INSTANCE;
        if (!overlay.isAvailable() || overlay.isModalOpen() || overlay.isLayoutEditing()) return;
        if (hasTextInputFocus(Minecraft.getMinecraft().currentScreen)) return;
        if (ClientKeyBindings.TOGGLE_COLLAPSE.isPressed()) {
            overlay.toggleCollapsed();
            return;
        }
        if (Config.collapsed) return;
        if (ClientKeyBindings.PREVIOUS_PAGE.isPressed()) {
            overlay.performActiveAction(ClipboardAction.PREVIOUS_PAGE);
        }
        if (ClientKeyBindings.NEXT_PAGE.isPressed()) {
            overlay.performActiveAction(ClipboardAction.NEXT_PAGE);
        }
        if (ClientKeyBindings.TOGGLE_INTERACTION.isPressed()) {
            toggleManualInteraction();
        }
    }

    @SubscribeEvent
    public void onHudRender(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.currentScreen == null) {
            ScaledResolution resolution = new ScaledResolution(
                minecraft,
                minecraft.displayWidth,
                minecraft.displayHeight);
            ClipboardOverlay.INSTANCE.render(resolution.getScaledWidth(), resolution.getScaledHeight(), -1, -1);
            renderNotice(resolution.getScaledWidth(), resolution.getScaledHeight());
        }
    }

    @SubscribeEvent
    public void onGuiDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        renderNotice(event.gui.width, event.gui.height);
    }

    public void renderNotice(int screenWidth, int screenHeight) {
        if (noticeTicks <= 0 || noticeKey == null) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        String text = StatCollector.translateToLocal(noticeKey);
        int x = (screenWidth - minecraft.fontRenderer.getStringWidth(text)) / 2;
        int y = screenHeight - 58;
        int color = noticeSuccess ? 0xFF9BE59B : 0xFFFFC66D;
        minecraft.fontRenderer.drawStringWithShadow(text, x, y, color);
    }

    private void updateHostScreen(Minecraft minecraft) {
        GuiScreen current = minecraft.currentScreen;
        if (current instanceof OverlayHostScreen) {
            if (!ClipboardOverlay.INSTANCE.isAvailable()) {
                ClipboardOverlay.INSTANCE.cancelLayoutEdit();
                ((OverlayHostScreen) current).restoreDelegate();
            }
            return;
        }
        if (current instanceof OverlayInteractionScreen) {
            if (!ClipboardOverlay.INSTANCE.isAvailable() || Config.collapsed) {
                ClipboardOverlay.INSTANCE.cancelLayoutEdit();
                minecraft.displayGuiScreen(null);
            }
            return;
        }
        if (current == null || !ClipboardOverlay.INSTANCE.isAvailable()) return;

        OverlayHostScreen host = new OverlayHostScreen(current);
        ScaledResolution resolution = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);
        minecraft.currentScreen = host;
        host.setWorldAndResolution(minecraft, resolution.getScaledWidth(), resolution.getScaledHeight());
    }

    private void toggleManualInteraction() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.currentScreen instanceof OverlayInteractionScreen) {
            minecraft.displayGuiScreen(null);
        } else if (minecraft.currentScreen == null) {
            minecraft.displayGuiScreen(new OverlayInteractionScreen());
        }
    }

    private void updateNotice() {
        Notice next = ClipboardClientState.INSTANCE.pollNotice();
        if (next != null) {
            noticeKey = next.getTranslationKey();
            noticeSuccess = next.isSuccess();
            noticeTicks = 60;
        } else if (noticeTicks > 0) {
            noticeTicks--;
        }
    }

    private static boolean hasTextInputFocus(GuiScreen screen) {
        GuiScreen delegate = screen instanceof OverlayHostScreen ? ((OverlayHostScreen) screen).getDelegate() : screen;
        if (delegate == null || delegate instanceof OverlayInteractionScreen) return false;
        if (delegate instanceof GuiChat || delegate instanceof GuiScreenBook || delegate instanceof GuiEditSign)
            return true;

        for (Class<?> type = delegate.getClass(); type != null
            && GuiScreen.class.isAssignableFrom(type); type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!GuiTextField.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    GuiTextField textField = (GuiTextField) field.get(delegate);
                    if (textField != null && textField.isFocused()) return true;
                } catch (IllegalAccessException | SecurityException ignored) {
                    // Explicit vanilla text screens are handled above; inaccessible mod fields are skipped.
                }
            }
        }
        return false;
    }
}
