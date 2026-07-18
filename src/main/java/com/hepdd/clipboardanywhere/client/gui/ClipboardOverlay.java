package com.hepdd.clipboardanywhere.client.gui;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.hepdd.clipboardanywhere.Config;
import com.hepdd.clipboardanywhere.client.ClipboardClientState;
import com.hepdd.clipboardanywhere.model.BindingView;
import com.hepdd.clipboardanywhere.model.ClipboardAction;
import com.hepdd.clipboardanywhere.model.ClipboardPageSnapshot;
import com.hepdd.clipboardanywhere.model.PlayerBindingSnapshot;
import com.hepdd.clipboardanywhere.network.NetworkHandler;
import com.hepdd.clipboardanywhere.network.message.C2SClipboardAction;
import com.hepdd.clipboardanywhere.network.message.C2SRenameBinding;
import com.hepdd.clipboardanywhere.network.message.C2SSelectBinding;
import com.hepdd.clipboardanywhere.network.message.C2SUnbind;

import jds.bibliocraft.items.ItemLoader;

public final class ClipboardOverlay {

    public static final ClipboardOverlay INSTANCE = new ClipboardOverlay();
    private static final int DROPDOWN_WIDTH = 130;
    private static final int ICON_WIDTH = 15;
    private static final int MAX_DROPDOWN_ROWS = 8;
    private static final RenderItem ITEM_RENDERER = new RenderItem();

    private boolean dropdownOpen;
    private int dropdownOffset;
    private GuiTextField renameField;
    private UUID renameId;
    private UUID pendingUnbindId;
    private boolean layoutEditing;
    private OverlayGeometry layoutGeometry;
    private double layoutOpacity;
    private boolean draggingLayout;
    private boolean resizingLayout;
    private boolean adjustingOpacity;
    private int dragRightOffset;
    private int dragTopOffset;
    private int resizeLeft;
    private int resizeTop;

    private ClipboardOverlay() {}

    public boolean isAvailable() {
        PlayerBindingSnapshot snapshot = ClipboardClientState.INSTANCE.getSnapshot();
        return !snapshot.getBindings()
            .isEmpty() && !snapshot.isAllDisconnected();
    }

    public boolean isModalOpen() {
        return renameField != null || pendingUnbindId != null;
    }

    public OverlayGeometry geometry(int screenWidth, int screenHeight) {
        if (layoutEditing && layoutGeometry != null) {
            layoutGeometry.clampToScreen(screenWidth, screenHeight);
            return layoutGeometry;
        }
        OverlayGeometry geometry = new OverlayGeometry(
            Config.anchorRight,
            Config.anchorTop,
            Config.scale,
            screenWidth,
            screenHeight);
        if (Config.anchorRight != geometry.getAnchorRight() || Config.anchorTop != geometry.getTop()
            || Double.compare(Config.scale, geometry.getScale()) != 0) {
            Config.saveLayout(geometry.getAnchorRight(), geometry.getTop(), geometry.getScale(), Config.opacity);
        }
        return geometry;
    }

    public void render(int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!isAvailable()) return;
        OverlayGeometry geometry = geometry(screenWidth, screenHeight);
        if (layoutEditing) {
            Gui.drawRect(0, 0, screenWidth, screenHeight, 0x884A4A4A);
        }
        if (Config.collapsed && !layoutEditing) {
            drawCollapsedIcon(geometry);
            return;
        }
        BindingView active = ClipboardClientState.INSTANCE.getSnapshot()
            .getActiveBinding();
        if (active == null) return;

        int logicalMouseX = geometry.toLogicalX(mouseX);
        int logicalMouseY = geometry.toLogicalY(mouseY);
        GL11.glPushMatrix();
        GL11.glTranslated(geometry.getLeft(), geometry.getTop(), 0.0D);
        GL11.glScaled(geometry.getScale(), geometry.getScale(), 1.0D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawExpanded(active, logicalMouseX, logicalMouseY);
        GL11.glPopMatrix();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        if (layoutEditing) drawLayoutButtons(screenWidth, mouseX, mouseY);
    }

