package com.ecommerce.workflow.agent.handler;

import com.ecommerce.workflow.agent.AgentRequest;
import com.ecommerce.workflow.agent.AgentResponse;
import com.ecommerce.workflow.entity.ChatSession;

public class DataAnalysisHandler extends AbstractIntentHandler {

    @Override
    public AgentResponse handle(AgentRequest request, ChatSession session, IntentResult intent) throws Exception {
        log.info("处理数据分析请求");

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append(String.format("""
                用户请求数据分析: %s

                请基于电商数据分析专家的角色,提供专业的数据分析和洞察。
                如果涉及具体数据查询,说明需要什么参数。
                """, request.getMessage()));

        appendRagContext(request.getContext(), promptBuilder);

        String response = gptChatService.chat(promptBuilder.toString());

        sessionService.saveMessage(session.getSessionId(), "assistant", response);
        return AgentResponse.success(response);
    }

    @Override
    public String getIntentCode() {
        return "data_analysis";
    }
}
