package com.hepdd.clipboardanywhere.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

public class ClipboardPageSnapshotTest {

    @Test
    public void normalizesInvalidValuesAndRoundTripsNbt() {
        ClipboardPageSnapshot page = new ClipboardPageSnapshot(
            20,
            3,
            null,
            new String[] { "one", null },
            new int[] { 1, 9, 2 },
            -4L);

        assertEquals(3, page.getCurrentPage());
        assertEquals(3, page.getTotalPages());
        assertEquals("", page.getTitle());
        assertEquals("one", page.getTask(0));
        assertEquals("", page.getTask(1));
        assertEquals(1, page.getTaskState(0));
        assertEquals(0, page.getTaskState(1));
        assertEquals(2, page.getTaskState(2));
        assertEquals(0L, page.getCapturedAt());

        NBTTagCompound tag = page.writeToNbt();
        ClipboardPageSnapshot restored = ClipboardPageSnapshot.readFromNbt(tag);
        assertEquals(page, restored);
        assertArrayEquals(page.getTasks(), restored.getTasks());
        assertArrayEquals(page.getTaskStates(), restored.getTaskStates());
    }
}