    public boolean mouseClicked(int screenWidth, int screenHeight, int mouseX, int mouseY, int button) {
        if (!isAvailable()) return false;
        if (layoutEditing) {
            handleLayoutMousePressed(screenWidth, screenHeight, mouseX, mouseY, button);
            return true;
        }
        if (Config.collapsed || button != 0) return false;
        OverlayGeometry geometry = geometry(screenWidth, screenHeight);
        int x = geometry.toLogicalX(mouseX);
        int y = geometry.toLogicalY(mouseY);
        if (x < 0 || x >= OverlayGeometry.LOGICAL_WIDTH || y < 0 || y >= OverlayGeometry.LOGICAL_HEIGHT) {
            if (dropdownOpen) {
                dropdownOpen = false;
                return true;
            }
            return false;
        }

        if (renameField != null) return handleRenameClick(x, y);
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
                beginRename(active);
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
        }

        if (!active.getStatus()
            .isReadable()) return true;
        int taskStart = OverlayGeometry.HEADER_HEIGHT + OverlayGeometry.TITLE_HEIGHT;
        if (y >= taskStart && y < taskStart + OverlayGeometry.ROW_HEIGHT * 9) {
            int row = (y - taskStart) / OverlayGeometry.ROW_HEIGHT;
            NetworkHandler.sendToServer(new C2SClipboardAction(active.getId(), ClipboardAction.CYCLE_TASK, row));
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
        if (layoutEditing) return wheelDelta != 0;
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

    public void toggleCollapsed() {
        if (!isAvailable() || layoutEditing) return;
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
    }

    public boolean isLayoutEditing() {
        return layoutEditing;
    }

    public void beginLayoutEdit(int screenWidth, int screenHeight) {
        if (!isAvailable() || Config.collapsed || layoutEditing) return;
        layoutGeometry = new OverlayGeometry(
            Config.anchorRight,
            Config.anchorTop,
            Config.scale,
            screenWidth,
            screenHeight);
        layoutOpacity = Config.opacity;
        layoutEditing = true;
        dropdownOpen = false;
        closeModal();
        clearLayoutDragState();
    }

    public void cancelLayoutEdit() {
        layoutEditing = false;
        layoutGeometry = null;
        layoutOpacity = Config.opacity;
        clearLayoutDragState();
    }

    public void confirmLayoutEdit() {
        if (!layoutEditing || layoutGeometry == null) return;
        Config.saveLayout(
            layoutGeometry.getAnchorRight(),
            layoutGeometry.getTop(),
            layoutGeometry.getScale(),
            layoutOpacity);
        cancelLayoutEdit();
    }

    public boolean mouseDragged(int screenWidth, int screenHeight, int mouseX, int mouseY, int button) {
        if (!layoutEditing || button != 0 || layoutGeometry == null) return layoutEditing;
        if (draggingLayout) {
            layoutGeometry.setPosition(mouseX + dragRightOffset, mouseY + dragTopOffset, screenWidth, screenHeight);
        } else if (resizingLayout) {
            double widthScale = (mouseX - resizeLeft) / (double) OverlayGeometry.LOGICAL_WIDTH;
            double heightScale = (mouseY - resizeTop) / (double) OverlayGeometry.LOGICAL_HEIGHT;
            layoutGeometry
                .resizeFromTopLeft(resizeLeft, resizeTop, Math.max(widthScale, heightScale), screenWidth, screenHeight);
        } else if (adjustingOpacity) {
            updateLayoutOpacity(screenWidth, screenHeight, mouseX);
        }
        return true;
    }

    public boolean mouseReleased(int button) {
        if (!layoutEditing) return false;
        if (button == 0) clearLayoutDragState();
        return true;
    }

    private void drawExpanded(BindingView active, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer font = minecraft.fontRenderer;
        double opacity = layoutEditing ? layoutOpacity : Config.opacity;
        int alpha = (int) (opacity * 255.0D) << 24;
        int panel = alpha | 0x20252A;
        int header = alpha | 0x353D43;
        int border = ((int) (Math.min(1.0D, opacity + 0.15D) * 255.0D) << 24) | 0x9AA4A8;
        Gui.drawRect(0, 0, OverlayGeometry.LOGICAL_WIDTH, OverlayGeometry.LOGICAL_HEIGHT, border);
        Gui.drawRect(1, 1, OverlayGeometry.LOGICAL_WIDTH - 1, OverlayGeometry.LOGICAL_HEIGHT - 1, panel);
        Gui.drawRect(1, 1, OverlayGeometry.LOGICAL_WIDTH - 1, OverlayGeometry.HEADER_HEIGHT, header);

        drawHeader(font, active, mouseX, mouseY);
        ClipboardPageSnapshot page = active.getSnapshot();
        String title = trim(
            font,
            page.getTitle()
                .isEmpty() ? " " : page.getTitle(),
            172);
        drawCentered(font, title, OverlayGeometry.HEADER_HEIGHT + 4, 0xFFF1E4B8);
        int taskTop = OverlayGeometry.HEADER_HEIGHT + OverlayGeometry.TITLE_HEIGHT;
        for (int row = 0; row < ClipboardPageSnapshot.TASK_COUNT; row++) {
            int y = taskTop + row * OverlayGeometry.ROW_HEIGHT;
            if ((row & 1) == 1)
                Gui.drawRect(2, y, OverlayGeometry.LOGICAL_WIDTH - 2, y + OverlayGeometry.ROW_HEIGHT, 0x182A3136);
            drawTask(
                font,
                page,
                row,
                y,
                active.getStatus()
                    .isReadable());
        }
        if (layoutEditing) {
            drawOpacitySlider(font);
            drawResizeHandle();
        } else {
            drawFooter(font, active, page);
        }

        if (!layoutEditing) {
            if (dropdownOpen) drawDropdown(font, mouseX, mouseY);
            if (renameField != null) drawRenameModal(font);
            if (pendingUnbindId != null) drawUnbindModal(font);
            if (!dropdownOpen && !isModalOpen()) drawHeaderTooltip(font, mouseX, mouseY);
        }
    }

    private void drawHeader(FontRenderer font, BindingView active, int mouseX, int mouseY) {
        boolean readable = active.getStatus()
            .isReadable();
        String suffix = readable ? "" : " [!]";
        font.drawStringWithShadow(trim(font, active.getDisplayName() + suffix, DROPDOWN_WIDTH - 14), 5, 5, 0xFFFFFFFF);
        if (layoutEditing) {
            String label = StatCollector.translateToLocal("gui.clipboardanywhere.drag_title");
            font.drawStringWithShadow(
                trim(font, label, OverlayGeometry.LOGICAL_WIDTH - DROPDOWN_WIDTH - 8),
                DROPDOWN_WIDTH,
                5,
                0xFFD8E0E3);
            return;
        }
        drawDropdownArrow(DROPDOWN_WIDTH - 9, 7, 0xFFD8E0E3);
        drawPencilIcon(DROPDOWN_WIDTH, 0, 0xFFFFFFFF);
        drawXIcon(DROPDOWN_WIDTH + ICON_WIDTH, 0, 0xFFFFB2B2);
        drawGearIcon(OverlayGeometry.LOGICAL_WIDTH - ICON_WIDTH * 2, 0, 0xFFD6E5EA);
        drawCollapseIcon(OverlayGeometry.LOGICAL_WIDTH - ICON_WIDTH, 0, 0xFFFFFFFF);
    }

    private void drawTask(FontRenderer font, ClipboardPageSnapshot page, int row, int y, boolean enabled) {
        int state = page.getTaskState(row);
        int boxColor = enabled ? 0xFFCAD1D3 : 0xFF777C7E;
        Gui.drawRect(6, y + 3, 14, y + 11, boxColor);
        Gui.drawRect(7, y + 4, 13, y + 10, 0xFF252B2E);
        if (state == 1) font.drawString("v", 7, y + 1, enabled ? 0xFF79D88C : 0xFF777C7E);
        if (state == 2) font.drawString("x", 7, y + 1, enabled ? 0xFFE77878 : 0xFF777C7E);
        int textColor = enabled ? 0xFFE8ECEC : 0xFF8C9294;
        font.drawString(trim(font, page.getTask(row), 164), 20, y + 3, textColor);
    }

    private void drawFooter(FontRenderer font, BindingView active, ClipboardPageSnapshot page) {
        int y = OverlayGeometry.LOGICAL_HEIGHT - OverlayGeometry.FOOTER_HEIGHT;
        Gui.drawRect(1, y, OverlayGeometry.LOGICAL_WIDTH - 1, OverlayGeometry.LOGICAL_HEIGHT - 1, 0x70353D43);
        int color = active.getStatus()
            .isReadable() ? 0xFFFFFFFF : 0xFF777C7E;
        font.drawStringWithShadow("<", 9, y + 5, color);
        font.drawStringWithShadow(">", OverlayGeometry.LOGICAL_WIDTH - 14, y + 5, color);
        String pageNumber = page.getCurrentPage() + " / " + page.getTotalPages();
        drawCentered(font, pageNumber, y + 5, color);
        if (!active.getStatus()
            .isReadable()) {
            font.drawStringWithShadow(
                StatCollector.translateToLocal("status.clipboardanywhere.disconnected"),
                31,
                y + 5,
                0xFFFFC66D);
        }
    }

    private void drawDropdown(FontRenderer font, int mouseX, int mouseY) {
        List<BindingView> bindings = ClipboardClientState.INSTANCE.getSnapshot()
            .getBindings();
        dropdownOffset = clamp(dropdownOffset, 0, Math.max(0, bindings.size() - MAX_DROPDOWN_ROWS));
        int visibleRows = Math.min(MAX_DROPDOWN_ROWS, bindings.size() - dropdownOffset);
        int bottom = OverlayGeometry.HEADER_HEIGHT + visibleRows * OverlayGeometry.ROW_HEIGHT;
        Gui.drawRect(1, OverlayGeometry.HEADER_HEIGHT, DROPDOWN_WIDTH, bottom, 0xF0283035);
        for (int row = 0; row < visibleRows; row++) {
            BindingView binding = bindings.get(dropdownOffset + row);
            int y = OverlayGeometry.HEADER_HEIGHT + row * OverlayGeometry.ROW_HEIGHT;
            if (mouseX >= 1 && mouseX < DROPDOWN_WIDTH && mouseY >= y && mouseY < y + OverlayGeometry.ROW_HEIGHT) {
                Gui.drawRect(2, y, DROPDOWN_WIDTH - 1, y + OverlayGeometry.ROW_HEIGHT, 0xFF4B5A62);
            }
            int color = binding.getStatus()
                .isReadable() ? 0xFFFFFFFF : 0xFF9B9FA1;
            String label = binding.getDisplayName() + (binding.getStatus()
                .isReadable() ? "" : " [!]");
            font.drawString(trim(font, label, DROPDOWN_WIDTH - 10), 5, y + 3, color);
        }
    }

    private void drawRenameModal(FontRenderer font) {
        drawModalFrame();
        drawCentered(font, StatCollector.translateToLocal("gui.clipboardanywhere.rename"), 48, 0xFFFFFFFF);
        renameField.drawTextBox();
        drawModalButtons(
            font,
            StatCollector.translateToLocal("gui.done"),
            StatCollector.translateToLocal("gui.cancel"));
    }

    private void drawUnbindModal(FontRenderer font) {
        drawModalFrame();
        drawCentered(font, StatCollector.translateToLocal("gui.clipboardanywhere.unbind_confirm"), 48, 0xFFFFD0D0);
        drawCentered(
            font,
            StatCollector.translateToLocal("gui.clipboardanywhere.unbind_keeps_content"),
            62,
            0xFFCED4D6);
        drawModalButtons(font, StatCollector.translateToLocal("gui.yes"), StatCollector.translateToLocal("gui.no"));
    }

    private void drawModalFrame() {
        Gui.drawRect(15, 38, OverlayGeometry.LOGICAL_WIDTH - 15, 116, 0xF01B2023);
        Gui.drawRect(16, 39, OverlayGeometry.LOGICAL_WIDTH - 16, 115, 0xF03A4449);
    }

    private void drawModalButtons(FontRenderer font, String confirm, String cancel) {
        Gui.drawRect(28, 92, 88, 109, 0xFF54666E);
        Gui.drawRect(102, 92, 162, 109, 0xFF4A555A);
        drawCenteredIn(font, confirm, new Rect(28, 92, 60, 17), 0xFFFFFFFF);
        drawCenteredIn(font, cancel, new Rect(102, 92, 60, 17), 0xFFFFFFFF);
    }

    private void drawHeaderTooltip(FontRenderer font, int mouseX, int mouseY) {
        if (mouseY < 0 || mouseY >= OverlayGeometry.HEADER_HEIGHT) return;
        String key = null;
        if (mouseX >= DROPDOWN_WIDTH && mouseX < DROPDOWN_WIDTH + ICON_WIDTH) {
            key = "tooltip.clipboardanywhere.rename";
        } else if (mouseX < DROPDOWN_WIDTH + ICON_WIDTH * 2 && mouseX >= DROPDOWN_WIDTH + ICON_WIDTH) {
            key = "tooltip.clipboardanywhere.unbind";
        } else if (mouseX >= OverlayGeometry.LOGICAL_WIDTH - ICON_WIDTH * 2
            && mouseX < OverlayGeometry.LOGICAL_WIDTH - ICON_WIDTH) {
                key = "tooltip.clipboardanywhere.layout";
            } else if (mouseX >= OverlayGeometry.LOGICAL_WIDTH - ICON_WIDTH) {
                key = "tooltip.clipboardanywhere.collapse";
            }
        if (key == null) return;
        String text = StatCollector.translateToLocal(key);
        int width = font.getStringWidth(text) + 8;
        int x = Math.max(2, Math.min(mouseX, OverlayGeometry.LOGICAL_WIDTH - width - 2));
        Gui.drawRect(x, 20, x + width, 34, 0xE0101214);
        font.drawString(text, x + 4, 23, 0xFFFFFFFF);
    }

    private void drawCollapsedIcon(OverlayGeometry geometry) {
        int left = geometry.getCollapsedLeft();
        int top = geometry.getTop();
        if (ItemLoader.clippy == null) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        GL11.glPushMatrix();
        GL11.glTranslated(left, top, 0.0D);
        GL11.glScaled(OverlayGeometry.COLLAPSED_SIZE / 16.0D, OverlayGeometry.COLLAPSED_SIZE / 16.0D, 1.0D);
        ITEM_RENDERER.renderItemAndEffectIntoGUI(
            minecraft.fontRenderer,
            minecraft.getTextureManager(),
            new ItemStack(ItemLoader.clippy),
            0,
            0);
        GL11.glPopMatrix();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawDropdownArrow(int x, int y, int color) {
        Gui.drawRect(x, y, x + 7, y + 1, color);
        Gui.drawRect(x + 1, y + 1, x + 6, y + 2, color);
        Gui.drawRect(x + 2, y + 2, x + 5, y + 3, color);
        Gui.drawRect(x + 3, y + 3, x + 4, y + 4, color);
    }

    private static void drawPencilIcon(int x, int y, int color) {
        for (int offset = 0; offset < 7; offset++) {
            Gui.drawRect(x + 3 + offset, y + 12 - offset, x + 5 + offset, y + 14 - offset, color);
        }
        Gui.drawRect(x + 2, y + 13, x + 4, y + 15, 0xFFE6C27A);
    }

    private static void drawXIcon(int x, int y, int color) {
        for (int offset = 0; offset < 7; offset++) {
            Gui.drawRect(x + 4 + offset, y + 5 + offset, x + 6 + offset, y + 7 + offset, color);
            Gui.drawRect(x + 10 - offset, y + 5 + offset, x + 12 - offset, y + 7 + offset, color);
        }
    }

    private static void drawGearIcon(int x, int y, int color) {
        Gui.drawRect(x + 5, y + 4, x + 10, y + 14, color);
        Gui.drawRect(x + 3, y + 6, x + 12, y + 12, color);
        Gui.drawRect(x + 2, y + 8, x + 13, y + 10, color);
        Gui.drawRect(x + 6, y + 7, x + 9, y + 11, 0xFF353D43);
    }

    private static void drawCollapseIcon(int x, int y, int color) {
        Gui.drawRect(x + 3, y + 11, x + 12, y + 13, color);
    }

    private void drawOpacitySlider(FontRenderer font) {
        int footerTop = OverlayGeometry.LOGICAL_HEIGHT - OverlayGeometry.FOOTER_HEIGHT;
        Gui.drawRect(1, footerTop, OverlayGeometry.LOGICAL_WIDTH - 1, OverlayGeometry.LOGICAL_HEIGHT - 1, 0xA0353D43);
        int left = 34;
        int right = OverlayGeometry.LOGICAL_WIDTH - 12;
        int centerY = footerTop + 9;
        Gui.drawRect(left, centerY - 1, right, centerY + 1, 0xFF899398);
        int knobX = left + (int) Math.round((layoutOpacity - 0.25D) / 0.75D * (right - left));
        Gui.drawRect(knobX - 2, centerY - 5, knobX + 3, centerY + 6, 0xFFE8ECEC);
        font.drawStringWithShadow(
            StatCollector.translateToLocal("gui.clipboardanywhere.opacity"),
            5,
            footerTop + 5,
            0xFFFFFFFF);
    }

    private void drawResizeHandle() {
        int right = OverlayGeometry.LOGICAL_WIDTH;
        int bottom = OverlayGeometry.LOGICAL_HEIGHT;
        Gui.drawRect(right - 7, bottom - 2, right - 1, bottom - 1, 0xFFE8ECEC);
        Gui.drawRect(right - 4, bottom - 5, right - 1, bottom - 3, 0xFFE8ECEC);
        Gui.drawRect(right - 2, bottom - 8, right - 1, bottom - 6, 0xFFE8ECEC);
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
            hovered ? 0xFF657982 : 0xFF46555B);
        Gui.drawRect(bounds.x + 1, bounds.y + 1, bounds.x + bounds.width - 1, bounds.y + bounds.height - 1, 0xFF283136);
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
            confirmLayoutEdit();
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
        } else if (logicalY >= OverlayGeometry.LOGICAL_HEIGHT - OverlayGeometry.FOOTER_HEIGHT) {
            adjustingOpacity = true;
            updateLayoutOpacity(screenWidth, screenHeight, mouseX);
        }
    }

