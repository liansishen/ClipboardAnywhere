package com.hepdd.clipboardanywhere.data;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.hepdd.clipboardanywhere.util.UuidNbt;

public final class PlayerBindings {

    private final UUID playerId;
    private final Map<UUID, PlayerBindingRecord> bindings = new LinkedHashMap<>();
    private UUID activeId;
    private boolean keepDisconnectedActive;

    public PlayerBindings(UUID playerId) {
        if (playerId == null) throw new IllegalArgumentException("playerId");
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getActiveId() {
        return activeId;
    }

    public boolean setActiveId(UUID activeId) {
        if (activeId != null && !bindings.containsKey(activeId)) return false;
        this.activeId = activeId;
        return true;
    }

    public boolean isKeepDisconnectedActive() {
        return keepDisconnectedActive;
    }

    public void setKeepDisconnectedActive(boolean keepDisconnectedActive) {
        this.keepDisconnectedActive = keepDisconnectedActive;
    }

    public PlayerBindingRecord get(UUID clipboardId) {
        return bindings.get(clipboardId);
    }

    public boolean contains(UUID clipboardId) {
        return bindings.containsKey(clipboardId);
    }

    public void put(PlayerBindingRecord binding) {
        bindings.put(binding.getClipboardId(), binding);
        if (activeId == null) activeId = binding.getClipboardId();
    }

    public PlayerBindingRecord remove(UUID clipboardId) {
        PlayerBindingRecord removed = bindings.remove(clipboardId);
        if (clipboardId != null && clipboardId.equals(activeId)) {
            activeId = bindings.isEmpty() ? null
                : bindings.keySet()
                    .iterator()
                    .next();
            keepDisconnectedActive = false;
        }
        return removed;
    }

    public Collection<PlayerBindingRecord> values() {
        return Collections.unmodifiableCollection(bindings.values());
    }

    public boolean isEmpty() {
        return bindings.isEmpty();
    }

    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        UuidNbt.write(tag, "playerId", playerId);
        UuidNbt.write(tag, "activeId", activeId);
        tag.setBoolean("keepDisconnectedActive", keepDisconnectedActive);
        NBTTagList list = new NBTTagList();
        for (PlayerBindingRecord binding : bindings.values()) {
            list.appendTag(binding.writeToNbt());
        }
        tag.setTag("bindings", list);
        return tag;
    }

    public static PlayerBindings readFromNbt(NBTTagCompound tag) {
        UUID playerId = UuidNbt.read(tag, "playerId");
        if (playerId == null) return null;
        PlayerBindings result = new PlayerBindings(playerId);
        NBTTagList list = tag.getTagList("bindings", 10);
        for (int index = 0; index < list.tagCount(); index++) {
            PlayerBindingRecord binding = PlayerBindingRecord.readFromNbt(list.getCompoundTagAt(index));
            if (binding != null) result.put(binding);
        }
        UUID activeId = UuidNbt.read(tag, "activeId");
        result.setActiveId(activeId != null && result.contains(activeId) ? activeId : null);
        result.keepDisconnectedActive = tag.getBoolean("keepDisconnectedActive");
        if (result.activeId == null && !result.bindings.isEmpty()) {
            result.activeId = result.bindings.keySet()
                .iterator()
                .next();
        }
        return result;
    }
}
