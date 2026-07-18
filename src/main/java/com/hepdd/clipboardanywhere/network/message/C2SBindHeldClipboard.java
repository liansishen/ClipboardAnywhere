package com.hepdd.clipboardanywhere.network.message;

import net.minecraft.entity.player.EntityPlayerMP;

import com.hepdd.clipboardanywhere.server.ClipboardServerService;
import com.hepdd.clipboardanywhere.server.ServerTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class C2SBindHeldClipboard implements IMessage {

    @Override
    public void fromBytes(ByteBuf buffer) {}

    @Override
    public void toBytes(ByteBuf buffer) {}

    public static final class Handler implements IMessageHandler<C2SBindHeldClipboard, IMessage> {

        @Override
        public IMessage onMessage(C2SBindHeldClipboard message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            ServerTaskQueue.INSTANCE
                .enqueue(() -> { if (player.isSneaking()) ClipboardServerService.INSTANCE.bindHeldClipboard(player); });
            return null;
        }
    }
}
