package com.choiceproduct.agentcenter.handler;

import com.choiceproduct.agentcenter.agent.AgentRequest;
import com.choiceproduct.agentcenter.agent.AgentResponse;
import com.choiceproduct.agentcenter.brain.DispatchDecision;
import com.choiceproduct.agentcenter.rag.RagContext;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GeneralChatHandler extends AbstractIntentHandler {
    @Override
    public String getHandlerCode() {
        return "general_chat";
    }

    @Override
    public AgentResponse handle(AgentRequest request, DispatchDecision decision, RagContext ragContext) {
        if (!decision.isCanExecuteNow() && decision.getNextQuestion() != null) {
            return response(decision.getNextQuestion(), decision, Map.of("missing_params", decision.getMissingParams()));
        }
        Object imageCount = decision.getEntities().get("image_count");
        if (imageCount != null) {
            return response(
                    "已收到 " + imageCount + " 张图片。当前网页已经能把图片传给专家上下文，但视觉识别模型还没有接入，所以现在只能先基于你的文字说明判断；下一步可以接 MiniMax/其他视觉模型，让 IP、材质、主图元素分析真正读取图片。",
                    decision,
                    Map.of("image_count", imageCount, "rag_context", ragContext));
        }
        return response(
                "我会先作为专家领导层和你确认方向：判断该调用哪些专家、需要哪些只读数据、是否要交给执行者。",
                decision,
                Map.of("rag_context", ragContext));
    }
}
