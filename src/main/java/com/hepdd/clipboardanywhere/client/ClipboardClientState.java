package com.hepdd.clipboardanywhere.client;

import com.hepdd.clipboardanywhere.model.PlayerBindingSnapshot;

public final class ClipboardClientState {

    public static final ClipboardClientState INSTANCE = new ClipboardClientState();
    private PlayerBindingSnapshot snapshot = PlayerBindingSnapshot.EMPTY;

    private ClipboardClientState() {}

    public PlayerBindingSnapshot getSnapshot() {
        return snapshot;
    }

    public void update(PlayerBindingSnapshot snapshot) {
        this.snapshot = snapshot == null ? PlayerBindingSnapshot.EMPTY : snapshot;
    }

    public void clear() {
        snapshot = PlayerBindingSnapshot.EMPTY;
    }
}
