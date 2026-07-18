package com.hepdd.clipboardanywhere.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.UUID;

import org.junit.After;
import org.junit.Test;

import com.hepdd.clipboardanywhere.client.ClipboardClientState.Notice;
import com.hepdd.clipboardanywhere.model.BindingView;
import com.hepdd.clipboardanywhere.model.ClipboardPageSnapshot;
import com.hepdd.clipboardanywhere.model.PlayerBindingSnapshot;
import com.hepdd.clipboardanywhere.model.TargetStatus;

public class ClipboardClientStateTest {

    @After
    public void clearState() {
        ClipboardClientState.INSTANCE.clear();
    }

    @Test
    public void reportsAllDisconnectedOnlyOnStateTransition() {
        UUID id = UUID.randomUUID();
        ClipboardClientState.INSTANCE.update(snapshot(id, TargetStatus.READABLE_PLACED));
        assertNull(ClipboardClientState.INSTANCE.pollNotice());

        ClipboardClientState.INSTANCE.update(snapshot(id, TargetStatus.DISCONNECTED));
        Notice first = ClipboardClientState.INSTANCE.pollNotice();
        assertEquals("message.clipboardanywhere.all_disconnected", first.getTranslationKey());

        ClipboardClientState.INSTANCE.update(snapshot(id, TargetStatus.DISCONNECTED));
        assertNull(ClipboardClientState.INSTANCE.pollNotice());

        ClipboardClientState.INSTANCE.update(snapshot(id, TargetStatus.READABLE_INVENTORY));
        ClipboardClientState.INSTANCE.update(snapshot(id, TargetStatus.DISCONNECTED));
        assertEquals(
            "message.clipboardanywhere.all_disconnected",
            ClipboardClientState.INSTANCE.pollNotice()
                .getTranslationKey());
    }

    @Test
    public void emptyBindingListDoesNotCountAsDisconnected() {
        ClipboardClientState.INSTANCE.update(PlayerBindingSnapshot.EMPTY);
        assertNull(ClipboardClientState.INSTANCE.pollNotice());
    }

    private static PlayerBindingSnapshot snapshot(UUID id, TargetStatus status) {
        BindingView binding = new BindingView(id, "Test", status, ClipboardPageSnapshot.EMPTY);
        return new PlayerBindingSnapshot(id, Arrays.asList(binding));
    }
}
