package com.hepdd.clipboardanywhere.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

public class UuidNbtTest {

    @Test
    public void roundTripsAndRemovesUuid() {
        UUID id = UUID.randomUUID();
        NBTTagCompound tag = new NBTTagCompound();

        UuidNbt.write(tag, "value", id);
        assertEquals(id, UuidNbt.read(tag, "value"));

        UuidNbt.write(tag, "value", null);
        assertNull(UuidNbt.read(tag, "value"));
    }
}
