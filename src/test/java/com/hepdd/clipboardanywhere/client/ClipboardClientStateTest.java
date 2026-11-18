package com.hepdd.clipboardanywhere.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.UUID;

import org.junit.After;
import org.junit.Test;

import com.hepdd.clipboardanywhere.client.ClipboardClientState.Notice;
import com.hepdd.clipboardanywhere.model.ClipboardAction;
import com.hepdd.clipboardanywhere.model.ClipboardPageSnapshot;
import com.hepdd.clipboardanywhere.model.TargetStatus;
import com.hepdd.clipboardanywhere.network.NetworkCodec.BindingStatePayload;
import com.hepdd.clipboardanywhere.network.NetworkCodec.BindingWireEntry;

public class ClipboardClientStateTest {

    @After
    public void tearDown() {
        ClipboardClientState.INSTANCE.clear();
    }

    @Test
    public void preservesCachedInactivePageAndSwitchesWithZeroBlankDelay() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        ClipboardPageSnapshot pageA1 = page(1, 1, "Alpha", "Task A1", 0);
        ClipboardPageSnapshot pageB = page(1, 3, "Beta", "Task B1", 2);

        ClipboardClientState.INSTANCE.applyPayload(
            new BindingStatePayload(
                idA,
                0,
                Arrays.asList(
                    BindingWireEntry.withPage(idA, "Board A", TargetStatus.READABLE_INVENTORY, pageA1),
                    BindingWireEntry.withPage(idB, "Board B", TargetStatus.READABLE_PLACED, pageB))));
        assertTrue(ClipboardClientState.INSTANCE.isSynced());

        // Subsequent update modifies A while sending B as summary-only (hasPage = false)
        ClipboardPageSnapshot pageA2 = page(1, 1, "Alpha", "Task A1", 1);
        ClipboardClientState.INSTANCE.applyPayload(
            new BindingStatePayload(
                idA,
                1,
                Arrays.asList(
                    BindingWireEntry.withPage(idA, "Board A", TargetStatus.READABLE_INVENTORY, pageA2),
                    BindingWireEntry.summaryOnly(idB, "Board B", TargetStatus.READABLE_PLACED))));

