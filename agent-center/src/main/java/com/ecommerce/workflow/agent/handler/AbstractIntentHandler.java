package com.ecommerce.workflow.agent.handler;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.workflow.agent.AgentRequest;
import com.ecommerce.workflow.agent.AgentResponse;
import com.ecommerce.workflow.engine.WorkflowEngine;
import com.ecommerce.workflow.entity.WorkflowDefinition;
import com.ecommerce.workflow.entity.WorkflowInstance;
import com.ecommerce.workflow.mapper.WorkflowDefinitionMapper;
import com.ecommerce.workflow.service.ai.GptChatService;
import com.ecommerce.workflow.service.session.SessionService;

public abstract class AbstractIntentHandler implements IntentHandler {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected WorkflowEngine workflowEngine;
    protected SessionService sessionService;
    protected WorkflowDefinitionMapper workflowDefinitionMapper;
    protected GptChatService gptChatService;

    public void setWorkflowEngine(WorkflowEngine workflowEngine) {
        this.workflowEngine = workflowEngine;
    }

    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public void setWorkflowDefinitionMapper(WorkflowDefinitionMapper workflowDefinitionMapper) {
        this.workflowDefinitionMapper = workflowDefinitionMapper;
    }

    public void setGptChatService(GptChatService gptChatService) {
        this.gptChatService = gptChatService;
    }

    protected WorkflowDefinition findWorkflowByCode(String code) {
        return workflowDefinitionMapper.selectOne(
                new QueryWrapper<WorkflowDefinition>()
                        .eq("workflow_code", code)
                        .eq("status", 1)
                        .eq("deleted", 0)
                        .last("LIMIT 1"));
    }

    protected String buildParameterCompletionMessage(IntentResult intent) {
        StringBuilder sb = new StringBuilder("请提供以下信息\n");
        for (String param : intent.getMissingParams()) {
            sb.append("- ").append(param).append("\n");
        }
        return sb.toString();
    }

    protected Map<String, Object> buildInputParams(AgentRequest request, IntentResult intent) {
        Map<String, Object> inputParams = new HashMap<>();
        inputParams.putAll(intent.getEntities());
        if (request.getContext() != null) {
            inputParams.putAll(request.getContext());
        }
        return inputParams;
    }

    /**
     * 将RAG知识增强上下文注入到prompt中，使GPT回答能参考知识库内容
     */
    protected void appendRagContext(Map<String, Object> context, StringBuilder promptBuilder) {
        if (context == null) {
            return;
        }
        Object knowledge = context.get("relevantKnowledge");
        if (knowledge != null && !knowledge.toString().isEmpty()) {
            promptBuilder.append("\n以下是与用户问题相关的知识库内容,请参考这些信息回答\n");
            promptBuilder.append(knowledge.toString());
            promptBuilder.append("\n");
        }
        Object enhancedQuery = context.get("enhancedQuery");
        if (enhancedQuery != null && !enhancedQuery.toString().isEmpty()) {
            promptBuilder.append("\n增强查询理解: ").append(enhancedQuery.toString()).append("\n");
        }
    }

    protected AgentResponse triggerWorkflow(String workflowCode, AgentRequest request,
            IntentResult intent, String successMessage) {
        if (!intent.getMissingParams().isEmpty()) {
            String reply = buildParameterCompletionMessage(intent);
            sessionService.saveMessage(request.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        }

        WorkflowDefinition definition = findWorkflowByCode(workflowCode);
        if (definition == null) {
            String reply = "抱歉,工作流尚未配置完成";
            sessionService.saveMessage(request.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        }

        Map<String, Object> inputParams = buildInputParams(request, intent);

        WorkflowInstance instance = workflowEngine.createInstance(
                definition.getId(),
                "agent",
                request.getUserId(),
                inputParams);

        workflowEngine.startExecution(instance.getId());

        String reply = String.format(successMessage, instance.getInstanceNo());
        sessionService.saveMessage(request.getSessionId(), "assistant", reply);

        return AgentResponse.workflowTriggered(reply, instance.getId());
    }
}
