package com.hepdd.clipboardanywhere.data;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import com.hepdd.clipboardanywhere.model.ClipboardPageSnapshot;
import com.hepdd.clipboardanywhere.util.UuidNbt;

public final class PlayerBindingRecord {

    private final UUID clipboardId;
    private String displayName;
    private long createdAt;
    private long updatedAt;
    private ClipboardPageSnapshot cachedPage;

    public PlayerBindingRecord(UUID clipboardId, String displayName, long createdAt, long updatedAt,
        ClipboardPageSnapshot cachedPage) {
        if (clipboardId == null) throw new IllegalArgumentException("clipboardId");
        this.clipboardId = clipboardId;
        this.displayName = displayName == null ? "" : displayName;
        this.createdAt = Math.max(0L, createdAt);
        this.updatedAt = Math.max(0L, updatedAt);
        this.cachedPage = cachedPage == null ? ClipboardPageSnapshot.EMPTY : cachedPage;
    }

    public UUID getClipboardId() {
        return clipboardId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName, long updatedAt) {
        this.displayName = displayName == null ? "" : displayName;
        this.updatedAt = Math.max(0L, updatedAt);
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public ClipboardPageSnapshot getCachedPage() {
        return cachedPage;
    }

    public boolean updateCachedPage(ClipboardPageSnapshot snapshot, long updatedAt) {
        ClipboardPageSnapshot normalized = snapshot == null ? ClipboardPageSnapshot.EMPTY : snapshot;
        if (normalized.equals(cachedPage)) return false;
        cachedPage = normalized;
        this.updatedAt = Math.max(0L, updatedAt);
        return true;
    }

    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        UuidNbt.write(tag, "clipboardId", clipboardId);
        tag.setString("displayName", displayName);
        tag.setLong("createdAt", createdAt);
        tag.setLong("updatedAt", updatedAt);
        tag.setTag("cachedPage", cachedPage.writeToNbt());
        return tag;
    }

    public static PlayerBindingRecord readFromNbt(NBTTagCompound tag) {
        UUID id = UuidNbt.read(tag, "clipboardId");
        if (id == null) return null;
        return new PlayerBindingRecord(
            id,
            tag.getString("displayName"),
            tag.getLong("createdAt"),
            tag.getLong("updatedAt"),
            ClipboardPageSnapshot.readFromNbt(tag.getCompoundTag("cachedPage")));
    }
}
