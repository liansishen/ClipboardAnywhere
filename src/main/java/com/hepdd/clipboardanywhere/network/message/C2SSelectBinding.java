package com.hepdd.clipboardanywhere.network.message;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.hepdd.clipboardanywhere.network.NetworkCodec;
import com.hepdd.clipboardanywhere.server.ClipboardServerService;
import com.hepdd.clipboardanywhere.server.ServerTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class C2SSelectBinding implements IMessage {

    private UUID clipboardId;

    public C2SSelectBinding() {}

    public C2SSelectBinding(UUID clipboardId) {
        this.clipboardId = clipboardId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        clipboardId = NetworkCodec.readUuid(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        NetworkCodec.writeUuid(buffer, clipboardId);
    }

    public static final class Handler implements IMessageHandler<C2SSelectBinding, IMessage> {

        @Override
        public IMessage onMessage(C2SSelectBinding message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            final UUID id = message.clipboardId;
            ServerTaskQueue.INSTANCE.enqueue(() -> ClipboardServerService.INSTANCE.selectBinding(player, id));
            return null;
        }
    }
}
