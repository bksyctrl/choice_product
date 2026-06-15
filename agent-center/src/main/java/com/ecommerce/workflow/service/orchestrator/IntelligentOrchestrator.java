package com.ecommerce.workflow.service.orchestrator;

import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.ecommerce.workflow.service.SmartRouterService;
import com.ecommerce.workflow.service.config.SysConfigService;

import jakarta.annotation.PostConstruct;

@Service
public class IntelligentOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(IntelligentOrchestrator.class);

    private final SmartRouterService smartRouterService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    private SysConfigService sysConfigService;

    private final PriorityBlockingQueue<OrchestratedTask> taskQueue = new PriorityBlockingQueue<>(100,
            Comparator.comparingInt(OrchestratedTask::getPriority).reversed()
                    .thenComparingLong(OrchestratedTask::getCreatedAt));

    private final Map<String, OrchestratedTask> runningTasks = new ConcurrentHashMap<>();
    private final Map<String, TaskResult> completedResults = new ConcurrentHashMap<>();
    private final Map<String, ToolDescriptor> toolRegistry = new ConcurrentHashMap<>();
    private final Map<String, Integer> retryCountMap = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;
    private ExecutorService workerPool;

    private int maxRetries = 3;
    private long defaultTimeoutMs = 120000;
    private int maxConcurrentTasks = 10;

    public IntelligentOrchestrator(SmartRouterService smartRouterService, JdbcTemplate jdbcTemplate) {
        this.smartRouterService = smartRouterService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        initTables();
        loadConfigFromSysConfig();
        registerBuiltinTools();
        initThreadPool();
        startTaskProcessor();
        startTimeoutMonitor();
        log.info("智能调度层初始化完成: maxRetries={}, defaultTimeoutMs={}, maxConcurrentTasks={}",
                maxRetries, defaultTimeoutMs, maxConcurrentTasks);
    }

    private void loadConfigFromSysConfig() {
        maxRetries = sysConfigService.getIntConfig("max_retries", 3);
        defaultTimeoutMs = sysConfigService.getLongConfig("default_timeout_ms", 120000);
        maxConcurrentTasks = sysConfigService.getIntConfig("max_concurrent_tasks", 10);
    }

    private void initThreadPool() {
        int poolSize = sysConfigService.getIntConfig("worker_pool_size", 10);
        scheduler = Executors.newScheduledThreadPool(3);
        workerPool = Executors.newFixedThreadPool(poolSize);
    }

    private void initTables() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_orchestrator_config (
                    config_key VARCHAR(100) PRIMARY KEY,
                    config_value VARCHAR(500),
                    description VARCHAR(200),
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        } catch (Exception e) {
            log.debug("sys_orchestrator_config table may already exist");
        }

        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_orchestrator_task_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    task_id VARCHAR(64) NOT NULL,
                    task_type VARCHAR(50),
                    tool_name VARCHAR(100),
                    priority INT DEFAULT 5,
                    status VARCHAR(20),
                    retry_count INT DEFAULT 0,
                    error_message TEXT,
                    input_params TEXT,
                    output_result TEXT,
                    latency_ms BIGINT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    completed_at TIMESTAMP NULL,
                    INDEX idx_task_type (task_type),
                    INDEX idx_status (status),
                    INDEX idx_created (created_at)
                )
                """);
        } catch (Exception e) {
            log.debug("sys_orchestrator_task_log table may already exist");
        }
    }

    private void loadConfigFromDb() {
        loadConfigFromSysConfig();
    }

    private void registerBuiltinTools() {
        registerTool("ai_chat", "AI对话", "chat", 1, 60000L);
        registerTool("ai_chat_stream", "AI流式对话", "chat_stream", 1, 120000L);
        registerTool("video_generate", "视频生成", "video", 2, 300000L);
        registerTool("video_query", "视频查询", "video", 2, 30000L);
        registerTool("image_analyze", "图片分析", "vision", 1, 60000L);
        registerTool("knowledge_search", "知识库搜索", "knowledge", 3, 15000L);
        registerTool("vector_search", "向量搜索", "vector", 3, 15000L);
        registerTool("compliance_check", "合规检查", "compliance", 1, 10000L);
        registerTool("skill_execute", "技能执行", "skill", 2, 60000L);
        registerTool("mcp_call", "MCP工具调用", "mcp", 2, 60000L);
        registerTool("snapshot_create", "快照创建", "snapshot", 3, 30000L);
        registerTool("delegate_task", "子任务委托", "delegate", 2, 120000L);
        registerTool("data_analysis", "数据分析", "analysis", 2, 60000L);
        registerTool("rag_query", "RAG查询", "rag", 3, 30000L);
        registerTool("embedding_create", "向量嵌入", "embedding", 3, 30000L);
        registerTool("project_file_scout", "项目文件侦察", "file", 2, 15000L);
        registerTool("database_query", "数据库只读查询", "database", 2, 15000L);

        log.info("注册内置工具: {} 个", toolRegistry.size());
    }

    public void registerTool(String toolName, String description, String category,
                             int requiredPermission, long timeoutMs) {
        ToolDescriptor tool = new ToolDescriptor();
        tool.setToolName(toolName);
        tool.setDescription(description);
        tool.setCategory(category);
        tool.setRequiredPermission(requiredPermission);
        tool.setTimeoutMs(timeoutMs);
        tool.setEnabled(true);
        toolRegistry.put(toolName, tool);
    }

    public String submitTask(String toolName, Map<String, Object> params, int priority) {
        return submitTask(toolName, params, priority, null);
    }

    public String submitTask(String toolName, Map<String, Object> params, int priority, String userId) {
        ToolDescriptor tool = toolRegistry.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("未注册的工具: " + toolName);
        }
        if (!tool.isEnabled()) {
            throw new IllegalStateException("工具已禁用: " + toolName);
        }

        String taskId = "TASK_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        OrchestratedTask task = new OrchestratedTask();
        task.setTaskId(taskId);
        task.setToolName(toolName);
        task.setParams(params);
        task.setPriority(priority);
        task.setUserId(userId);
        task.setTimeoutMs(tool.getTimeoutMs());
        task.setCreatedAt(System.currentTimeMillis());
        task.setStatus("QUEUED");

        taskQueue.offer(task);
        logTaskToDb(task);

        log.info("任务已提交: taskId={}, tool={}, priority={}", taskId, toolName, priority);
        return taskId;
    }

    public TaskResult executeDirect(String toolName, Map<String, Object> params) {
        long startTime = System.currentTimeMillis();
        String taskId = "DIRECT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        ToolDescriptor tool = toolRegistry.get(toolName);
        if (tool == null) {
            return TaskResult.failure(taskId, "未注册的工具: " + toolName);
        }

        try {
            TaskResult result = executeWithRetry(taskId, toolName, params, maxRetries, tool.getTimeoutMs());
            result.setLatencyMs(System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            return TaskResult.failure(taskId, e.getMessage());
        }
    }

    private TaskResult executeWithRetry(String taskId, String toolName, Map<String, Object> params,
                                        int maxRetries, long timeoutMs) {
        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                CompletableFuture<TaskResult> future = CompletableFuture.supplyAsync(
                        () -> executeToolInternal(taskId, toolName, params), workerPool);

                TaskResult result = future.get(timeoutMs, TimeUnit.MILLISECONDS);

                if (result.isSuccess()) {
                    retryCountMap.remove(taskId);
                    return result;
                }

                lastException = new RuntimeException(result.getErrorMessage());
            } catch (TimeoutException e) {
                lastException = new RuntimeException("任务超时: " + timeoutMs + "ms");
                log.warn("任务超时: taskId={}, tool={}, attempt={}", taskId, toolName, attempt);
            } catch (Exception e) {
                lastException = e;
                log.warn("任务执行失败: taskId={}, tool={}, attempt={}, error={}",
                        taskId, toolName, attempt, e.getMessage());
            }

            if (attempt < maxRetries) {
                long backoffBase = sysConfigService.getLongConfig("retry_backoff_base", 1000);
                long backoffMax = sysConfigService.getLongConfig("retry_backoff_max", 30000);
                long backoff = (long) Math.min(backoffBase * Math.pow(2, attempt), backoffMax);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        retryCountMap.put(taskId, maxRetries);
        return TaskResult.failure(taskId, lastException != null ? lastException.getMessage() : "未知错误");
    }

    private TaskResult executeToolInternal(String taskId, String toolName, Map<String, Object> params) {
        try {
            switch (toolName) {
                case "ai_chat", "ai_chat_stream" -> {
                    return TaskResult.success(taskId, Map.of("tool", toolName, "params", params, "status", "dispatched"));
                }
                case "video_generate", "video_query" -> {
                    return TaskResult.success(taskId, Map.of("tool", toolName, "params", params, "status", "dispatched"));
                }
                case "image_analyze" -> {
                    return TaskResult.success(taskId, Map.of("tool", toolName, "params", params, "status", "dispatched"));
                }
                case "knowledge_search", "vector_search", "rag_query" -> {
                    return TaskResult.success(taskId, Map.of("tool", toolName, "params", params, "status", "dispatched"));
                }
                case "compliance_check" -> {
                    return TaskResult.success(taskId, Map.of("tool", toolName, "params", params, "status", "dispatched"));
                }
                case "skill_execute" -> {
                    return TaskResult.success(taskId, Map.of("tool", toolName, "params", params, "status", "dispatched"));
                }
                case "mcp_call" -> {
                    return TaskResult.success(taskId, Map.of("tool", toolName, "params", params, "status", "dispatched"));
                }
                case "snapshot_create" -> {
                    return TaskResult.success(taskId, Map.of("tool", toolName, "params", params, "status", "dispatched"));
                }
                case "delegate_task" -> {
                    return TaskResult.success(taskId, Map.of("tool", toolName, "params", params, "status", "dispatched"));
                }
                case "project_file_scout" -> {
                    return executeProjectFileScout(taskId, params);
                }
                case "database_query" -> {
                    return executeDatabaseQuery(taskId, params);
                }
                default -> {
                    return TaskResult.failure(taskId, "未知的工具: " + toolName);
                }
            }
        } catch (Exception e) {
            return TaskResult.failure(taskId, e.getMessage());
        }
    }

    private TaskResult executeProjectFileScout(String taskId, Map<String, Object> params) {
        Object target = params != null ? params.get("target_file") : null;
        if (target == null || target.toString().isBlank()) {
            return TaskResult.failure(taskId, "缺少 target_file 参数");
        }
        try {
            Path base = Paths.get("D:\\choice_product").toAbsolutePath().normalize();
            Path file = Paths.get(target.toString().replace("/", "\\")).toAbsolutePath().normalize();
            if (!file.startsWith(base)) {
                return TaskResult.failure(taskId, "安全拦截：不允许读取项目目录外文件");
            }
            if (!Files.isRegularFile(file)) {
                return TaskResult.failure(taskId, "文件不存在或不是普通文件: " + file);
            }
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.length() > 15000) {
                content = content.substring(0, 15000) + "\n\n...(内容过长已截断)...";
            }
            return TaskResult.success(taskId, Map.of("file", file.toString(), "content", content));
        } catch (Exception e) {
            return TaskResult.failure(taskId, "读取文件失败: " + e.getMessage());
        }
    }

    private TaskResult executeDatabaseQuery(String taskId, Map<String, Object> params) {
        Object table = params != null ? params.get("table_name") : null;
        if (table == null || table.toString().isBlank()) {
            return TaskResult.failure(taskId, "缺少 table_name 参数");
        }
        String tableName = table.toString().replaceAll("[^a-zA-Z0-9_]", "");
        if (tableName.isBlank()) {
            return TaskResult.failure(taskId, "table_name 非法");
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SHOW CREATE TABLE " + tableName);
            return TaskResult.success(taskId, Map.of("table", tableName, "rows", rows));
        } catch (Exception e) {
            return TaskResult.failure(taskId, "数据库只读查询失败: " + e.getMessage());
        }
    }

    private void startTaskProcessor() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                while (runningTasks.size() < maxConcurrentTasks && !taskQueue.isEmpty()) {
                    OrchestratedTask task = taskQueue.poll();
                    if (task != null) {
                        processTask(task);
                    }
                }
            } catch (Exception e) {
                log.error("任务处理器异常", e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void processTask(OrchestratedTask task) {
        task.setStatus("RUNNING");
        task.setStartedAt(System.currentTimeMillis());
        runningTasks.put(task.getTaskId(), task);

        workerPool.submit(() -> {
            try {
                TaskResult result = executeWithRetry(task.getTaskId(), task.getToolName(),
                        task.getParams(), maxRetries, task.getTimeoutMs());

                result.setLatencyMs(System.currentTimeMillis() - task.getCreatedAt());
                completedResults.put(task.getTaskId(), result);
                task.setStatus(result.isSuccess() ? "COMPLETED" : "FAILED");
                task.setErrorMessage(result.getErrorMessage());

                updateTaskLogInDb(task, result);
            } catch (Exception e) {
                task.setStatus("FAILED");
                task.setErrorMessage(e.getMessage());
            } finally {
                runningTasks.remove(task.getTaskId());
            }
        });
    }

    private void startTimeoutMonitor() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                long now = System.currentTimeMillis();
                for (Map.Entry<String, OrchestratedTask> entry : runningTasks.entrySet()) {
                    OrchestratedTask task = entry.getValue();
                    if (task.getTimeoutMs() > 0 && (now - task.getStartedAt()) > task.getTimeoutMs()) {
                        task.setStatus("TIMEOUT");
                        task.setErrorMessage("任务超时");
                        runningTasks.remove(entry.getKey());
                        log.warn("任务超时: taskId={}, tool={}", task.getTaskId(), task.getToolName());
                    }
                }
            } catch (Exception e) {
                log.error("超时监控异常", e);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    public TaskResult getTaskResult(String taskId) {
        TaskResult result = completedResults.get(taskId);
        if (result != null) {
            return result;
        }
        OrchestratedTask running = runningTasks.get(taskId);
        if (running != null) {
            return TaskResult.pending(taskId, running.getStatus());
        }
        return TaskResult.pending(taskId, "UNKNOWN");
    }

    public List<OrchestratedTask> getQueuedTasks() {
        return new ArrayList<>(taskQueue);
    }

    public List<OrchestratedTask> getRunningTasks() {
        return new ArrayList<>(runningTasks.values());
    }

    public Map<String, Object> getOrchestratorStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("queuedTasks", taskQueue.size());
        stats.put("runningTasks", runningTasks.size());
        stats.put("completedTasks", completedResults.size());
        stats.put("registeredTools", toolRegistry.size());
        stats.put("maxRetries", maxRetries);
        stats.put("defaultTimeoutMs", defaultTimeoutMs);
        stats.put("maxConcurrentTasks", maxConcurrentTasks);

        long successCount = completedResults.values().stream().filter(TaskResult::isSuccess).count();
        long failCount = completedResults.values().stream().filter(r -> !r.isSuccess()).count();
        stats.put("successCount", successCount);
        stats.put("failCount", failCount);

        return stats;
    }

    public List<ToolDescriptor> getRegisteredTools() {
        return new ArrayList<>(toolRegistry.values());
    }

    public void updateConfig(String key, String value) {
        try {
            sysConfigService.setConfig("orchestrator", key, value, "STRING", "调度层配置");

            switch (key) {
                case "max_retries" -> maxRetries = Integer.parseInt(value);
                case "default_timeout_ms" -> defaultTimeoutMs = Long.parseLong(value);
                case "max_concurrent_tasks" -> maxConcurrentTasks = Integer.parseInt(value);
            }
            log.info("调度配置已更新: {}={}", key, value);
        } catch (Exception e) {
            log.error("更新调度配置失败: {}", key, e);
        }
    }

    private void logTaskToDb(OrchestratedTask task) {
        try {
            String inputParams = task.getParams() != null ? task.getParams().toString() : null;
            jdbcTemplate.update("""
                INSERT INTO sys_orchestrator_task_log
                (task_id, task_type, tool_name, priority, status, retry_count, input_params, created_at)
                VALUES (?, ?, ?, ?, ?, 0, ?, NOW())
                """, task.getTaskId(), task.getToolName(), task.getToolName(),
                    task.getPriority(), task.getStatus(), inputParams);
        } catch (Exception e) {
            log.debug("记录任务日志失败: {}", task.getTaskId(), e);
        }
    }

    private void updateTaskLogInDb(OrchestratedTask task, TaskResult result) {
        try {
            String outputResult = result.getData() != null ? result.getData().toString() : null;
            jdbcTemplate.update("""
                UPDATE sys_orchestrator_task_log
                SET status = ?, error_message = ?, output_result = ?, latency_ms = ?,
                    retry_count = ?, completed_at = NOW()
                WHERE task_id = ?
                """, task.getStatus(), task.getErrorMessage(), outputResult,
                    result.getLatencyMs(), retryCountMap.getOrDefault(task.getTaskId(), 0),
                    task.getTaskId());
        } catch (Exception e) {
            log.debug("更新任务日志失败: {}", task.getTaskId(), e);
        }
    }

    public static class OrchestratedTask {
        private String taskId;
        private String toolName;
        private Map<String, Object> params;
        private int priority;
        private String userId;
        private long timeoutMs;
        private long createdAt;
        private long startedAt;
        private String status;
        private String errorMessage;

        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        public Map<String, Object> getParams() { return params; }
        public void setParams(Map<String, Object> params) { this.params = params; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getStartedAt() { return startedAt; }
        public void setStartedAt(long startedAt) { this.startedAt = startedAt; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }

    public static class TaskResult {
        private String taskId;
        private boolean success;
        private String errorMessage;
        private Map<String, Object> data;
        private long latencyMs;

        public static TaskResult success(String taskId, Map<String, Object> data) {
            TaskResult r = new TaskResult();
            r.taskId = taskId;
            r.success = true;
            r.data = data;
            return r;
        }

        public static TaskResult failure(String taskId, String errorMessage) {
            TaskResult r = new TaskResult();
            r.taskId = taskId;
            r.success = false;
            r.errorMessage = errorMessage;
            return r;
        }

        public static TaskResult pending(String taskId, String status) {
            TaskResult r = new TaskResult();
            r.taskId = taskId;
            r.success = false;
            r.errorMessage = "Task status: " + status;
            return r;
        }

        public String getTaskId() { return taskId; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public Map<String, Object> getData() { return data; }
        public long getLatencyMs() { return latencyMs; }
        public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    }

    public static class ToolDescriptor {
        private String toolName;
        private String description;
        private String category;
        private int requiredPermission;
        private long timeoutMs;
        private boolean enabled;

        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public int getRequiredPermission() { return requiredPermission; }
        public void setRequiredPermission(int requiredPermission) { this.requiredPermission = requiredPermission; }
        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
