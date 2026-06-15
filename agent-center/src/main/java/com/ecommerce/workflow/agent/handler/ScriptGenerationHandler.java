package com.ecommerce.workflow.agent.handler;

import com.ecommerce.workflow.agent.AgentRequest;
import com.ecommerce.workflow.agent.AgentResponse;
import com.ecommerce.workflow.entity.ChatSession;
import com.ecommerce.workflow.entity.WorkflowDefinition;

public class ScriptGenerationHandler extends AbstractIntentHandler {

    @Override
    public AgentResponse handle(AgentRequest request, ChatSession session, IntentResult intent) throws Exception {
        log.info("处理脚本生成请求: entities={}", intent.getEntities());

        if (!intent.getMissingParams().isEmpty()) {
            String reply = buildParameterCompletionMessage(intent);
            sessionService.saveMessage(session.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        }

        WorkflowDefinition definition = findWorkflowByCode("script_batch_generation");
        if (definition == null) {
            definition = findWorkflowByCode("single_script_generation");
        }

        if (definition == null) {
            String reply = "抱歉,脚本生成工作流尚未配置完成。";
            sessionService.saveMessage(session.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        }

        String successMessage = "已为您启动脚本生成工作流!\n" +
                "- 任务编号: %s\n" +
                "- 正在基于爆款因子生成差异化脚本...\n" +
                "- 将自动进行合规校验\n\n" +
                "生成完成后会通知您审核。";

        return triggerWorkflow(definition.getWorkflowCode(), request, intent, successMessage);
    }

    @Override
    public String getIntentCode() {
        return "script_generation";
    }
}
