package com.choiceproduct.agentcenter.agent;

public interface Agent {
    String getAgentType();

    String getName();

    AgentResponse process(AgentRequest request);
}
