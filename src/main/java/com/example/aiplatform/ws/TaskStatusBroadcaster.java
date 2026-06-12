package com.example.aiplatform.ws;

import com.example.aiplatform.task.TaskStatus;
import com.example.aiplatform.ws.dto.TaskStatusEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TaskStatusBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(TaskStatusBroadcaster.class);

    private final ObjectMapper objectMapper;

    /**
     * userId -> sessions
     */
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    public TaskStatusBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(Long userId, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("WS register userId={}, sessionId={}", userId, session.getId());
    }

    public void unregister(Long userId, WebSocketSession session) {
        Set<WebSocketSession> set = userSessions.get(userId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) userSessions.remove(userId);
        }
        log.info("WS unregister userId={}, sessionId={}", userId, session.getId());
    }

    public void publish(Long taskId, Long userId, TaskStatus status, String message) {
        TaskStatusEvent event = TaskStatusEvent.of(taskId, userId, status, message);
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.warn("WS publish serialize failed taskId={}, userId={}, status={}, err={}", taskId, userId, status, e.toString());
            return;
        }

        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        TextMessage text = new TextMessage(json);
        for (WebSocketSession s : sessions) {
            try {
                if (s.isOpen()) {
                    s.sendMessage(text);
                }
            } catch (Exception e) {
                log.warn("WS publish failed userId={}, sessionId={}, err={}", userId, s.getId(), e.toString());
            }
        }
    }
}
