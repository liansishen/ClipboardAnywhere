package com.hepdd.clipboardanywhere.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class PlayerBindingSnapshot {

    public static final PlayerBindingSnapshot EMPTY = new PlayerBindingSnapshot(
        null,
        Collections.<BindingView>emptyList());

    private final UUID activeId;
    private final List<BindingView> bindings;

    public PlayerBindingSnapshot(UUID activeId, List<BindingView> bindings) {
        this.bindings = Collections
            .unmodifiableList(bindings == null ? Collections.<BindingView>emptyList() : new ArrayList<>(bindings));
        this.activeId = contains(this.bindings, activeId) ? activeId : null;
    }

    public UUID getActiveId() {
        return activeId;
    }

    public List<BindingView> getBindings() {
        return bindings;
    }

    public BindingView getActiveBinding() {
        return get(activeId);
    }

    public BindingView get(UUID id) {
        if (id == null) return null;
        for (BindingView binding : bindings) {
            if (id.equals(binding.getId())) return binding;
        }
        return null;
    }

    public boolean isAllDisconnected() {
        if (bindings.isEmpty()) return false;
        for (BindingView binding : bindings) {
            if (binding.getStatus()
                .isReadable()) return false;
        }
        return true;
    }

    public UUID firstReadableId() {
        for (BindingView binding : bindings) {
            if (binding.getStatus()
                .isReadable()) return binding.getId();
        }
        return null;
    }

    private static boolean contains(List<BindingView> bindings, UUID id) {
        if (id == null) return false;
        for (BindingView binding : bindings) {
            if (id.equals(binding.getId())) return true;
        }
        return false;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PlayerBindingSnapshot)) return false;
        PlayerBindingSnapshot that = (PlayerBindingSnapshot) other;
        return (activeId == null ? that.activeId == null : activeId.equals(that.activeId))
            && bindings.equals(that.bindings);
    }

    @Override
    public int hashCode() {
        return 31 * (activeId == null ? 0 : activeId.hashCode()) + bindings.hashCode();
    }
}
