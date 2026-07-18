package com.hepdd.clipboardanywhere.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.hepdd.clipboardanywhere.data.ClipboardRecord;
import com.hepdd.clipboardanywhere.data.ClipboardWorldData;
import com.hepdd.clipboardanywhere.data.PlayerBindingRecord;
import com.hepdd.clipboardanywhere.data.PlayerBindings;
import com.hepdd.clipboardanywhere.model.BindingView;
import com.hepdd.clipboardanywhere.model.ClipboardPageSnapshot;
import com.hepdd.clipboardanywhere.model.PlayerBindingSnapshot;
import com.hepdd.clipboardanywhere.model.TargetStatus;
import com.hepdd.clipboardanywhere.network.NetworkHandler;
import com.hepdd.clipboardanywhere.network.message.S2CBindingState;

public final class ClipboardServerService {

    public static final ClipboardServerService INSTANCE = new ClipboardServerService();
    private final ServerTargetResolver resolver = new ServerTargetResolver();

    private ClipboardServerService() {}

    public void sendState(EntityPlayerMP player) {
        if (player == null || player.playerNetServerHandler == null) return;
        NetworkHandler.sendTo(new S2CBindingState(buildSnapshot(player)), player);
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
}
