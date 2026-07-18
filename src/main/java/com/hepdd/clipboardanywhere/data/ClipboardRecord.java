package com.hepdd.clipboardanywhere.data;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import com.hepdd.clipboardanywhere.util.UuidNbt;

public final class ClipboardRecord {

    private final UUID id;
    private boolean hasPlacedTarget;
    private int dimension;
    private int x;
    private int y;
    private int z;
    private long updatedAt;

    public ClipboardRecord(UUID id) {
        if (id == null) throw new IllegalArgumentException("id");
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public boolean hasPlacedTarget() {
        return hasPlacedTarget;
    }

    public int getDimension() {
        return dimension;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setPlacedTarget(int dimension, int x, int y, int z, long updatedAt) {
        this.hasPlacedTarget = true;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.updatedAt = Math.max(0L, updatedAt);
    }

    public void clearPlacedTarget(long updatedAt) {
        hasPlacedTarget = false;
        this.updatedAt = Math.max(0L, updatedAt);
    }

    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        UuidNbt.write(tag, "id", id);
        tag.setBoolean("hasPlacedTarget", hasPlacedTarget);
        tag.setInteger("dimension", dimension);
        tag.setInteger("x", x);
        tag.setInteger("y", y);
        tag.setInteger("z", z);
        tag.setLong("updatedAt", updatedAt);
        return tag;
    }

    public static ClipboardRecord readFromNbt(NBTTagCompound tag) {
        UUID id = UuidNbt.read(tag, "id");
        if (id == null) return null;
        ClipboardRecord record = new ClipboardRecord(id);
        if (tag.getBoolean("hasPlacedTarget")) {
            record.setPlacedTarget(
                tag.getInteger("dimension"),
                tag.getInteger("x"),
                tag.getInteger("y"),
                tag.getInteger("z"),
                tag.getLong("updatedAt"));
        } else {
            record.clearPlacedTarget(tag.getLong("updatedAt"));
        }
        return record;
    }
}
