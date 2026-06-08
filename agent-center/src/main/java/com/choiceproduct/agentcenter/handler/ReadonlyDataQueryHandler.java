package com.choiceproduct.agentcenter.handler;

import com.choiceproduct.agentcenter.agent.AgentRequest;
import com.choiceproduct.agentcenter.agent.AgentResponse;
import com.choiceproduct.agentcenter.brain.DispatchDecision;
import com.choiceproduct.agentcenter.rag.RagContext;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ReadonlyDataQueryHandler extends AbstractIntentHandler {
    @Override
    public String getHandlerCode() {
        return "readonly_data_query";
    }

    @Override
    public AgentResponse handle(AgentRequest request, DispatchDecision decision, RagContext ragContext) {
        return response(
                "已进入只读数据核查模式。专家可以读取规则库、历史经验库、商品快照，但不会直接修改数据。",
                decision,
                Map.of(
                        "allowed_actions", "SELECT, read-only HTTP",
                        "blocked_actions", "INSERT, UPDATE, DELETE, DDL"));
    }
}
