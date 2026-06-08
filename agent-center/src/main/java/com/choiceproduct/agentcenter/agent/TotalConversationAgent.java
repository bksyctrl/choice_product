package com.choiceproduct.agentcenter.agent;

import com.choiceproduct.agentcenter.brain.BrainService;
import com.choiceproduct.agentcenter.brain.DispatchDecision;
import com.choiceproduct.agentcenter.compliance.ComplianceCheckResult;
import com.choiceproduct.agentcenter.compliance.ComplianceControlAgent;
import com.choiceproduct.agentcenter.handler.IntentHandler;
import com.choiceproduct.agentcenter.handler.IntentHandlerFactory;
import com.choiceproduct.agentcenter.learning.LearningService;
import com.choiceproduct.agentcenter.rag.RagContext;
import com.choiceproduct.agentcenter.rag.RagService;
import com.choiceproduct.agentcenter.session.SessionService;
import org.springframework.stereotype.Component;

@Component
public class TotalConversationAgent implements Agent {
    private final SessionService sessionService;
    private final ComplianceControlAgent complianceControlAgent;
    private final RagService ragService;
    private final BrainService brainService;
    private final IntentHandlerFactory handlerFactory;
    private final LearningService learningService;

    public TotalConversationAgent(
            SessionService sessionService,
            ComplianceControlAgent complianceControlAgent,
            RagService ragService,
            BrainService brainService,
            IntentHandlerFactory handlerFactory,
            LearningService learningService) {
        this.sessionService = sessionService;
        this.complianceControlAgent = complianceControlAgent;
        this.ragService = ragService;
        this.brainService = brainService;
        this.handlerFactory = handlerFactory;
        this.learningService = learningService;
    }

    @Override
    public String getAgentType() {
        return "total_conversation_agent";
    }

    @Override
    public String getName() {
        return "CEO 总控专家";
    }

    @Override
    public AgentResponse process(AgentRequest request) {
        String sessionId = sessionService.getOrCreateSessionId(request);
        sessionService.appendMessage(sessionId, "user", request.getMessage());

        ComplianceCheckResult compliance = complianceControlAgent.checkCompliance(request.getMessage(), "ALL");
        if (!compliance.isAllowed()) {
            AgentResponse blocked = AgentResponse.failure(compliance.getReason());
            blocked.setSessionId(sessionId);
            sessionService.appendMessage(sessionId, "assistant", blocked.getMessage());
            return blocked;
        }

        RagContext ragContext = ragService.enrich(request);
        DispatchDecision decision = brainService.decide(request, ragContext);
        IntentHandler handler = handlerFactory.getHandler(decision.getHandlerCode());
        AgentResponse response = handler.handle(request, decision, ragContext);
        response.setSessionId(sessionId);

        sessionService.appendMessage(sessionId, "assistant", response.getMessage());
        learningService.recordTurn(request, decision, response);
        return response;
    }
}
