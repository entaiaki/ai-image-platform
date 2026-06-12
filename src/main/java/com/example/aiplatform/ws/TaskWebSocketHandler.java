package com.example.aiplatform.ws;

import com.example.aiplatform.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket 连接方式：
 * ws://localhost:8080/ws/tasks?token=Bearer%20xxx
 * 或者带 header: Authorization: Bearer xxx (浏览器 websocket 不方便带 header，所以提供 query token)
 */
@Component
public class TaskWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TaskWebSocketHandler.class);

    private final JwtUtil jwtUtil;
    private final TaskStatusBroadcaster broadcaster;

    public TaskWebSocketHandler(JwtUtil jwtUtil, TaskStatusBroadcaster broadcaster) {
        this.jwtUtil = jwtUtil;
        this.broadcaster = broadcaster;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = authUserId(session);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized"));
            return;
        }
        session.getAttributes().put("userId", userId);
        broadcaster.register(userId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Object uid = session.getAttributes().get("userId");
        if (uid instanceof Long userId) {
            broadcaster.unregister(userId, session);
        }
    }

    private Long authUserId(WebSocketSession session) {
        try {
            // 1) header Authorization
            String auth = getHeader(session, HttpHeaders.AUTHORIZATION);
            String token = null;
            if (auth != null && auth.startsWith("Bearer ")) {
                token = auth.substring("Bearer ".length()).trim();
            }

            // 2) query param token
            if (token == null) {
                URI uri = session.getUri();
                if (uri != null && uri.getQuery() != null) {
                    Map<String, String> q = parseQuery(uri.getQuery());
                    String qt = q.get("token");
                    if (qt != null) {
                        if (qt.startsWith("Bearer ")) {
                            token = qt.substring("Bearer ".length()).trim();
                        } else {
                            token = qt.trim();
                        }
                    }
                }
            }

            if (token == null || token.isBlank()) return null;

            Claims claims = jwtUtil.parse(token);
            return Long.parseLong(claims.getSubject());
        } catch (Exception e) {
            log.warn("WS auth failed sessionId={}, err={}", session.getId(), e.toString());
            return null;
        }
    }

    private String getHeader(WebSocketSession session, String name) {
        try {
            return session.getHandshakeHeaders().getFirst(name);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, String> parseQuery(String query) {
        // 简单解析 a=b&c=d
        java.util.HashMap<String, String> map = new java.util.HashMap<>();
        for (String part : query.split("&")) {
            int idx = part.indexOf('=');
            if (idx <= 0) continue;
            String k = urlDecode(part.substring(0, idx));
            String v = urlDecode(part.substring(idx + 1));
            map.put(k, v);
        }
        return map;
    }

    private String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