        // Optimistic select of B immediately exposes B's full cached page without waiting for RTT
        int selectSeq = ClipboardClientState.INSTANCE.applyOptimisticSelect(idB);
        assertTrue(selectSeq > 0);
        assertEquals(
            idB,
            ClipboardClientState.INSTANCE.getSnapshot()
                .getActiveId());
        assertEquals(
            "Beta",
            ClipboardClientState.INSTANCE.getSnapshot()
                .getActiveBinding()
                .getSnapshot()
                .getTitle());
        assertEquals(
            "Task B1",
            ClipboardClientState.INSTANCE.getSnapshot()
                .getActiveBinding()
                .getSnapshot()
                .getTask(0));
    }

    @Test
    public void ignoresStaleAckDuringRapidClicksAndRollsBackOnServerRejection() {
        UUID id = UUID.randomUUID();
        ClipboardPageSnapshot initial = page(1, 1, "Main", "Original", 0);
        ClipboardClientState.INSTANCE.applyPayload(
            new BindingStatePayload(
                id,
                0,
                Arrays.asList(BindingWireEntry.withPage(id, "Main", TargetStatus.READABLE_INVENTORY, initial))));

        int seq1 = ClipboardClientState.INSTANCE.applyOptimisticAction(id, ClipboardAction.CYCLE_TASK, 0);
        int seq2 = ClipboardClientState.INSTANCE.applyOptimisticTaskText(id, 1, 0, "Edited");
        assertEquals(
            1,
            ClipboardClientState.INSTANCE.getSnapshot()
                .getActiveBinding()
                .getSnapshot()
                .getTaskState(0));
        assertEquals(
            "Edited",
            ClipboardClientState.INSTANCE.getSnapshot()
                .getActiveBinding()
                .getSnapshot()
                .getTask(0));

        // Packet 1 response arrives while packet 2 is still in flight: must NOT flash back to "Original"!
        ClipboardPageSnapshot afterFirstPacket = page(1, 1, "Main", "Original", 1);
        ClipboardClientState.INSTANCE.applyPayload(
            new BindingStatePayload(
                id,
                seq1,
                Arrays
                    .asList(BindingWireEntry.withPage(id, "Main", TargetStatus.READABLE_INVENTORY, afterFirstPacket))));
        assertEquals(
            "Edited",
            ClipboardClientState.INSTANCE.getSnapshot()
                .getActiveBinding()
                .getSnapshot()
                .getTask(0));

        // Server rejects packet 2 due to conflict and sends authoritative state with ackSeq = seq2: must roll back!
        ClipboardPageSnapshot conflictServerPage = page(1, 1, "Main", "ChangedByOther", 1);
        ClipboardClientState.INSTANCE.applyPayload(
            new BindingStatePayload(
                id,
                seq2,
                Arrays.asList(
                    BindingWireEntry.withPage(id, "Main", TargetStatus.READABLE_INVENTORY, conflictServerPage))));
        assertEquals(
            "ChangedByOther",
            ClipboardClientState.INSTANCE.getSnapshot()
                .getActiveBinding()
                .getSnapshot()
                .getTask(0));
    }

    @Test
    public void cachesVisitedPagesForInstantPageSwitchingAndExpiresUnackedOptimisticState() {
        UUID id = UUID.randomUUID();
        ClipboardPageSnapshot p1 = page(1, 2, "Page 1", "Row 1", 1);
        ClipboardPageSnapshot p2 = page(2, 2, "Page 2", "Row 2", 2);

        ClipboardClientState.INSTANCE.applyPayload(
            new BindingStatePayload(
                id,
                0,
                Arrays.asList(BindingWireEntry.withPage(id, "Clip", TargetStatus.READABLE_PLACED, p1))));
        ClipboardClientState.INSTANCE.applyPayload(
            new BindingStatePayload(
                id,
                0,
                Arrays.asList(BindingWireEntry.withPage(id, "Clip", TargetStatus.READABLE_PLACED, p2))));

        // Optimistic PREVIOUS_PAGE immediately restores cached Page 1 content
        ClipboardClientState.INSTANCE.applyOptimisticAction(id, ClipboardAction.PREVIOUS_PAGE, -1);
        ClipboardPageSnapshot shown = ClipboardClientState.INSTANCE.getSnapshot()
            .getActiveBinding()
            .getSnapshot();
        assertEquals(1, shown.getCurrentPage());
        assertEquals("Page 1", shown.getTitle());
        assertEquals("Row 1", shown.getTask(0));

        // If server never acknowledges within OPTIMISTIC_EXPIRE_TICKS, client self-heals back to authoritative Page 2
        for (int i = 0; i < ClipboardClientState.OPTIMISTIC_EXPIRE_TICKS; i++) {
            ClipboardClientState.INSTANCE.tick();
        }
        assertEquals(
            2,
            ClipboardClientState.INSTANCE.getSnapshot()
                .getActiveBinding()
                .getSnapshot()
                .getCurrentPage());
    }

    @Test
    public void reportsAllDisconnectedOnlyOnEdgeTransition() {
        UUID id = UUID.randomUUID();
        ClipboardPageSnapshot p = page(1, 1, "T", "R", 0);
        ClipboardClientState.INSTANCE.applyPayload(
            new BindingStatePayload(
                id,
                0,
                Arrays.asList(BindingWireEntry.withPage(id, "Clip", TargetStatus.READABLE_PLACED, p))));
        assertNull(ClipboardClientState.INSTANCE.pollNotice());

        ClipboardClientState.INSTANCE.applyPayload(
            new BindingStatePayload(
                id,
                0,
                Arrays.asList(BindingWireEntry.summaryOnly(id, "Clip", TargetStatus.DISCONNECTED))));
        Notice first = ClipboardClientState.INSTANCE.pollNotice();
        assertNotNull(first);
        assertFalse(first.isSuccess());
        assertEquals("message.clipboardanywhere.all_disconnected", first.getTranslationKey());

        ClipboardClientState.INSTANCE.applyPayload(
            new BindingStatePayload(
                id,
                0,
                Arrays.asList(BindingWireEntry.summaryOnly(id, "Clip", TargetStatus.DISCONNECTED))));
        assertNull(ClipboardClientState.INSTANCE.pollNotice());
    }

    private static ClipboardPageSnapshot page(int currentPage, int totalPages, String title, String firstTask,
        int firstState) {
        String[] tasks = new String[ClipboardPageSnapshot.TASK_COUNT];
        tasks[0] = firstTask;
        int[] states = new int[ClipboardPageSnapshot.TASK_COUNT];
        states[0] = firstState;
        return new ClipboardPageSnapshot(currentPage, totalPages, title, tasks, states, 10L);
    }
}
