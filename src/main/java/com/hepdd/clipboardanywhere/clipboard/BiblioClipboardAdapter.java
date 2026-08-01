package com.hepdd.clipboardanywhere.clipboard;

import java.util.Arrays;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.hepdd.clipboardanywhere.model.ClipboardAction;
import com.hepdd.clipboardanywhere.model.ClipboardPageSnapshot;

import jds.bibliocraft.tileentities.TileEntityClipboard;

public final class BiblioClipboardAdapter {

    private BiblioClipboardAdapter() {}

    public static boolean ensureStructure(ItemStack stack) {
        if (!ClipboardIdentity.isClipboard(stack)) return false;

        boolean changed = !stack.hasTagCompound();
        NBTTagCompound root = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        int totalPages = Math.max(1, root.getInteger("totalPages"));
        int currentPage = clamp(root.getInteger("currentPage"), 1, totalPages);
        if (!root.hasKey("totalPages") || root.getInteger("totalPages") != totalPages) {
            root.setInteger("totalPages", totalPages);
            changed = true;
        }
        if (!root.hasKey("currentPage") || root.getInteger("currentPage") != currentPage) {
            root.setInteger("currentPage", currentPage);
            changed = true;
        }
        changed |= ensurePage(root, currentPage);
        stack.setTagCompound(root);
        return changed;
    }

    public static ClipboardPageSnapshot read(ItemStack stack, long capturedAt) {
        if (!ClipboardIdentity.isClipboard(stack) || !stack.hasTagCompound()) {
            return ClipboardPageSnapshot.EMPTY;
        }
        NBTTagCompound root = stack.getTagCompound();
        int totalPages = Math.max(1, root.getInteger("totalPages"));
        int currentPage = clamp(root.getInteger("currentPage"), 1, totalPages);
        NBTTagCompound page = root.getCompoundTag("page" + currentPage);
        NBTTagCompound taskTag = page.getCompoundTag("tasks");
        String[] tasks = new String[ClipboardPageSnapshot.TASK_COUNT];
        for (int row = 0; row < tasks.length; row++) {
            tasks[row] = taskTag.getString("task" + (row + 1));
        }
        return new ClipboardPageSnapshot(
            currentPage,
            totalPages,
            page.getString("title"),
            tasks,
            page.getIntArray("taskStates"),
            capturedAt);
    }

    public static ClipboardPageSnapshot read(TileEntityClipboard tile, long capturedAt) {
        if (tile == null || !ClipboardIdentity.isClipboard(tile.getStackInSlot(0))) {
            return ClipboardPageSnapshot.EMPTY;
        }
        String[] tasks = { tile.button0text, tile.button1text, tile.button2text, tile.button3text, tile.button4text,
            tile.button5text, tile.button6text, tile.button7text, tile.button8text };
        int[] states = { tile.button0state, tile.button1state, tile.button2state, tile.button3state, tile.button4state,
            tile.button5state, tile.button6state, tile.button7state, tile.button8state };
        return new ClipboardPageSnapshot(tile.currentPage, tile.totalPages, tile.titletext, tasks, states, capturedAt);
    }

    public static boolean apply(ItemStack stack, ClipboardAction action, int row) {
        if (!ClipboardIdentity.isClipboard(stack) || action == null) {
            return false;
        }
        ensureStructure(stack);
        NBTTagCompound root = stack.getTagCompound();
        int totalPages = Math.max(1, root.getInteger("totalPages"));
        int currentPage = clamp(root.getInteger("currentPage"), 1, totalPages);
        switch (action) {
            case PREVIOUS_PAGE:
                if (currentPage <= 1) return false;
                ensurePage(root, currentPage - 1);
                root.setInteger("currentPage", currentPage - 1);
                break;
            case NEXT_PAGE:
                int nextPage = currentPage + 1;
                ensurePage(root, nextPage);
                root.setInteger("currentPage", nextPage);
                if (nextPage > totalPages) root.setInteger("totalPages", nextPage);
                break;
            case CYCLE_TASK:
                if (row < 0 || row >= ClipboardPageSnapshot.TASK_COUNT) return false;
                NBTTagCompound page = root.getCompoundTag("page" + currentPage);
                int[] states = normalizeStates(page.getIntArray("taskStates"));
                states[row] = (states[row] + 1) % 3;
                page.setIntArray("taskStates", states);
                root.setTag("page" + currentPage, page);
                break;
            default:
                return false;
        }
        stack.setTagCompound(root);
        return true;
    }

