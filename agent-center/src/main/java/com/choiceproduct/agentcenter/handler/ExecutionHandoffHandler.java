package com.choiceproduct.agentcenter.handler;

import com.choiceproduct.agentcenter.agent.AgentRequest;
import com.choiceproduct.agentcenter.agent.AgentResponse;
import com.choiceproduct.agentcenter.brain.DispatchDecision;
import com.choiceproduct.agentcenter.rag.RagContext;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ExecutionHandoffHandler extends AbstractIntentHandler {
    @Override
    public String getHandlerCode() {
        return "execution_handoff";
    }

    @Override
    public AgentResponse handle(AgentRequest request, DispatchDecision decision, RagContext ragContext) {
        String handoff = """
                已生成执行派工单：
                1. 目标：按用户确认的方案执行，不擅自扩大范围。
                2. 所需专家：%s。
                3. 所需工具：%s。
                4. 执行边界：先读上下文，再改动；涉及数据库写入、长任务或服务启动必须显式确认。
                5. 验收方式：返回修改文件、接口、验证结果和未完成风险。
                """.formatted(decision.getRequiredExperts(), decision.getRequiredTools());
        return response(
                handoff,
                decision,
                Map.of(
                        "executor", decision.getExecutor(),
                        "requires_confirmation", decision.isRequiresConfirmation(),
                        "rag_context", ragContext));
    }
}