    private void updateLayoutOpacity(int screenWidth, int screenHeight, int mouseX) {
        OverlayGeometry geometry = geometry(screenWidth, screenHeight);
        int logicalX = geometry.toLogicalX(mouseX);
        int left = 34;
        int right = OverlayGeometry.LOGICAL_WIDTH - 12;
        double ratio = Math.max(0.0D, Math.min(1.0D, (logicalX - left) / (double) (right - left)));
        layoutOpacity = 0.25D + ratio * 0.75D;
    }

    private void clearLayoutDragState() {
        draggingLayout = false;
        resizingLayout = false;
        adjustingOpacity = false;
    }

    private static Rect layoutCancelBounds(int screenWidth) {
        return new Rect(screenWidth / 2 - 78, 8, 70, 20);
    }

    private static Rect layoutConfirmBounds(int screenWidth) {
        return new Rect(screenWidth / 2 + 8, 8, 70, 20);
    }

    private boolean handleRenameClick(int x, int y) {
        renameField.mouseClicked(x, y, 0);
        if (new Rect(28, 92, 60, 17).contains(x, y)) {
            String name = renameField.getText()
                .trim();
            if (!name.isEmpty()) NetworkHandler.sendToServer(new C2SRenameBinding(renameId, name));
            closeModal();
        } else if (new Rect(102, 92, 60, 17).contains(x, y)) {
            closeModal();
        }
        return true;
    }

