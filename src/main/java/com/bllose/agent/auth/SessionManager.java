package com.bllose.agent.auth;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionManager {

    private record SessionInfo(Long userId, Integer userNumber) {}

    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public String createSession(Long userId, Integer userNumber) {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new SessionInfo(userId, userNumber));
        return sessionId;
    }

    public Long getUserId(String sessionId) {
        SessionInfo info = sessions.get(sessionId);
        return info != null ? info.userId() : null;
    }

    public Integer getUserNumber(String sessionId) {
        SessionInfo info = sessions.get(sessionId);
        return info != null ? info.userNumber() : null;
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    public boolean isValid(String sessionId) {
        return sessions.containsKey(sessionId);
    }
}
