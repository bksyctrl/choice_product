package com.ecommerce.workflow.agent.handler;

import com.ecommerce.workflow.agent.AgentRequest;
import com.ecommerce.workflow.agent.AgentResponse;
import com.ecommerce.workflow.entity.ChatSession;

public class VideoGenerationHandler extends AbstractIntentHandler {

    @Override
    public AgentResponse handle(AgentRequest request, ChatSession session, IntentResult intent) throws Exception {
        log.info("处理视频生成请求: entities={}", intent.getEntities());

        String successMessage = "已为您启动视频生产工作流!\n" +
                "- 任务编号: %s\n" +
                "- 正在调用VEO模型生成视频...\n" +
                "- 包含品控复核环节\n\n" +
                "视频生成需要较长时�?请耐心等待";

        return triggerWorkflow("video_batch_production", request, intent, successMessage);
    }

    @Override
    public String getIntentCode() {
        return "video_generation";
    }
}