    private boolean handleUnbindConfirmationClick(int x, int y) {
        if (new Rect(28, 92, 60, 17).contains(x, y)) {
            NetworkHandler.sendToServer(new C2SUnbind(pendingUnbindId));
            closeModal();
        } else if (new Rect(102, 92, 60, 17).contains(x, y)) {
            closeModal();
        }
        return true;
    }

    private void beginRename(BindingView active) {
        renameId = active.getId();
        renameField = new GuiTextField(Minecraft.getMinecraft().fontRenderer, 28, 69, 134, 16);
        renameField.setMaxStringLength(32);
        renameField.setText(active.getDisplayName());
        renameField.setFocused(true);
        dropdownOpen = false;
    }

    private void closeModal() {
        renameField = null;
        renameId = null;
        pendingUnbindId = null;
    }

    private static String trim(FontRenderer font, String text, int width) {
        if (font.getStringWidth(text) <= width) return text;
        String ellipsis = "...";
        return font.trimStringToWidth(text, Math.max(0, width - font.getStringWidth(ellipsis))) + ellipsis;
    }

    private static void drawCentered(FontRenderer font, String text, int y, int color) {
        font.drawStringWithShadow(text, (OverlayGeometry.LOGICAL_WIDTH - font.getStringWidth(text)) / 2, y, color);
    }

    private static void drawCenteredIn(FontRenderer font, String text, Rect bounds, int color) {
        int x = bounds.x + (bounds.width - font.getStringWidth(text)) / 2;
        int y = bounds.y + (bounds.height - 8) / 2;
        font.drawString(text, x, y, color);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
