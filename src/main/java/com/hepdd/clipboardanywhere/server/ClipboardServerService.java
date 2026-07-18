package com.hepdd.clipboardanywhere.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.hepdd.clipboardanywhere.network.NetworkHandler;
import com.hepdd.clipboardanywhere.network.message.S2CBindingState;
import com.hepdd.clipboardanywhere.network.message.S2COperationResult;

import jds.bibliocraft.tileentities.TileEntityClipboard;

public final class ClipboardServerService {

    public static final ClipboardServerService INSTANCE = new ClipboardServerService();
    private final ServerTargetResolver resolver = new ServerTargetResolver();
    private final Map<UUID, PlayerBindingSnapshot> lastSentSnapshots = new LinkedHashMap<>();

    private ClipboardServerService() {}

    public void sendState(EntityPlayerMP player) {
        if (player == null || player.playerNetServerHandler == null) return;
        PlayerBindingSnapshot snapshot = buildSnapshot(player);
        lastSentSnapshots.put(player.getUniqueID(), snapshot);
        NetworkHandler.sendTo(new S2CBindingState(snapshot), player);
    }

    public void sendStateIfChanged(EntityPlayerMP player) {
        if (player == null || player.playerNetServerHandler == null) return;
        PlayerBindingSnapshot snapshot = buildSnapshot(player);
        if (snapshot.equals(lastSentSnapshots.get(player.getUniqueID()))) return;
        lastSentSnapshots.put(player.getUniqueID(), snapshot);
        NetworkHandler.sendTo(new S2CBindingState(snapshot), player);
    }

    public void forgetPlayer(UUID playerId) {
        lastSentSnapshots.remove(playerId);
    }

    public void bindHeldClipboard(EntityPlayerMP player) {
        ItemStack held = player.getCurrentEquippedItem();
        if (!ClipboardIdentity.isClipboard(held)) return;

        ClipboardWorldData data = ClipboardWorldData.get(player.worldObj);
        UUID id = ClipboardIdentity.getId(held);
        if (id != null && hasStableDuplicate(player, held, id, data)) {
            id = UUID.randomUUID();
        }
        if (id == null) id = UUID.randomUUID();
        ClipboardIdentity.setId(held, id);

        long now = player.worldObj.getTotalWorldTime();
        data.getOrCreateClipboard(id);
        PlayerBindings playerBindings = data.getOrCreatePlayer(player.getUniqueID());
        boolean created = !playerBindings.contains(id);
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
            sendFailureAndState(player, "message.clipboardanywhere.invalid_binding");
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
            sendFailureAndState(player, "message.clipboardanywhere.invalid_name");
            return;
        }
        long now = player.worldObj.getTotalWorldTime();
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
            sendFailureAndState(player, "message.clipboardanywhere.invalid_binding");
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
        UUID id = ClipboardIdentity.getId(tile.getStackInSlot(0));
        if (id == null) return;
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
            sendFailureAndState(player, "message.clipboardanywhere.invalid_action");
            return;
        }
        ServerTarget target = resolver
            .resolve(player, data.getClipboard(id), binding, player.worldObj.getTotalWorldTime());
        if (!target.getStatus()
            .isReadable()) {
            sendFailureAndState(player, "message.clipboardanywhere.disconnected");
            return;
        }
        if (!target.apply(action, row)) {
            sendFailureAndState(player, "message.clipboardanywhere.action_unavailable");
            return;
        }
        sendState(player);
    }

    public PlayerBindingSnapshot buildSnapshot(EntityPlayerMP player) {
        ClipboardWorldData data = ClipboardWorldData.get(player.worldObj);
        PlayerBindings playerBindings = data.getPlayer(player.getUniqueID());
        if (playerBindings == null || playerBindings.isEmpty()) {
            return PlayerBindingSnapshot.EMPTY;
        }

        long now = player.worldObj.getTotalWorldTime();
        Map<UUID, TargetStatus> statuses = new LinkedHashMap<>();
        UUID firstReadable = null;
        for (PlayerBindingRecord binding : playerBindings.values()) {
            ClipboardRecord clipboard = data.getClipboard(binding.getClipboardId());
            ServerTarget target = resolver.resolve(player, clipboard, binding, now);
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

        UUID activeId = playerBindings.getActiveId();
        TargetStatus activeStatus = statuses.get(activeId);
        if (activeStatus != null && activeStatus.isReadable()) {
            if (playerBindings.isKeepDisconnectedActive()) {
                playerBindings.setKeepDisconnectedActive(false);
                data.markDirty();
            }
        } else if (!playerBindings.isKeepDisconnectedActive() && firstReadable != null) {
            playerBindings.setActiveId(firstReadable);
            activeId = firstReadable;
            data.markDirty();
        }

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

    private void sendFailureAndState(EntityPlayerMP player, String translationKey) {
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
            for (ItemStack stack : player.inventory.mainInventory) {
                if (stack != held && id.equals(ClipboardIdentity.getId(stack))) return true;
            }
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
        if (input == null) return "";
        StringBuilder output = new StringBuilder();
        boolean skipFormattingValue = false;
        for (int index = 0; index < input.length() && output.length() < 32; index++) {
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
}
