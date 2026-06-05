package com.berotech.cceb.client;

import java.net.InetSocketAddress;

import com.berotech.cceb.CCEditorBridge;
import com.berotech.cceb.Config;
import com.berotech.cceb.network.payload.FileEventPayload;
import com.berotech.cceb.network.payload.FileEventType;
import com.berotech.cceb.protocol.EditorMessage;
import com.berotech.cceb.protocol.EditorMessageCodec;
import com.berotech.cceb.protocol.MessageType;

public final class EditorBridgeService {
    private static EditorSocketServer server;
    private static volatile boolean shutdownHookRegistered;

    private EditorBridgeService() {}

    public static void startIfEnabled() {
        stop();

        if (!Config.ENABLED.get()) {
            CCEditorBridge.LOGGER.info("Editor bridge disabled in config");
            return;
        }

        registerShutdownHook();

        int port = Config.SOCKET_PORT.get();
        server = new EditorSocketServer(new InetSocketAddress("127.0.0.1", port));
        server.start();
    }

    public static void stop() {
        if (server == null) {
            return;
        }

        EditorSocketServer stopping = server;
        server = null;

        try {
            stopping.stop(1000);
            CCEditorBridge.LOGGER.info("Editor WebSocket server stopped");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            CCEditorBridge.LOGGER.warn("Interrupted while stopping editor WebSocket server", exception);
        }
    }

    public static boolean isRunning() {
        return server != null;
    }

    public static BridgeStatus status() {
        int port = Config.SOCKET_PORT.get();
        if (server == null) {
            return new BridgeStatus(
                    Config.ENABLED.get(),
                    false,
                    port,
                    EditorAuth.isRequired(),
                    0,
                    0,
                    Config.PREFER_LABEL_IDS.get()
            );
        }

        return new BridgeStatus(
                Config.ENABLED.get(),
                true,
                server.getPort(),
                EditorAuth.isRequired(),
                server.getConnectionCount(),
                server.getAuthenticatedConnectionCount(),
                Config.PREFER_LABEL_IDS.get()
        );
    }

    public static int getPort() {
        return server == null ? Config.SOCKET_PORT.get() : server.getPort();
    }

    public static void forwardFileEvent(FileEventPayload payload) {
        if (server == null) {
            return;
        }

        MessageType messageType = switch (payload.eventType()) {
            case CREATED -> MessageType.FILE_CREATED;
            case MODIFIED -> MessageType.FILE_MODIFIED;
            case DELETED -> MessageType.FILE_DELETED;
        };

        String json = EditorMessageCodec.encode(EditorMessage.fileEvent(messageType, payload.computerId(), payload.path()));
        server.broadcastToAuthenticated(json);
        CCEditorBridge.LOGGER.info(
                "Forwarded {} event for computer '{}' path '{}' to editor clients",
                payload.eventType(),
                payload.computerId(),
                payload.path()
        );
    }

    private static void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(EditorBridgeService::stop, "cceditor-bridge-shutdown"));
        shutdownHookRegistered = true;
    }
}
