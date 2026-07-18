package com.hepdd.clipboardanywhere.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.UUID;

import org.junit.Test;

import com.hepdd.clipboardanywhere.model.BindingView;
import com.hepdd.clipboardanywhere.model.ClipboardPageSnapshot;
import com.hepdd.clipboardanywhere.model.PlayerBindingSnapshot;
import com.hepdd.clipboardanywhere.model.TargetStatus;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class NetworkCodecTest {

    @Test
    public void roundTripsPlayerSnapshot() {
        UUID readableId = UUID.randomUUID();
        UUID disconnectedId = UUID.randomUUID();
        ClipboardPageSnapshot page = new ClipboardPageSnapshot(
            1,
            2,
            "Page",
            new String[] { "Task" },
            new int[] { 2 },
            30L);
        PlayerBindingSnapshot source = new PlayerBindingSnapshot(
            readableId,
            Arrays.asList(
                new BindingView(readableId, "Readable", TargetStatus.READABLE_PLACED, page),
                new BindingView(disconnectedId, "Lost", TargetStatus.DISCONNECTED, page)));

        ByteBuf buffer = Unpooled.buffer();
        NetworkCodec.writePlayerSnapshot(buffer, source);
        PlayerBindingSnapshot restored = NetworkCodec.readPlayerSnapshot(buffer);

        assertEquals(readableId, restored.getActiveId());
        assertEquals(
            2,
            restored.getBindings()
                .size());
        assertEquals(
            page,
            restored.get(readableId)
                .getSnapshot());
        assertFalse(restored.isAllDisconnected());

        PlayerBindingSnapshot lost = new PlayerBindingSnapshot(
            disconnectedId,
            Arrays.asList(new BindingView(disconnectedId, "Lost", TargetStatus.DISCONNECTED, page)));
        assertTrue(lost.isAllDisconnected());
    }
}
