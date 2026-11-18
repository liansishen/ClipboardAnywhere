package com.hepdd.clipboardanywhere.network.message;

import com.hepdd.clipboardanywhere.ClipboardAnywhere;
import com.hepdd.clipboardanywhere.model.PlayerBindingSnapshot;
import com.hepdd.clipboardanywhere.network.NetworkCodec;
import com.hepdd.clipboardanywhere.network.NetworkCodec.BindingStatePayload;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class S2CBindingState implements IMessage {

    private BindingStatePayload payload = BindingStatePayload.EMPTY;

    public S2CBindingState() {}

    public S2CBindingState(BindingStatePayload payload) {
        this.payload = payload == null ? BindingStatePayload.EMPTY : payload;
    }

    public S2CBindingState(PlayerBindingSnapshot snapshot) {
        this(BindingStatePayload.fullFromSnapshot(snapshot, 0));
    }

    public BindingStatePayload getPayload() {
        return payload;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        payload = NetworkCodec.readStatePayload(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        NetworkCodec.writeStatePayload(buffer, payload);
    }

    public static final class Handler implements IMessageHandler<S2CBindingState, IMessage> {

        @Override
        public IMessage onMessage(S2CBindingState message, MessageContext context) {
            ClipboardAnywhere.proxy.handleBindingState(message.getPayload());
            return null;
        }
    }
}
