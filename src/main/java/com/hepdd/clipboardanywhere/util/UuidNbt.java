package com.hepdd.clipboardanywhere.util;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

public final class UuidNbt {

    private UuidNbt() {}

    public static void write(NBTTagCompound tag, String key, UUID value) {
        if (value == null) {
            tag.removeTag(key + "Most");
            tag.removeTag(key + "Least");
            return;
        }
        tag.setLong(key + "Most", value.getMostSignificantBits());
        tag.setLong(key + "Least", value.getLeastSignificantBits());
    }

    public static UUID read(NBTTagCompound tag, String key) {
        if (tag == null || !tag.hasKey(key + "Most") || !tag.hasKey(key + "Least")) {
            return null;
        }
        return new UUID(tag.getLong(key + "Most"), tag.getLong(key + "Least"));
    }
}
