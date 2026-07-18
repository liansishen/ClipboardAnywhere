package com.hepdd.clipboardanywhere.clipboard;

import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.hepdd.clipboardanywhere.util.UuidNbt;

import jds.bibliocraft.items.ItemClipboard;

public final class ClipboardIdentity {

    public static final String ROOT_KEY = "ClipboardAnywhere";
    private static final int SCHEMA_VERSION = 1;

    private ClipboardIdentity() {}

    public static boolean isClipboard(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemClipboard;
    }

    public static UUID getId(ItemStack stack) {
        if (!isClipboard(stack) || !stack.hasTagCompound()) {
            return null;
        }
        NBTTagCompound root = stack.getTagCompound();
        if (!root.hasKey(ROOT_KEY)) {
            return null;
        }
        NBTTagCompound identity = root.getCompoundTag(ROOT_KEY);
        UUID id = UuidNbt.read(identity, "id");
        if (id != null) {
            return id;
        }
        String legacyId = identity.getString("id");
        if (legacyId.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(legacyId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static UUID ensureId(ItemStack stack) {
        UUID id = getId(stack);
        if (id == null) {
            id = UUID.randomUUID();
        }
        setId(stack, id);
        return id;
    }

    public static void setId(ItemStack stack, UUID id) {
        if (!isClipboard(stack) || id == null) {
            throw new IllegalArgumentException("A BiblioCraft clipboard and non-null id are required");
        }
        NBTTagCompound root = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        NBTTagCompound identity = new NBTTagCompound();
        identity.setInteger("schema", SCHEMA_VERSION);
        UuidNbt.write(identity, "id", id);
        root.setTag(ROOT_KEY, identity);
        stack.setTagCompound(root);
    }

    public static void clearId(ItemStack stack) {
        if (stack != null && stack.hasTagCompound()) {
            stack.getTagCompound()
                .removeTag(ROOT_KEY);
        }
    }
}
