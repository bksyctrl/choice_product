package com.ecommerce.workflow.agent.handler;

import java.util.Map;

import com.ecommerce.workflow.agent.AgentRequest;
import com.ecommerce.workflow.agent.AgentResponse;
import com.ecommerce.workflow.entity.ChatSession;
import com.ecommerce.workflow.entity.WorkflowInstance;
import com.ecommerce.workflow.mapper.WorkflowInstanceMapper;

public class WorkflowQueryHandler extends AbstractIntentHandler {

    private WorkflowInstanceMapper workflowInstanceMapper;

    public void setWorkflowInstanceMapper(WorkflowInstanceMapper workflowInstanceMapper) {
        this.workflowInstanceMapper = workflowInstanceMapper;
    }

    @Override
    public AgentResponse handle(AgentRequest request, ChatSession session, IntentResult intent) throws Exception {
        log.info("处理工作流查询请求");

        Long instanceId = null;
        if (request.getParameters() != null && request.getParameters().containsKey("instanceId")) {
            instanceId = ((Number) request.getParameters().get("instanceId")).longValue();
        }

        if (instanceId != null) {
            WorkflowInstance instance = workflowInstanceMapper.selectById(instanceId);
            if (instance != null) {
                String statusReply = formatWorkflowStatus(instance);
                sessionService.saveMessage(session.getSessionId(), "assistant", statusReply);
                return AgentResponse.success(statusReply, Map.of("instance", instance));
            }
        }

        String reply = "未找到相关的工作流实例。请提供任务编号或描述您要查询的任务。";
        sessionService.saveMessage(session.getSessionId(), "assistant", reply);
        return AgentResponse.success(reply);
    }

    private String formatWorkflowStatus(WorkflowInstance instance) {
        return String.format("""
                工作流状态:
                - 编号: %s
                - 状态: %s
                - 进度: %d%%
                - 创建时间: %s
                """,
                instance.getInstanceNo(),
                instance.getStatus(),
                instance.getProgress() != null ? instance.getProgress() : 0,
                instance.getCreatedAt());
    }

    @Override
    public String getIntentCode() {
        return "workflow_query";
    }
}
