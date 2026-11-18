package com.hepdd.clipboardanywhere.network;

import java.util.ArrayList;
import java.util.Collections;
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

    public static void writeWireEntry(ByteBuf buffer, BindingWireEntry entry) {
        writeUuid(buffer, entry.getId());
        writeBoundedUtf8(buffer, entry.getDisplayName(), MAX_DISPLAY_NAME_CHARS);
        buffer.writeByte(
            entry.getStatus()
                .getNetworkId());
        buffer.writeBoolean(entry.hasPage());
        if (entry.hasPage()) {
            writePage(buffer, entry.getPage());
        }
    }

    public static BindingWireEntry readWireEntry(ByteBuf buffer) {
        UUID id = readUuid(buffer);
        if (id == null) {
            throw new IllegalArgumentException("Binding entry id cannot be null");
        }
        String displayName = readBoundedUtf8(buffer, MAX_DISPLAY_NAME_CHARS);
        TargetStatus status = TargetStatus.fromNetworkId(buffer.readUnsignedByte());
        boolean hasPage = buffer.readBoolean();
        ClipboardPageSnapshot page = hasPage ? readPage(buffer) : null;
        return hasPage ? BindingWireEntry.withPage(id, displayName, status, page)
            : BindingWireEntry.summaryOnly(id, displayName, status);
    }

    public static void writeStatePayload(ByteBuf buffer, BindingStatePayload payload) {
        BindingStatePayload value = payload == null ? BindingStatePayload.EMPTY : payload;
        writeUuid(buffer, value.getActiveId());
        buffer.writeInt(value.getAckSeq());
        List<BindingWireEntry> entries = value.getEntries();
        if (entries.size() > MAX_BINDINGS) {
            throw new IllegalArgumentException("Too many clipboard bindings: " + entries.size());
        }
        buffer.writeShort(entries.size());
        for (BindingWireEntry entry : entries) {
            writeWireEntry(buffer, entry);
        }
    }

    public static BindingStatePayload readStatePayload(ByteBuf buffer) {
        UUID activeId = readUuid(buffer);
        int ackSeq = buffer.readInt();
        int count = buffer.readUnsignedShort();
        if (count > MAX_BINDINGS) {
            throw new IllegalArgumentException("Too many clipboard bindings: " + count);
        }
        List<BindingWireEntry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(readWireEntry(buffer));
        }
        return new BindingStatePayload(activeId, ackSeq, entries);
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

    public static String sanitizeName(String input) {
        if (input == null) return "";
        StringBuilder output = new StringBuilder();
        boolean skipFormattingValue = false;
        for (int index = 0; index < input.length() && output.length() < MAX_DISPLAY_NAME_CHARS; index++) {
            char character = input.charAt(index);
            if (skipFormattingValue) {
                skipFormattingValue = false;
                continue;
            }
            if (character == '\u00a7') {
                skipFormattingValue = true;
                continue;
            }
            if (!Character.isISOControl(character)) output.append(character);
        }
        return output.toString()
            .trim();
    }

    public static String sanitizeTaskText(String input) {
        if (input == null || input.length() > MAX_EDIT_TASK_TEXT_CHARS) return null;
        StringBuilder output = new StringBuilder();
        boolean skipFormattingValue = false;
        for (int index = 0; index < input.length(); index++) {
            char character = input.charAt(index);
            if (skipFormattingValue) {
                skipFormattingValue = false;
                continue;
            }
            if (character == '\u00a7') {
                skipFormattingValue = true;
                continue;
            }
            if (!Character.isISOControl(character)) output.append(character);
        }
        return output.toString();
    }

    public static final class BindingWireEntry {

        private final UUID id;
        private final String displayName;
        private final TargetStatus status;
        private final boolean hasPage;
        private final ClipboardPageSnapshot page;

        private BindingWireEntry(UUID id, String displayName, TargetStatus status, boolean hasPage,
            ClipboardPageSnapshot page) {
            if (id == null) throw new IllegalArgumentException("id");
            this.id = id;
            this.displayName = displayName == null ? "" : displayName;
            this.status = status == null ? TargetStatus.MISSING : status;
            this.hasPage = hasPage;
            this.page = hasPage ? (page == null ? ClipboardPageSnapshot.EMPTY : page) : null;
        }

        public static BindingWireEntry withPage(UUID id, String displayName, TargetStatus status,
            ClipboardPageSnapshot page) {
            return new BindingWireEntry(id, displayName, status, true, page);
        }

        public static BindingWireEntry summaryOnly(UUID id, String displayName, TargetStatus status) {
            return new BindingWireEntry(id, displayName, status, false, null);
        }

        public UUID getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public TargetStatus getStatus() {
            return status;
        }

        public boolean hasPage() {
            return hasPage;
        }

        public ClipboardPageSnapshot getPage() {
            return page;
        }
    }

    public static final class BindingStatePayload {

        public static final BindingStatePayload EMPTY = new BindingStatePayload(
            null,
            0,
            Collections.<BindingWireEntry>emptyList());

        private final UUID activeId;
        private final int ackSeq;
        private final List<BindingWireEntry> entries;

        public BindingStatePayload(UUID activeId, int ackSeq, List<BindingWireEntry> entries) {
            this.entries = Collections.unmodifiableList(
                entries == null ? Collections.<BindingWireEntry>emptyList() : new ArrayList<>(entries));
            this.activeId = contains(this.entries, activeId) ? activeId : null;
            this.ackSeq = ackSeq;
        }

        public static BindingStatePayload fullFromSnapshot(PlayerBindingSnapshot snapshot, int ackSeq) {
            PlayerBindingSnapshot value = snapshot == null ? PlayerBindingSnapshot.EMPTY : snapshot;
            List<BindingView> bindings = value.getBindings();
            List<BindingWireEntry> entries = new ArrayList<>(bindings.size());
            for (BindingView binding : bindings) {
                entries.add(
                    BindingWireEntry.withPage(
                        binding.getId(),
                        binding.getDisplayName(),
                        binding.getStatus(),
                        binding.getSnapshot()));
            }
            return new BindingStatePayload(value.getActiveId(), ackSeq, entries);
        }

        public UUID getActiveId() {
            return activeId;
        }

        public int getAckSeq() {
            return ackSeq;
        }

        public List<BindingWireEntry> getEntries() {
            return entries;
        }

        private static boolean contains(List<BindingWireEntry> entries, UUID id) {
            if (id == null) return false;
            for (BindingWireEntry entry : entries) {
                if (id.equals(entry.getId())) return true;
            }
            return false;
        }
    }
}
