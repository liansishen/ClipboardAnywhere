package com.hepdd.clipboardanywhere.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.hepdd.clipboardanywhere.network.message.C2SRequestState;
import com.hepdd.clipboardanywhere.network.message.S2CBindingState;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class NetworkHandler {

    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("clipboardanywhere");
    private static boolean initialized;

    private NetworkHandler() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        CHANNEL.registerMessage(C2SRequestState.Handler.class, C2SRequestState.class, 0, Side.SERVER);
        CHANNEL.registerMessage(S2CBindingState.Handler.class, S2CBindingState.class, 1, Side.CLIENT);
    }

    public static void sendTo(IMessage message, EntityPlayerMP player) {
        CHANNEL.sendTo(message, player);
    }

    public static void sendToServer(IMessage message) {
        CHANNEL.sendToServer(message);
    }
}
