package com.ecommerce.workflow.agent.handler;

import com.ecommerce.workflow.agent.AgentRequest;
import com.ecommerce.workflow.agent.AgentResponse;
import com.ecommerce.workflow.entity.ChatSession;

public class GeneralChatHandler extends AbstractIntentHandler {

    @Override
    public AgentResponse handle(AgentRequest request, ChatSession session, IntentResult intent) throws Exception {
        log.info("处理一般对话请求");

        StringBuilder systemPromptBuilder = new StringBuilder();
        systemPromptBuilder.append("""
                你是电商内容自动化生产系统的智能助手,名为"不可思议AI助手"。
                你的主要能力包括:
                1. 智能选品 - 帮助用户发现高潜力爆款
                2. 爆款归因 - 分析爆款成功的关键因素
                3. 脚本生成 - 自动生成高转化带货脚本
                4. 视频生产 - AI驱动短视频自动化生产
                5. 合规管控 - 全链路合规检查与优化
                6. 数据分析 - 提供专业的电商数据分析
                7. 自进化配置 - 自动配置Skill参数,系统会根据效果数据持续优化
                回答要求:
                - 专业且友好
                - 引导用户使用系统功能
                - 如果用户的需求可以触发工作流,主动引导
                """);

        appendRagContext(request.getContext(), systemPromptBuilder);
        String response = gptChatService.chat(systemPromptBuilder.toString(), request.getMessage());
        sessionService.saveMessage(session.getSessionId(), "assistant", response);
        return AgentResponse.success(response);
    }

    @Override
    public String getIntentCode() {
        return "general_chat";
    }
}
