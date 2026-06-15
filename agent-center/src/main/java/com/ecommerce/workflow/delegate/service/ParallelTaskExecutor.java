package com.ecommerce.workflow.delegate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.workflow.delegate.entity.SubAgentTask;
import com.ecommerce.workflow.delegate.mapper.SubAgentTaskMapper;
import com.ecommerce.workflow.engine.WorkflowEngine;
import com.ecommerce.workflow.service.ai.AiProviderService;
import com.ecommerce.workflow.service.ai.GptChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
public class ParallelTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(ParallelTaskExecutor.class);

    @Autowired
    private SubAgentTaskMapper taskMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AiProviderService aiProviderService;

    @Autowired
    private GptChatService gptChatService;

    @Autowired
    private WorkflowEngine workflowEngine;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Transactional
    public SubAgentTask createTask(String parentAgentId, String subAgentId, String taskType, String taskDescription) {
        String taskId = "TASK_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);

        SubAgentTask task = new SubAgentTask();
        task.setTaskId(taskId);
        task.setParentAgentId(parentAgentId);
        task.setSubAgentId(subAgentId);
        task.setTaskType(taskType);
        task.setTaskDescription(taskDescription);
        task.setStatus("pending");
        task.setRetryCount(0);
        task.setCreatedAt(LocalDateTime.now());

        taskMapper.insert(task);

        log.info("创建子Agent任务: taskId={}, parent={}, sub={}", taskId, parentAgentId, subAgentId);

        return task;
    }

    public List<SubAgentTask> executeTasksInParallel(List<SubAgentTask> tasks) {
        List<Future<SubAgentTask>> futures = new ArrayList<>();

        for (SubAgentTask task : tasks) {
            Future<SubAgentTask> future = executorService.submit(() -> executeTask(task));
            futures.add(future);
        }

        List<SubAgentTask> results = new ArrayList<>();
        for (Future<SubAgentTask> future : futures) {
            try {
                SubAgentTask result = future.get(30, TimeUnit.SECONDS);
                results.add(result);
            } catch (TimeoutException e) {
                log.error("子任务执行超时: taskId={}", future.toString());
            } catch (Exception e) {
                log.error("子任务执行失败", e);
            }
        }

        return results;
    }

    @Transactional
    public SubAgentTask executeTask(SubAgentTask task) {
        task.setStatus("running");
        task.setStartTime(LocalDateTime.now());
        taskMapper.updateById(task);

        try {
            String result = executeByType(task.getTaskType(), task.getTaskDescription());

            task.setStatus("completed");
            task.setResult(result);
            task.setEndTime(LocalDateTime.now());

            log.info("子任务执行成功: taskId={}", task.getTaskId());
        } catch (Exception e) {
            task.setStatus("failed");
            task.setResult(e.getMessage());
            task.setEndTime(LocalDateTime.now());
            task.setRetryCount(task.getRetryCount() + 1);

            log.error("子任务执行失败: taskId={}", task.getTaskId(), e);
        }

        taskMapper.updateById(task);
        return task;
    }

    private String executeByType(String taskType, String taskDescription) {
        switch (taskType) {
            case "VIDEO_GENERATION":
                return executeVideoGeneration(taskDescription);
            case "CONTENT_ANALYSIS":
                return executeContentAnalysis(taskDescription);
            case "DATA_PROCESSING":
                return executeDataProcessing(taskDescription);
            case "REPORT_GENERATION":
                return executeReportGeneration(taskDescription);
            case "AI_CHAT":
                return executeAiChat(taskDescription);
            case "WORKFLOW_TRIGGER":
                return executeWorkflowTrigger(taskDescription);
            default:
                return executeGenericTask(taskType, taskDescription);
        }
    }

    private String executeVideoGeneration(String description) {
        log.info("执行视频生成任务: {}", description);
        try {
            Map<String, Object> params = parseTaskParams(description);
            String prompt = (String) params.getOrDefault("prompt", description);
            String model = (String) params.getOrDefault("model", "veo3.1-fast");

            Map<String, Object> result = aiProviderService.createVideoWithFallback(
                    prompt, model, true, true, null, "9:16");

            String taskId = (String) result.get("id");
            return objectMapper.writeValueAsString(Map.of(
                    "status", "created",
                    "taskId", taskId,
                    "message", "视频生成任务创建成功"));
        } catch (Exception e) {
            log.error("视频生成任务创建失败", e);
            return "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    private String executeContentAnalysis(String description) {
        log.info("执行内容分析任务: {}", description);
        try {
            String analysisPrompt = "请分析以下内容并提供详细的分析报告:\n\n" + description;
            String result = gptChatService.chat(analysisPrompt);

            return objectMapper.writeValueAsString(Map.of(
                    "status", "completed",
                    "analysis", result,
                    "message", "内容分析完成"));
        } catch (Exception e) {
            log.error("内容分析任务执行失败", e);
            return "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    private String executeDataProcessing(String description) {
        log.info("执行数据处理任务: {}", description);
        try {
            Map<String, Object> params = parseTaskParams(description);
            String operation = (String) params.getOrDefault("operation", "analyze");
            String data = (String) params.getOrDefault("data", description);

            String processPrompt = String.format(
                    "请根据以下操作类型处理数据: %s\n\n待处理数据:\n%s", operation, data);
            String result = gptChatService.chat(processPrompt);

            return objectMapper.writeValueAsString(Map.of(
                    "status", "completed",
                    "operation", operation,
                    "result", result,
                    "message", "数据处理完成"));
        } catch (Exception e) {
            log.error("数据处理任务执行失败", e);
            return "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    private String executeReportGeneration(String description) {
        log.info("执行报告生成任务: {}", description);
        try {
            Map<String, Object> params = parseTaskParams(description);
            String reportType = (String) params.getOrDefault("reportType", "daily");
            String context = (String) params.getOrDefault("context", description);

            String reportPrompt = String.format(
                    "请生成%s报告，包含以下内容:\n" +
                            "1. 摘要\n2. 关键指标\n3. 详细分析\n4. 建议和结论\n\n" +
                            "背景信息:\n%s",
                    reportType, context);

            String report = gptChatService.chat(reportPrompt);

            return objectMapper.writeValueAsString(Map.of(
                    "status", "completed",
                    "reportType", reportType,
                    "report", report,
                    "message", "报告生成完成"));
        } catch (Exception e) {
            log.error("报告生成任务执行失败", e);
            return "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    private String executeAiChat(String description) {
        log.info("执行AI对话任务: {}", description);
        try {
            String response = gptChatService.chat(description);

            return objectMapper.writeValueAsString(Map.of(
                    "status", "completed",
                    "response", response,
                    "message", "AI对话完成"));
        } catch (Exception e) {
            log.error("AI对话任务执行失败", e);
            return "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    private String executeWorkflowTrigger(String description) {
        log.info("执行任务触发工作流: {}", description);
        try {
            Map<String, Object> params = parseTaskParams(description);
            Long workflowId = params.containsKey("workflowId") ? ((Number) params.get("workflowId")).longValue() : null;

            if (workflowId == null) {
                return "{\"status\":\"error\",\"message\":\"缺少workflowId参数\"}";
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> inputParams = params.getOrDefault("inputParams", new HashMap<>()) instanceof Map
                    ? (Map<String, Object>) params.getOrDefault("inputParams", new HashMap<>())
                    : new HashMap<>();

            var instance = workflowEngine.createInstance(workflowId, "SUB_AGENT", 1L, inputParams);
            workflowEngine.startExecution(instance.getId());

            return objectMapper.writeValueAsString(Map.of(
                    "status", "triggered",
                    "instanceId", instance.getId(),
                    "instanceNo", instance.getInstanceNo(),
                    "message", "工作流触发成功"));
        } catch (Exception e) {
            log.error("执行任务触发工作流失败", e);
            return "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    private String executeGenericTask(String taskType, String description) {
        log.info("执行通用任务处理: type={}, description={}", taskType, description);
        try {
            String prompt = String.format(
                    "请处理以下任务:\n类型: %s\n描述: %s\n\n请提供详细的执行结果和建议。",
                    taskType, description);

            String result = gptChatService.chat(prompt);

            return objectMapper.writeValueAsString(Map.of(
                    "status", "completed",
                    "taskType", taskType,
                    "result", result,
                    "message", "任务执行完成"));
        } catch (Exception e) {
            log.error("执行通用任务处理失败", e);
            return "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    private Map<String, Object> parseTaskParams(String description) {
        try {
            if (description.startsWith("{") && description.endsWith("}")) {
                return objectMapper.readValue(description, Map.class);
            }
            Map<String, Object> params = new HashMap<>();
            params.put("description", description);
            return params;
        } catch (Exception e) {
            Map<String, Object> params = new HashMap<>();
            params.put("description", description);
            return params;
        }
    }

    public List<SubAgentTask> listTasksByParent(String parentAgentId) {
        LambdaQueryWrapper<SubAgentTask> wrapper = new LambdaQueryWrapper<>();
        if (parentAgentId != null && !parentAgentId.isEmpty() && !"null".equals(parentAgentId)) {
            wrapper.eq(SubAgentTask::getParentAgentId, parentAgentId);
        }
        wrapper.orderByDesc(SubAgentTask::getCreatedAt);

        return taskMapper.selectList(wrapper);
    }

    public List<SubAgentTask> listAllTasks() {
        LambdaQueryWrapper<SubAgentTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SubAgentTask::getCreatedAt);
        return taskMapper.selectList(wrapper);
    }

    public SubAgentTask getTask(String taskId) {
        LambdaQueryWrapper<SubAgentTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubAgentTask::getTaskId, taskId);

        return taskMapper.selectOne(wrapper);
    }

    @Transactional
    public void retryTask(String taskId) {
        SubAgentTask task = getTask(taskId);

        if (task != null && "failed".equals(task.getStatus())) {
            task.setStatus("pending");
            task.setRetryCount(task.getRetryCount() + 1);
            taskMapper.updateById(task);

            executeTask(task);
        }
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}
