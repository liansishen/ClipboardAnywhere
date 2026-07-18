package com.hepdd.clipboardanywhere.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;

import com.hepdd.clipboardanywhere.data.PlayerBindingRecord;
import com.hepdd.clipboardanywhere.data.PlayerBindings;
import com.hepdd.clipboardanywhere.model.ClipboardPageSnapshot;
import com.hepdd.clipboardanywhere.model.TargetStatus;

public class ClipboardServerServiceTest {

    @Test
    public void sanitizesFormattingControlCharactersAndLength() {
        String longName = "\u00a7aGreen\nName 1234567890123456789012345678901234567890";
        String result = ClipboardServerService.sanitizeName(longName);

        assertEquals("GreenName 1234567890123456789012", result);
        assertEquals(32, result.length());
    }

    @Test
    public void fallsBackWhenActiveClipboardDisconnectsNaturally() {
        BindingFixture fixture = new BindingFixture();
        fixture.statuses.put(fixture.firstId, TargetStatus.DISCONNECTED);
        fixture.statuses.put(fixture.secondId, TargetStatus.READABLE_INVENTORY);

        assertEquals(
            fixture.secondId,
            ClipboardServerService.reconcileActiveBinding(fixture.bindings, fixture.statuses, fixture.secondId));
        assertEquals(fixture.secondId, fixture.bindings.getActiveId());
        assertFalse(fixture.bindings.isKeepDisconnectedActive());
    }

    @Test
    public void keepsManuallySelectedDisconnectedClipboardUntilItReconnects() {
        BindingFixture fixture = new BindingFixture();
        fixture.bindings.setKeepDisconnectedActive(true);
        fixture.statuses.put(fixture.firstId, TargetStatus.DISCONNECTED);
        fixture.statuses.put(fixture.secondId, TargetStatus.READABLE_INVENTORY);

        assertEquals(
            fixture.firstId,
            ClipboardServerService.reconcileActiveBinding(fixture.bindings, fixture.statuses, fixture.secondId));
        assertTrue(fixture.bindings.isKeepDisconnectedActive());

        fixture.statuses.put(fixture.firstId, TargetStatus.READABLE_PLACED);
        assertEquals(
            fixture.firstId,
            ClipboardServerService.reconcileActiveBinding(fixture.bindings, fixture.statuses, fixture.firstId));
        assertFalse(fixture.bindings.isKeepDisconnectedActive());
    }

    @Test
    public void keepsCurrentSelectionWhenEveryClipboardIsDisconnected() {
        BindingFixture fixture = new BindingFixture();
        fixture.statuses.put(fixture.firstId, TargetStatus.DISCONNECTED);
        fixture.statuses.put(fixture.secondId, TargetStatus.DISCONNECTED);

        assertEquals(
            fixture.firstId,
            ClipboardServerService.reconcileActiveBinding(fixture.bindings, fixture.statuses, null));
        assertEquals(fixture.firstId, fixture.bindings.getActiveId());
    }

    private static final class BindingFixture {

        private final UUID firstId = UUID.randomUUID();
        private final UUID secondId = UUID.randomUUID();
        private final PlayerBindings bindings = new PlayerBindings(UUID.randomUUID());
        private final Map<UUID, TargetStatus> statuses = new LinkedHashMap<>();

        private BindingFixture() {
            bindings.put(new PlayerBindingRecord(firstId, "First", 1L, 1L, ClipboardPageSnapshot.EMPTY));
            bindings.put(new PlayerBindingRecord(secondId, "Second", 1L, 1L, ClipboardPageSnapshot.EMPTY));
        }
    }
}
