package com.berotech.cceb.client;

import java.net.InetSocketAddress;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.berotech.cceb.CCEditorBridge;

public class EditorSocketServer extends WebSocketServer {
    public EditorSocketServer(InetSocketAddress address) {
        super(address);
        setReuseAddr(true);
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        CCEditorBridge.LOGGER.info("Editor connected from {}", connection.getRemoteSocketAddress());
        EditorMessageHandler.onConnect(connection);
    }

    @Override
    public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        CCEditorBridge.LOGGER.info("Editor disconnected (code={}, reason={}, remote={})", code, reason, remote);
    }

    @Override
    public void onMessage(WebSocket connection, String message) {
        CCEditorBridge.LOGGER.debug("Editor message received ({} bytes)", message.length());
        EditorMessageHandler.handle(connection, message);
    }

    @Override
    public void onError(WebSocket connection, Exception exception) {
        if (connection == null) {
            CCEditorBridge.LOGGER.error("Editor WebSocket server error", exception);
        } else {
            CCEditorBridge.LOGGER.error("Editor WebSocket connection error from {}", connection.getRemoteSocketAddress(), exception);
        }
    }

    @Override
    public void onStart() {
        CCEditorBridge.LOGGER.info("Editor WebSocket server listening on ws://127.0.0.1:{}/", getAddress().getPort());
    }

    public void broadcastToAuthenticated(String message) {
        for (WebSocket connection : getConnections()) {
            EditorConnectionState state = (EditorConnectionState) connection.getAttachment();
            if (state != null && state.isAuthenticated()) {
                connection.send(message);
            }
        }
    }

    public int getPort() {
        return getAddress().getPort();
    }

    public int getConnectionCount() {
        return getConnections().size();
    }

    public int getAuthenticatedConnectionCount() {
        int count = 0;
        for (WebSocket connection : getConnections()) {
            EditorConnectionState state = (EditorConnectionState) connection.getAttachment();
            if (state != null && state.isAuthenticated()) {
                count++;
            }
        }
        return count;
    }
}
