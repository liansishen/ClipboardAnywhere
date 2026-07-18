package com.hepdd.clipboardanywhere;

import net.minecraft.client.Minecraft;

import com.hepdd.clipboardanywhere.client.ClipboardClientState;
import com.hepdd.clipboardanywhere.model.PlayerBindingSnapshot;

public class ClientProxy extends CommonProxy {

    @Override
    public void handleBindingState(final PlayerBindingSnapshot snapshot) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> ClipboardClientState.INSTANCE.update(snapshot));
    }

    @Override
    public void handleOperationResult(final boolean success, final String translationKey) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> ClipboardClientState.INSTANCE.addNotice(success, translationKey));
    }
}
