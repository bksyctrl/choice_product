package com.choiceproduct.agentcenter.handler;

import com.choiceproduct.agentcenter.agent.AgentRequest;
import com.choiceproduct.agentcenter.agent.AgentResponse;
import com.choiceproduct.agentcenter.brain.DispatchDecision;
import com.choiceproduct.agentcenter.rag.RagContext;

public interface IntentHandler {
    String getHandlerCode();

    AgentResponse handle(AgentRequest request, DispatchDecision decision, RagContext ragContext);
}
