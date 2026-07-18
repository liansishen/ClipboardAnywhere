package com.hepdd.clipboardanywhere.server;

import static org.junit.Assert.assertEquals;

import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.Test;

import com.hepdd.clipboardanywhere.clipboard.ClipboardIdentity;

import jds.bibliocraft.items.ItemClipboard;

public class ServerTargetResolverTest {

    @Test
    public void findsClipboardIdentityInAnyMainInventorySlot() {
        UUID id = UUID.randomUUID();
        ItemStack clipboard = new ItemStack(new ItemClipboard());
        ClipboardIdentity.setId(clipboard, id);
        ItemStack[] inventory = new ItemStack[36];
        inventory[0] = new ItemStack(Items.stick);
        inventory[27] = clipboard;

        assertEquals(27, ServerTargetResolver.findInventorySlot(inventory, id));
        assertEquals(-1, ServerTargetResolver.findInventorySlot(inventory, UUID.randomUUID()));
        assertEquals(-1, ServerTargetResolver.findInventorySlot(null, id));
        assertEquals(-1, ServerTargetResolver.findInventorySlot(inventory, null));
    }
}
