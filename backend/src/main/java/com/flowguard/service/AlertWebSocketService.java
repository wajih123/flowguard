package com.flowguard.service;

import com.flowguard.dto.AlertDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@ApplicationScoped
public class AlertWebSocketService {

    private static final Logger LOG = Logger.getLogger(AlertWebSocketService.class);

    @Inject
    ObjectMapper objectMapper;

    private final Map<UUID, Consumer<String>> connections = new ConcurrentHashMap<>();

    public void registerConnection(UUID userId, Consumer<String> sender) {
        connections.put(userId, sender);
        LOG.debugf("WebSocket connection registered for user %s", userId);
    }

    public void removeConnection(UUID userId) {
        connections.remove(userId);
        LOG.debugf("WebSocket connection removed for user %s", userId);
    }

    public void broadcastToUser(UUID userId, AlertDto alert) {
        Consumer<String> sender = connections.get(userId);
        if (sender != null) {
            try {
                String json = objectMapper.writeValueAsString(alert);
                sender.accept(json);
            } catch (Exception e) {
                LOG.errorf(e, "Error sending WebSocket alert to user %s", userId);
            }
        }
    }
}
