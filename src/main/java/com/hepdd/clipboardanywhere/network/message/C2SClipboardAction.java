package com.hepdd.clipboardanywhere.network.message;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.hepdd.clipboardanywhere.model.ClipboardAction;
import com.hepdd.clipboardanywhere.network.NetworkCodec;
import com.hepdd.clipboardanywhere.server.ClipboardServerService;
import com.hepdd.clipboardanywhere.server.ServerTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class C2SClipboardAction implements IMessage {

    private UUID clipboardId;
    private ClipboardAction action;
    private int row;
    private int clientSeq;

    public C2SClipboardAction() {}

    public C2SClipboardAction(UUID clipboardId, ClipboardAction action, int row) {
        this(clipboardId, action, row, 0);
    }

    public C2SClipboardAction(UUID clipboardId, ClipboardAction action, int row, int clientSeq) {
        this.clipboardId = clipboardId;
        this.action = action;
        this.row = row;
        this.clientSeq = clientSeq;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        clipboardId = NetworkCodec.readUuid(buffer);
        action = ClipboardAction.fromNetworkId(buffer.readUnsignedByte());
        row = buffer.readByte();
        clientSeq = buffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        NetworkCodec.writeUuid(buffer, clipboardId);
        buffer.writeByte(action == null ? -1 : action.getNetworkId());
        buffer.writeByte(row);
        buffer.writeInt(clientSeq);
    }

    public static final class Handler implements IMessageHandler<C2SClipboardAction, IMessage> {

        @Override
        public IMessage onMessage(C2SClipboardAction message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            final UUID id = message.clipboardId;
            final ClipboardAction action = message.action;
            final int row = message.row;
            final int seq = message.clientSeq;
            ServerTaskQueue.INSTANCE.enqueue(() -> {
                ClipboardServerService.INSTANCE.recordClientSeq(player, seq);
                ClipboardServerService.INSTANCE.performAction(player, id, action, row);
            });
            return null;
        }
    }
}
