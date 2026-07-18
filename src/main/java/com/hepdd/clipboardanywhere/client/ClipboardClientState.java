package com.hepdd.clipboardanywhere.client;

import java.util.ArrayDeque;
import java.util.Queue;

import com.hepdd.clipboardanywhere.model.PlayerBindingSnapshot;

public final class ClipboardClientState {

    public static final ClipboardClientState INSTANCE = new ClipboardClientState();
    private PlayerBindingSnapshot snapshot = PlayerBindingSnapshot.EMPTY;
    private final Queue<Notice> notices = new ArrayDeque<>();

    private ClipboardClientState() {}

    public PlayerBindingSnapshot getSnapshot() {
        return snapshot;
    }

    public void update(PlayerBindingSnapshot snapshot) {
        boolean wasAllDisconnected = this.snapshot.isAllDisconnected();
        this.snapshot = snapshot == null ? PlayerBindingSnapshot.EMPTY : snapshot;
        if (!wasAllDisconnected && this.snapshot.isAllDisconnected()) {
            addNotice(false, "message.clipboardanywhere.all_disconnected");
        }
    }

    public void addNotice(boolean success, String translationKey) {
        if (translationKey != null && !translationKey.isEmpty()) {
            notices.offer(new Notice(success, translationKey));
        }
    }

    public Notice pollNotice() {
        return notices.poll();
    }

    public void clear() {
        snapshot = PlayerBindingSnapshot.EMPTY;
        notices.clear();
    }

    public static final class Notice {

        private final boolean success;
        private final String translationKey;

        private Notice(boolean success, String translationKey) {
            this.success = success;
            this.translationKey = translationKey;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getTranslationKey() {
            return translationKey;
        }
    }
}
