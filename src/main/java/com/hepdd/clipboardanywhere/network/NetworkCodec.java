package com.hepdd.clipboardanywhere.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.hepdd.clipboardanywhere.model.BindingView;
import com.hepdd.clipboardanywhere.model.ClipboardPageSnapshot;
import com.hepdd.clipboardanywhere.model.PlayerBindingSnapshot;
import com.hepdd.clipboardanywhere.model.TargetStatus;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public final class NetworkCodec {

    private static final int MAX_BINDINGS = 256;

    private NetworkCodec() {}

    public static void writeUuid(ByteBuf buffer, UUID id) {
        buffer.writeBoolean(id != null);
        if (id != null) {
            buffer.writeLong(id.getMostSignificantBits());
            buffer.writeLong(id.getLeastSignificantBits());
        }
    }

    public static UUID readUuid(ByteBuf buffer) {
        return buffer.readBoolean() ? new UUID(buffer.readLong(), buffer.readLong()) : null;
    }

    public static void writePage(ByteBuf buffer, ClipboardPageSnapshot page) {
        ClipboardPageSnapshot value = page == null ? ClipboardPageSnapshot.EMPTY : page;
        buffer.writeInt(value.getCurrentPage());
        buffer.writeInt(value.getTotalPages());
        ByteBufUtils.writeUTF8String(buffer, value.getTitle());
        for (int row = 0; row < ClipboardPageSnapshot.TASK_COUNT; row++) {
            ByteBufUtils.writeUTF8String(buffer, value.getTask(row));
            buffer.writeByte(value.getTaskState(row));
        }
        buffer.writeLong(value.getCapturedAt());
    }

    public static ClipboardPageSnapshot readPage(ByteBuf buffer) {
        int currentPage = buffer.readInt();
        int totalPages = buffer.readInt();
        String title = ByteBufUtils.readUTF8String(buffer);
        String[] tasks = new String[ClipboardPageSnapshot.TASK_COUNT];
        int[] states = new int[ClipboardPageSnapshot.TASK_COUNT];
        for (int row = 0; row < ClipboardPageSnapshot.TASK_COUNT; row++) {
            tasks[row] = ByteBufUtils.readUTF8String(buffer);
            states[row] = buffer.readUnsignedByte();
        }
        return new ClipboardPageSnapshot(currentPage, totalPages, title, tasks, states, buffer.readLong());
    }

    public static void writeBinding(ByteBuf buffer, BindingView binding) {
        writeUuid(buffer, binding.getId());
        ByteBufUtils.writeUTF8String(buffer, binding.getDisplayName());
        buffer.writeByte(
            binding.getStatus()
                .getNetworkId());
        writePage(buffer, binding.getSnapshot());
    }

    public static BindingView readBinding(ByteBuf buffer) {
        return new BindingView(
            readUuid(buffer),
            ByteBufUtils.readUTF8String(buffer),
            TargetStatus.fromNetworkId(buffer.readUnsignedByte()),
            readPage(buffer));
    }

    public static void writePlayerSnapshot(ByteBuf buffer, PlayerBindingSnapshot snapshot) {
        PlayerBindingSnapshot value = snapshot == null ? PlayerBindingSnapshot.EMPTY : snapshot;
        writeUuid(buffer, value.getActiveId());
        buffer.writeShort(
            value.getBindings()
                .size());
        for (BindingView binding : value.getBindings()) {
            writeBinding(buffer, binding);
        }
    }

    public static PlayerBindingSnapshot readPlayerSnapshot(ByteBuf buffer) {
        UUID activeId = readUuid(buffer);
        int count = buffer.readUnsignedShort();
        if (count > MAX_BINDINGS) {
            throw new IllegalArgumentException("Too many clipboard bindings: " + count);
        }
        List<BindingView> bindings = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            bindings.add(readBinding(buffer));
        }
        return new PlayerBindingSnapshot(activeId, bindings);
    }
}
