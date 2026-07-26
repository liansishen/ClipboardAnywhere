package com.hepdd.clipboardanywhere.client;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.gui.GuiScreenOptionsSounds;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.GuiSnooper;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.hepdd.clipboardanywhere.Config;
import com.hepdd.clipboardanywhere.client.ClipboardClientState.Notice;
import com.hepdd.clipboardanywhere.client.gui.ClipboardOverlay;
import com.hepdd.clipboardanywhere.client.gui.OverlayInteractionScreen;
import com.hepdd.clipboardanywhere.model.ClipboardAction;
import com.hepdd.clipboardanywhere.network.NetworkHandler;
import com.hepdd.clipboardanywhere.network.message.C2SRequestState;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import jds.bibliocraft.gui.GuiBiblioTextField;

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

        if (minecraft.currentScreen instanceof OverlayInteractionScreen && !ClipboardOverlay.INSTANCE.isAvailable()) {
            ClipboardOverlay.INSTANCE.cancelLayoutEdit();
            minecraft.displayGuiScreen(null);
        } else if (isOverlaySuppressed(minecraft.currentScreen)) {
            ClipboardOverlay.INSTANCE.resetTransientState();
        }
        updateNotice();
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        ClipboardOverlay overlay = ClipboardOverlay.INSTANCE;
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen != null) return;
        if (!overlay.isAvailable() || overlay.isModalOpen() || overlay.isLayoutEditing()) return;
        if (hasTextInputFocus(screen)) return;
        if (ClientKeyBindings.TOGGLE_COLLAPSE.isPressed()) {
            overlay.toggleCollapsed();
            return;
        }
        if (ClientKeyBindings.TOGGLE_INTERACTION.isPressed()) {
            toggleManualInteraction();
            return;
        }
        if (Config.collapsed) return;
        if (ClientKeyBindings.PREVIOUS_PAGE.isPressed()) {
            overlay.performActiveAction(ClipboardAction.PREVIOUS_PAGE);
        }
        if (ClientKeyBindings.NEXT_PAGE.isPressed()) {
            overlay.performActiveAction(ClipboardAction.NEXT_PAGE);
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
        if (shouldUseOverlay(event.gui)) {
            ClipboardOverlay.INSTANCE.render(event.gui.width, event.gui.height, event.mouseX, event.mouseY);
        }
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

    public static boolean hasTextInputFocus(GuiScreen screen) {
        if (screen == null || screen instanceof OverlayInteractionScreen) return false;
        if (screen instanceof GuiChat || screen instanceof GuiScreenBook || screen instanceof GuiEditSign) return true;

        for (Class<?> type = screen.getClass(); type != null
            && GuiScreen.class.isAssignableFrom(type); type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                Class<?> fieldType = field.getType();
                if (!GuiTextField.class.isAssignableFrom(fieldType)
                    && !GuiBiblioTextField.class.isAssignableFrom(fieldType)) continue;
                try {
                    field.setAccessible(true);
                    Object textField = field.get(screen);
                    if (textField instanceof GuiTextField && ((GuiTextField) textField).isFocused()) return true;
                    if (textField instanceof GuiBiblioTextField && ((GuiBiblioTextField) textField).isFocused())
                        return true;
                } catch (IllegalAccessException | SecurityException ignored) {
                    // Explicit vanilla text screens are handled above; inaccessible mod fields are skipped.
                }
            }
        }
        return false;
    }

    public static boolean isOverlaySuppressed(GuiScreen screen) {
        if (screen == null) return false;
        return screen instanceof GuiIngameMenu || screen instanceof GuiOptions
            || screen instanceof GuiControls
            || screen instanceof GuiVideoSettings
            || screen instanceof GuiLanguage
            || screen instanceof GuiScreenOptionsSounds
            || screen instanceof GuiScreenResourcePacks
            || screen instanceof GuiSnooper
            || isSuppressedScreenClass(
                screen.getClass()
                    .getName());
    }

    public static boolean handleGuiMouseInput(GuiScreen screen) {
        if (!shouldUseOverlay(screen)) return false;
        ClipboardOverlay overlay = ClipboardOverlay.INSTANCE;
        Minecraft minecraft = Minecraft.getMinecraft();
        int mouseX = Mouse.getEventX() * screen.width / minecraft.displayWidth;
        int mouseY = screen.height - Mouse.getEventY() * screen.height / minecraft.displayHeight - 1;
        int wheel = Mouse.getEventDWheel();
        boolean handled = wheel != 0 && overlay.mouseScrolled(wheel);
        int button = Mouse.getEventButton();
        if (!handled && button >= 0) {
            handled = Mouse.getEventButtonState()
                ? overlay.mouseClicked(screen.width, screen.height, mouseX, mouseY, button)
                : overlay.mouseReleased(screen.width, screen.height, button);
        } else if (!handled && Mouse.isButtonDown(0)) {
            handled = overlay.mouseDragged(screen.width, screen.height, mouseX, mouseY, 0);
        }
        return handled || overlay.isLayoutEditing();
    }

    public static boolean handleGuiKeyboardInput(GuiScreen screen) {
        if (!shouldUseOverlay(screen) || !Keyboard.getEventKeyState()) return false;
        ClipboardOverlay overlay = ClipboardOverlay.INSTANCE;
        int keyCode = Keyboard.getEventKey();
        if (overlay.keyTyped(Keyboard.getEventCharacter(), keyCode)) return true;
        if (!hasTextInputFocus(screen) && overlay.handleShortcut(keyCode)) return true;
        return overlay.isLayoutEditing();
    }

    static boolean isSuppressedScreenClass(String className) {
        return className.startsWith("com.gtnewhorizons.angelica.client.gui.")
            || className.startsWith("com.gtnewhorizons.angelica.config.")
            || className.startsWith("jss.notfine.config.")
            || className.startsWith("jss.notfine.gui.")
            || className.startsWith("me.flashyreese.mods.reeses_sodium_options.client.gui.")
            || className.startsWith("me.jellysquid.mods.sodium.client.gui.")
            || className.startsWith("net.coderbot.iris.gui.screen.");
    }

    private static boolean shouldUseOverlay(GuiScreen screen) {
        return screen != null && !(screen instanceof OverlayInteractionScreen)
            && !isOverlaySuppressed(screen)
            && ClipboardOverlay.INSTANCE.isAvailable();
    }
}
