package com.hepdd.clipboardanywhere.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

import com.hepdd.clipboardanywhere.model.ClipboardPageSnapshot;

public class ClipboardWorldDataTest {

    @Test
    public void roundTripsWorldRecordsAndKeepsPlayersIndependent() {
        UUID clipboardId = UUID.randomUUID();
        UUID firstPlayerId = UUID.randomUUID();
        UUID secondPlayerId = UUID.randomUUID();
        ClipboardPageSnapshot page = new ClipboardPageSnapshot(
            2,
            4,
            "Title",
            new String[] { "Task" },
            new int[] { 1 },
            90L);

        ClipboardWorldData source = new ClipboardWorldData("test");
        source.getOrCreateClipboard(clipboardId)
            .setPlacedTarget(7, 1, 2, 3, 100L);
        PlayerBindings first = source.getOrCreatePlayer(firstPlayerId);
        first.put(new PlayerBindingRecord(clipboardId, "First", 10L, 20L, page));
        PlayerBindings second = source.getOrCreatePlayer(secondPlayerId);
        second.put(new PlayerBindingRecord(clipboardId, "Second", 11L, 21L, page));

        NBTTagCompound tag = new NBTTagCompound();
        source.writeToNBT(tag);
        ClipboardWorldData restored = new ClipboardWorldData("test");
        restored.readFromNBT(tag);

        ClipboardRecord clipboard = restored.getClipboard(clipboardId);
        assertNotNull(clipboard);
        assertTrue(clipboard.hasPlacedTarget());
        assertEquals(7, clipboard.getDimension());
        assertEquals(
            "First",
            restored.getPlayer(firstPlayerId)
                .get(clipboardId)
                .getDisplayName());
        assertEquals(
            "Second",
            restored.getPlayer(secondPlayerId)
                .get(clipboardId)
                .getDisplayName());
        assertTrue(restored.isReferenced(clipboardId));

        restored.getPlayer(firstPlayerId)
            .remove(clipboardId);
        assertTrue(restored.isReferenced(clipboardId));
        restored.getPlayer(secondPlayerId)
            .remove(clipboardId);
        assertFalse(restored.isReferenced(clipboardId));
    }
}
