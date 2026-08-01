package com.hepdd.clipboardanywhere.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.common.base.Charsets;
import com.hepdd.clipboardanywhere.model.BindingView;
import com.hepdd.clipboardanywhere.model.ClipboardPageSnapshot;
import com.hepdd.clipboardanywhere.model.PlayerBindingSnapshot;
import com.hepdd.clipboardanywhere.model.TargetStatus;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public final class NetworkCodec {

    public static final int MAX_BINDINGS = 256;
    public static final int MAX_DISPLAY_NAME_CHARS = 32;
    public static final int MAX_PAGE_TITLE_CHARS = 256;
    public static final int MAX_TASK_TEXT_CHARS = 1024;
    public static final int MAX_EDIT_TASK_TEXT_CHARS = 23;
    public static final int MAX_TRANSLATION_KEY_CHARS = 128;
    private static final int MAX_UTF8_BYTES_PER_CHAR = 4;

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
        writeBoundedUtf8(buffer, value.getTitle(), MAX_PAGE_TITLE_CHARS);
        for (int row = 0; row < ClipboardPageSnapshot.TASK_COUNT; row++) {
            writeBoundedUtf8(buffer, value.getTask(row), MAX_TASK_TEXT_CHARS);
            buffer.writeByte(value.getTaskState(row));
        }
        buffer.writeLong(value.getCapturedAt());
    }

    public static ClipboardPageSnapshot readPage(ByteBuf buffer) {
        int currentPage = buffer.readInt();
        int totalPages = buffer.readInt();
        String title = readBoundedUtf8(buffer, MAX_PAGE_TITLE_CHARS);
        String[] tasks = new String[ClipboardPageSnapshot.TASK_COUNT];
        int[] states = new int[ClipboardPageSnapshot.TASK_COUNT];
        for (int row = 0; row < ClipboardPageSnapshot.TASK_COUNT; row++) {
            tasks[row] = readBoundedUtf8(buffer, MAX_TASK_TEXT_CHARS);
            states[row] = buffer.readUnsignedByte();
        }
        return new ClipboardPageSnapshot(currentPage, totalPages, title, tasks, states, buffer.readLong());
    }

    public static void writeBinding(ByteBuf buffer, BindingView binding) {
        writeUuid(buffer, binding.getId());
        writeBoundedUtf8(buffer, binding.getDisplayName(), MAX_DISPLAY_NAME_CHARS);
        buffer.writeByte(
            binding.getStatus()
                .getNetworkId());
        writePage(buffer, binding.getSnapshot());
    }

    public static BindingView readBinding(ByteBuf buffer) {
        return new BindingView(
            readUuid(buffer),
            readBoundedUtf8(buffer, MAX_DISPLAY_NAME_CHARS),
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

    public static void writeBoundedUtf8(ByteBuf buffer, String value, int maxCharacters) {
        if (maxCharacters < 0) throw new IllegalArgumentException("maxCharacters");
        String bounded = value == null ? "" : value;
        if (bounded.length() > maxCharacters) bounded = bounded.substring(0, maxCharacters);
        byte[] bytes = bounded.getBytes(Charsets.UTF_8);
        int maxBytes = maxCharacters * MAX_UTF8_BYTES_PER_CHAR;
        while (bytes.length > maxBytes && !bounded.isEmpty()) {
            bounded = bounded.substring(0, bounded.length() - 1);
            bytes = bounded.getBytes(Charsets.UTF_8);
        }
        ByteBufUtils.writeVarInt(buffer, bytes.length, 2);
        buffer.writeBytes(bytes);
    }

    public static String readBoundedUtf8(ByteBuf buffer, int maxCharacters) {
        if (maxCharacters < 0) throw new IllegalArgumentException("maxCharacters");
        int byteLength = ByteBufUtils.readVarInt(buffer, 2);
        int maxBytes = maxCharacters * MAX_UTF8_BYTES_PER_CHAR;
        if (byteLength < 0 || byteLength > maxBytes || byteLength > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid UTF-8 string length: " + byteLength);
        }
        String value = buffer.toString(buffer.readerIndex(), byteLength, Charsets.UTF_8);
        buffer.skipBytes(byteLength);
        if (value.length() > maxCharacters) {
            throw new IllegalArgumentException("UTF-8 string exceeds character limit: " + value.length());
        }
        return value;
    }
}
