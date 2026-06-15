package com.ecommerce.workflow.agent.handler;

import com.ecommerce.workflow.agent.AgentRequest;
import com.ecommerce.workflow.agent.AgentResponse;
import com.ecommerce.workflow.entity.ChatSession;

public class ProductSelectionHandler extends AbstractIntentHandler {

    @Override
    public AgentResponse handle(AgentRequest request, ChatSession session, IntentResult intent) throws Exception {
        log.info("处理选品请求: entities={}", intent.getEntities());

        String successMessage = "已为您启动智能选品工作流!\n" +
                "- 任务编号: %s\n" +
                "- 正在抓取和分析数据...\n" +
                "- 预计耗时: 2-5分钟\n\n" +
                "您可以随时询问执行进度。";

        return triggerWorkflow("smart_selection_workflow", request, intent, successMessage);
    }

    @Override
    public String getIntentCode() {
        return "product_selection";
    }
}
