package com.hepdd.clipboardanywhere.model;

public enum TargetStatus {

    READABLE_INVENTORY(0),
    READABLE_PLACED(1),
    DISCONNECTED(2),
    MISSING(3);

    private final int networkId;

    TargetStatus(int networkId) {
        this.networkId = networkId;
    }

    public int getNetworkId() {
        return networkId;
    }

    public boolean isReadable() {
        return this == READABLE_INVENTORY || this == READABLE_PLACED;
    }

    public static TargetStatus fromNetworkId(int id) {
        for (TargetStatus status : values()) {
            if (status.networkId == id) {
                return status;
            }
        }
        return MISSING;
    }
}
