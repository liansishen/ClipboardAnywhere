package com.hepdd.clipboardanywhere;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cpw.mods.fml.relauncher.FMLInjectionData;

public class ConfigTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void initFmlHome() throws Exception {
        Field home = FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        if (home.get(null) == null) {
            home.set(null, tempFolder.getRoot());
        }
    }

    @Test
    public void debouncesCollapsedAndLayoutWritesAndPersistsOnFlush() throws Exception {
        File cfgFile = tempFolder.newFile("clipboardanywhere.cfg");
        Config.synchronizeConfiguration(cfgFile);
        assertFalse(Config.collapsed);

        // Rapid toggles update memory immediately and coalesce until flushSave()
        Config.setCollapsed(true);
        assertTrue(Config.collapsed);
        Config.setCollapsed(false);
        assertFalse(Config.collapsed);
        Config.setCollapsed(true);
        assertTrue(Config.collapsed);

        Config.saveLayout("left", "bottom", 12, 24, 0.25D, 0.75D, 1.5D, 0.65D, 0.85D);
        Config.flushSave();

        // Reload into clean state from disk and verify persistence
        Config.collapsed = false;
        Config.scale = 1.0D;
        Config.synchronizeConfiguration(cfgFile);
        assertTrue(Config.collapsed);
        assertEquals("left", Config.horizontalPositionMode);
        assertEquals("bottom", Config.verticalPositionMode);
        assertEquals(12, Config.horizontalOffset);
        assertEquals(24, Config.verticalOffset);
        assertEquals(1.5D, Config.scale, 1e-6D);
        assertEquals(0.65D, Config.backgroundOpacity, 1e-6D);
        assertEquals(0.85D, Config.textOpacity, 1e-6D);
    }
}
