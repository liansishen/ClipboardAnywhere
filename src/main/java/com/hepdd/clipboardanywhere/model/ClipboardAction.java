package com.hepdd.clipboardanywhere.model;

public enum ClipboardAction {

    PREVIOUS_PAGE(0),
    NEXT_PAGE(1),
    CYCLE_TASK(2);

    private final int networkId;

    ClipboardAction(int networkId) {
        this.networkId = networkId;
    }

    public int getNetworkId() {
        return networkId;
    }

    public static ClipboardAction fromNetworkId(int id) {
        for (ClipboardAction action : values()) {
            if (action.networkId == id) {
                return action;
            }
        }
        return null;
    }
}