    public static boolean apply(TileEntityClipboard tile, ClipboardAction action, int row) {
        if (tile == null || action == null || !ClipboardIdentity.isClipboard(tile.getStackInSlot(0))) {
            return false;
        }
        ItemStack stack = tile.getStackInSlot(0);
        if (!apply(stack, action, row)) return false;
        synchronizeTile(tile);
        return true;
    }

    public static boolean updateTaskText(ItemStack stack, int pageNumber, int row, String expectedText,
        String replacementText) {
        if (!ClipboardIdentity.isClipboard(stack) || row < 0 || row >= ClipboardPageSnapshot.TASK_COUNT) {
            return false;
        }
        ensureStructure(stack);
        NBTTagCompound root = stack.getTagCompound();
        int totalPages = Math.max(1, root.getInteger("totalPages"));
        if (pageNumber < 1 || pageNumber > totalPages) return false;
        ensurePage(root, pageNumber);
        NBTTagCompound page = root.getCompoundTag("page" + pageNumber);
        NBTTagCompound tasks = page.getCompoundTag("tasks");
        String key = "task" + (row + 1);
        String currentText = tasks.getString(key);
        if (expectedText == null || !currentText.equals(expectedText)) return false;
        String value = replacementText == null ? "" : replacementText;
        if (currentText.equals(value)) return true;
        tasks.setString(key, value);
        page.setTag("tasks", tasks);
        root.setTag("page" + pageNumber, page);
        stack.setTagCompound(root);
        return true;
    }

    public static boolean updateTaskText(TileEntityClipboard tile, int pageNumber, int row, String expectedText,
        String replacementText) {
        if (tile == null || !ClipboardIdentity.isClipboard(tile.getStackInSlot(0))) return false;
        ItemStack stack = tile.getStackInSlot(0);
        if (!updateTaskText(stack, pageNumber, row, expectedText, replacementText)) return false;
        synchronizeTile(tile);
        return true;
    }

    private static void synchronizeTile(TileEntityClipboard tile) {
        tile.getNBTData();
        tile.markDirty();
        if (tile.getWorldObj() != null) {
            tile.getWorldObj()
                .markBlockForUpdate(tile.xCoord, tile.yCoord, tile.zCoord);
        }
    }

    private static boolean ensurePage(NBTTagCompound root, int pageNumber) {
        String pageKey = "page" + pageNumber;
        boolean changed = !root.hasKey(pageKey);
        NBTTagCompound page = root.getCompoundTag(pageKey);

        int[] originalStates = page.getIntArray("taskStates");
        int[] states = normalizeStates(originalStates);
        if (!Arrays.equals(originalStates, states)) {
            page.setIntArray("taskStates", states);
            changed = true;
        }

        boolean hadTasks = page.hasKey("tasks");
        NBTTagCompound tasks = page.getCompoundTag("tasks");
        for (int row = 1; row <= ClipboardPageSnapshot.TASK_COUNT; row++) {
            String key = "task" + row;
            if (!tasks.hasKey(key)) {
                tasks.setString(key, "");
                changed = true;
            }
        }
        if (!hadTasks) changed = true;
        page.setTag("tasks", tasks);
        if (!page.hasKey("title")) {
            page.setString("title", "");
            changed = true;
        }
        root.setTag(pageKey, page);
        return changed;
    }

    private static int[] normalizeStates(int[] source) {
        int[] result = new int[ClipboardPageSnapshot.TASK_COUNT];
        for (int row = 0; row < result.length; row++) {
            int value = source != null && row < source.length ? source[row] : 0;
            result[row] = value >= 0 && value <= 2 ? value : 0;
        }
        return result;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
