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

public final class C2SRenameBinding implements IMessage {

    private UUID clipboardId;
    private String displayName = "";

    public C2SRenameBinding() {}

    public C2SRenameBinding(UUID clipboardId, String displayName) {
        this.clipboardId = clipboardId;
        this.displayName = displayName == null ? "" : displayName;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        clipboardId = NetworkCodec.readUuid(buffer);
        displayName = NetworkCodec.readBoundedUtf8(buffer, NetworkCodec.MAX_DISPLAY_NAME_CHARS);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        NetworkCodec.writeUuid(buffer, clipboardId);
        NetworkCodec.writeBoundedUtf8(buffer, displayName, NetworkCodec.MAX_DISPLAY_NAME_CHARS);
    }

    public static final class Handler implements IMessageHandler<C2SRenameBinding, IMessage> {

        @Override
        public IMessage onMessage(C2SRenameBinding message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            final UUID id = message.clipboardId;
            final String name = message.displayName;
            ServerTaskQueue.INSTANCE.enqueue(() -> ClipboardServerService.INSTANCE.renameBinding(player, id, name));
            return null;
        }
    }
}
