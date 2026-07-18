package com.hepdd.clipboardanywhere.server;

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
        if (inventorySlot >= 0) {
            ItemStack stack = player.inventory.mainInventory[inventorySlot];
            if (BiblioClipboardAdapter.ensureStructure(stack)) {
                player.inventory.markDirty();
                player.inventoryContainer.detectAndSendChanges();
            }
            return ServerTarget.inventory(player, inventorySlot, stack, BiblioClipboardAdapter.read(stack, now));
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
        if (!id.equals(ClipboardIdentity.getId(tile.getStackInSlot(0)))) {
            return ServerTarget.disconnected(binding.getCachedPage());
        }
        if (BiblioClipboardAdapter.ensureStructure(tile.getStackInSlot(0))) {
            tile.getNBTData();
            tile.markDirty();
            world.markBlockForUpdate(clipboard.getX(), clipboard.getY(), clipboard.getZ());
        }
        return ServerTarget.placed(tile, BiblioClipboardAdapter.read(tile, now));
    }

    static int findInventorySlot(ItemStack[] inventory, UUID id) {
        if (inventory == null || id == null) return -1;
        for (int slot = 0; slot < inventory.length; slot++) {
            if (id.equals(ClipboardIdentity.getId(inventory[slot]))) return slot;
        }
        return -1;
    }
}
