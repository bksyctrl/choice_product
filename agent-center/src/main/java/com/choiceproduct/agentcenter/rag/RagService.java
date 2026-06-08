package com.choiceproduct.agentcenter.rag;

import com.choiceproduct.agentcenter.agent.AgentRequest;
import org.springframework.stereotype.Service;

@Service
public class RagService {
    public RagContext enrich(AgentRequest request) {
        RagContext context = new RagContext();
        context.getSuccessExperiences().add("专家团队优先给出低阅读成本结论，再按需展开详细分析。");
        context.getAvoidanceGuides().add("涉及执行动作时，专家领导层只做判断和派工，实际改动交给具备能力的业务执行者。");
        context.getPlatformRules().add("IP/材质分析应优先读取规则库和历史经验库，再把精简后的证据交给 AI。");
        context.getMetadata().put("session_id", request.getSessionId());
        context.getMetadata().put("project_code", request.getProjectCode());
        return context;
    }
}
