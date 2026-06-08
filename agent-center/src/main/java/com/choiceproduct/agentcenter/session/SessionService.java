package com.choiceproduct.agentcenter.session;

import com.choiceproduct.agentcenter.agent.AgentRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
    private final Map<String, List<ConversationMessage>> sessions = new ConcurrentHashMap<>();

    public String getOrCreateSessionId(AgentRequest request) {
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "cp-agent-" + UUID.randomUUID();
            request.setSessionId(sessionId);
        }
        sessions.computeIfAbsent(sessionId, key -> new ArrayList<>());
        return sessionId;
    }

    public void appendMessage(String sessionId, String role, String content) {
        sessions.computeIfAbsent(sessionId, key -> new ArrayList<>())
                .add(new ConversationMessage(role, content));
    }

    public List<ConversationMessage> listMessages(String sessionId) {
        return sessions.getOrDefault(sessionId, List.of());
    }

    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
    }
}
