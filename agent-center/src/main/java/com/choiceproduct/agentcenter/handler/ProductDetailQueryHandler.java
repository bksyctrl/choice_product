package com.choiceproduct.agentcenter.handler;

import com.choiceproduct.agentcenter.agent.AgentRequest;
import com.choiceproduct.agentcenter.agent.AgentResponse;
import com.choiceproduct.agentcenter.brain.DispatchDecision;
import com.choiceproduct.agentcenter.rag.RagContext;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProductDetailQueryHandler extends AbstractIntentHandler {
    @Override
    public String getHandlerCode() {
        return "product_detail_query";
    }

    @Override
    public AgentResponse handle(AgentRequest request, DispatchDecision decision, RagContext ragContext) {
        if (!decision.isCanExecuteNow()) {
            return response(decision.getNextQuestion(), decision, Map.of("missing_params", decision.getMissingParams()));
        }
        return response(
                "已识别为商品详情只读查询。下一步可接入 Flask 的商品详情接口或数据库只读账号，读取 attributes、selling_points、IP/材质回执。",
                decision,
                Map.of(
                        "product_id", decision.getEntities().get("product_id"),
                        "source", decision.getEntities().getOrDefault("source", "unknown"),
                        "readonly_policy", "只允许 SELECT 和白名单接口，不执行写入。"));
    }
}
