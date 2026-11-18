package com.hepdd.clipboardanywhere.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

import com.hepdd.clipboardanywhere.clipboard.BiblioClipboardAdapter;
import com.hepdd.clipboardanywhere.clipboard.ClipboardIdentity;
import com.hepdd.clipboardanywhere.model.BindingView;
import com.hepdd.clipboardanywhere.model.ClipboardAction;
import com.hepdd.clipboardanywhere.model.ClipboardPageSnapshot;
import com.hepdd.clipboardanywhere.model.PlayerBindingSnapshot;
import com.hepdd.clipboardanywhere.model.TargetStatus;
import com.hepdd.clipboardanywhere.network.NetworkCodec.BindingStatePayload;
import com.hepdd.clipboardanywhere.server.ClipboardServerService.PlayerSessionSyncState;

import jds.bibliocraft.items.ItemClipboard;

public class ServerSyncAndAdapterTest {

    @Test
    public void readPathDoesNotMutateUninitializedOrIdentityOnlyClipboardNbt() {
        ItemStack untagged = new ItemStack(new ItemClipboard());
        ClipboardPageSnapshot emptySnap = BiblioClipboardAdapter.read(untagged, 100L);
        assertEquals(ClipboardPageSnapshot.EMPTY, emptySnap);
        assertFalse("Read must not attach NBT to an untagged clipboard", untagged.hasTagCompound());

        ItemStack identityOnly = new ItemStack(new ItemClipboard());
        UUID id = UUID.randomUUID();
        ClipboardIdentity.setId(identityOnly, id);
        NBTTagCompound snapshotBefore = (NBTTagCompound) identityOnly.getTagCompound()
            .copy();

        ClipboardPageSnapshot readSnap = BiblioClipboardAdapter.read(identityOnly, 200L);
        assertEquals(1, readSnap.getCurrentPage());
        assertEquals(1, readSnap.getTotalPages());
        assertEquals("", readSnap.getTitle());
        assertEquals("", readSnap.getTask(0));
        assertEquals(0, readSnap.getTaskState(0));
        assertEquals(
            "Read-only poll must not add page1/totalPages keys to item NBT",
            snapshotBefore,
            identityOnly.getTagCompound());
        assertFalse(
            identityOnly.getTagCompound()
                .hasKey("page1"));
    }

    @Test
    public void ensureStructureOnlyMutatesWhenNeededAndPreservesIdentity() {
        ItemStack stack = new ItemStack(new ItemClipboard());
        UUID id = UUID.randomUUID();
        ClipboardIdentity.setId(stack, id);

        assertTrue(BiblioClipboardAdapter.ensureStructure(stack));
        assertEquals(id, ClipboardIdentity.getId(stack));
        assertFalse(
            "Second ensureStructure on valid stack must report no change",
            BiblioClipboardAdapter.ensureStructure(stack));

        assertTrue(BiblioClipboardAdapter.apply(stack, ClipboardAction.CYCLE_TASK, 2));
        assertEquals(
            1,
            BiblioClipboardAdapter.read(stack, 10L)
                .getTaskState(2));
    }

    @Test
    public void singlePassInventoryIndexAvoidsAllocationWhenEmptyAndKeepsFirstSlot() {
        ServerTargetResolver resolver = new ServerTargetResolver();
        ItemStack[] emptyInv = new ItemStack[36];
        Map<UUID, Integer> emptyIndex = resolver.indexInventoryClipboards(emptyInv);
        assertSame(Collections.emptyMap(), emptyIndex);

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        ItemStack first = new ItemStack(new ItemClipboard());
        ClipboardIdentity.setId(first, id1);
        ItemStack duplicate = new ItemStack(new ItemClipboard());
        ClipboardIdentity.setId(duplicate, id1);
        ItemStack second = new ItemStack(new ItemClipboard());
        ClipboardIdentity.setId(second, id2);

        emptyInv[5] = first;
        emptyInv[19] = duplicate;
        emptyInv[27] = second;

        Map<UUID, Integer> index = resolver.indexInventoryClipboards(emptyInv);
        assertEquals(2, index.size());
        assertEquals(Integer.valueOf(5), index.get(id1));
        assertEquals(Integer.valueOf(27), index.get(id2));
    }

    @Test
    public void incrementalPayloadSendsPagesOnlyWhenChangedAndSupportsRollbackInvalidation() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        ClipboardPageSnapshot pageA1 = new ClipboardPageSnapshot(
            1,
            1,
            "A",
            new String[] { "One" },
            new int[] { 0 },
            1L);
        ClipboardPageSnapshot pageB1 = new ClipboardPageSnapshot(
            1,
            1,
            "B",
            new String[] { "Two" },
            new int[] { 1 },
            1L);

        PlayerSessionSyncState state = new PlayerSessionSyncState();
        state.lastAckSeq = 7;
        PlayerBindingSnapshot snap1 = new PlayerBindingSnapshot(
            idA,
            Arrays.asList(
                new BindingView(idA, "Board A", TargetStatus.READABLE_INVENTORY, pageA1),
                new BindingView(idB, "Board B", TargetStatus.READABLE_PLACED, pageB1)));

        BindingStatePayload firstPayload = ClipboardServerService.buildIncrementalPayload(snap1, state);
        assertEquals(7, firstPayload.getAckSeq());
        assertTrue(
            firstPayload.getEntries()
                .get(0)
                .hasPage());
        assertTrue(
            firstPayload.getEntries()
                .get(1)
                .hasPage());

        // Second sync: only A's page changed, B must be summary-only
        ClipboardPageSnapshot pageA2 = new ClipboardPageSnapshot(
            1,
            1,
            "A",
            new String[] { "One" },
            new int[] { 1 },
            2L);
        PlayerBindingSnapshot snap2 = new PlayerBindingSnapshot(
            idA,
            Arrays.asList(
                new BindingView(idA, "Board A", TargetStatus.READABLE_INVENTORY, pageA2),
                new BindingView(idB, "Board B", TargetStatus.READABLE_PLACED, pageB1)));
        state.lastAckSeq = 8;
        BindingStatePayload secondPayload = ClipboardServerService.buildIncrementalPayload(snap2, state);
        assertTrue(
            secondPayload.getEntries()
                .get(0)
                .hasPage());
        assertFalse(
            secondPayload.getEntries()
                .get(1)
                .hasPage());

        // Rollback invalidation forces resending A's authoritative page even if unchanged on server
        state.sentPages.remove(idA);
        BindingStatePayload rollbackPayload = ClipboardServerService.buildIncrementalPayload(snap2, state);
        assertTrue(
            rollbackPayload.getEntries()
                .get(0)
                .hasPage());
        assertFalse(
            rollbackPayload.getEntries()
                .get(1)
                .hasPage());
    }

    @Test
    public void throttlesServerSideBindAttemptsByMonotonicTick() {
        PlayerSessionSyncState state = new PlayerSessionSyncState();
        assertTrue(state.tryAcquireBindSlot(100, ClipboardServerService.BIND_COOLDOWN_TICKS));
        assertFalse(state.tryAcquireBindSlot(100, ClipboardServerService.BIND_COOLDOWN_TICKS));
        assertFalse(state.tryAcquireBindSlot(105, ClipboardServerService.BIND_COOLDOWN_TICKS));
        assertTrue(state.tryAcquireBindSlot(106, ClipboardServerService.BIND_COOLDOWN_TICKS));
    }
}
