package com.flowguard.websocket;

import com.flowguard.dto.AlertDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/ws/alerts/{accountId}")
@ApplicationScoped
public class AlertWebSocket {
    private static final Logger LOG = Logger.getLogger(AlertWebSocket.class);
    private final Map<String, Set<Session>> sessions = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("accountId") String accountId) {
        sessions.computeIfAbsent(accountId, k -> ConcurrentHashMap.newKeySet()).add(session);
        int unreadCount = 0;
        try {
            session.getBasicRemote().sendText("{" +
                "\"type\":\"CONNECTED\"," +
                "\"accountId\":\"" + accountId + "\"," +
                "\"unreadCount\":" + unreadCount + "}");
        } catch (IOException e) {
            LOG.warnf("Failed to send CONNECTED message: %s", e.getMessage());
        }
        LOG.infof("WebSocket opened for account %s", accountId);
    }

    @OnClose
    public void onClose(Session session, @PathParam("accountId") String accountId) {
        Set<Session> set = sessions.get(accountId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) sessions.remove(accountId);
        }
        LOG.infof("WebSocket closed for account %s", accountId);
    }

    @OnError
    public void onError(Session session, @PathParam("accountId") String accountId, Throwable error) {
        LOG.warn("WebSocket error: " + error.getMessage());
    }

    @OnMessage
    public void onText(String message, @PathParam("accountId") String accountId, Session session) {
        if ("ping".equals(message)) {
            try {
                session.getBasicRemote().sendText("pong");
            } catch (IOException e) {
                LOG.warnf("Failed to send pong: %s", e.getMessage());
            }
        }
    }

    public void broadcastAlert(String accountId, AlertDto alert) {
        Set<Session> set = sessions.get(accountId);
        if (set == null) return;
        String payload = buildAlertPayload(alert);
        for (Session s : new HashSet<>(set)) {
            try {
                s.getBasicRemote().sendText(payload);
            } catch (Exception e) {
                set.remove(s);
            }
        }
    }

    public void broadcastUnreadCount(String accountId, int count) {
        Set<Session> set = sessions.get(accountId);
        if (set == null) return;
        String payload = "{" +
            "\"type\":\"UNREAD_COUNT\"," +
            "\"count\":" + count + "}";
        for (Session s : new HashSet<>(set)) {
            try {
                s.getBasicRemote().sendText(payload);
            } catch (Exception e) {
                set.remove(s);
            }
        }
    }

    private String buildAlertPayload(AlertDto alert) {
        return "{" +
            "\"type\":\"NEW_ALERT\"," +
            "\"alert\":" + alert.toJson() + "}";
    }
}
