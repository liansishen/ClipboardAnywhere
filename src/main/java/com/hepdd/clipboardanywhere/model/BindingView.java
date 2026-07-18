package com.hepdd.clipboardanywhere.model;

import java.util.UUID;

public final class BindingView {

    private final UUID id;
    private final String displayName;
    private final TargetStatus status;
    private final ClipboardPageSnapshot snapshot;

    public BindingView(UUID id, String displayName, TargetStatus status, ClipboardPageSnapshot snapshot) {
        if (id == null) throw new IllegalArgumentException("id");
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

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BindingView)) return false;
        BindingView that = (BindingView) other;
        return id.equals(that.id) && displayName.equals(that.displayName)
            && status == that.status
            && snapshot.equals(that.snapshot);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + displayName.hashCode();
        result = 31 * result + status.hashCode();
        result = 31 * result + snapshot.hashCode();
        return result;
    }
}
