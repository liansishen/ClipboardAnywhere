package com.hepdd.clipboardanywhere.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.UUID;

import org.junit.Test;

import com.hepdd.clipboardanywhere.model.ClipboardAction;
import com.hepdd.clipboardanywhere.model.ClipboardPageSnapshot;
import com.hepdd.clipboardanywhere.model.TargetStatus;
import com.hepdd.clipboardanywhere.network.NetworkCodec.BindingStatePayload;
import com.hepdd.clipboardanywhere.network.NetworkCodec.BindingWireEntry;
import com.hepdd.clipboardanywhere.network.message.C2SClipboardAction;
import com.hepdd.clipboardanywhere.network.message.C2SRenameBinding;
import com.hepdd.clipboardanywhere.network.message.C2SSelectBinding;
import com.hepdd.clipboardanywhere.network.message.C2SUpdateTaskText;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class NetworkCodecTest {

    @Test
    public void roundTripsIncrementalPayloadAndShrinksInactiveEntries() {
        UUID activeId = UUID.randomUUID();
        UUID inactiveId = UUID.randomUUID();
        String[] richTasks = new String[ClipboardPageSnapshot.TASK_COUNT];
        Arrays.fill(richTasks, "Long task description for bandwidth comparison");
        ClipboardPageSnapshot page = new ClipboardPageSnapshot(
            2,
            5,
            "Factory Checklist",
            richTasks,
            new int[] { 1, 2, 0, 1, 0, 2, 1, 0, 1 },
            120L);

        BindingStatePayload slimPayload = new BindingStatePayload(
            activeId,
            42,
            Arrays.asList(
                BindingWireEntry.withPage(activeId, "Main Base", TargetStatus.READABLE_PLACED, page),
                BindingWireEntry.summaryOnly(inactiveId, "Outpost", TargetStatus.READABLE_INVENTORY)));

        BindingStatePayload fullPayload = new BindingStatePayload(
            activeId,
            42,
            Arrays.asList(
                BindingWireEntry.withPage(activeId, "Main Base", TargetStatus.READABLE_PLACED, page),
                BindingWireEntry.withPage(inactiveId, "Outpost", TargetStatus.READABLE_INVENTORY, page)));

        ByteBuf slimBuffer = Unpooled.buffer();
        NetworkCodec.writeStatePayload(slimBuffer, slimPayload);
        int slimBytes = slimBuffer.readableBytes();

        ByteBuf fullBuffer = Unpooled.buffer();
        NetworkCodec.writeStatePayload(fullBuffer, fullPayload);
        int fullBytes = fullBuffer.readableBytes();

        assertTrue("Summary-only inactive entry should be much smaller", slimBytes < fullBytes - 300);

        BindingStatePayload restored = NetworkCodec.readStatePayload(slimBuffer);
        assertEquals(activeId, restored.getActiveId());
        assertEquals(42, restored.getAckSeq());
        assertEquals(
            2,
            restored.getEntries()
                .size());
        assertTrue(
            restored.getEntries()
                .get(0)
                .hasPage());
        assertEquals(
            page,
            restored.getEntries()
                .get(0)
                .getPage());
        assertFalse(
            restored.getEntries()
                .get(1)
                .hasPage());
        assertNull(
            restored.getEntries()
                .get(1)
                .getPage());
    }

    @Test
    public void roundTripsClientSequenceAcrossMutationPackets() {
        UUID id = UUID.randomUUID();

        ByteBuf buf1 = Unpooled.buffer();
        new C2SClipboardAction(id, ClipboardAction.CYCLE_TASK, 3, 17).toBytes(buf1);
        C2SClipboardAction decodedAction = new C2SClipboardAction();
        decodedAction.fromBytes(buf1);
        assertEquals(0, buf1.readableBytes());

        ByteBuf buf2 = Unpooled.buffer();
        new C2SUpdateTaskText(id, 2, 4, "old", "new", 18).toBytes(buf2);
        C2SUpdateTaskText decodedText = new C2SUpdateTaskText();
        decodedText.fromBytes(buf2);
        assertEquals(0, buf2.readableBytes());

        ByteBuf buf3 = Unpooled.buffer();
        new C2SRenameBinding(id, "Renamed", 19).toBytes(buf3);
        C2SRenameBinding decodedRename = new C2SRenameBinding();
        decodedRename.fromBytes(buf3);
        assertEquals(0, buf3.readableBytes());

        ByteBuf buf4 = Unpooled.buffer();
        new C2SSelectBinding(id, 20).toBytes(buf4);
        C2SSelectBinding decodedSelect = new C2SSelectBinding();
        decodedSelect.fromBytes(buf4);
        assertEquals(0, buf4.readableBytes());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTooManyBindingsInPayload() {
        ByteBuf buffer = Unpooled.buffer();
        NetworkCodec.writeUuid(buffer, null);
        buffer.writeInt(0);
        buffer.writeShort(NetworkCodec.MAX_BINDINGS + 1);

        NetworkCodec.readStatePayload(buffer);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOversizedUtf8LengthBeforeReadingPayload() {
        ByteBuf buffer = Unpooled.buffer();
        ByteBufUtils.writeVarInt(buffer, NetworkCodec.MAX_DISPLAY_NAME_CHARS * 4 + 1, 2);

        NetworkCodec.readBoundedUtf8(buffer, NetworkCodec.MAX_DISPLAY_NAME_CHARS);
    }

    @Test
    public void sanitizesFormattingAndControlCharacters() {
        assertEquals("Clean Name", NetworkCodec.sanitizeName(" \u00a7aClean\u0007 \u00a7lName "));
        assertEquals("CheckLV pump", NetworkCodec.sanitizeTaskText("\u00a7eCheck\nLV pump"));
        assertNull(NetworkCodec.sanitizeTaskText("123456789012345678901234"));
    }
}
