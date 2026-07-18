package com.hepdd.clipboardanywhere.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.hepdd.clipboardanywhere.clipboard.BiblioClipboardAdapter;
import com.hepdd.clipboardanywhere.model.ClipboardAction;
import com.hepdd.clipboardanywhere.model.ClipboardPageSnapshot;
import com.hepdd.clipboardanywhere.model.TargetStatus;

import jds.bibliocraft.tileentities.TileEntityClipboard;

public final class ServerTarget {

    private final TargetStatus status;
    private final EntityPlayerMP player;
    private final int inventorySlot;
    private final ItemStack itemStack;
    private final TileEntityClipboard tile;
    private final ClipboardPageSnapshot snapshot;

    private ServerTarget(TargetStatus status, EntityPlayerMP player, int inventorySlot, ItemStack itemStack,
        TileEntityClipboard tile, ClipboardPageSnapshot snapshot) {
        this.status = status;
        this.player = player;
        this.inventorySlot = inventorySlot;
        this.itemStack = itemStack;
        this.tile = tile;
        this.snapshot = snapshot == null ? ClipboardPageSnapshot.EMPTY : snapshot;
    }

    public static ServerTarget inventory(EntityPlayerMP player, int slot, ItemStack stack,
        ClipboardPageSnapshot snapshot) {
        return new ServerTarget(TargetStatus.READABLE_INVENTORY, player, slot, stack, null, snapshot);
    }

    public static ServerTarget placed(TileEntityClipboard tile, ClipboardPageSnapshot snapshot) {
        return new ServerTarget(TargetStatus.READABLE_PLACED, null, -1, null, tile, snapshot);
    }

    public static ServerTarget disconnected(ClipboardPageSnapshot snapshot) {
        return new ServerTarget(TargetStatus.DISCONNECTED, null, -1, null, null, snapshot);
    }

    public TargetStatus getStatus() {
        return status;
    }

    public ClipboardPageSnapshot getSnapshot() {
        return snapshot;
    }

    public ItemStack getIdentityStack() {
        return status == TargetStatus.READABLE_INVENTORY ? itemStack
            : status == TargetStatus.READABLE_PLACED ? tile.getStackInSlot(0) : null;
    }

    public boolean apply(ClipboardAction action, int row) {
        if (status == TargetStatus.READABLE_INVENTORY) {
            boolean changed = BiblioClipboardAdapter.apply(itemStack, action, row);
            if (changed) {
                player.inventory.setInventorySlotContents(inventorySlot, itemStack);
                player.inventory.markDirty();
                player.inventoryContainer.detectAndSendChanges();
            }
            return changed;
        }
        return status == TargetStatus.READABLE_PLACED && BiblioClipboardAdapter.apply(tile, action, row);
    }
}
