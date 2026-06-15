package com.ecommerce.workflow.agent;

public interface Agent {
    String getAgentType();
    String getName();

    AgentResponse process(AgentRequest request) throws Exception;

    default boolean canHandle(String intent) {
        return true;
    }
}
