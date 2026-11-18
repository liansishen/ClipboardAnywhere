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
    private int clientSeq;

    public C2SRenameBinding() {}

    public C2SRenameBinding(UUID clipboardId, String displayName) {
        this(clipboardId, displayName, 0);
    }

    public C2SRenameBinding(UUID clipboardId, String displayName, int clientSeq) {
        this.clipboardId = clipboardId;
        this.displayName = displayName == null ? "" : displayName;
        this.clientSeq = clientSeq;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        clipboardId = NetworkCodec.readUuid(buffer);
        displayName = NetworkCodec.readBoundedUtf8(buffer, NetworkCodec.MAX_DISPLAY_NAME_CHARS);
        clientSeq = buffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        NetworkCodec.writeUuid(buffer, clipboardId);
        NetworkCodec.writeBoundedUtf8(buffer, displayName, NetworkCodec.MAX_DISPLAY_NAME_CHARS);
        buffer.writeInt(clientSeq);
    }

    public static final class Handler implements IMessageHandler<C2SRenameBinding, IMessage> {

        @Override
        public IMessage onMessage(C2SRenameBinding message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            final UUID id = message.clipboardId;
            final String name = message.displayName;
            final int seq = message.clientSeq;
            ServerTaskQueue.INSTANCE.enqueue(() -> {
                ClipboardServerService.INSTANCE.recordClientSeq(player, seq);
                ClipboardServerService.INSTANCE.renameBinding(player, id, name);
            });
            return null;
        }
    }
}
