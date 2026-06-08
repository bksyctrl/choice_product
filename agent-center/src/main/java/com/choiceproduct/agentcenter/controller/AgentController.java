package com.choiceproduct.agentcenter.controller;

import com.choiceproduct.agentcenter.agent.AgentRequest;
import com.choiceproduct.agentcenter.agent.AgentResponse;
import com.choiceproduct.agentcenter.agent.TotalConversationAgent;
import com.choiceproduct.agentcenter.handler.IntentHandlerFactory;
import com.choiceproduct.agentcenter.registry.AgentIntentDefinition;
import com.choiceproduct.agentcenter.registry.AgentIntentRegistry;
import com.choiceproduct.agentcenter.session.ConversationMessage;
import com.choiceproduct.agentcenter.session.SessionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final TotalConversationAgent totalConversationAgent;
    private final AgentIntentRegistry registry;
    private final IntentHandlerFactory handlerFactory;
    private final SessionService sessionService;

    public AgentController(
            TotalConversationAgent totalConversationAgent,
            AgentIntentRegistry registry,
            IntentHandlerFactory handlerFactory,
            SessionService sessionService) {
        this.totalConversationAgent = totalConversationAgent;
        this.registry = registry;
        this.handlerFactory = handlerFactory;
        this.sessionService = sessionService;
    }

    @PostMapping("/chat")
    public AgentResponse chat(@Valid @RequestBody AgentRequest request) {
        return totalConversationAgent.process(request);
    }

    @GetMapping("/intents")
    public List<AgentIntentDefinition> intents() {
        return registry.listDefinitions();
    }

    @GetMapping("/handlers")
    public Map<String, String> handlers() {
        return handlerFactory.listHandlers();
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<ConversationMessage> messages(@PathVariable String sessionId) {
        return sessionService.listMessages(sessionId);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Map<String, Object> clearSession(@PathVariable String sessionId) {
        sessionService.clearSession(sessionId);
        return Map.of("success", true, "session_id", sessionId);
    }
}
