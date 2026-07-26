package com.hepdd.clipboardanywhere;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;

import com.hepdd.clipboardanywhere.client.ClientEventHandler;
import com.hepdd.clipboardanywhere.client.ClientKeyBindings;
import com.hepdd.clipboardanywhere.client.ClipboardClientState;
import com.hepdd.clipboardanywhere.client.ModularUiInputCompat;
import com.hepdd.clipboardanywhere.model.PlayerBindingSnapshot;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        ClientKeyBindings.register();
        FMLCommonHandler.instance()
            .bus()
            .register(ClientEventHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ClientEventHandler.INSTANCE);
        if (Loader.isModLoaded("modularui2")) MinecraftForge.EVENT_BUS.register(ModularUiInputCompat.INSTANCE);
    }

    @Override
    public void handleBindingState(final PlayerBindingSnapshot snapshot) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> ClipboardClientState.INSTANCE.update(snapshot));
    }

    @Override
    public void handleOperationResult(final boolean success, final String translationKey) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> ClipboardClientState.INSTANCE.addNotice(success, translationKey));
    }
}
