package com.hepdd.clipboardanywhere;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.lang.reflect.Field;

import net.minecraftforge.common.config.Configuration;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cpw.mods.fml.relauncher.FMLInjectionData;

public class ConfigTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void migratesAndClampsLegacyOpacity() throws Exception {
        Field minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        minecraftHome.set(null, temporaryFolder.getRoot());
        File file = temporaryFolder.newFile("clipboardanywhere.cfg");
        Configuration legacy = new Configuration(file);
        legacy.load();
        legacy.get("overlay", "opacity", 0.05D)
            .set(0.05D);
        legacy.save();

        Config.synchronizeConfiguration(file);

        assertEquals(0.10D, Config.backgroundOpacity, 0.0001D);
        assertEquals(0.10D, Config.textOpacity, 0.0001D);
        Configuration migrated = new Configuration(file);
        migrated.load();
        assertFalse(
            migrated.getCategory("overlay")
                .containsKey("opacity"));
        assertEquals(
            0.10D,
            migrated.get("overlay", "backgroundOpacity", 0.0D)
                .getDouble(),
            0.0001D);
        assertEquals(
            0.10D,
            migrated.get("overlay", "textOpacity", 0.0D)
                .getDouble(),
            0.0001D);
    }
}
