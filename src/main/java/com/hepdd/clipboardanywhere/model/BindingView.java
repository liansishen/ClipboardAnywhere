package com.hepdd.clipboardanywhere.model;

import java.util.UUID;

public final class BindingView {

    private final UUID id;
    private final String displayName;
    private final TargetStatus status;
    private final ClipboardPageSnapshot snapshot;

    public BindingView(UUID id, String displayName, TargetStatus status, ClipboardPageSnapshot snapshot) {
        this.id = id;
        this.displayName = displayName == null ? "" : displayName;
        this.status = status == null ? TargetStatus.MISSING : status;
        this.snapshot = snapshot == null ? ClipboardPageSnapshot.EMPTY : snapshot;
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public TargetStatus getStatus() {
        return status;
    }

    public ClipboardPageSnapshot getSnapshot() {
        return snapshot;
    }
}
