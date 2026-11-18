package com.hepdd.clipboardanywhere.server;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.hepdd.clipboardanywhere.clipboard.BiblioClipboardAdapter;
import com.hepdd.clipboardanywhere.clipboard.ClipboardIdentity;
import com.hepdd.clipboardanywhere.data.ClipboardRecord;
import com.hepdd.clipboardanywhere.data.PlayerBindingRecord;

import jds.bibliocraft.tileentities.TileEntityClipboard;

public final class ServerTargetResolver {

    public ServerTarget resolve(EntityPlayerMP player, ClipboardRecord clipboard, PlayerBindingRecord binding,
        long now) {
        UUID id = binding.getClipboardId();
        int inventorySlot = findInventorySlot(player.inventory.mainInventory, id);
        return resolveWithInventorySlot(player, inventorySlot, clipboard, binding, now);
    }

    public ServerTarget resolve(EntityPlayerMP player, Map<UUID, Integer> inventoryIndex, ClipboardRecord clipboard,
        PlayerBindingRecord binding, long now) {
        Integer slotObj = inventoryIndex == null ? null : inventoryIndex.get(binding.getClipboardId());
        int inventorySlot = slotObj == null ? -1 : slotObj.intValue();
        return resolveWithInventorySlot(player, inventorySlot, clipboard, binding, now);
    }

    private ServerTarget resolveWithInventorySlot(EntityPlayerMP player, int inventorySlot, ClipboardRecord clipboard,
        PlayerBindingRecord binding, long now) {
        if (inventorySlot >= 0 && inventorySlot < player.inventory.mainInventory.length) {
            ItemStack stack = player.inventory.mainInventory[inventorySlot];
            if (binding.getClipboardId()
                .equals(ClipboardIdentity.getId(stack))) {
                return ServerTarget.inventory(player, inventorySlot, stack, BiblioClipboardAdapter.read(stack, now));
            }
        }

        if (clipboard == null || !clipboard.hasPlacedTarget()) {
            return ServerTarget.disconnected(binding.getCachedPage());
        }
        WorldServer world = DimensionManager.getWorld(clipboard.getDimension());
        if (world == null || !world.getChunkProvider()
            .chunkExists(clipboard.getX() >> 4, clipboard.getZ() >> 4)) {
            return ServerTarget.disconnected(binding.getCachedPage());
        }
        TileEntity rawTile = world.getTileEntity(clipboard.getX(), clipboard.getY(), clipboard.getZ());
        if (!(rawTile instanceof TileEntityClipboard)) {
            return ServerTarget.disconnected(binding.getCachedPage());
        }
        TileEntityClipboard tile = (TileEntityClipboard) rawTile;
        if (!binding.getClipboardId()
            .equals(ClipboardIdentity.getId(tile.getStackInSlot(0)))) {
            return ServerTarget.disconnected(binding.getCachedPage());
        }
        return ServerTarget.placed(tile, BiblioClipboardAdapter.read(tile, now));
    }

    public Map<UUID, Integer> indexInventoryClipboards(ItemStack[] inventory) {
        if (inventory == null) return Collections.emptyMap();
        Map<UUID, Integer> slots = null;
        for (int slot = 0; slot < inventory.length; slot++) {
            UUID id = ClipboardIdentity.getId(inventory[slot]);
            if (id != null) {
                if (slots == null) slots = new HashMap<>();
                if (!slots.containsKey(id)) {
                    slots.put(id, Integer.valueOf(slot));
                }
            }
        }
        return slots == null ? Collections.<UUID, Integer>emptyMap() : slots;
    }

    static int findInventorySlot(ItemStack[] inventory, UUID id) {
        if (inventory == null || id == null) return -1;
        for (int slot = 0; slot < inventory.length; slot++) {
            if (id.equals(ClipboardIdentity.getId(inventory[slot]))) return slot;
        }
        return -1;
    }
}
