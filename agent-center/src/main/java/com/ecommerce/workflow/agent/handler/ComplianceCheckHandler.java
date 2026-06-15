package com.ecommerce.workflow.agent.handler;

import com.ecommerce.workflow.agent.AgentRequest;
import com.ecommerce.workflow.agent.AgentResponse;
import com.ecommerce.workflow.entity.ChatSession;

public class ComplianceCheckHandler extends AbstractIntentHandler {

    @Override
    public AgentResponse handle(AgentRequest request, ChatSession session, IntentResult intent) throws Exception {
        log.info("处理合规检查请求");

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append(String.format("""
                用户请求合规检查: %s

                作为电商合规专家,请帮助用户进行合规性检查,包括:
                1. 广告法合规性
                2. 平台规则符合性
                3. 知识产权风险
                4. 违禁词检测
                如果用户提供具体文本或图片,请详细分析;否则询问需要检查的内容。
                """, request.getMessage()));

        appendRagContext(request.getContext(), promptBuilder);

        String response = gptChatService.chat(promptBuilder.toString());

        sessionService.saveMessage(session.getSessionId(), "assistant", response);
        return AgentResponse.success(response);
    }

    @Override
    public String getIntentCode() {
        return "compliance_check";
    }
}
