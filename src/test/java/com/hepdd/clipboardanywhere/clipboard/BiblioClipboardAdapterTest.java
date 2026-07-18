package com.hepdd.clipboardanywhere.clipboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

import com.hepdd.clipboardanywhere.model.ClipboardAction;
import com.hepdd.clipboardanywhere.model.ClipboardPageSnapshot;

import jds.bibliocraft.items.ItemClipboard;

public class BiblioClipboardAdapterTest {

    @Test
    public void readsAndMutatesOneBasedBiblioCraftPages() {
        ItemStack stack = new ItemStack(new ItemClipboard());
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("currentPage", 1);
        root.setInteger("totalPages", 2);
        root.setTag("page1", page("First", "Task one", 0));
        root.setTag("page2", page("Second", "Task two", 2));
        stack.setTagCompound(root);

        ClipboardPageSnapshot first = BiblioClipboardAdapter.read(stack, 5L);
        assertEquals(1, first.getCurrentPage());
        assertEquals("First", first.getTitle());
        assertEquals("Task one", first.getTask(0));

        assertTrue(BiblioClipboardAdapter.apply(stack, ClipboardAction.CYCLE_TASK, 0));
        assertEquals(
            1,
            BiblioClipboardAdapter.read(stack, 6L)
                .getTaskState(0));
        assertTrue(BiblioClipboardAdapter.apply(stack, ClipboardAction.NEXT_PAGE, -1));
        assertEquals(
            "Second",
            BiblioClipboardAdapter.read(stack, 7L)
                .getTitle());
        assertFalse(BiblioClipboardAdapter.apply(stack, ClipboardAction.NEXT_PAGE, -1));
        assertTrue(BiblioClipboardAdapter.apply(stack, ClipboardAction.PREVIOUS_PAGE, -1));
        assertFalse(BiblioClipboardAdapter.apply(stack, ClipboardAction.CYCLE_TASK, 9));
    }

    private static NBTTagCompound page(String title, String task, int state) {
        NBTTagCompound page = new NBTTagCompound();
        page.setString("title", title);
        int[] states = new int[ClipboardPageSnapshot.TASK_COUNT];
        states[0] = state;
        page.setIntArray("taskStates", states);
        NBTTagCompound tasks = new NBTTagCompound();
        tasks.setString("task1", task);
        page.setTag("tasks", tasks);
        return page;
    }
}
