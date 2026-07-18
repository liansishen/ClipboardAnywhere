package com.hepdd.clipboardanywhere.data;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;

public final class ClipboardWorldData extends WorldSavedData {

    public static final String DATA_NAME = "clipboardanywhere_bindings";
    private static final int SCHEMA_VERSION = 1;

    private final Map<UUID, ClipboardRecord> clipboards = new LinkedHashMap<>();
    private final Map<UUID, PlayerBindings> players = new LinkedHashMap<>();

    public ClipboardWorldData() {
        this(DATA_NAME);
    }

    public ClipboardWorldData(String name) {
        super(name);
    }

    public static ClipboardWorldData get(World world) {
        if (world == null || world.isRemote) {
            throw new IllegalArgumentException("Clipboard world data is server-side only");
        }
        WorldServer storageWorld = MinecraftServer.getServer()
            .worldServerForDimension(0);
        ClipboardWorldData data = (ClipboardWorldData) storageWorld.mapStorage
            .loadData(ClipboardWorldData.class, DATA_NAME);
        if (data == null) {
            data = new ClipboardWorldData();
            storageWorld.mapStorage.setData(DATA_NAME, data);
            data.markDirty();
        }
        return data;
    }

    public ClipboardRecord getClipboard(UUID id) {
        return clipboards.get(id);
    }

    public ClipboardRecord getOrCreateClipboard(UUID id) {
        ClipboardRecord record = clipboards.get(id);
        if (record == null) {
            record = new ClipboardRecord(id);
            clipboards.put(id, record);
            markDirty();
        }
        return record;
    }

    public ClipboardRecord removeClipboard(UUID id) {
        ClipboardRecord removed = clipboards.remove(id);
        if (removed != null) markDirty();
        return removed;
    }

    public PlayerBindings getPlayer(UUID playerId) {
        return players.get(playerId);
    }

    public PlayerBindings getOrCreatePlayer(UUID playerId) {
        PlayerBindings result = players.get(playerId);
        if (result == null) {
            result = new PlayerBindings(playerId);
            players.put(playerId, result);
            markDirty();
        }
        return result;
    }

    public Collection<PlayerBindings> getPlayers() {
        return Collections.unmodifiableCollection(players.values());
    }

    public Collection<ClipboardRecord> getClipboards() {
        return Collections.unmodifiableCollection(clipboards.values());
    }

    public boolean isReferenced(UUID clipboardId) {
        for (PlayerBindings player : players.values()) {
            if (player.contains(clipboardId)) return true;
        }
        return false;
    }

    @Override
    public void readFromNBT(NBTTagCompound root) {
        clipboards.clear();
        players.clear();

        NBTTagList clipboardList = root.getTagList("clipboards", 10);
        for (int index = 0; index < clipboardList.tagCount(); index++) {
            ClipboardRecord record = ClipboardRecord.readFromNbt(clipboardList.getCompoundTagAt(index));
            if (record != null) clipboards.put(record.getId(), record);
        }

        NBTTagList playerList = root.getTagList("players", 10);
        for (int index = 0; index < playerList.tagCount(); index++) {
            PlayerBindings player = PlayerBindings.readFromNbt(playerList.getCompoundTagAt(index));
            if (player != null) players.put(player.getPlayerId(), player);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound root) {
        root.setInteger("schema", SCHEMA_VERSION);
        NBTTagList clipboardList = new NBTTagList();
        for (ClipboardRecord record : clipboards.values()) {
            clipboardList.appendTag(record.writeToNbt());
        }
        root.setTag("clipboards", clipboardList);

        NBTTagList playerList = new NBTTagList();
        for (PlayerBindings player : players.values()) {
            playerList.appendTag(player.writeToNbt());
        }
        root.setTag("players", playerList);
    }
}
