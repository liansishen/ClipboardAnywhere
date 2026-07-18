package com.hepdd.clipboardanywhere;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
        modid = ClipboardAnywhere.MODID,
        version = Tags.VERSION,
        name = ClipboardAnywhere.NAME,
        acceptedMinecraftVersions = "[1.7.10]",
        dependencies = "required-after:BiblioCraft")
public class ClipboardAnywhere {

    public static final String MODID = "clipboardanywhere";
    public static final String NAME = "Clipboard Anywhere";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(
            clientSide = "com.hepdd.clipboardanywhere.ClientProxy",
            serverSide = "com.hepdd.clipboardanywhere.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
