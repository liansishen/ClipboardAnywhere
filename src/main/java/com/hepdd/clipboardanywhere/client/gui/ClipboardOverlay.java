package com.hepdd.clipboardanywhere.client.gui;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.hepdd.clipboardanywhere.Config;
import com.hepdd.clipboardanywhere.client.ClientKeyBindings;
import com.hepdd.clipboardanywhere.client.ClipboardClientState;
import com.hepdd.clipboardanywhere.client.KeyBindingDisplay;
import com.hepdd.clipboardanywhere.model.BindingView;
import com.hepdd.clipboardanywhere.model.ClipboardAction;
import com.hepdd.clipboardanywhere.model.ClipboardPageSnapshot;
import com.hepdd.clipboardanywhere.model.PlayerBindingSnapshot;
import com.hepdd.clipboardanywhere.network.NetworkCodec;
import com.hepdd.clipboardanywhere.network.NetworkHandler;
import com.hepdd.clipboardanywhere.network.message.C2SClipboardAction;
import com.hepdd.clipboardanywhere.network.message.C2SRenameBinding;
import com.hepdd.clipboardanywhere.network.message.C2SSelectBinding;
import com.hepdd.clipboardanywhere.network.message.C2SUnbind;
import com.hepdd.clipboardanywhere.network.message.C2SUpdateTaskText;

import jds.bibliocraft.items.ItemLoader;

public final class ClipboardOverlay {

    public static final ClipboardOverlay INSTANCE = new ClipboardOverlay();
    private static final int DROPDOWN_WIDTH = 65;
    private static final int ICON_WIDTH = 12;
    private static final int MAX_DROPDOWN_ROWS = 8;
    private static final int MODAL_MARGIN = 7;
    private static final int MODAL_BUTTON_GAP = 5;
    private static final int OPACITY_SLIDER_LEFT = 50;
    private static final int OPACITY_SLIDER_RIGHT_MARGIN = 12;
    private static final int COLLAPSED_DRAG_THRESHOLD = 3;
    private static final RenderItem ITEM_RENDERER = new RenderItem();

    private boolean dropdownOpen;
    private int dropdownOffset;
    private GuiTextField renameField;
    private UUID renameId;
    private GuiTextField taskEditField;
    private UUID taskEditId;
    private int taskEditPage;
    private int taskEditRow = -1;
    private String taskEditOriginalText;
    private boolean taskEditRepeatWasEnabled;
    private UUID pendingUnbindId;
    private boolean layoutEditing;
    private OverlayGeometry layoutGeometry;
    private double layoutBackgroundOpacity;
    private double layoutTextOpacity;
    private boolean draggingLayout;
    private boolean resizingLayout;
    private boolean adjustingBackgroundOpacity;
    private boolean adjustingTextOpacity;
    private boolean draggingOverlay;
    private boolean draggingCollapsed;
    private boolean collapsedPressed;
    private OverlayGeometry draggedGeometry;
    private int collapsedPressX;
    private int collapsedPressY;
    private int dragRightOffset;
    private int dragTopOffset;
    private int resizeLeft;
    private int resizeTop;
    private int backgroundAlpha = 255;
    private int foregroundAlpha = 255;

    private ClipboardOverlay() {}

    public boolean isAvailable() {
        PlayerBindingSnapshot snapshot = ClipboardClientState.INSTANCE.getSnapshot();
        return !snapshot.getBindings()
            .isEmpty() && !snapshot.isAllDisconnected();
    }

    public boolean isModalOpen() {
        return renameField != null || taskEditField != null || pendingUnbindId != null;
    }

    public boolean isTextEditing() {
        return renameField != null || taskEditField != null;
    }

    public OverlayGeometry geometry(int screenWidth, int screenHeight) {
        if (draggingOverlay && draggedGeometry != null) {
            if (draggingCollapsed) draggedGeometry.clampCollapsedToScreen(screenWidth, screenHeight);
            else draggedGeometry.clampToScreen(screenWidth, screenHeight);
            return draggedGeometry;
        }
        if (layoutEditing && layoutGeometry != null) {
            layoutGeometry.clampToScreen(screenWidth, screenHeight);
            return layoutGeometry;
        }
        boolean collapsed = Config.collapsed && !layoutEditing;
        if (Config.hasLegacyPosition()) {
            OverlayGeometry legacyGeometry = new OverlayGeometry(
                Config.getLegacyAnchorRight(),
                Config.getLegacyAnchorTop(),
                Config.scale,
                screenWidth,
                screenHeight,
                false);
            saveLayout(legacyGeometry, screenWidth, screenHeight, false, Config.backgroundOpacity, Config.textOpacity);
            return configuredGeometry(screenWidth, screenHeight, collapsed);
        }
        return configuredGeometry(screenWidth, screenHeight, collapsed);
    }

    private static OverlayGeometry configuredGeometry(int screenWidth, int screenHeight, boolean collapsed) {
        return OverlayGeometry.fromPosition(
            Config.horizontalPositionMode,
            Config.verticalPositionMode,
            Config.horizontalOffset,
            Config.verticalOffset,
            Config.horizontalPosition,
            Config.verticalPosition,
            Config.scale,
            screenWidth,
            screenHeight,
            collapsed);
    }

    public void render(int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!isAvailable()) return;
        OverlayGeometry geometry = geometry(screenWidth, screenHeight);
        if (layoutEditing) {
            Gui.drawRect(0, 0, screenWidth, screenHeight, 0x884A4A4A);
        }
        if (Config.collapsed && !layoutEditing) {
            drawCollapsedIcon(geometry);
            if (geometry.containsCollapsed(mouseX, mouseY)) {
                drawWrappedTooltip(
                    Minecraft.getMinecraft().fontRenderer,
                    StatCollector.translateToLocalFormatted(
                        "tooltip.clipboardanywhere.collapsed",
                        KeyBindingDisplay.getDisplayString(ClientKeyBindings.TOGGLE_COLLAPSE)),
                    screenWidth,
                    screenHeight,
                    mouseX,
                    mouseY);
            }
            return;
        }
        BindingView active = ClipboardClientState.INSTANCE.getSnapshot()
            .getActiveBinding();
        if (active == null) return;

