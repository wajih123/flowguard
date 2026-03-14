package com.flowguard.websocket;

import com.flowguard.dto.AlertDto;
import io.quarkus.websockets.next.*;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@WebSocket(path = "/ws/alerts/{accountId}")
@ApplicationScoped
public class AlertWebSocket {
    private static final Logger LOG = Logger.getLogger(AlertWebSocket.class);
    private final Map<String, Set<WebSocketConnection>> sessions = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(WebSocketConnection connection, String accountId) {
        sessions.computeIfAbsent(accountId, k -> new HashSet<>()).add(connection);
        int unreadCount = 0; // TODO: fetch from DB
        connection.sendTextAndAwait("{" +
            "\"type\":\"CONNECTED\"," +
            "\"accountId\":\"" + accountId + "\"," +
            "\"unreadCount\":" + unreadCount + "}");
        LOG.infof("WebSocket opened for account %s", accountId);
    }

    @OnClose
    public void onClose(WebSocketConnection connection, String accountId) {
        Set<WebSocketConnection> set = sessions.get(accountId);
        if (set != null) {
            set.remove(connection);
            if (set.isEmpty()) sessions.remove(accountId);
        }
        LOG.infof("WebSocket closed for account %s", accountId);
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable error) {
        LOG.warn("WebSocket error: " + error.getMessage());
    }

    @OnTextMessage
    public void onText(WebSocketConnection connection, String message, String accountId) {
        if ("ping".equals(message)) {
            connection.sendTextAndAwait("pong");
        }
    }

    public void broadcastAlert(String accountId, AlertDto alert) {
        Set<WebSocketConnection> set = sessions.get(accountId);
        if (set == null) return;
        String payload = buildAlertPayload(alert);
        for (WebSocketConnection conn : set) {
            try {
                conn.sendTextAndAwait(payload);
            } catch (Exception e) {
                set.remove(conn);
            }
        }
    }

    public void broadcastUnreadCount(String accountId, int count) {
        Set<WebSocketConnection> set = sessions.get(accountId);
        if (set == null) return;
        String payload = "{" +
            "\"type\":\"UNREAD_COUNT\"," +
            "\"count\":" + count + "}";
        for (WebSocketConnection conn : set) {
            try {
                conn.sendTextAndAwait(payload);
            } catch (Exception e) {
                set.remove(conn);
            }
        }
    }

    private String buildAlertPayload(AlertDto alert) {
        return "{" +
            "\"type\":\"NEW_ALERT\"," +
            "\"alert\":" + alert.toJson() + "}";
    }
}
