package com.berotech.cceb.client;

public record BridgeStatus(
        boolean enabled,
        boolean running,
        int port,
        boolean authRequired,
        int totalConnections,
        int authenticatedConnections,
        boolean preferLabelIds
) {
    public String format() {
        return "enabled=" + enabled
                + ", running=" + running
                + ", port=" + port
                + ", authRequired=" + authRequired
                + ", connections=" + totalConnections
                + ", authenticated=" + authenticatedConnections
                + ", preferLabelIds=" + preferLabelIds;
    }
}
