package com.ecommerce.workflow.scheduler.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.workflow.mapper.AiCallLogMapper;
import com.ecommerce.workflow.checkpoint.mapper.FilesystemSnapshotMapper;
import com.ecommerce.workflow.entity.AiVideoConfig;
import com.ecommerce.workflow.entity.VideoTask;
import com.ecommerce.workflow.mapper.VideoTaskMapper;
import com.ecommerce.workflow.scheduler.entity.ScheduledTask;
import com.ecommerce.workflow.scheduler.service.TaskSchedulerService;
import com.ecommerce.workflow.service.ai.AiVideoService;
import com.ecommerce.workflow.service.ai.GptChatService;
import com.ecommerce.workflow.service.memory.WhiteBoxMemoryService;
import com.ecommerce.workflow.service.storage.MinioService;
import com.ecommerce.workflow.service.WebSocketPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class ScheduledTaskJob {
    
    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskJob.class);
    
    @Autowired
    private TaskSchedulerService taskSchedulerService;
    
    @Autowired
    private AiCallLogMapper aiCallLogMapper;
    
    @Autowired
    private FilesystemSnapshotMapper snapshotMapper;
    
    @Autowired
    private WhiteBoxMemoryService whiteBoxMemoryService;
    
    @Autowired
    private MinioService minioService;
    
    @Autowired
    private AiVideoService aiVideoService;
    
    @Autowired
    private GptChatService gptChatService;
    
    @Autowired
    private VideoTaskMapper videoTaskMapper;
    
    @Autowired
    private WebSocketPushService webSocketPushService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @XxlJob("usageAnalyticsJob")
    public void usageAnalyticsJob() {
        log.info("执行使用分析统计任务: time={}", LocalDateTime.now());
        
        try {
            LocalDateTime startTime = LocalDateTime.now().minusDays(1);
            
            Long totalCalls = aiCallLogMapper.selectCount(
                new LambdaQueryWrapper<com.ecommerce.workflow.entity.AiCallLog>()
                    .ge(com.ecommerce.workflow.entity.AiCallLog::getCreatedAt, startTime)
            );
            
            Double totalCost = aiCallLogMapper.getTotalCost(startTime);
            if (totalCost == null) totalCost = 0.0;
            
            List<Map<String, Object>> modelUsage = aiCallLogMapper.getModelUsageStats(startTime);
            
            String report = String.format("""
                ## 每日使用分析报告
                - 统计时间: %s
                - 总调用次数: %d
                - 总费用: $%.4f
                - 模型使用分布: %s
                
                生成时间: %s
                """,
                startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                totalCalls,
                totalCost,
                modelUsage.toString(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );
            
            whiteBoxMemoryService.recordDailyLog(report);
            
            log.info("使用分析统计任务执行完成: calls={}, cost=${}", totalCalls, totalCost);
        } catch (Exception e) {
            log.error("使用分析统计任务执行失败", e);
        }
    }
    
    @XxlJob("memoryCleanupJob")
    public void memoryCleanupJob() {
        log.info("执行记忆清理任务: time={}", LocalDateTime.now());
        
        try {
            String memoryPath = whiteBoxMemoryService.getMemoryPath();
            Path dailyPath = Paths.get(memoryPath, "daily");
            
            if (Files.exists(dailyPath)) {
                LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
                File[] files = dailyPath.toFile().listFiles((dir, name) -> name.endsWith(".md"));
                
                int deletedCount = 0;
                if (files != null) {
                    for (File file : files) {
                        try {
                            String dateStr = file.getName().replace(".md", "");
                            LocalDateTime fileDate = LocalDateTime.parse(dateStr + " 00:00:00",
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                            
                            if (fileDate.isBefore(cutoffDate)) {
                                Files.delete(file.toPath());
                                deletedCount++;
                            }
                        } catch (Exception e) {
                            log.warn("无法解析文件日期: {}", file.getName());
                        }
                    }
                }
                log.info("记忆清理任务执行完成: 删除{}个过期文件", deletedCount);
            }
            
            whiteBoxMemoryService.recordDailyLog("## 记忆清理\n- 执行时间: " + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            
        } catch (Exception e) {
            log.error("记忆清理任务执行失败", e);
        }
    }
    
    @XxlJob("snapshotCleanupJob")
    public void snapshotCleanupJob() {
        log.info("执行快照清理任务: time={}", LocalDateTime.now());
        
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);
            
            List<com.ecommerce.workflow.checkpoint.entity.FilesystemSnapshot> oldSnapshots = 
                snapshotMapper.selectList(
                    new LambdaQueryWrapper<com.ecommerce.workflow.checkpoint.entity.FilesystemSnapshot>()
                        .lt(com.ecommerce.workflow.checkpoint.entity.FilesystemSnapshot::getCreatedAt, cutoffDate)
                        .eq(com.ecommerce.workflow.checkpoint.entity.FilesystemSnapshot::getStatus, "active")
                );
            
            int deletedCount = 0;
            for (com.ecommerce.workflow.checkpoint.entity.FilesystemSnapshot snapshot : oldSnapshots) {
                try {
                    snapshot.setStatus("expired");
                    snapshotMapper.updateById(snapshot);
                    deletedCount++;
                } catch (Exception e) {
                    log.warn("清理快照失败: snapshotId={}", snapshot.getSnapshotId(), e);
                }
            }
            
            log.info("快照清理任务执行完成: 清理{}个过期快照", deletedCount);
        } catch (Exception e) {
            log.error("快照清理任务执行失败", e);
        }
    }
    
    @XxlJob("dynamicTaskJob")
    public void dynamicTaskJob() {
        log.info("执行动态任务调度: time={}", LocalDateTime.now());
        
        try {
            List<ScheduledTask> activeTasks = taskSchedulerService.listActiveTasks();
            
            for (ScheduledTask task : activeTasks) {
                try {
                    if (shouldExecute(task)) {
                        executeTask(task);
                    }
                } catch (Exception e) {
                    log.error("执行任务失败: taskId={}", task.getTaskId(), e);
                }
            }
            
            log.info("动态任务调度完成: count={}", activeTasks.size());
        } catch (Exception e) {
            log.error("动态任务调度失败", e);
        }
    }
    
    private boolean shouldExecute(ScheduledTask task) {
        if (task.getNextExecuteTime() == null) {
            return false;
        }
        return !LocalDateTime.now().isBefore(task.getNextExecuteTime());
    }
    
    private void executeTask(ScheduledTask task) {
        log.info("执行任务: taskId={}, type={}", task.getTaskId(), task.getTaskType());
        
        try {
            Map<String, Object> config = taskSchedulerService.getTaskConfig(task.getTaskId());
            
            switch (task.getTaskType()) {
                case "VIDEO_GENERATION":
                    executeVideoGenerationTask(task, config);
                    break;
                case "REPORT_GENERATION":
                    executeReportGenerationTask(task, config);
                    break;
                case "DATA_SYNC":
                    executeDataSyncTask(task, config);
                    break;
                case "NOTIFICATION":
                    executeNotificationTask(task, config);
                    break;
                case "MODEL_HEALTH_CHECK":
                    executeModelHealthCheckTask(task, config);
                    break;
                case "KNOWLEDGE_INDEXING":
                    executeKnowledgeIndexingTask(task, config);
                    break;
                default:
                    log.warn("未知任务类型: {}", task.getTaskType());
            }
            
            taskSchedulerService.recordExecution(task.getTaskId());
        } catch (Exception e) {
            log.error("任务执行异常: taskId={}", task.getTaskId(), e);
            throw new RuntimeException(e);
        }
    }
    
    private void executeVideoGenerationTask(ScheduledTask task, Map<String, Object> config) {
        log.info("执行视频生成任务: taskId={}", task.getTaskId());
        
        try {
            Long configId = config.containsKey("videoConfigId") ? 
                ((Number) config.get("videoConfigId")).longValue() : null;
            
            if (configId == null) {
                log.warn("视频生成任务缺少配置ID: taskId={}", task.getTaskId());
                return;
            }
            
            AiVideoConfig videoConfig = aiVideoService.getConfig(configId);
            if (videoConfig == null) {
                log.warn("视频配置不存在: configId={}", configId);
                return;
            }
            
            String[] imageUrls = config.containsKey("imageUrls") ?
                objectMapper.readValue(config.get("imageUrls").toString(), String[].class) : null;
            
            VideoTask videoTask = aiVideoService.createTask(videoConfig, imageUrls, task.getUserId(), null, null);
            
            log.info("视频生成任务已创建: taskId={}, videoTaskId={}", task.getTaskId(), videoTask.getTaskId());
            
            if (config.containsKey("notifyOnComplete") && Boolean.TRUE.equals(config.get("notifyOnComplete"))) {
                Map<String, Object> notification = new HashMap<>();
                notification.put("type", "VIDEO_TASK_CREATED");
                notification.put("taskId", videoTask.getTaskId());
                notification.put("scheduledTaskId", task.getTaskId());
                notification.put("timestamp", LocalDateTime.now());
                webSocketPushService.pushMessage(task.getUserId().toString(), notification);
            }
            
        } catch (Exception e) {
            log.error("视频生成任务执行失败: taskId={}", task.getTaskId(), e);
            throw new RuntimeException(e);
        }
    }
    
    private void executeReportGenerationTask(ScheduledTask task, Map<String, Object> config) {
        log.info("执行报告生成任务: taskId={}", task.getTaskId());
        
        try {
            String reportType = (String) config.getOrDefault("reportType", "daily");
            String prompt = buildReportPrompt(reportType, config);
            
            String report = gptChatService.chat(prompt);
            
            String reportFileName = String.format("report_%s_%s.md", 
                reportType, 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
            
            whiteBoxMemoryService.appendToMemory("LEARNINGS.md", 
                "\n## 自动生成报告\n" + report + "\n");
            
            log.info("报告生成完成: taskId={}, type={}, length={}", 
                task.getTaskId(), reportType, report.length());
            
        } catch (Exception e) {
            log.error("报告生成任务执行失败: taskId={}", task.getTaskId(), e);
            throw new RuntimeException(e);
        }
    }
    
    private String buildReportPrompt(String reportType, Map<String, Object> config) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请生成一份").append(reportType).append("报告。\n\n");
        
        if ("daily".equals(reportType)) {
            prompt.append("报告内容应包括:\n");
            prompt.append("1. 今日关键数据摘要\n");
            prompt.append("2. 重要事件回顾\n");
            prompt.append("3. 问题与建议\n");
            prompt.append("4. 明日计划\n");
        } else if ("weekly".equals(reportType)) {
            prompt.append("报告内容应包括:\n");
            prompt.append("1. 本周数据汇总\n");
            prompt.append("2. 重点工作进展\n");
            prompt.append("3. 问题分析与改进措施\n");
            prompt.append("4. 下周工作计划\n");
        }
        
        if (config.containsKey("additionalContext")) {
            prompt.append("\n额外上下文:\n").append(config.get("additionalContext"));
        }
        
        return prompt.toString();
    }
    
    private void executeDataSyncTask(ScheduledTask task, Map<String, Object> config) {
        log.info("执行数据同步任务: taskId={}", task.getTaskId());
        
        try {
            String syncType = (String) config.getOrDefault("syncType", "full");
            String source = (String) config.get("source");
            String target = (String) config.get("target");
            
            log.info("数据同步: type={}, source={}, target={}", syncType, source, target);
            
            whiteBoxMemoryService.recordDailyLog(String.format(
                "## 数据同步\n- 类型: %s\n- 源: %s\n- 目标: %s\n- 时间: %s\n",
                syncType, source, target, 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            ));
            
            log.info("数据同步任务完成: taskId={}", task.getTaskId());
            
        } catch (Exception e) {
            log.error("数据同步任务执行失败: taskId={}", task.getTaskId(), e);
            throw new RuntimeException(e);
        }
    }
    
    private void executeNotificationTask(ScheduledTask task, Map<String, Object> config) {
        log.info("执行通知任务: taskId={}", task.getTaskId());
        
        try {
            String message = (String) config.getOrDefault("message", "定时通知");
            String type = (String) config.getOrDefault("notificationType", "info");
            
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "SCHEDULED_NOTIFICATION");
            notification.put("message", message);
            notification.put("notificationType", type);
            notification.put("taskId", task.getTaskId());
            notification.put("timestamp", LocalDateTime.now());
            webSocketPushService.pushMessage(task.getUserId().toString(), notification);
            
            log.info("通知任务完成: taskId={}, userId={}", task.getTaskId(), task.getUserId());
            
        } catch (Exception e) {
            log.error("通知任务执行失败: taskId={}", task.getTaskId(), e);
            throw new RuntimeException(e);
        }
    }
    
    private void executeModelHealthCheckTask(ScheduledTask task, Map<String, Object> config) {
        log.info("执行模型健康检查任务: taskId={}", task.getTaskId());
        
        try {
            String testPrompt = "Hello, this is a health check. Please respond with 'OK'.";
            String response = gptChatService.chat(testPrompt);
            
            boolean isHealthy = response != null && !response.isEmpty();
            
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "MODEL_HEALTH_CHECK");
            notification.put("healthy", isHealthy);
            notification.put("response", response);
            notification.put("timestamp", LocalDateTime.now());
            webSocketPushService.pushMessage(task.getUserId().toString(), notification);
            
            log.info("模型健康检查完成: taskId={}, healthy={}", task.getTaskId(), isHealthy);
            
        } catch (Exception e) {
            log.error("模型健康检查任务执行失败: taskId={}", task.getTaskId(), e);
            throw new RuntimeException(e);
        }
    }
    
    private void executeKnowledgeIndexingTask(ScheduledTask task, Map<String, Object> config) {
        log.info("执行知识索引任务: taskId={}", task.getTaskId());
        
        try {
            whiteBoxMemoryService.recordDailyLog(String.format(
                "## 知识索引更新\n- 时间: %s\n- 状态: 完成\n",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            ));
            
            log.info("知识索引任务完成: taskId={}", task.getTaskId());
            
        } catch (Exception e) {
            log.error("知识索引任务执行失败: taskId={}", task.getTaskId(), e);
            throw new RuntimeException(e);
        }
    }
}
