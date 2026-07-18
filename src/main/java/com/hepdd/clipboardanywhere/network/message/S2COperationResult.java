package com.hepdd.clipboardanywhere.network.message;

import com.hepdd.clipboardanywhere.ClipboardAnywhere;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class S2COperationResult implements IMessage {

    private boolean success;
    private String translationKey = "";

    public S2COperationResult() {}

    public S2COperationResult(boolean success, String translationKey) {
        this.success = success;
        this.translationKey = translationKey == null ? "" : translationKey;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        success = buffer.readBoolean();
        translationKey = ByteBufUtils.readUTF8String(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(success);
        ByteBufUtils.writeUTF8String(buffer, translationKey);
    }

    public static final class Handler implements IMessageHandler<S2COperationResult, IMessage> {

        @Override
        public IMessage onMessage(S2COperationResult message, MessageContext context) {
            ClipboardAnywhere.proxy.handleOperationResult(message.success, message.translationKey);
            return null;
        }
    }
}
