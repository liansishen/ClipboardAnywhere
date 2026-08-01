package com.hepdd.clipboardanywhere.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.hepdd.clipboardanywhere.network.message.C2SBindHeldClipboard;
import com.hepdd.clipboardanywhere.network.message.C2SClipboardAction;
import com.hepdd.clipboardanywhere.network.message.C2SRenameBinding;
import com.hepdd.clipboardanywhere.network.message.C2SRequestState;
import com.hepdd.clipboardanywhere.network.message.C2SSelectBinding;
import com.hepdd.clipboardanywhere.network.message.C2SUnbind;
import com.hepdd.clipboardanywhere.network.message.C2SUpdateTaskText;
import com.hepdd.clipboardanywhere.network.message.S2CBindingState;
import com.hepdd.clipboardanywhere.network.message.S2COperationResult;

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
        CHANNEL.registerMessage(C2SSelectBinding.Handler.class, C2SSelectBinding.class, 2, Side.SERVER);
        CHANNEL.registerMessage(C2SRenameBinding.Handler.class, C2SRenameBinding.class, 3, Side.SERVER);
        CHANNEL.registerMessage(C2SUnbind.Handler.class, C2SUnbind.class, 4, Side.SERVER);
        CHANNEL.registerMessage(C2SClipboardAction.Handler.class, C2SClipboardAction.class, 5, Side.SERVER);
        CHANNEL.registerMessage(S2COperationResult.Handler.class, S2COperationResult.class, 6, Side.CLIENT);
        CHANNEL.registerMessage(C2SBindHeldClipboard.Handler.class, C2SBindHeldClipboard.class, 7, Side.SERVER);
        CHANNEL.registerMessage(C2SUpdateTaskText.Handler.class, C2SUpdateTaskText.class, 8, Side.SERVER);
    }

    public static void sendTo(IMessage message, EntityPlayerMP player) {
        CHANNEL.sendTo(message, player);
    }

    public static void sendToServer(IMessage message) {
        CHANNEL.sendToServer(message);
    }
}
