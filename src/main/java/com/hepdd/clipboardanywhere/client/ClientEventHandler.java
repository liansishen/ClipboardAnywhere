package com.hepdd.clipboardanywhere.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
import net.minecraft.client.gui.inventory.GuiContainer;
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

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import jds.bibliocraft.gui.GuiBiblioTextField;

public final class ClientEventHandler {

    public static final ClientEventHandler INSTANCE = new ClientEventHandler();
    private static final int INITIAL_SYNC_FALLBACK_TICKS = 10;
    private static final Field[] EMPTY_FIELDS = new Field[0];
    private static final ConcurrentMap<Class<?>, Field[]> TEXT_FIELD_CACHE = new ConcurrentHashMap<>();
    private static final boolean MODULARUI2_LOADED = isModLoadedSafe("modularui2");
    private static final boolean MODULARUI1_LOADED = isModLoadedSafe("modularui");
    private static final boolean NEI_LOADED = isModLoadedSafe("NotEnoughItems");

    private Object lastWorld;
    private GuiScreen lastScreen;
    private int requestDelay;
    private String noticeKey;
    private boolean noticeSuccess;
    private int noticeTicks;

    private ClientEventHandler() {}

    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                lastWorld = null;
                lastScreen = null;
                requestDelay = 0;
                ClipboardClientState.INSTANCE.clear();
                ClipboardOverlay.INSTANCE.resetTransientState();
            });
    }

    @SubscribeEvent
    public void onClientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                lastWorld = null;
                lastScreen = null;
                requestDelay = 0;
                ClipboardClientState.INSTANCE.clear();
                ClipboardOverlay.INSTANCE.resetTransientState();
            });
    }

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
            lastScreen = null;
            requestDelay = 0;
            return;
        }
        if (lastWorld != minecraft.theWorld) {
            lastWorld = minecraft.theWorld;
            ClipboardOverlay.INSTANCE.resetTransientState();
            requestDelay = ClipboardClientState.INSTANCE.isSynced() ? 0 : INITIAL_SYNC_FALLBACK_TICKS;
        } else if (requestDelay > 0 && --requestDelay == 0) {
            if (!ClipboardClientState.INSTANCE.isSynced()) {
                NetworkHandler.sendToServer(new C2SRequestState());
            }
        }
        ClipboardClientState.INSTANCE.tick();
        if (lastScreen != minecraft.currentScreen) {
            lastScreen = minecraft.currentScreen;
            ClipboardOverlay.INSTANCE.resetTransientState();
        }

        if (minecraft.currentScreen instanceof OverlayInteractionScreen && !ClipboardOverlay.INSTANCE.isAvailable()) {
            ClipboardOverlay.INSTANCE.cancelLayoutEdit();
            minecraft.displayGuiScreen(null);
        } else if (isOverlaySuppressed(minecraft.currentScreen)) {
            ClipboardOverlay.INSTANCE.resetTransientState();
        }
        ClipboardOverlay.INSTANCE.updateTextFields();
        updateNotice();
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        ClipboardOverlay overlay = ClipboardOverlay.INSTANCE;
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen != null) return;
        if (!overlay.isAvailable() || overlay.isModalOpen() || overlay.isLayoutEditing()) return;
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
        if (minecraft.currentScreen != null) return;
        boolean hasNotice = noticeTicks > 0 && noticeKey != null;
        if (!ClipboardOverlay.INSTANCE.isAvailable() && !hasNotice) return;
        int width = event.resolution.getScaledWidth();
        int height = event.resolution.getScaledHeight();
        ClipboardOverlay.INSTANCE.render(width, height, -1, -1);
        renderNotice(width, height);
    }

    @SubscribeEvent
    public void onGuiDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (shouldUseOverlay(event.gui)) {
            ClipboardOverlay.INSTANCE.render(event.gui.width, event.gui.height, event.mouseX, event.mouseY);
        }
        renderNotice(event.gui.width, event.gui.height);
    }

    public void renderNotice(int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.currentScreen instanceof OverlayInteractionScreen) {
            String shortcut = KeyBindingDisplay.getDisplayString(ClientKeyBindings.TOGGLE_INTERACTION);
            String text = StatCollector
                .translateToLocalFormatted("message.clipboardanywhere.interaction_exit_hint", shortcut);
            int x = (screenWidth - minecraft.fontRenderer.getStringWidth(text)) / 2;
            minecraft.fontRenderer.drawStringWithShadow(text, x, screenHeight - 70, 0xFFFFFFFF);
        }
        if (noticeTicks <= 0 || noticeKey == null) return;
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
        if (MODULARUI2_LOADED && ModularUiInputCompat.hasTextInputFocus(screen)) return true;
        if (MODULARUI1_LOADED && ModularUi1FocusBridge.hasTextInputFocus(screen)) return true;
        if (NEI_LOADED && screen instanceof GuiContainer && NeiFocusBridge.hasTextInputFocus(screen)) return true;
        return hasReflectedTextFieldFocus(screen);
    }

    static boolean hasReflectedTextFieldFocus(Object screen) {
        if (screen == null) return false;
        Field[] fields = getCachedTextFields(screen.getClass());
        for (Field field : fields) {
            try {
                Object value = field.get(screen);
                if (isTextFieldObjectFocused(value)) return true;
            } catch (IllegalAccessException | SecurityException ignored) {
                // Inaccessible mod fields are skipped safely.
            }
        }
        return false;
    }

    static Field[] getCachedTextFields(Class<?> screenClass) {
        Field[] cached = TEXT_FIELD_CACHE.get(screenClass);
        if (cached != null) return cached;
        Field[] discovered = discoverTextFields(screenClass);
        Field[] existing = TEXT_FIELD_CACHE.putIfAbsent(screenClass, discovered);
        return existing != null ? existing : discovered;
    }

    private static Field[] discoverTextFields(Class<?> screenClass) {
        List<Field> matched = null;
        for (Class<?> type = screenClass; type != null && type != Object.class
            && !"net.minecraft.client.gui.GuiScreen".equals(type.getName()); type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!isSupportedTextFieldType(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    if (matched == null) matched = new ArrayList<>(4);
                    matched.add(field);
                } catch (SecurityException ignored) {
                    // Skip fields blocked by a security manager.
                }
            }
        }
        return matched == null ? EMPTY_FIELDS : matched.toArray(new Field[matched.size()]);
    }

    private static boolean isSupportedTextFieldType(Class<?> fieldType) {
        if (GuiTextField.class.isAssignableFrom(fieldType) || GuiBiblioTextField.class.isAssignableFrom(fieldType)) {
            return true;
        }
        if (fieldType.isArray()) {
            Class<?> component = fieldType.getComponentType();
            return GuiTextField.class.isAssignableFrom(component)
                || GuiBiblioTextField.class.isAssignableFrom(component);
        }
        return false;
    }

    private static boolean isTextFieldObjectFocused(Object value) {
        if (value == null) return false;
        if (value instanceof GuiTextField) {
            return ((GuiTextField) value).isFocused();
        }
        if (value instanceof GuiBiblioTextField) {
            return ((GuiBiblioTextField) value).isFocused();
        }
        if (value instanceof GuiTextField[]) {
            for (GuiTextField element : (GuiTextField[]) value) {
                if (element != null && element.isFocused()) return true;
            }
            return false;
        }
        if (value instanceof GuiBiblioTextField[]) {
            for (GuiBiblioTextField element : (GuiBiblioTextField[]) value) {
                if (element != null && element.isFocused()) return true;
            }
        }
        return false;
    }

    private static boolean isModLoadedSafe(String modId) {
        try {
            return Loader.isModLoaded(modId);
        } catch (Throwable ignored) {
            return false;
        }
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

    private static final class ModularUi1FocusBridge {

        private static final Class<?> MODULAR_GUI_CLASS;
        private static final Method GET_CURSOR_METHOD;
        private static final Method GET_FOCUSED_METHOD;
        private static final Class<?> BASE_TEXT_FIELD_CLASS;
        private static final boolean AVAILABLE;

        static {
            Class<?> guiClass = null;
            Method getCursor = null;
            Method getFocused = null;
            Class<?> textFieldClass = null;
            boolean ok = false;
            try {
                guiClass = Class.forName("com.gtnewhorizons.modularui.common.internal.wrapper.ModularGui");
                Class<?> cursorClass = Class.forName("com.gtnewhorizons.modularui.api.screen.Cursor");
                textFieldClass = Class
                    .forName("com.gtnewhorizons.modularui.common.widget.textfield.BaseTextFieldWidget");
                getCursor = guiClass.getMethod("getCursor");
                getFocused = cursorClass.getMethod("getFocused");
                ok = true;
            } catch (Throwable ignored) {
                // Degrade cleanly if ModularUI 1 internals are unavailable.
            }
            MODULAR_GUI_CLASS = guiClass;
            GET_CURSOR_METHOD = getCursor;
            GET_FOCUSED_METHOD = getFocused;
            BASE_TEXT_FIELD_CLASS = textFieldClass;
            AVAILABLE = ok;
        }

        private ModularUi1FocusBridge() {}

        static boolean hasTextInputFocus(GuiScreen screen) {
            if (!AVAILABLE || !MODULAR_GUI_CLASS.isInstance(screen)) return false;
            try {
                Object cursor = GET_CURSOR_METHOD.invoke(screen);
                if (cursor == null) return false;
                Object focused = GET_FOCUSED_METHOD.invoke(cursor);
                return BASE_TEXT_FIELD_CLASS.isInstance(focused);
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    private static final class NeiFocusBridge {

        private static final Field LAYOUT_SEARCH_FIELD;
        private static final Field LAYOUT_QUANTITY_FIELD;
        private static final Method NEI_IS_HIDDEN_METHOD;
        private static final Method NEI_IS_ENABLED_METHOD;
        private static final Method TEXT_FIELD_FOCUSED_METHOD;
        private static final Method SEARCH_FIELD_VISIBLE_METHOD;
        private static final Class<?> GUI_RECIPE_CLASS;
        private static final Field RECIPE_SEARCH_FIELD;
        private static final Method RECIPE_SEARCH_VISIBLE_METHOD;
        private static final boolean AVAILABLE;

        static {
            Field layoutSearch = null;
            Field layoutQuantity = null;
            Method isHidden = null;
            Method isEnabled = null;
            Method focused = null;
            Method searchVisible = null;
            Class<?> guiRecipe = null;
            Field recipeSearch = null;
            Method recipeVisible = null;
            boolean ok = false;
            try {
                Class<?> layoutManager = Class.forName("codechicken.nei.LayoutManager");
                Class<?> neiConfig = Class.forName("codechicken.nei.NEIClientConfig");
                Class<?> textFieldClass = Class.forName("codechicken.nei.TextField");
                Class<?> searchFieldClass = Class.forName("codechicken.nei.SearchField");

                layoutSearch = layoutManager.getField("searchField");
                layoutQuantity = layoutManager.getField("quantity");
                isHidden = neiConfig.getMethod("isHidden");
                isEnabled = neiConfig.getMethod("isEnabled");
                focused = textFieldClass.getMethod("focused");
                searchVisible = searchFieldClass.getMethod("isVisible");
                ok = true;

                try {
                    guiRecipe = Class.forName("codechicken.nei.recipe.GuiRecipe");
                    Class<?> recipeSearchClass = Class.forName("codechicken.nei.RecipeSearchField");
                    recipeSearch = guiRecipe.getDeclaredField("searchField");
                    recipeSearch.setAccessible(true);
                    recipeVisible = recipeSearchClass.getMethod("isVisible");
                } catch (Throwable ignored) {
                    // Older NEI builds without recipe search field remain supported via main searchField.
                    guiRecipe = null;
                    recipeSearch = null;
                    recipeVisible = null;
                }
            } catch (Throwable ignored) {
                // Degrade cleanly if NEI internals are unavailable.
            }
            LAYOUT_SEARCH_FIELD = layoutSearch;
            LAYOUT_QUANTITY_FIELD = layoutQuantity;
            NEI_IS_HIDDEN_METHOD = isHidden;
            NEI_IS_ENABLED_METHOD = isEnabled;
            TEXT_FIELD_FOCUSED_METHOD = focused;
            SEARCH_FIELD_VISIBLE_METHOD = searchVisible;
            GUI_RECIPE_CLASS = guiRecipe;
            RECIPE_SEARCH_FIELD = recipeSearch;
            RECIPE_SEARCH_VISIBLE_METHOD = recipeVisible;
            AVAILABLE = ok;
        }

        private NeiFocusBridge() {}

        static boolean hasTextInputFocus(GuiScreen screen) {
            if (!AVAILABLE) return false;
            try {
                if (GUI_RECIPE_CLASS != null && GUI_RECIPE_CLASS.isInstance(screen) && RECIPE_SEARCH_FIELD != null) {
                    Object recipeField = RECIPE_SEARCH_FIELD.get(null);
                    if (recipeField != null && Boolean.TRUE.equals(RECIPE_SEARCH_VISIBLE_METHOD.invoke(recipeField))
                        && Boolean.TRUE.equals(TEXT_FIELD_FOCUSED_METHOD.invoke(recipeField))) {
                        return true;
                    }
                }
                if (Boolean.TRUE.equals(NEI_IS_HIDDEN_METHOD.invoke(null))
                    || !Boolean.TRUE.equals(NEI_IS_ENABLED_METHOD.invoke(null))) {
                    return false;
                }
                Object searchField = LAYOUT_SEARCH_FIELD.get(null);
                if (searchField != null && Boolean.TRUE.equals(SEARCH_FIELD_VISIBLE_METHOD.invoke(searchField))
                    && Boolean.TRUE.equals(TEXT_FIELD_FOCUSED_METHOD.invoke(searchField))) {
                    return true;
                }
                Object quantityField = LAYOUT_QUANTITY_FIELD.get(null);
                if (quantityField != null && Boolean.TRUE.equals(TEXT_FIELD_FOCUSED_METHOD.invoke(quantityField))) {
                    return true;
                }
            } catch (Throwable ignored) {
                return false;
            }
            return false;
        }
    }
}
