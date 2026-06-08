package com.choiceproduct.agentcenter.handler;

import com.choiceproduct.agentcenter.agent.AgentResponse;
import com.choiceproduct.agentcenter.brain.DispatchDecision;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractIntentHandler implements IntentHandler {
    protected AgentResponse response(String message, DispatchDecision decision, Map<String, Object> data) {
        AgentResponse response = AgentResponse.success(message);
        response.setIntent(decision.getIntentCode());
        response.setDecision(decision);
        response.setData(data == null ? new HashMap<>() : data);
        return response;
    }
}
