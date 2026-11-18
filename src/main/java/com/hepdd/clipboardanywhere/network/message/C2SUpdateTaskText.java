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

public final class C2SUpdateTaskText implements IMessage {

    private UUID clipboardId;
    private int pageNumber;
    private int row;
    private String expectedText = "";
    private String replacementText = "";
    private int clientSeq;

    public C2SUpdateTaskText() {}

    public C2SUpdateTaskText(UUID clipboardId, int pageNumber, int row, String expectedText, String replacementText) {
        this(clipboardId, pageNumber, row, expectedText, replacementText, 0);
    }

    public C2SUpdateTaskText(UUID clipboardId, int pageNumber, int row, String expectedText, String replacementText,
        int clientSeq) {
        this.clipboardId = clipboardId;
        this.pageNumber = pageNumber;
        this.row = row;
        this.expectedText = expectedText == null ? "" : expectedText;
        this.replacementText = replacementText == null ? "" : replacementText;
        this.clientSeq = clientSeq;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        clipboardId = NetworkCodec.readUuid(buffer);
        pageNumber = buffer.readInt();
        row = buffer.readByte();
        expectedText = NetworkCodec.readBoundedUtf8(buffer, NetworkCodec.MAX_TASK_TEXT_CHARS);
        replacementText = NetworkCodec.readBoundedUtf8(buffer, NetworkCodec.MAX_EDIT_TASK_TEXT_CHARS);
        clientSeq = buffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        NetworkCodec.writeUuid(buffer, clipboardId);
        buffer.writeInt(pageNumber);
        buffer.writeByte(row);
        NetworkCodec.writeBoundedUtf8(buffer, expectedText, NetworkCodec.MAX_TASK_TEXT_CHARS);
        NetworkCodec.writeBoundedUtf8(buffer, replacementText, NetworkCodec.MAX_EDIT_TASK_TEXT_CHARS);
        buffer.writeInt(clientSeq);
    }

    public static final class Handler implements IMessageHandler<C2SUpdateTaskText, IMessage> {

        @Override
        public IMessage onMessage(C2SUpdateTaskText message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            final UUID id = message.clipboardId;
            final int pageNumber = message.pageNumber;
            final int row = message.row;
            final String expectedText = message.expectedText;
            final String replacementText = message.replacementText;
            final int seq = message.clientSeq;
            ServerTaskQueue.INSTANCE.enqueue(() -> {
                ClipboardServerService.INSTANCE.recordClientSeq(player, seq);
                ClipboardServerService.INSTANCE
                    .updateTaskText(player, id, pageNumber, row, expectedText, replacementText);
            });
            return null;
        }
    }
}