        int logicalMouseX = geometry.toLogicalX(mouseX);
        int logicalMouseY = geometry.toLogicalY(mouseY);
        boolean controlsVisible = shouldShowControls(logicalMouseX, logicalMouseY);
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LIGHTING_BIT
                | GL11.GL_TEXTURE_BIT);
        GL11.glPushMatrix();
        GL11.glTranslated(geometry.getLeft(), geometry.getTop(), 0.0D);
        GL11.glScaled(geometry.getScale(), geometry.getScale(), 1.0D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawExpanded(active, logicalMouseX, logicalMouseY, controlsVisible, geometry.getLogicalHeight());
        GL11.glPopMatrix();
        if (layoutEditing) {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            drawLayoutButtons(screenWidth, mouseX, mouseY);
        } else if (!isModalOpen()) drawContentTooltip(active, geometry, screenWidth, screenHeight, mouseX, mouseY);
        GL11.glPopAttrib();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private boolean shouldShowControls(int logicalMouseX, int logicalMouseY) {
        boolean hovered = logicalMouseX >= 0 && logicalMouseX < OverlayGeometry.LOGICAL_WIDTH
            && logicalMouseY >= 0
            && logicalMouseY < OverlayGeometry.LOGICAL_HEIGHT;
        return layoutEditing || dropdownOpen || isModalOpen() || draggingOverlay || hovered;
    }

    public boolean mouseClicked(int screenWidth, int screenHeight, int mouseX, int mouseY, int button) {
        if (!isAvailable()) return false;
        if (layoutEditing) {
            handleLayoutMousePressed(screenWidth, screenHeight, mouseX, mouseY, button);
            return true;
        }
        OverlayGeometry geometry = geometry(screenWidth, screenHeight);
        if (Config.collapsed) {
            if (button != 0 || !geometry.containsCollapsed(mouseX, mouseY)) return false;
            collapsedPressed = true;
            draggingCollapsed = false;
            draggedGeometry = geometry;
            collapsedPressX = mouseX;
            collapsedPressY = mouseY;
            dragRightOffset = geometry.getAnchorRight() - mouseX;
            dragTopOffset = geometry.getTop() - mouseY;
            return true;
        }
        if (button != 0) return false;
        int x = geometry.toLogicalX(mouseX);
        int y = geometry.toLogicalY(mouseY);
        if (x < 0 || x >= OverlayGeometry.LOGICAL_WIDTH || y < 0 || y >= OverlayGeometry.LOGICAL_HEIGHT) {
            if (taskEditField != null) {
                submitTaskEdit();
                return true;
            }
            if (dropdownOpen) {
                dropdownOpen = false;
                return true;
            }
            return false;
        }

        if (renameField != null) return handleRenameClick(x, y);
        if (taskEditField != null) return handleTaskEditClick(x, y);
        if (pendingUnbindId != null) return handleUnbindConfirmationClick(x, y);

        PlayerBindingSnapshot snapshot = ClipboardClientState.INSTANCE.getSnapshot();
        BindingView active = snapshot.getActiveBinding();
        if (active == null) return false;

        if (dropdownOpen && x < DROPDOWN_WIDTH && y >= OverlayGeometry.HEADER_HEIGHT) {
            dropdownOffset = clamp(
                dropdownOffset,
                0,
                Math.max(
                    0,
                    snapshot.getBindings()
                        .size() - MAX_DROPDOWN_ROWS));
            int visibleRows = Math.min(
                MAX_DROPDOWN_ROWS,
                snapshot.getBindings()
                    .size() - dropdownOffset);
            int row = (y - OverlayGeometry.HEADER_HEIGHT) / OverlayGeometry.ROW_HEIGHT;
            if (row >= 0 && row < visibleRows) {
                BindingView selected = snapshot.getBindings()
                    .get(dropdownOffset + row);
                dropdownOpen = false;
                NetworkHandler.sendToServer(new C2SSelectBinding(selected.getId()));
                return true;
            }
        }

        if (y < OverlayGeometry.HEADER_HEIGHT) {
            if (x < DROPDOWN_WIDTH) {
                dropdownOpen = !dropdownOpen;
                return true;
            }
            if (x < DROPDOWN_WIDTH + ICON_WIDTH) {
                if (active.getStatus()
                    .isReadable()) beginRename(active);
                return true;
            }
            if (x < DROPDOWN_WIDTH + ICON_WIDTH * 2) {
                pendingUnbindId = active.getId();
                dropdownOpen = false;
                return true;
            }
            if (x >= OverlayGeometry.LOGICAL_WIDTH - ICON_WIDTH) {
                Config.setCollapsed(true);
                dropdownOpen = false;
                return true;
            }
            if (x >= OverlayGeometry.LOGICAL_WIDTH - ICON_WIDTH * 2) {
                beginLayoutEdit(screenWidth, screenHeight);
                return true;
            }
            if (isHeaderDragArea(x, y)) {
                beginOverlayDrag(geometry, mouseX, mouseY);
                return true;
            }
        }

        if (!active.getStatus()
            .isReadable()) return true;
        int taskStart = OverlayGeometry.HEADER_HEIGHT;
        int taskRow = OverlayGeometry.checkboxRowAt(x, y, taskStart, ClipboardPageSnapshot.TASK_COUNT);
        if (taskRow >= 0) {
            NetworkHandler.sendToServer(new C2SClipboardAction(active.getId(), ClipboardAction.CYCLE_TASK, taskRow));
            return true;
        }
        int textRow = taskTextRowAt(x, y, taskStart);
        if (textRow >= 0) {
            beginTaskEdit(active, textRow);
            return true;
        }
        int footerTop = OverlayGeometry.LOGICAL_HEIGHT - OverlayGeometry.FOOTER_HEIGHT;
        if (y >= footerTop && x < 28) {
            NetworkHandler.sendToServer(new C2SClipboardAction(active.getId(), ClipboardAction.PREVIOUS_PAGE, -1));
            return true;
        }
        if (y >= footerTop && x >= OverlayGeometry.LOGICAL_WIDTH - 28) {
            NetworkHandler.sendToServer(new C2SClipboardAction(active.getId(), ClipboardAction.NEXT_PAGE, -1));
            return true;
        }
        return true;
    }

    public boolean mouseScrolled(int wheelDelta) {
        if (layoutEditing || taskEditField != null) return wheelDelta != 0;
        if (!dropdownOpen || wheelDelta == 0) return false;
        int bindingCount = ClipboardClientState.INSTANCE.getSnapshot()
            .getBindings()
            .size();
        int maxOffset = Math.max(0, bindingCount - MAX_DROPDOWN_ROWS);
        dropdownOffset = clamp(dropdownOffset + (wheelDelta < 0 ? 1 : -1), 0, maxOffset);
        return true;
    }

    public boolean keyTyped(char character, int keyCode) {
        if (layoutEditing) {
            if (keyCode == 1) cancelLayoutEdit();
            return true;
        }
        if (renameField != null) {
            if (keyCode == 1) {
                closeModal();
            } else if (keyCode == 28 || keyCode == 156) {
                String name = renameField.getText()
                    .trim();
                if (!name.isEmpty()) NetworkHandler.sendToServer(new C2SRenameBinding(renameId, name));
                closeModal();
            } else {
                renameField.textboxKeyTyped(character, keyCode);
            }
            return true;
        }
        if (taskEditField != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                closeModal();
            } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                submitTaskEdit();
            } else if (keyCode == Keyboard.KEY_TAB) {
                int direction = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) ? -1
                    : 1;
                submitTaskEdit(taskEditRow + direction);
            } else {
                taskEditField.textboxKeyTyped(character, keyCode);
            }
            return true;
        }
        if (pendingUnbindId != null) {
            if (keyCode == 1) {
                closeModal();
            } else if (keyCode == 28 || keyCode == 156) {
                NetworkHandler.sendToServer(new C2SUnbind(pendingUnbindId));
                closeModal();
            }
            return true;
        }
        return false;
    }

    public boolean handleShortcut(int keyCode) {
        if (!isAvailable() || isModalOpen() || layoutEditing || keyCode == Keyboard.KEY_NONE) return false;
        if (KeyBindingDisplay.isActiveAndMatches(ClientKeyBindings.TOGGLE_COLLAPSE, keyCode)) {
            toggleCollapsed();
            return true;
        }
        if (Config.collapsed) return false;
        if (KeyBindingDisplay.isActiveAndMatches(ClientKeyBindings.PREVIOUS_PAGE, keyCode)) {
            performActiveAction(ClipboardAction.PREVIOUS_PAGE);
            return true;
        }
        if (KeyBindingDisplay.isActiveAndMatches(ClientKeyBindings.NEXT_PAGE, keyCode)) {
            performActiveAction(ClipboardAction.NEXT_PAGE);
            return true;
        }
        return false;
    }

    public void toggleCollapsed() {
        if (!isAvailable() || layoutEditing) return;
        cancelOverlayDrag();
        Config.setCollapsed(!Config.collapsed);
        dropdownOpen = false;
        closeModal();
    }

    public void performActiveAction(ClipboardAction action) {
        if (!isAvailable() || Config.collapsed || isModalOpen() || layoutEditing) return;
        BindingView active = ClipboardClientState.INSTANCE.getSnapshot()
            .getActiveBinding();
        if (active != null && active.getStatus()
            .isReadable()) {
            NetworkHandler.sendToServer(new C2SClipboardAction(active.getId(), action, -1));
        }
    }

    public void resetTransientState() {
        dropdownOpen = false;
        dropdownOffset = 0;
        closeModal();
        cancelLayoutEdit();
        cancelOverlayDrag();
    }

    public void updateTextFields() {
        if (renameField != null) renameField.updateCursorCounter();
        if (taskEditField != null) {
            BindingView active = ClipboardClientState.INSTANCE.getSnapshot()
                .getActiveBinding();
            if (active == null || !active.getStatus()
                .isReadable()
                || !active.getId()
                    .equals(taskEditId)
                || active.getSnapshot()
                    .getCurrentPage() != taskEditPage) {
                closeModal();
            } else {
                taskEditField.updateCursorCounter();
            }
        }
    }

    public boolean isLayoutEditing() {
        return layoutEditing;
    }

    public void beginLayoutEdit(int screenWidth, int screenHeight) {
        if (!isAvailable() || Config.collapsed || layoutEditing) return;
        layoutGeometry = geometry(screenWidth, screenHeight);
        layoutBackgroundOpacity = Config.backgroundOpacity;
        layoutTextOpacity = Config.textOpacity;
        layoutEditing = true;
        dropdownOpen = false;
        closeModal();
        clearLayoutDragState();
    }

    public void cancelLayoutEdit() {
        layoutEditing = false;
        layoutGeometry = null;
        layoutBackgroundOpacity = Config.backgroundOpacity;
        layoutTextOpacity = Config.textOpacity;
        clearLayoutDragState();
    }

    public void confirmLayoutEdit(int screenWidth, int screenHeight) {
        if (!layoutEditing || layoutGeometry == null) return;
        saveLayout(layoutGeometry, screenWidth, screenHeight, false, layoutBackgroundOpacity, layoutTextOpacity);
        cancelLayoutEdit();
    }

    public boolean mouseDragged(int screenWidth, int screenHeight, int mouseX, int mouseY, int button) {
        if (collapsedPressed && button == 0 && draggedGeometry != null) {
            if (!draggingCollapsed) {
                int deltaX = mouseX - collapsedPressX;
                int deltaY = mouseY - collapsedPressY;
                draggingCollapsed = deltaX * deltaX + deltaY * deltaY
                    > COLLAPSED_DRAG_THRESHOLD * COLLAPSED_DRAG_THRESHOLD;
                draggingOverlay = draggingCollapsed;
            }
            if (draggingCollapsed) {
                draggedGeometry
                    .setCollapsedPosition(mouseX + dragRightOffset, mouseY + dragTopOffset, screenWidth, screenHeight);
            }
            return true;
        }
        if (draggingOverlay && button == 0 && draggedGeometry != null) {
            draggedGeometry.setPosition(mouseX + dragRightOffset, mouseY + dragTopOffset, screenWidth, screenHeight);
            return true;
        }
        if (!layoutEditing || button != 0 || layoutGeometry == null) return layoutEditing;
        if (draggingLayout) {
            layoutGeometry.setPosition(mouseX + dragRightOffset, mouseY + dragTopOffset, screenWidth, screenHeight);
        } else if (resizingLayout) {
            double widthScale = (mouseX - resizeLeft) / (double) OverlayGeometry.LOGICAL_WIDTH;
            double heightScale = (mouseY - resizeTop) / (double) layoutGeometry.getLogicalHeight();
            layoutGeometry
                .resizeFromTopLeft(resizeLeft, resizeTop, Math.max(widthScale, heightScale), screenWidth, screenHeight);
        } else if (adjustingBackgroundOpacity) {
            updateLayoutOpacity(screenWidth, screenHeight, mouseX, false);
        } else if (adjustingTextOpacity) {
            updateLayoutOpacity(screenWidth, screenHeight, mouseX, true);
        }
        return true;
    }

    public boolean mouseReleased(int screenWidth, int screenHeight, int button) {
        if (collapsedPressed && button == 0) {
            if (draggingCollapsed && draggedGeometry != null) {
                saveLayout(
                    draggedGeometry,
                    screenWidth,
                    screenHeight,
                    true,
                    Config.backgroundOpacity,
                    Config.textOpacity);
            } else {
                Config.setCollapsed(false);
            }
            cancelOverlayDrag();
            return true;
        }
        if (draggingOverlay && button == 0 && draggedGeometry != null) {
            saveLayout(draggedGeometry, screenWidth, screenHeight, false, Config.backgroundOpacity, Config.textOpacity);
            cancelOverlayDrag();
            return true;
        }
        if (!layoutEditing) return false;
        if (button == 0) clearLayoutDragState();
        return true;
    }

    private static void saveLayout(OverlayGeometry geometry, int screenWidth, int screenHeight, boolean collapsed,
        double backgroundOpacity, double textOpacity) {
        OverlayGeometry.SavedPosition position = geometry.toSavedPosition(screenWidth, screenHeight, collapsed);
        Config.saveLayout(
            position.getHorizontalMode(),
            position.getVerticalMode(),
            position.getHorizontalOffset(),
            position.getVerticalOffset(),
            position.getHorizontalPosition(),
            position.getVerticalPosition(),
            geometry.getScale(),
            backgroundOpacity,
            textOpacity);
    }

    static boolean isHeaderDragArea(int x, int y) {
        return y >= 0 && y < OverlayGeometry.HEADER_HEIGHT
            && x >= DROPDOWN_WIDTH + ICON_WIDTH * 2
            && x < OverlayGeometry.LOGICAL_WIDTH - ICON_WIDTH * 2;
    }

    private void beginOverlayDrag(OverlayGeometry geometry, int mouseX, int mouseY) {
        draggingOverlay = true;
        draggedGeometry = geometry;
        dragRightOffset = geometry.getAnchorRight() - mouseX;
        dragTopOffset = geometry.getTop() - mouseY;
        dropdownOpen = false;
    }

    private void cancelOverlayDrag() {
        draggingOverlay = false;
        draggingCollapsed = false;
        collapsedPressed = false;
        draggedGeometry = null;
    }

    private void drawExpanded(BindingView active, int mouseX, int mouseY, boolean controlsVisible, int logicalHeight) {
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer font = minecraft.fontRenderer;
        double backgroundOpacity = layoutEditing ? layoutBackgroundOpacity : Config.backgroundOpacity;
        double textOpacity = layoutEditing ? layoutTextOpacity : Config.textOpacity;
        backgroundAlpha = (int) Math.round(backgroundOpacity * 255.0D);
        foregroundAlpha = (int) Math.round(textOpacity * 255.0D);
        int alpha = (int) (backgroundOpacity * 255.0D) << 24;
        int panel = alpha | 0x050505;
        int header = alpha | 0x111111;
        int border = alpha | 0x626262;
        int footer = (int) (backgroundOpacity * 0.75D * 255.0D) << 24;
        Gui.drawRect(0, 0, OverlayGeometry.LOGICAL_WIDTH, logicalHeight, border);
        Gui.drawRect(1, 1, OverlayGeometry.LOGICAL_WIDTH - 1, logicalHeight - 1, panel);

        ClipboardPageSnapshot page = active.getSnapshot();
        if (controlsVisible) {
            Gui.drawRect(1, 1, OverlayGeometry.LOGICAL_WIDTH - 1, OverlayGeometry.HEADER_HEIGHT, header);
            drawHeader(font, active, mouseX, mouseY);
        } else {
            String title = trim(
                font,
                page.getTitle()
                    .isEmpty() ? " " : page.getTitle(),
                OverlayGeometry.LOGICAL_WIDTH - 18);
            drawCentered(font, title, 5, 0xFFE8E8E8);
        }
        int taskTop = OverlayGeometry.HEADER_HEIGHT;
        int visibleTasks = layoutEditing ? ClipboardPageSnapshot.TASK_COUNT - 1 : ClipboardPageSnapshot.TASK_COUNT;
        for (int row = 0; row < visibleTasks; row++) {
            int y = taskTop + row * OverlayGeometry.ROW_HEIGHT;
            drawTask(
                font,
                page,
                row,
                y,
                active.getStatus()
                    .isReadable());
        }
        if (taskEditField != null) taskEditField.drawTextBox();
        if (layoutEditing) {
            drawOpacitySliders(font, footer, taskTop, logicalHeight);
            drawResizeHandle(logicalHeight);
        } else {
            drawFooter(font, active, page, footer, logicalHeight);
        }

        if (!layoutEditing && controlsVisible) {
            if (dropdownOpen || renameField != null || pendingUnbindId != null) {
                int previousBackgroundAlpha = backgroundAlpha;
                int previousForegroundAlpha = foregroundAlpha;
                backgroundAlpha = 255;
                foregroundAlpha = 255;
                if (dropdownOpen) drawDropdown(font, mouseX, mouseY);
                if (renameField != null) drawRenameModal(font);
                if (pendingUnbindId != null) drawUnbindModal(font);
                backgroundAlpha = previousBackgroundAlpha;
                foregroundAlpha = previousForegroundAlpha;
            }
            if (!dropdownOpen && !isModalOpen()) drawHeaderTooltip(font, mouseX, mouseY);
        }
    }

    private void drawHeader(FontRenderer font, BindingView active, int mouseX, int mouseY) {
        boolean readable = active.getStatus()
            .isReadable();
        String suffix = readable ? "" : " [!]";
        font.drawStringWithShadow(
            trim(font, active.getDisplayName() + suffix, DROPDOWN_WIDTH - 8),
            5,
            5,
            fade(0xFFFFFFFF));
        if (layoutEditing) {
            String label = StatCollector.translateToLocal("gui.clipboardanywhere.drag_title");
            font.drawStringWithShadow(
                trim(font, label, OverlayGeometry.LOGICAL_WIDTH - DROPDOWN_WIDTH - 8),
                DROPDOWN_WIDTH,
                5,
                fade(0xFFD8E0E3));
            return;
        }
        drawOutline(1, 1, DROPDOWN_WIDTH, OverlayGeometry.HEADER_HEIGHT, 0xFF6A6A6A);
        drawPencilIcon(DROPDOWN_WIDTH, 0, readable ? 0xFFFFFFFF : 0xFF777C7E);
        drawXIcon(DROPDOWN_WIDTH + ICON_WIDTH, 0, 0xFFFFB2B2);
        drawGearIcon(OverlayGeometry.LOGICAL_WIDTH - ICON_WIDTH * 2, 0, 0xFFD6E5EA);
        drawCollapseIcon(OverlayGeometry.LOGICAL_WIDTH - ICON_WIDTH, 0, 0xFFFFFFFF);
    }

    private void drawTask(FontRenderer font, ClipboardPageSnapshot page, int row, int y, boolean enabled) {
        Gui.drawRect(
            18,
            y + OverlayGeometry.ROW_HEIGHT - 1,
            OverlayGeometry.LOGICAL_WIDTH - 6,
            y + OverlayGeometry.ROW_HEIGHT,
            fadeBackground(0xFFB8C0C3));
        int state = page.getTaskState(row);
        int boxColor = fade(enabled ? 0xFFC8C8C8 : 0xFF666666);
        Gui.drawRect(6, y + 3, 14, y + 11, boxColor);
        Gui.drawRect(7, y + 4, 13, y + 10, fadeBackground(0xFF080808));
        if (state == 1) drawCheckMark(y, fade(enabled ? 0xFF79D88C : 0xFF777C7E));
        if (state == 2) drawTaskX(y, fade(enabled ? 0xFFE77878 : 0xFF777C7E));
        if (taskEditField != null && row == taskEditRow) return;
        int textColor = fade(enabled ? 0xFFE8ECEC : 0xFF8C9294);
        font.drawString(trim(font, page.getTask(row), OverlayGeometry.LOGICAL_WIDTH - 26), 20, y + 3, textColor);
    }

    private static void drawCheckMark(int rowTop, int color) {
        Gui.drawRect(8, rowTop + 6, 9, rowTop + 8, color);
        Gui.drawRect(9, rowTop + 7, 10, rowTop + 9, color);
        Gui.drawRect(10, rowTop + 6, 11, rowTop + 8, color);
        Gui.drawRect(11, rowTop + 5, 12, rowTop + 7, color);
        Gui.drawRect(12, rowTop + 4, 13, rowTop + 6, color);
    }

    private static void drawTaskX(int rowTop, int color) {
        for (int offset = 0; offset < 5; offset++) {
            Gui.drawRect(8 + offset, rowTop + 4 + offset, 9 + offset, rowTop + 5 + offset, color);
            Gui.drawRect(12 - offset, rowTop + 4 + offset, 13 - offset, rowTop + 5 + offset, color);
        }
    }

    private void drawFooter(FontRenderer font, BindingView active, ClipboardPageSnapshot page, int background,
        int logicalHeight) {
        int y = logicalHeight - OverlayGeometry.FOOTER_HEIGHT;
        Gui.drawRect(1, y, OverlayGeometry.LOGICAL_WIDTH - 1, logicalHeight - 1, background);
        int color = active.getStatus()
            .isReadable() ? 0xFFFFFFFF : 0xFF777C7E;
        font.drawStringWithShadow("<", 9, y + 5, fade(color));
        font.drawStringWithShadow(">", OverlayGeometry.LOGICAL_WIDTH - 14, y + 5, fade(color));
        String pageNumber = page.getCurrentPage() + " / " + page.getTotalPages();
        if (!active.getStatus()
            .isReadable()) {
            String status = StatCollector.translateToLocal("status.clipboardanywhere.disconnected");
            int pageX = OverlayGeometry.LOGICAL_WIDTH - 20 - font.getStringWidth(pageNumber);
            font.drawStringWithShadow(trim(font, status, Math.max(12, pageX - 22)), 22, y + 5, fade(0xFFFFC66D));
            font.drawStringWithShadow(pageNumber, pageX, y + 5, fade(color));
        } else {
            drawCentered(font, pageNumber, y + 5, color);
        }
    }

    private void drawDropdown(FontRenderer font, int mouseX, int mouseY) {
        List<BindingView> bindings = ClipboardClientState.INSTANCE.getSnapshot()
            .getBindings();
        dropdownOffset = clamp(dropdownOffset, 0, Math.max(0, bindings.size() - MAX_DROPDOWN_ROWS));
        int visibleRows = Math.min(MAX_DROPDOWN_ROWS, bindings.size() - dropdownOffset);
        int bottom = OverlayGeometry.HEADER_HEIGHT + visibleRows * OverlayGeometry.ROW_HEIGHT;
        Gui.drawRect(0, OverlayGeometry.HEADER_HEIGHT, DROPDOWN_WIDTH + 1, bottom + 1, fadeBackground(0xFF6A6A6A));
        Gui.drawRect(1, OverlayGeometry.HEADER_HEIGHT + 1, DROPDOWN_WIDTH, bottom, fadeBackground(0xF0080808));
        for (int row = 0; row < visibleRows; row++) {
            BindingView binding = bindings.get(dropdownOffset + row);
            int y = OverlayGeometry.HEADER_HEIGHT + row * OverlayGeometry.ROW_HEIGHT;
            if (mouseX >= 1 && mouseX < DROPDOWN_WIDTH && mouseY >= y && mouseY < y + OverlayGeometry.ROW_HEIGHT) {
                Gui.drawRect(2, y + 1, DROPDOWN_WIDTH - 1, y + OverlayGeometry.ROW_HEIGHT, fadeBackground(0xFF292929));
            }
            int color = binding.getStatus()
                .isReadable() ? fade(0xFFFFFFFF) : fade(0xFF9B9FA1);
            String label = binding.getDisplayName() + (binding.getStatus()
                .isReadable() ? "" : " [!]");
            font.drawString(trim(font, label, DROPDOWN_WIDTH - 10), 5, y + 3, color);
        }
    }

    private void drawRenameModal(FontRenderer font) {
        drawModalFrame();
        drawCentered(
            font,
            trim(
                font,
                StatCollector.translateToLocal("gui.clipboardanywhere.rename"),
                OverlayGeometry.LOGICAL_WIDTH - 18),
            48,
            0xFFFFFFFF);
        renameField.setTextColor(fade(0xFFE8E8E8));
        renameField.drawTextBox();
        drawModalButtons(
            font,
            StatCollector.translateToLocal("gui.done"),
            StatCollector.translateToLocal("gui.cancel"));
    }

    private void drawUnbindModal(FontRenderer font) {
        drawModalFrame();
        drawCentered(
            font,
            trim(
                font,
                StatCollector.translateToLocal("gui.clipboardanywhere.unbind_confirm"),
                OverlayGeometry.LOGICAL_WIDTH - 18),
            45,
            0xFFFFD0D0);
        List<String> lines = font.listFormattedStringToWidth(
            StatCollector.translateToLocal("gui.clipboardanywhere.unbind_keeps_content"),
            OverlayGeometry.LOGICAL_WIDTH - 18);
        for (int index = 0; index < Math.min(3, lines.size()); index++) {
            String line = index == 2 && lines.size() > 3
                ? trim(font, lines.get(index) + "...", OverlayGeometry.LOGICAL_WIDTH - 18)
                : lines.get(index);
            drawCentered(font, line, 57 + index * 10, 0xFFCECECE);
        }
        drawModalButtons(font, StatCollector.translateToLocal("gui.yes"), StatCollector.translateToLocal("gui.no"));
    }

    private void drawModalFrame() {
        Gui.drawRect(4, 36, OverlayGeometry.LOGICAL_WIDTH - 4, 116, fadeBackground(0xF0000000));
        Gui.drawRect(5, 37, OverlayGeometry.LOGICAL_WIDTH - 5, 115, fadeBackground(0xF0181818));
    }

    private void drawModalButtons(FontRenderer font, String confirm, String cancel) {
        Rect confirmBounds = modalConfirmBounds();
        Rect cancelBounds = modalCancelBounds();
        Gui.drawRect(
            confirmBounds.x,
            confirmBounds.y,
            confirmBounds.x + confirmBounds.width,
            confirmBounds.y + confirmBounds.height,
            fadeBackground(0xFF363636));
        Gui.drawRect(
            cancelBounds.x,
            cancelBounds.y,
            cancelBounds.x + cancelBounds.width,
            cancelBounds.y + cancelBounds.height,
            fadeBackground(0xFF282828));
        drawCenteredIn(font, trim(font, confirm, confirmBounds.width - 4), confirmBounds, 0xFFFFFFFF);
        drawCenteredIn(font, trim(font, cancel, cancelBounds.width - 4), cancelBounds, 0xFFFFFFFF);
    }

    private void drawHeaderTooltip(FontRenderer font, int mouseX, int mouseY) {
        if (mouseY < 0 || mouseY >= OverlayGeometry.HEADER_HEIGHT) return;
        String key = null;
        KeyBinding shortcut = null;
        if (mouseX >= DROPDOWN_WIDTH && mouseX < DROPDOWN_WIDTH + ICON_WIDTH) {
            BindingView active = ClipboardClientState.INSTANCE.getSnapshot()
                .getActiveBinding();
            if (active != null && active.getStatus()
                .isReadable()) key = "tooltip.clipboardanywhere.rename";
        } else if (mouseX < DROPDOWN_WIDTH + ICON_WIDTH * 2 && mouseX >= DROPDOWN_WIDTH + ICON_WIDTH) {
            key = "tooltip.clipboardanywhere.unbind";
        } else if (mouseX >= OverlayGeometry.LOGICAL_WIDTH - ICON_WIDTH * 2
            && mouseX < OverlayGeometry.LOGICAL_WIDTH - ICON_WIDTH) {
                key = "tooltip.clipboardanywhere.layout";
            } else if (mouseX >= OverlayGeometry.LOGICAL_WIDTH - ICON_WIDTH) {
                key = "tooltip.clipboardanywhere.collapse";
                shortcut = ClientKeyBindings.TOGGLE_COLLAPSE;
            }
        if (key == null) return;
        String text = shortcut == null ? StatCollector.translateToLocal(key) : shortcutTooltip(key, shortcut);
        int width = font.getStringWidth(text) + 8;
        int x = Math.max(2, Math.min(mouseX, OverlayGeometry.LOGICAL_WIDTH - width - 2));
        Gui.drawRect(x, 20, x + width, 34, 0xE0101214);
        font.drawString(text, x + 4, 23, 0xFFFFFFFF);
    }

    private void drawContentTooltip(BindingView active, OverlayGeometry geometry, int screenWidth, int screenHeight,
        int mouseX, int mouseY) {
        if (mouseX < 0 || mouseY < 0) return;
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int logicalX = geometry.toLogicalX(mouseX);
        int logicalY = geometry.toLogicalY(mouseY);
        if (logicalX < 0 || logicalX >= OverlayGeometry.LOGICAL_WIDTH
            || logicalY < 0
            || logicalY >= OverlayGeometry.LOGICAL_HEIGHT) return;

        String text = null;
        int availableWidth = 0;
        boolean alwaysShow = false;
        if (dropdownOpen && logicalX < DROPDOWN_WIDTH && logicalY >= OverlayGeometry.HEADER_HEIGHT) {
            List<BindingView> bindings = ClipboardClientState.INSTANCE.getSnapshot()
                .getBindings();
            int row = (logicalY - OverlayGeometry.HEADER_HEIGHT) / OverlayGeometry.ROW_HEIGHT;
            int index = dropdownOffset + row;
            if (row >= 0 && row < MAX_DROPDOWN_ROWS && index < bindings.size()) {
                text = bindings.get(index)
                    .getDisplayName();
                availableWidth = DROPDOWN_WIDTH - 10;
            }
        } else if (logicalY < OverlayGeometry.HEADER_HEIGHT && logicalX < DROPDOWN_WIDTH) {
            text = active.getDisplayName();
            availableWidth = DROPDOWN_WIDTH - 8;
        } else if (logicalY >= OverlayGeometry.LOGICAL_HEIGHT - OverlayGeometry.FOOTER_HEIGHT && logicalX < 28) {
            text = shortcutTooltip("tooltip.clipboardanywhere.previous_page", ClientKeyBindings.PREVIOUS_PAGE);
            alwaysShow = true;
        } else if (logicalY >= OverlayGeometry.LOGICAL_HEIGHT - OverlayGeometry.FOOTER_HEIGHT
            && logicalX >= OverlayGeometry.LOGICAL_WIDTH - 28) {
                text = shortcutTooltip("tooltip.clipboardanywhere.next_page", ClientKeyBindings.NEXT_PAGE);
                alwaysShow = true;
            } else {
                int taskStart = OverlayGeometry.HEADER_HEIGHT;
                if (logicalY >= taskStart && logicalY < taskStart + OverlayGeometry.ROW_HEIGHT * 9) {
                    int row = (logicalY - taskStart) / OverlayGeometry.ROW_HEIGHT;
                    text = active.getSnapshot()
                        .getTask(row);
                    availableWidth = OverlayGeometry.LOGICAL_WIDTH - 26;
                    if (active.getStatus()
                        .isReadable() && logicalX >= 16
                        && font.getStringWidth(text) <= availableWidth) {
                        text = StatCollector.translateToLocal("tooltip.clipboardanywhere.edit_task");
                        alwaysShow = true;
                    }
                }
            }
        if (text == null || text.isEmpty() || !alwaysShow && font.getStringWidth(text) <= availableWidth) return;
        drawWrappedTooltip(font, text, screenWidth, screenHeight, mouseX, mouseY);
    }

    private static String shortcutTooltip(String translationKey, KeyBinding binding) {
        return StatCollector.translateToLocal(translationKey) + " ["
            + KeyBindingDisplay.getDisplayString(binding)
            + "]";
    }

    private static void drawWrappedTooltip(FontRenderer font, String text, int screenWidth, int screenHeight,
        int mouseX, int mouseY) {
        int wrapWidth = Math.max(40, Math.min(320, screenWidth - 12));
        List<String> lines = font.listFormattedStringToWidth(text, wrapWidth);
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.getStringWidth(line));
        }
        int height = lines.size() * 10 + 6;
        int x = mouseX + 8;
        if (x + width + 8 > screenWidth) x = mouseX - width - 8;
        x = clamp(x, 2, Math.max(2, screenWidth - width - 6));
        int y = clamp(mouseY + 8, 2, Math.max(2, screenHeight - height - 2));
        Gui.drawRect(x, y, x + width + 6, y + height, 0xF0101214);
        for (int index = 0; index < lines.size(); index++) {
            font.drawString(lines.get(index), x + 3, y + 3 + index * 10, 0xFFFFFFFF);
        }
    }

    private void drawCollapsedIcon(OverlayGeometry geometry) {
        int left = geometry.getCollapsedLeft();
        int top = geometry.getTop();
        if (ItemLoader.clippy == null) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LIGHTING_BIT
                | GL11.GL_TEXTURE_BIT);
        GL11.glPushMatrix();
        GL11.glTranslated(left, top, 0.0D);
        double iconScale = OverlayGeometry.COLLAPSED_SIZE / 16.0D;
        GL11.glScaled(iconScale, iconScale, 1.0D);
        ITEM_RENDERER.renderItemAndEffectIntoGUI(
            minecraft.fontRenderer,
            minecraft.getTextureManager(),
            new ItemStack(ItemLoader.clippy),
            0,
            0);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawOutline(int left, int top, int right, int bottom, int color) {
        color = fadeBackground(color);
        Gui.drawRect(left, top, right, top + 1, color);
        Gui.drawRect(left, bottom - 1, right, bottom, color);
        Gui.drawRect(left, top, left + 1, bottom, color);
        Gui.drawRect(right - 1, top, right, bottom, color);
    }

    private void drawPencilIcon(int x, int y, int color) {
        color = fade(color);
        for (int offset = 0; offset < 7; offset++) {
            Gui.drawRect(x + 3 + offset, y + 12 - offset, x + 5 + offset, y + 14 - offset, color);
        }
        Gui.drawRect(x + 2, y + 13, x + 4, y + 15, fade(0xFFE6C27A));
    }

    private void drawXIcon(int x, int y, int color) {
        color = fade(color);
        for (int offset = 0; offset < 7; offset++) {
            Gui.drawRect(x + 4 + offset, y + 5 + offset, x + 6 + offset, y + 7 + offset, color);
            Gui.drawRect(x + 10 - offset, y + 5 + offset, x + 12 - offset, y + 7 + offset, color);
        }
    }

    private void drawGearIcon(int x, int y, int color) {
        color = fade(color);
        Gui.drawRect(x + 4, y + 4, x + 9, y + 14, color);
        Gui.drawRect(x + 2, y + 6, x + 11, y + 12, color);
        Gui.drawRect(x + 1, y + 8, x + 12, y + 10, color);
        Gui.drawRect(x + 5, y + 7, x + 8, y + 11, fadeBackground(0xFF111111));
    }

    private void drawCollapseIcon(int x, int y, int color) {
        Gui.drawRect(x + 3, y + 11, x + 12, y + 13, fade(color));
    }

    private void drawOpacitySliders(FontRenderer font, int footerBackground, int taskTop, int logicalHeight) {
        int backgroundTop = taskTop + OverlayGeometry.ROW_HEIGHT * (ClipboardPageSnapshot.TASK_COUNT - 1);
        int footerTop = logicalHeight - OverlayGeometry.FOOTER_HEIGHT;
        Gui.drawRect(1, footerTop, OverlayGeometry.LOGICAL_WIDTH - 1, logicalHeight - 1, footerBackground);
        drawOpacitySlider(
            font,
            backgroundTop,
            OverlayGeometry.ROW_HEIGHT,
            StatCollector.translateToLocal("gui.clipboardanywhere.background_opacity"),
            layoutBackgroundOpacity);
        drawOpacitySlider(
            font,
            footerTop,
            OverlayGeometry.FOOTER_HEIGHT,
            StatCollector.translateToLocal("gui.clipboardanywhere.text_opacity"),
            layoutTextOpacity);
    }

    private void drawOpacitySlider(FontRenderer font, int top, int height, String label, double value) {
        int left = OPACITY_SLIDER_LEFT;
        int right = OverlayGeometry.LOGICAL_WIDTH - OPACITY_SLIDER_RIGHT_MARGIN;
        int centerY = top + height / 2;
        Gui.drawRect(left, centerY - 1, right, centerY + 1, fade(0xFF899398));
        int knobX = left + (int) Math
            .round((value - Config.MIN_OPACITY) / (Config.MAX_OPACITY - Config.MIN_OPACITY) * (right - left));
        Gui.drawRect(knobX - 2, centerY - 4, knobX + 3, centerY + 5, fade(0xFFE8ECEC));
        font.drawStringWithShadow(trim(font, label, OPACITY_SLIDER_LEFT - 8), 5, centerY - 4, fade(0xFFFFFFFF));
    }

    private void drawResizeHandle(int logicalHeight) {
        int right = OverlayGeometry.LOGICAL_WIDTH;
        int bottom = logicalHeight;
        int color = fade(0xFFE8ECEC);
        Gui.drawRect(right - 7, bottom - 2, right - 1, bottom - 1, color);
        Gui.drawRect(right - 4, bottom - 5, right - 1, bottom - 3, color);
        Gui.drawRect(right - 2, bottom - 8, right - 1, bottom - 6, color);
    }

    private void drawLayoutButtons(int screenWidth, int mouseX, int mouseY) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        Rect cancel = layoutCancelBounds(screenWidth);
        Rect confirm = layoutConfirmBounds(screenWidth);
        drawScreenButton(font, cancel, StatCollector.translateToLocal("gui.cancel"), cancel.contains(mouseX, mouseY));
        drawScreenButton(font, confirm, StatCollector.translateToLocal("gui.done"), confirm.contains(mouseX, mouseY));
    }

    private void drawScreenButton(FontRenderer font, Rect bounds, String text, boolean hovered) {
        Gui.drawRect(
            bounds.x,
            bounds.y,
            bounds.x + bounds.width,
            bounds.y + bounds.height,
            hovered ? 0xFF4A4A4A : 0xFF303030);
        Gui.drawRect(bounds.x + 1, bounds.y + 1, bounds.x + bounds.width - 1, bounds.y + bounds.height - 1, 0xFF111111);
        int x = bounds.x + (bounds.width - font.getStringWidth(text)) / 2;
        int y = bounds.y + (bounds.height - 8) / 2;
        font.drawStringWithShadow(text, x, y, 0xFFFFFFFF);
    }

    private void handleLayoutMousePressed(int screenWidth, int screenHeight, int mouseX, int mouseY, int button) {
        if (button != 0 || layoutGeometry == null) return;
        if (layoutCancelBounds(screenWidth).contains(mouseX, mouseY)) {
            cancelLayoutEdit();
            return;
        }
        if (layoutConfirmBounds(screenWidth).contains(mouseX, mouseY)) {
            confirmLayoutEdit(screenWidth, screenHeight);
            return;
        }
        int logicalX = layoutGeometry.toLogicalX(mouseX);
        int logicalY = layoutGeometry.toLogicalY(mouseY);
        if (logicalX < 0 || logicalX >= OverlayGeometry.LOGICAL_WIDTH
            || logicalY < 0
            || logicalY >= OverlayGeometry.LOGICAL_HEIGHT) return;
        if (logicalX >= OverlayGeometry.LOGICAL_WIDTH - 10 && logicalY >= OverlayGeometry.LOGICAL_HEIGHT - 10) {
            resizingLayout = true;
            resizeLeft = layoutGeometry.getLeft();
            resizeTop = layoutGeometry.getTop();
        } else if (logicalY < OverlayGeometry.HEADER_HEIGHT) {
            draggingLayout = true;
            dragRightOffset = layoutGeometry.getAnchorRight() - mouseX;
            dragTopOffset = layoutGeometry.getTop() - mouseY;
        } else if (OverlayGeometry.layoutOpacitySliderAt(logicalY, ClipboardPageSnapshot.TASK_COUNT) == 0) {
            adjustingBackgroundOpacity = true;
            updateLayoutOpacity(screenWidth, screenHeight, mouseX, false);
        } else if (OverlayGeometry.layoutOpacitySliderAt(logicalY, ClipboardPageSnapshot.TASK_COUNT) == 1) {
            adjustingTextOpacity = true;
            updateLayoutOpacity(screenWidth, screenHeight, mouseX, true);
        }
    }

    private void updateLayoutOpacity(int screenWidth, int screenHeight, int mouseX, boolean text) {
        OverlayGeometry geometry = geometry(screenWidth, screenHeight);
        int logicalX = geometry.toLogicalX(mouseX);
        int left = OPACITY_SLIDER_LEFT;
        int right = OverlayGeometry.LOGICAL_WIDTH - OPACITY_SLIDER_RIGHT_MARGIN;
        double ratio = Math.max(0.0D, Math.min(1.0D, (logicalX - left) / (double) (right - left)));
        double value = Config.MIN_OPACITY + ratio * (Config.MAX_OPACITY - Config.MIN_OPACITY);
        if (text) layoutTextOpacity = value;
        else layoutBackgroundOpacity = value;
    }

    private void clearLayoutDragState() {
        draggingLayout = false;
        resizingLayout = false;
        adjustingBackgroundOpacity = false;
        adjustingTextOpacity = false;
    }

    private static Rect layoutCancelBounds(int screenWidth) {
        return new Rect(screenWidth / 2 - 78, 8, 70, 20);
    }

    private static Rect layoutConfirmBounds(int screenWidth) {
        return new Rect(screenWidth / 2 + 8, 8, 70, 20);
    }

    private static Rect modalConfirmBounds() {
        int buttonWidth = (OverlayGeometry.LOGICAL_WIDTH - MODAL_MARGIN * 2 - MODAL_BUTTON_GAP) / 2;
        return new Rect(MODAL_MARGIN, 92, buttonWidth, 17);
    }

    private static Rect modalCancelBounds() {
        Rect confirm = modalConfirmBounds();
        return new Rect(confirm.x + confirm.width + MODAL_BUTTON_GAP, confirm.y, confirm.width, confirm.height);
    }

    private boolean handleRenameClick(int x, int y) {
        renameField.mouseClicked(x, y, 0);
        if (modalConfirmBounds().contains(x, y)) {
            String name = renameField.getText()
                .trim();
            if (!name.isEmpty()) NetworkHandler.sendToServer(new C2SRenameBinding(renameId, name));
            closeModal();
        } else if (modalCancelBounds().contains(x, y)) {
            closeModal();
        }
        return true;
    }

    private boolean handleTaskEditClick(int x, int y) {
        if (taskEditField == null) return true;
        if (isTaskEditFieldAt(x, y)) {
            taskEditField.mouseClicked(x, y, 0);
            return true;
        }

        int nextRow = taskTextRowAt(x, y, OverlayGeometry.HEADER_HEIGHT);
        if (nextRow >= 0 && nextRow != taskEditRow) submitTaskEdit(nextRow);
        else submitTaskEdit();
        return true;
    }

    private boolean isTaskEditFieldAt(int x, int y) {
        if (taskEditField == null) return false;
        int top = OverlayGeometry.HEADER_HEIGHT + taskEditRow * OverlayGeometry.ROW_HEIGHT + 1;
        return x >= 18 && x < OverlayGeometry.LOGICAL_WIDTH - 4 && y >= top && y < top + OverlayGeometry.ROW_HEIGHT - 2;
    }

    private boolean handleUnbindConfirmationClick(int x, int y) {
        if (modalConfirmBounds().contains(x, y)) {
            NetworkHandler.sendToServer(new C2SUnbind(pendingUnbindId));
            closeModal();
        } else if (modalCancelBounds().contains(x, y)) {
            closeModal();
        }
        return true;
    }

    private void beginTaskEdit(BindingView active, int row) {
        ClipboardPageSnapshot page = active.getSnapshot();
        taskEditId = active.getId();
        taskEditPage = page.getCurrentPage();
        taskEditRow = row;
        taskEditOriginalText = page.getTask(row);
        taskEditField = new GuiTextField(
            Minecraft.getMinecraft().fontRenderer,
            18,
            OverlayGeometry.HEADER_HEIGHT + row * OverlayGeometry.ROW_HEIGHT + 1,
            OverlayGeometry.LOGICAL_WIDTH - 22,
            OverlayGeometry.ROW_HEIGHT - 2);
        taskEditField.setMaxStringLength(NetworkCodec.MAX_EDIT_TASK_TEXT_CHARS);
        taskEditField.setText(taskEditOriginalText);
        taskEditField.setFocused(true);
        taskEditRepeatWasEnabled = Keyboard.areRepeatEventsEnabled();
        Keyboard.enableRepeatEvents(true);
        dropdownOpen = false;
    }

    private void submitTaskEdit() {
        submitTaskEdit(-1);
    }

    private void submitTaskEdit(int nextRow) {
        if (taskEditField == null) return;
        BindingView active = ClipboardClientState.INSTANCE.getSnapshot()
            .getActiveBinding();
        UUID editingId = taskEditId;
        int editingPage = taskEditPage;
        int editingRow = taskEditRow;
        String originalText = taskEditOriginalText;
        String replacementText = taskEditField.getText();
        closeModal();
        NetworkHandler
            .sendToServer(new C2SUpdateTaskText(editingId, editingPage, editingRow, originalText, replacementText));
        if (nextRow >= 0 && nextRow < ClipboardPageSnapshot.TASK_COUNT
            && active != null
            && active.getId()
                .equals(editingId)
            && active.getStatus()
                .isReadable()
            && active.getSnapshot()
                .getCurrentPage() == editingPage) {
            beginTaskEdit(active, nextRow);
        }
    }

    private static int taskTextRowAt(int x, int y, int taskStart) {
        if (x < 16 || x >= OverlayGeometry.LOGICAL_WIDTH
            || y < taskStart
            || y >= taskStart + OverlayGeometry.ROW_HEIGHT * ClipboardPageSnapshot.TASK_COUNT) return -1;
        return (y - taskStart) / OverlayGeometry.ROW_HEIGHT;
    }

    private void beginRename(BindingView active) {
        renameId = active.getId();
        renameField = new GuiTextField(
            Minecraft.getMinecraft().fontRenderer,
            MODAL_MARGIN + 3,
            69,
            OverlayGeometry.LOGICAL_WIDTH - (MODAL_MARGIN + 3) * 2,
            16);
        renameField.setMaxStringLength(32);
        renameField.setText(active.getDisplayName());
        renameField.setFocused(true);
        dropdownOpen = false;
    }

    private void closeModal() {
        if (renameField != null) renameField.setFocused(false);
        renameField = null;
        renameId = null;
        if (taskEditField != null) {
            taskEditField.setFocused(false);
            Keyboard.enableRepeatEvents(taskEditRepeatWasEnabled);
        }
        taskEditField = null;
        taskEditId = null;
        taskEditPage = 0;
        taskEditRow = -1;
        taskEditOriginalText = null;
        taskEditRepeatWasEnabled = false;
        pendingUnbindId = null;
    }

    private static String trim(FontRenderer font, String text, int width) {
        if (font.getStringWidth(text) <= width) return text;
        String ellipsis = "...";
        return font.trimStringToWidth(text, Math.max(0, width - font.getStringWidth(ellipsis))) + ellipsis;
    }

    private void drawCentered(FontRenderer font, String text, int y, int color) {
        font.drawStringWithShadow(
            text,
            (OverlayGeometry.LOGICAL_WIDTH - font.getStringWidth(text)) / 2,
            y,
            fade(color));
    }

    private void drawCenteredIn(FontRenderer font, String text, Rect bounds, int color) {
        int x = bounds.x + (bounds.width - font.getStringWidth(text)) / 2;
        int y = bounds.y + (bounds.height - 8) / 2;
        font.drawString(text, x, y, fade(color));
    }

    private int fade(int color) {
        return OverlayGeometry.multiplyAlpha(color, foregroundAlpha);
    }

    private int fadeBackground(int color) {
        return OverlayGeometry.multiplyAlpha(color, backgroundAlpha);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
