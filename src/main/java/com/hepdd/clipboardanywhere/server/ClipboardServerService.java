package com.hepdd.clipboardanywhere.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.hepdd.clipboardanywhere.clipboard.BiblioClipboardAdapter;
import com.hepdd.clipboardanywhere.clipboard.ClipboardIdentity;
import com.hepdd.clipboardanywhere.data.ClipboardRecord;
import com.hepdd.clipboardanywhere.data.ClipboardWorldData;
import com.hepdd.clipboardanywhere.data.PlayerBindingRecord;
import com.hepdd.clipboardanywhere.data.PlayerBindings;
import com.hepdd.clipboardanywhere.model.BindingView;
import com.hepdd.clipboardanywhere.model.ClipboardAction;
import com.hepdd.clipboardanywhere.model.ClipboardPageSnapshot;
import com.hepdd.clipboardanywhere.model.PlayerBindingSnapshot;
import com.hepdd.clipboardanywhere.model.TargetStatus;
import com.hepdd.clipboardanywhere.network.NetworkCodec;
import com.hepdd.clipboardanywhere.network.NetworkCodec.BindingStatePayload;
import com.hepdd.clipboardanywhere.network.NetworkCodec.BindingWireEntry;
import com.hepdd.clipboardanywhere.network.NetworkHandler;
import com.hepdd.clipboardanywhere.network.message.S2CBindingState;
import com.hepdd.clipboardanywhere.network.message.S2COperationResult;

import jds.bibliocraft.tileentities.TileEntityClipboard;

public final class ClipboardServerService {

    public static final ClipboardServerService INSTANCE = new ClipboardServerService();
    static final int BIND_COOLDOWN_TICKS = 6;
    private final ServerTargetResolver resolver = new ServerTargetResolver();
    private final Map<UUID, PlayerSessionSyncState> sessionStates = new LinkedHashMap<>();

    private ClipboardServerService() {}

    public void recordClientSeq(EntityPlayerMP player, int clientSeq) {
        if (player == null) return;
        stateFor(player.getUniqueID()).lastAckSeq = clientSeq;
    }

    public void sendFullState(EntityPlayerMP player) {
        if (player == null || player.playerNetServerHandler == null) return;
        PlayerSessionSyncState state = stateFor(player.getUniqueID());
        state.sentPages.clear();
        sendState(player);
    }

    public void sendState(EntityPlayerMP player) {
        if (player == null || player.playerNetServerHandler == null) return;
        PlayerSessionSyncState state = stateFor(player.getUniqueID());
        PlayerBindingSnapshot snapshot = buildSnapshot(player);
        BindingStatePayload payload = buildIncrementalPayload(snapshot, state);
        state.lastSnapshot = snapshot;
        NetworkHandler.sendTo(new S2CBindingState(payload), player);
    }

    public void sendStateIfChanged(EntityPlayerMP player) {
        if (player == null || player.playerNetServerHandler == null) return;
        PlayerSessionSyncState state = stateFor(player.getUniqueID());
        PlayerBindingSnapshot snapshot = buildSnapshot(player);
        if (snapshot.equals(state.lastSnapshot)) return;
        BindingStatePayload payload = buildIncrementalPayload(snapshot, state);
        state.lastSnapshot = snapshot;
        NetworkHandler.sendTo(new S2CBindingState(payload), player);
    }

    public void forgetPlayer(UUID playerId) {
        sessionStates.remove(playerId);
    }

    public void bindHeldClipboard(EntityPlayerMP player) {
        if (player == null || player.playerNetServerHandler == null) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null) {
            PlayerSessionSyncState state = stateFor(player.getUniqueID());
            if (!state.tryAcquireBindSlot(server.getTickCounter(), BIND_COOLDOWN_TICKS)) {
                return;
            }
        }

        ItemStack held = player.getCurrentEquippedItem();
        if (!ClipboardIdentity.isClipboard(held)) return;

        BiblioClipboardAdapter.ensureStructure(held);

        ClipboardWorldData data = ClipboardWorldData.get(player.worldObj);
        UUID id = ClipboardIdentity.getId(held);
        if (id != null && hasStableDuplicate(player, held, id, data)) {
            id = UUID.randomUUID();
        }
        if (id == null) id = UUID.randomUUID();

