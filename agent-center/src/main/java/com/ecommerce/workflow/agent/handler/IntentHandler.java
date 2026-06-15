package com.ecommerce.workflow.agent.handler;

import com.ecommerce.workflow.agent.AgentRequest;
import com.ecommerce.workflow.agent.AgentResponse;
import com.ecommerce.workflow.entity.ChatSession;

public interface IntentHandler {
    
    AgentResponse handle(AgentRequest request, ChatSession session, IntentResult intent) throws Exception;
    
    String getIntentCode();
}
