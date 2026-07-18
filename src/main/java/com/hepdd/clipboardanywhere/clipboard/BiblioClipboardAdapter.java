package com.hepdd.clipboardanywhere.clipboard;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.hepdd.clipboardanywhere.model.ClipboardAction;
import com.hepdd.clipboardanywhere.model.ClipboardPageSnapshot;

import jds.bibliocraft.tileentities.TileEntityClipboard;

public final class BiblioClipboardAdapter {

    private BiblioClipboardAdapter() {}

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
        NBTTagCompound root = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        int totalPages = Math.max(1, root.getInteger("totalPages"));
        int currentPage = clamp(root.getInteger("currentPage"), 1, totalPages);
        switch (action) {
            case PREVIOUS_PAGE:
                if (currentPage <= 1) return false;
                root.setInteger("currentPage", currentPage - 1);
                break;
            case NEXT_PAGE:
                if (currentPage >= totalPages) return false;
                root.setInteger("currentPage", currentPage + 1);
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
        int selection;
        switch (action) {
            case PREVIOUS_PAGE:
                if (tile.currentPage <= 1) return false;
                selection = 10;
                break;
            case NEXT_PAGE:
                if (tile.currentPage >= Math.max(1, tile.totalPages)) return false;
                selection = 11;
                break;
            case CYCLE_TASK:
                if (row < 0 || row >= ClipboardPageSnapshot.TASK_COUNT) return false;
                selection = row;
                break;
            default:
                return false;
        }
        tile.updateClipboardFromPlayerSelection(selection);
        tile.markDirty();
        if (tile.getWorldObj() != null) {
            tile.getWorldObj()
                .markBlockForUpdate(tile.xCoord, tile.yCoord, tile.zCoord);
        }
        return true;
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
