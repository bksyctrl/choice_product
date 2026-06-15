package com.ecommerce.workflow.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.workflow.scheduler.entity.ScheduledTask;
import com.ecommerce.workflow.scheduler.mapper.ScheduledTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class TaskSchedulerService {
    
    private static final Logger log = LoggerFactory.getLogger(TaskSchedulerService.class);
    
    @Autowired
    private ScheduledTaskMapper taskMapper;
    
    @Autowired
    private CronExpressionParser cronParser;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Transactional
    public ScheduledTask createTask(String taskName, String cronExpression, String taskType, 
                                    Map<String, Object> taskConfig, Long userId) {
        if (!cronParser.isValidCron(cronExpression)) {
            throw new IllegalArgumentException("无效的cron表达式: " + cronExpression);
        }
        
        String taskId = "TASK_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        
        ScheduledTask task = new ScheduledTask();
        task.setTaskId(taskId);
        task.setTaskName(taskName);
        task.setCronExpression(cronExpression);
        task.setTaskType(taskType);
        task.setStatus("active");
        task.setUserId(userId);
        task.setExecuteCount(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        
        try {
            task.setTaskConfig(objectMapper.writeValueAsString(taskConfig));
        } catch (Exception e) {
            log.warn("序列化任务配置失败", e);
            task.setTaskConfig("{}");
        }
        
        Date nextTime = cronParser.getNextExecuteTime(cronExpression);
        if (nextTime != null) {
            task.setNextExecuteTime(LocalDateTime.ofInstant(nextTime.toInstant(), 
                java.time.ZoneId.systemDefault()));
        }
        
        taskMapper.insert(task);
        
        log.info("创建定时任务成功: taskId={}, name={}, cron={}", taskId, taskName, cronExpression);
        
        return task;
    }
    
    public List<ScheduledTask> listTasks(Long userId) {
        LambdaQueryWrapper<ScheduledTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScheduledTask::getUserId, userId)
               .orderByDesc(ScheduledTask::getCreatedAt);
        
        return taskMapper.selectList(wrapper);
    }
    
    public List<ScheduledTask> listActiveTasks() {
        LambdaQueryWrapper<ScheduledTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScheduledTask::getStatus, "active");
        
        return taskMapper.selectList(wrapper);
    }
    
    public ScheduledTask getTask(String taskId) {
        LambdaQueryWrapper<ScheduledTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScheduledTask::getTaskId, taskId);
        
        return taskMapper.selectOne(wrapper);
    }
    
    @Transactional
    public void updateTask(String taskId, String cronExpression, Map<String, Object> taskConfig) {
        ScheduledTask task = getTask(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        
        if (cronExpression != null && cronParser.isValidCron(cronExpression)) {
            task.setCronExpression(cronExpression);
            
            Date nextTime = cronParser.getNextExecuteTime(cronExpression);
            if (nextTime != null) {
                task.setNextExecuteTime(LocalDateTime.ofInstant(nextTime.toInstant(), 
                    java.time.ZoneId.systemDefault()));
            }
        }
        
        if (taskConfig != null) {
            try {
                task.setTaskConfig(objectMapper.writeValueAsString(taskConfig));
            } catch (Exception e) {
                log.warn("序列化任务配置失败", e);
            }
        }
        
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        
        log.info("更新定时任务成功: taskId={}", taskId);
    }
    
    @Transactional
    public void pauseTask(String taskId) {
        ScheduledTask task = getTask(taskId);
        if (task != null) {
            task.setStatus("paused");
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            
            log.info("暂停定时任务成功: taskId={}", taskId);
        }
    }
    
    @Transactional
    public void resumeTask(String taskId) {
        ScheduledTask task = getTask(taskId);
        if (task != null) {
            task.setStatus("active");
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            
            log.info("恢复定时任务成功: taskId={}", taskId);
        }
    }
    
    @Transactional
    public void deleteTask(String taskId) {
        ScheduledTask task = getTask(taskId);
        if (task != null) {
            task.setStatus("deleted");
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            
            log.info("删除定时任务成功: taskId={}", taskId);
        }
    }
    
    @Transactional
    public void recordExecution(String taskId) {
        ScheduledTask task = getTask(taskId);
        if (task != null) {
            task.setLastExecuteTime(LocalDateTime.now());
            task.setExecuteCount(task.getExecuteCount() + 1);
            
            Date nextTime = cronParser.getNextExecuteTime(task.getCronExpression());
            if (nextTime != null) {
                task.setNextExecuteTime(LocalDateTime.ofInstant(nextTime.toInstant(), 
                    java.time.ZoneId.systemDefault()));
            }
            
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }
    }
    
    public Map<String, Object> getTaskConfig(String taskId) {
        ScheduledTask task = getTask(taskId);
        if (task != null && task.getTaskConfig() != null) {
            try {
                return objectMapper.readValue(task.getTaskConfig(), Map.class);
            } catch (Exception e) {
                log.warn("解析任务配置失败", e);
            }
        }
        return new HashMap<>();
    }
}