        long now = player.worldObj.getTotalWorldTime();
        PlayerBindings playerBindings = data.getOrCreatePlayer(player.getUniqueID());
        boolean created = !playerBindings.contains(id);
        if (created && playerBindings.size() >= NetworkCodec.MAX_BINDINGS) {
            sendFailureAndState(player, id, "message.clipboardanywhere.binding_limit");
            return;
        }

        ClipboardIdentity.setId(held, id);
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
        data.getOrCreateClipboard(id);
        if (created) {
            ClipboardPageSnapshot page = BiblioClipboardAdapter.read(held, now);
            playerBindings.put(new PlayerBindingRecord(id, defaultName(playerBindings, page), now, now, page));
        }
        playerBindings.setActiveId(id);
        playerBindings.setKeepDisconnectedActive(false);
        data.markDirty();

        sendResult(player, true, created ? "message.clipboardanywhere.bound" : "message.clipboardanywhere.selected");
        sendState(player);
    }

    public void selectBinding(EntityPlayerMP player, UUID id) {
        ClipboardWorldData data = ClipboardWorldData.get(player.worldObj);
        PlayerBindings playerBindings = data.getPlayer(player.getUniqueID());
        PlayerBindingRecord binding = playerBindings == null ? null : playerBindings.get(id);
        if (binding == null) {
            sendFailureAndState(player, id, "message.clipboardanywhere.invalid_binding");
            return;
        }
        ServerTarget target = resolver
            .resolve(player, data.getClipboard(id), binding, player.worldObj.getTotalWorldTime());
        playerBindings.setActiveId(id);
        playerBindings.setKeepDisconnectedActive(
            !target.getStatus()
                .isReadable());
        data.markDirty();
        sendState(player);
    }

    public void renameBinding(EntityPlayerMP player, UUID id, String requestedName) {
        ClipboardWorldData data = ClipboardWorldData.get(player.worldObj);
        PlayerBindings playerBindings = data.getPlayer(player.getUniqueID());
        PlayerBindingRecord binding = playerBindings == null ? null : playerBindings.get(id);
        String name = sanitizeName(requestedName);
        if (binding == null || name.isEmpty()) {
            sendFailureAndState(player, id, "message.clipboardanywhere.invalid_name");
            return;
        }
        long now = player.worldObj.getTotalWorldTime();
        ServerTarget target = resolver.resolve(player, data.getClipboard(id), binding, now);
        if (!target.getStatus()
            .isReadable()) {
            sendFailureAndState(player, id, "message.clipboardanywhere.disconnected");
            return;
        }
        binding.setDisplayName(name, now);
        data.markDirty();
        sendResult(player, true, "message.clipboardanywhere.renamed");
        sendState(player);
    }

    public void unbind(EntityPlayerMP player, UUID id) {
        ClipboardWorldData data = ClipboardWorldData.get(player.worldObj);
        PlayerBindings playerBindings = data.getPlayer(player.getUniqueID());
        PlayerBindingRecord binding = playerBindings == null ? null : playerBindings.get(id);
        if (binding == null) {
            sendFailureAndState(player, id, "message.clipboardanywhere.invalid_binding");
            return;
        }
        ServerTarget target = resolver
            .resolve(player, data.getClipboard(id), binding, player.worldObj.getTotalWorldTime());
        playerBindings.remove(id);
        playerBindings.setKeepDisconnectedActive(false);
        if (!data.isReferenced(id)) {
            ItemStack identityStack = target.getIdentityStack();
            if (identityStack != null) {
                ClipboardIdentity.clearId(identityStack);
                target.identityChanged();
            }
            data.removeClipboard(id);
        }
        data.markDirty();
        sendResult(player, true, "message.clipboardanywhere.unbound");
        sendState(player);
    }

    public void trackPlacedClipboard(WorldServer world, int x, int y, int z) {
        TileEntity rawTile = world.getTileEntity(x, y, z);
        if (!(rawTile instanceof TileEntityClipboard)) return;
        TileEntityClipboard tile = (TileEntityClipboard) rawTile;
        ItemStack stack = tile.getStackInSlot(0);
        UUID id = ClipboardIdentity.getId(stack);
        if (id == null) return;
        if (BiblioClipboardAdapter.ensureStructure(stack)) {
            tile.getNBTData();
            tile.markDirty();
            world.markBlockForUpdate(x, y, z);
        }
        ClipboardWorldData data = ClipboardWorldData.get(world);
        ClipboardRecord record = data.getClipboard(id);
        if (record == null || !data.isReferenced(id)) return;
        record.setPlacedTarget(world.provider.dimensionId, x, y, z, world.getTotalWorldTime());
        data.markDirty();
        sendToBoundOnlinePlayers(data, id);
    }

    public void trackBrokenClipboard(WorldServer world, int x, int y, int z) {
        TileEntity rawTile = world.getTileEntity(x, y, z);
        if (!(rawTile instanceof TileEntityClipboard)) return;
        TileEntityClipboard tile = (TileEntityClipboard) rawTile;
        UUID id = ClipboardIdentity.getId(tile.getStackInSlot(0));
        if (id == null) return;
        ClipboardWorldData data = ClipboardWorldData.get(world);
        ClipboardRecord record = data.getClipboard(id);
        if (record == null) return;
        long now = world.getTotalWorldTime();
        ClipboardPageSnapshot page = BiblioClipboardAdapter.read(tile, now);
        for (PlayerBindings bindings : data.getPlayers()) {
            PlayerBindingRecord binding = bindings.get(id);
            if (binding != null && !binding.getCachedPage()
                .hasSameContent(page)) {
                binding.updateCachedPage(page, now);
            }
        }
        record.clearPlacedTarget(now);
        data.markDirty();
        sendToBoundOnlinePlayers(data, id);
    }

    public void performAction(EntityPlayerMP player, UUID id, ClipboardAction action, int row) {
        ClipboardWorldData data = ClipboardWorldData.get(player.worldObj);
        PlayerBindings playerBindings = data.getPlayer(player.getUniqueID());
        PlayerBindingRecord binding = playerBindings == null ? null : playerBindings.get(id);
        if (binding == null || action == null || action == ClipboardAction.CYCLE_TASK && (row < 0 || row >= 9)) {
            sendFailureAndState(player, id, "message.clipboardanywhere.invalid_action");
            return;
        }
        long now = player.worldObj.getTotalWorldTime();
        ServerTarget target = resolver.resolve(player, data.getClipboard(id), binding, now);
        if (!target.getStatus()
            .isReadable()) {
            sendFailureAndState(player, id, "message.clipboardanywhere.disconnected");
            return;
        }
        if (!target.apply(action, row)) {
            sendFailureAndState(player, id, "message.clipboardanywhere.action_unavailable");
            return;
        }
        ClipboardPageSnapshot fresh = target.readSnapshot(now);
        if (!binding.getCachedPage()
            .hasSameContent(fresh)) {
            binding.updateCachedPage(fresh, now);
            data.markDirty();
        }
        sendState(player);
        sendToBoundOnlinePlayers(data, id);
    }

    public void updateTaskText(EntityPlayerMP player, UUID id, int pageNumber, int row, String expectedText,
        String requestedText) {
        ClipboardWorldData data = ClipboardWorldData.get(player.worldObj);
        PlayerBindings playerBindings = data.getPlayer(player.getUniqueID());
        PlayerBindingRecord binding = playerBindings == null ? null : playerBindings.get(id);
        String text = sanitizeTaskText(requestedText);
        if (binding == null || pageNumber < 1
            || row < 0
            || row >= ClipboardPageSnapshot.TASK_COUNT
            || expectedText == null
            || expectedText.length() > NetworkCodec.MAX_TASK_TEXT_CHARS
            || text == null) {
            sendFailureAndState(player, id, "message.clipboardanywhere.invalid_task_text");
            return;
        }
        long now = player.worldObj.getTotalWorldTime();
        ServerTarget target = resolver.resolve(player, data.getClipboard(id), binding, now);
        if (!target.getStatus()
            .isReadable()) {
            sendFailureAndState(player, id, "message.clipboardanywhere.disconnected");
            return;
        }
        ClipboardPageSnapshot current = target.getSnapshot();
        if (pageNumber != current.getCurrentPage() || row >= ClipboardPageSnapshot.TASK_COUNT
            || !current.getTask(row)
                .equals(expectedText)) {
            sendFailureAndState(player, id, "message.clipboardanywhere.task_text_conflict");
            return;
        }
        if (!target.updateTaskText(pageNumber, row, expectedText, text)) {
            sendFailureAndState(player, id, "message.clipboardanywhere.action_unavailable");
            return;
        }
        ClipboardPageSnapshot fresh = target.readSnapshot(now);
        if (!binding.getCachedPage()
            .hasSameContent(fresh)) {
            binding.updateCachedPage(fresh, now);
            data.markDirty();
        }
        sendResult(player, true, "message.clipboardanywhere.task_text_updated");
        sendState(player);
        sendToBoundOnlinePlayers(data, id);
    }

    public PlayerBindingSnapshot buildSnapshot(EntityPlayerMP player) {
        ClipboardWorldData data = ClipboardWorldData.get(player.worldObj);
        PlayerBindings playerBindings = data.getPlayer(player.getUniqueID());
        if (playerBindings == null || playerBindings.isEmpty()) {
            return PlayerBindingSnapshot.EMPTY;
        }

        long now = player.worldObj.getTotalWorldTime();
        Map<UUID, Integer> inventoryIndex = resolver.indexInventoryClipboards(player.inventory.mainInventory);
        Map<UUID, TargetStatus> statuses = new LinkedHashMap<>();
        UUID firstReadable = null;
        for (PlayerBindingRecord binding : playerBindings.values()) {
            ClipboardRecord clipboard = data.getClipboard(binding.getClipboardId());
            ServerTarget target = resolver.resolve(player, inventoryIndex, clipboard, binding, now);
            statuses.put(binding.getClipboardId(), target.getStatus());
            if (target.getStatus()
                .isReadable()) {
                if (firstReadable == null) firstReadable = binding.getClipboardId();
                ClipboardPageSnapshot fresh = target.getSnapshot();
                if (!binding.getCachedPage()
                    .hasSameContent(fresh)) {
                    binding.updateCachedPage(fresh, now);
                    data.markDirty();
                }
            }
        }

        UUID previousActiveId = playerBindings.getActiveId();
        boolean previousKeepDisconnected = playerBindings.isKeepDisconnectedActive();
        UUID activeId = reconcileActiveBinding(playerBindings, statuses, firstReadable);
        if (!(previousActiveId == null ? activeId == null : previousActiveId.equals(activeId))
            || previousKeepDisconnected != playerBindings.isKeepDisconnectedActive()) data.markDirty();

        List<BindingView> readable = new ArrayList<>();
        List<BindingView> disconnected = new ArrayList<>();
        for (PlayerBindingRecord binding : playerBindings.values()) {
            TargetStatus status = statuses.get(binding.getClipboardId());
            BindingView view = new BindingView(
                binding.getClipboardId(),
                binding.getDisplayName(),
                status,
                binding.getCachedPage());
            (status != null && status.isReadable() ? readable : disconnected).add(view);
        }
        readable.addAll(disconnected);
        return new PlayerBindingSnapshot(activeId, readable);
    }

    static BindingStatePayload buildIncrementalPayload(PlayerBindingSnapshot snapshot, PlayerSessionSyncState state) {
        PlayerBindingSnapshot value = snapshot == null ? PlayerBindingSnapshot.EMPTY : snapshot;
        List<BindingView> bindings = value.getBindings();
        Set<UUID> currentIds = new HashSet<>(bindings.size());
        List<BindingWireEntry> entries = new ArrayList<>(bindings.size());
        for (BindingView binding : bindings) {
            UUID id = binding.getId();
            currentIds.add(id);
            ClipboardPageSnapshot lastSentPage = state.sentPages.get(id);
            if (lastSentPage == null || !lastSentPage.equals(binding.getSnapshot())) {
                state.sentPages.put(id, binding.getSnapshot());
                entries.add(
                    BindingWireEntry
                        .withPage(id, binding.getDisplayName(), binding.getStatus(), binding.getSnapshot()));
            } else {
                entries.add(BindingWireEntry.summaryOnly(id, binding.getDisplayName(), binding.getStatus()));
            }
        }
        state.sentPages.keySet()
            .retainAll(currentIds);
        return new BindingStatePayload(value.getActiveId(), state.lastAckSeq, entries);
    }

    static UUID reconcileActiveBinding(PlayerBindings bindings, Map<UUID, TargetStatus> statuses, UUID firstReadable) {
        UUID activeId = bindings.getActiveId();
        TargetStatus activeStatus = statuses.get(activeId);
        if (activeStatus != null && activeStatus.isReadable()) {
            bindings.setKeepDisconnectedActive(false);
        } else if (!bindings.isKeepDisconnectedActive() && firstReadable != null) {
            bindings.setActiveId(firstReadable);
            activeId = firstReadable;
        }
        return activeId;
    }

    private PlayerSessionSyncState stateFor(UUID playerId) {
        PlayerSessionSyncState state = sessionStates.get(playerId);
        if (state == null) {
            state = new PlayerSessionSyncState();
            sessionStates.put(playerId, state);
        }
        return state;
    }

    private void sendFailureAndState(EntityPlayerMP player, UUID affectedBindingId, String translationKey) {
        if (player != null && affectedBindingId != null) {
            stateFor(player.getUniqueID()).sentPages.remove(affectedBindingId);
        }
        sendResult(player, false, translationKey);
        sendState(player);
    }

    private void sendResult(EntityPlayerMP player, boolean success, String translationKey) {
        NetworkHandler.sendTo(new S2COperationResult(success, translationKey), player);
    }

    private void sendToBoundOnlinePlayers(ClipboardWorldData data, UUID id) {
        List<?> onlinePlayers = MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList;
        for (Object entry : onlinePlayers) {
            EntityPlayerMP player = (EntityPlayerMP) entry;
            PlayerBindings bindings = data.getPlayer(player.getUniqueID());
            if (bindings != null && bindings.contains(id)) sendStateIfChanged(player);
        }
    }

    private boolean hasStableDuplicate(EntityPlayerMP bindingPlayer, ItemStack held, UUID id, ClipboardWorldData data) {
        List<?> onlinePlayers = MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList;
        for (Object entry : onlinePlayers) {
            EntityPlayerMP player = (EntityPlayerMP) entry;
            if (containsInventoryDuplicate(player.inventory.mainInventory, held, id)) return true;
        }

        ClipboardRecord record = data.getClipboard(id);
        if (record == null || !record.hasPlacedTarget()) return false;
        WorldServer world = DimensionManager.getWorld(record.getDimension());
        if (world == null || !world.getChunkProvider()
            .chunkExists(record.getX() >> 4, record.getZ() >> 4)) return false;
        TileEntity tile = world.getTileEntity(record.getX(), record.getY(), record.getZ());
        return tile instanceof TileEntityClipboard
            && id.equals(ClipboardIdentity.getId(((TileEntityClipboard) tile).getStackInSlot(0)));
    }

    static boolean containsInventoryDuplicate(ItemStack[] inventory, ItemStack excluded, UUID id) {
        if (inventory == null || id == null) return false;
        for (ItemStack stack : inventory) {
            if (stack != excluded && id.equals(ClipboardIdentity.getId(stack))) return true;
        }
        return false;
    }

    private static String defaultName(PlayerBindings bindings, ClipboardPageSnapshot page) {
        String title = sanitizeName(page.getTitle());
        if (!title.isEmpty() && !nameExists(bindings, title)) return title;
        int index = 1;
        String candidate;
        do {
            candidate = "Clipboard " + index++;
        } while (nameExists(bindings, candidate));
        return candidate;
    }

    private static boolean nameExists(PlayerBindings bindings, String name) {
        for (PlayerBindingRecord binding : bindings.values()) {
            if (binding.getDisplayName()
                .equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    static String sanitizeName(String input) {
        return NetworkCodec.sanitizeName(input);
    }

    static String sanitizeTaskText(String input) {
        return NetworkCodec.sanitizeTaskText(input);
    }

    static final class PlayerSessionSyncState {

        PlayerBindingSnapshot lastSnapshot;
        final Map<UUID, ClipboardPageSnapshot> sentPages = new HashMap<>();
        int lastAckSeq;
        private int lastBindTick;
        private boolean hasBoundBefore;

        boolean tryAcquireBindSlot(int serverTick, int cooldownTicks) {
            if (hasBoundBefore && serverTick - lastBindTick >= 0 && serverTick - lastBindTick < cooldownTicks) {
                return false;
            }
            hasBoundBefore = true;
            lastBindTick = serverTick;
            return true;
        }
    }
}
