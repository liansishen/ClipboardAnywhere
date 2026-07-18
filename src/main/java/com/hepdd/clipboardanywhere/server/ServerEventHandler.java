package com.hepdd.clipboardanywhere.server;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;

import com.hepdd.clipboardanywhere.clipboard.ClipboardIdentity;
import com.hepdd.clipboardanywhere.network.NetworkHandler;
import com.hepdd.clipboardanywhere.network.message.C2SBindHeldClipboard;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class ServerEventHandler {

    public static final ServerEventHandler INSTANCE = new ServerEventHandler();
    private static final int POLL_INTERVAL_TICKS = 10;
    private int tickCounter;
    private int lastClientBindTick = -1000;

    private ServerEventHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack held = event.entityPlayer.getCurrentEquippedItem();
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR || !event.entityPlayer.isSneaking()
            || !ClipboardIdentity.isClipboard(held)) {
            return;
        }
        event.setCanceled(true);
        if (event.world.isRemote) {
            int currentTick = event.entityPlayer.ticksExisted;
            if (currentTick < lastClientBindTick || currentTick - lastClientBindTick >= 6) {
                lastClientBindTick = currentTick;
                NetworkHandler.sendToServer(new C2SBindHeldClipboard());
            }
        } else if (event.entityPlayer instanceof EntityPlayerMP) {
            ClipboardServerService.INSTANCE.bindHeldClipboard((EntityPlayerMP) event.entityPlayer);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockPlaced(BlockEvent.PlaceEvent event) {
        if (event.world.isRemote || event.isCanceled() || !(event.world instanceof WorldServer)) return;
        final WorldServer world = (WorldServer) event.world;
        final int x = event.x;
        final int y = event.y;
        final int z = event.z;
        ServerTaskQueue.INSTANCE.enqueue(() -> ClipboardServerService.INSTANCE.trackPlacedClipboard(world, x, y, z));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.world.isRemote || event.isCanceled() || !(event.world instanceof WorldServer)) return;
        ClipboardServerService.INSTANCE.trackBrokenClipboard((WorldServer) event.world, event.x, event.y, event.z);
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            ClipboardServerService.INSTANCE.sendState((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            ClipboardServerService.INSTANCE.sendState((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ClipboardServerService.INSTANCE.forgetPlayer(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tickCounter < POLL_INTERVAL_TICKS) return;
        tickCounter = 0;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return;
        List<?> players = server.getConfigurationManager().playerEntityList;
        for (Object entry : players) {
            ClipboardServerService.INSTANCE.sendStateIfChanged((EntityPlayerMP) entry);
        }
    }
}
