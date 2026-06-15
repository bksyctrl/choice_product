package com.ecommerce.workflow.agent.handler;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ecommerce.workflow.agent.AgentRequest;
import com.ecommerce.workflow.agent.AgentResponse;
import com.ecommerce.workflow.delegate.entity.SubAgentTask;
import com.ecommerce.workflow.delegate.service.ParallelTaskExecutor;
import com.ecommerce.workflow.entity.ChatSession;
import com.ecommerce.workflow.service.ai.GptChatService;
import com.ecommerce.workflow.service.checkpoint.AutoSnapshotService;
import com.ecommerce.workflow.service.memory.UnifiedMemoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class DelegateIntentHandler extends AbstractIntentHandler {

    private static final Logger log = LoggerFactory.getLogger(DelegateIntentHandler.class);

    @Autowired
    private ParallelTaskExecutor taskExecutor;

    @Autowired
    private GptChatService chatService;

    @Autowired
    private UnifiedMemoryService unifiedMemoryService;

    @Autowired
    private AutoSnapshotService autoSnapshotService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String getIntentCode() {
        return "agent_delegate";
    }

    @Override
    public AgentResponse handle(AgentRequest request, ChatSession session, IntentResult intent) throws Exception {
        log.info("子任务委托意图处理: message={}", request.getMessage());

        String action = resolveDelegateAction(request.getMessage());

        return switch (action) {
            case "create" -> handleCreateTask(request, session, intent);
            case "parallel" -> handleParallelTasks(request, session, intent);
            case "status" -> handleTaskStatus(request, session, intent);
            case "retry" -> handleRetryTask(request, session, intent);
            case "auto" -> handleAutoDelegate(request, session, intent);
            default -> handleAutoDelegate(request, session, intent);
        };
    }

    private String resolveDelegateAction(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("并行") || lower.contains("同时") || lower.contains("批量")) {
            return "parallel";
        }
        if (lower.contains("状态") || lower.contains("进度") || lower.contains("查询")) {
            return "status";
        }
        if (lower.contains("重试") || lower.contains("重新")) {
            return "retry";
        }
        if (lower.contains("创建") || lower.contains("分配") || lower.contains("委托")) {
            return "create";
        }
        return "auto";
    }

    private AgentResponse handleCreateTask(AgentRequest request, ChatSession session, IntentResult intent) {
        try {
            Map<String, Object> params = extractDelegateParams(request.getMessage(), intent);

            String parentAgentId = (String) params.getOrDefault("parentAgentId", "main_agent");
            String subAgentId = (String) params.getOrDefault("subAgentId", "sub_" + System.currentTimeMillis());
            String taskType = (String) params.getOrDefault("taskType", "AI_CHAT");
            String taskDescription = (String) params.getOrDefault("taskDescription", request.getMessage());

            autoSnapshotService.autoSnapshotBeforeAction("agent_delegate",
                    "创建子任务: " + taskType, Map.of("userId", request.getUserId()));

            SubAgentTask task = taskExecutor.createTask(parentAgentId, subAgentId, taskType, taskDescription);

            String reply = String.format("""
                    子任务已创建成功!
                    - 任务ID: %s
                    - 父Agent: %s
                    - 子Agent: %s
                    - 任务类型: %s
                    - 任务描述: %s
                    - 状态: %s
                    
                    系统将自动执行此任务，您可以通过对话查询任务状态。
                    """, task.getTaskId(), parentAgentId, subAgentId, taskType, taskDescription, task.getStatus());

            sessionService.saveMessage(session.getSessionId(), "assistant", reply);

            unifiedMemoryService.recordBusinessAction("agent_delegate", "create",
                    Map.of("taskType", taskType, "parentAgentId", parentAgentId),
                    Map.of("taskId", task.getTaskId(), "status", task.getStatus()),
                    true, null);

            return AgentResponse.success(reply);
        } catch (Exception e) {
            log.error("创建子任务失败", e);
            String reply = "创建子任务失败: " + e.getMessage();
            sessionService.saveMessage(session.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        }
    }

    private AgentResponse handleParallelTasks(AgentRequest request, ChatSession session, IntentResult intent) {
        try {
            Map<String, Object> params = extractDelegateParams(request.getMessage(), intent);

            String systemPrompt = """
                    你是任务分解专家。请将用户的请求分解为多个可并行执行的子任务。
                    返回JSON格式:
                    {
                      "tasks": [
                        {"taskType": "VIDEO_GENERATION", "description": "任务描述", "subAgentId": "sub_1"},
                        {"taskType": "CONTENT_ANALYSIS", "description": "任务描述", "subAgentId": "sub_2"}
                      ]
                    }
                    
                    可用任务类型: VIDEO_GENERATION, CONTENT_ANALYSIS, DATA_PROCESSING, REPORT_GENERATION, AI_CHAT, WORKFLOW_TRIGGER
                    """;

            String response = chatService.chat(systemPrompt, request.getMessage());
            Map<String, Object> parsed = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) parsed.get("tasks");

            if (tasks == null || tasks.isEmpty()) {
                String reply = "无法分解为并行任务，请提供更明确的任务描述";
                sessionService.saveMessage(session.getSessionId(), "assistant", reply);
                return AgentResponse.success(reply);
            }

            autoSnapshotService.autoSnapshotBeforeAction("agent_delegate",
                    "并行任务: " + tasks.size() + "个子任务", Map.of("userId", request.getUserId()));

            List<SubAgentTask> createdTasks = new ArrayList<>();
            String parentAgentId = "main_agent";

            for (Map<String, Object> taskDef : tasks) {
                String taskType = (String) taskDef.getOrDefault("taskType", "AI_CHAT");
                String description = (String) taskDef.getOrDefault("description", "");
                String subAgentId = (String) taskDef.getOrDefault("subAgentId", "sub_" + System.currentTimeMillis());

                SubAgentTask task = taskExecutor.createTask(parentAgentId, subAgentId, taskType, description);
                createdTasks.add(task);
            }

            List<SubAgentTask> results = taskExecutor.executeTasksInParallel(createdTasks);

            StringBuilder reply = new StringBuilder();
            reply.append("并行任务执行完成!\n\n");
            for (int i = 0; i < results.size(); i++) {
                SubAgentTask result = results.get(i);
                reply.append(String.format("任务%d: %s - 状态: %s\n", i + 1,
                        result.getTaskType(), result.getStatus()));
            }

            String replyStr = reply.toString();
            sessionService.saveMessage(session.getSessionId(), "assistant", replyStr);

            unifiedMemoryService.recordBusinessAction("agent_delegate", "parallel",
                    Map.of("taskCount", tasks.size()),
                    Map.of("completedCount", results.stream().filter(t -> "completed".equals(t.getStatus())).count()),
                    true, null);

            return AgentResponse.success(replyStr);
        } catch (Exception e) {
            log.error("并行任务执行失败", e);
            String reply = "并行任务执行失败: " + e.getMessage();
            sessionService.saveMessage(session.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        }
    }

    private AgentResponse handleTaskStatus(AgentRequest request, ChatSession session, IntentResult intent) {
        try {
            Map<String, Object> params = extractDelegateParams(request.getMessage(), intent);
            String taskId = (String) params.get("taskId");

            if (taskId != null) {
                SubAgentTask task = taskExecutor.getTask(taskId);
                if (task != null) {
                    String reply = String.format("""
                            任务状态:
                            - 任务ID: %s
                            - 类型: %s
                            - 状态: %s
                            - 描述: %s
                            - 结果: %s
                            """, task.getTaskId(), task.getTaskType(), task.getStatus(),
                            task.getTaskDescription(), task.getResult());
                    sessionService.saveMessage(session.getSessionId(), "assistant", reply);
                    return AgentResponse.success(reply);
                }
            }

            List<SubAgentTask> allTasks = taskExecutor.listAllTasks();
            StringBuilder reply = new StringBuilder("所有子任务状态:\n\n");
            for (SubAgentTask task : allTasks) {
                reply.append(String.format("- %s [%s]: %s\n", task.getTaskId(), task.getTaskType(), task.getStatus()));
            }

            String replyStr = reply.toString();
            sessionService.saveMessage(session.getSessionId(), "assistant", replyStr);
            return AgentResponse.success(replyStr);
        } catch (Exception e) {
            log.error("查询任务状态失败", e);
            String reply = "查询任务状态失败: " + e.getMessage();
            sessionService.saveMessage(session.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        }
    }

    private AgentResponse handleRetryTask(AgentRequest request, ChatSession session, IntentResult intent) {
        try {
            Map<String, Object> params = extractDelegateParams(request.getMessage(), intent);
            String taskId = (String) params.get("taskId");

            if (taskId == null) {
                String reply = "请指定要重试的任务ID";
                sessionService.saveMessage(session.getSessionId(), "assistant", reply);
                return AgentResponse.success(reply);
            }

            taskExecutor.retryTask(taskId);
            String reply = "任务已重新提交: " + taskId;
            sessionService.saveMessage(session.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        } catch (Exception e) {
            log.error("重试任务失败", e);
            String reply = "重试任务失败: " + e.getMessage();
            sessionService.saveMessage(session.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        }
    }

    private AgentResponse handleAutoDelegate(AgentRequest request, ChatSession session, IntentResult intent) {
        String systemPrompt = """
                你是子任务委托助手。请分析用户消息，判断用户想要执行的操作。
                返回JSON格式:
                {
                  "action": "create|parallel|status|retry",
                  "parentAgentId": "父Agent ID",
                  "subAgentId": "子Agent ID",
                  "taskType": "VIDEO_GENERATION|CONTENT_ANALYSIS|DATA_PROCESSING|REPORT_GENERATION|AI_CHAT|WORKFLOW_TRIGGER",
                  "taskDescription": "任务描述",
                  "taskId": "任务ID(查询/重试时)"
                }
                
                如果用户描述的是一个复杂任务，建议使用parallel模式分解。
                如果用户只是想创建一个简单子任务，使用create模式。
                """;

        String response = chatService.chat(systemPrompt, request.getMessage());

        try {
            Map<String, Object> parsed = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
            String action = (String) parsed.getOrDefault("action", "create");

            IntentResult enhancedIntent = new IntentResult(
                    intent.getIntent(), intent.getConfidence(),
                    parsed, intent.getMissingParams(), intent.getSuggestedWorkflow());

            return switch (action) {
                case "parallel" -> handleParallelTasks(request, session, enhancedIntent);
                case "status" -> handleTaskStatus(request, session, enhancedIntent);
                case "retry" -> handleRetryTask(request, session, enhancedIntent);
                default -> handleCreateTask(request, session, enhancedIntent);
            };
        } catch (Exception e) {
            log.warn("自动解析委托意图失败，创建单任务", e);
            return handleCreateTask(request, session, intent);
        }
    }

    private Map<String, Object> extractDelegateParams(String message, IntentResult intent) {
        Map<String, Object> params = new HashMap<>();
        if (intent.getEntities() != null) {
            params.putAll(intent.getEntities());
        }
        return params;
    }
}
