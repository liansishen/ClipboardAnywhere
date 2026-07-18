package com.hepdd.clipboardanywhere.network.message;

import com.hepdd.clipboardanywhere.ClipboardAnywhere;
import com.hepdd.clipboardanywhere.model.PlayerBindingSnapshot;
import com.hepdd.clipboardanywhere.network.NetworkCodec;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class S2CBindingState implements IMessage {

    private PlayerBindingSnapshot snapshot = PlayerBindingSnapshot.EMPTY;

    public S2CBindingState() {}

    public S2CBindingState(PlayerBindingSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public PlayerBindingSnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        snapshot = NetworkCodec.readPlayerSnapshot(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        NetworkCodec.writePlayerSnapshot(buffer, snapshot);
    }

    public static final class Handler implements IMessageHandler<S2CBindingState, IMessage> {

        @Override
        public IMessage onMessage(S2CBindingState message, MessageContext context) {
            ClipboardAnywhere.proxy.handleBindingState(message.getSnapshot());
            return null;
        }
    }
}
