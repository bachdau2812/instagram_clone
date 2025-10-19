package com.dauducbach.chat_service.service;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionRegistry {
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void addSession(String userId, WebSocketSession session) {
        sessions.putIfAbsent(userId, session);
    }

    public void removeSession(String userId) {
        sessions.remove(userId);
    }

    public WebSocketSession getSession(String userId) {
        return sessions.get(userId);
    }


    public boolean isOnline(String userId) {
        WebSocketSession session = sessions.get(userId);

        return session != null && session.isOpen();
    }

    public Map<String, WebSocketSession> print() {
        return sessions;
    }
}
