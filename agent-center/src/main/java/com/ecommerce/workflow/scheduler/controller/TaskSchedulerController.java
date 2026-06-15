package com.ecommerce.workflow.scheduler.controller;

import com.ecommerce.workflow.controller.ApiResponse;
import com.ecommerce.workflow.scheduler.entity.ScheduledTask;
import com.ecommerce.workflow.scheduler.service.CronExpressionParser;
import com.ecommerce.workflow.scheduler.service.TaskSchedulerService;
import com.ecommerce.workflow.util.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scheduler")
public class TaskSchedulerController {
    
    @Autowired
    private TaskSchedulerService taskSchedulerService;
    
    @Autowired
    private CronExpressionParser cronParser;
    
    @PostMapping("/create")
    public ApiResponse<Map<String, Object>> createTask(
            @RequestParam String taskName,
            @RequestParam String cronExpression,
            @RequestParam String taskType,
            @RequestBody(required = false) Map<String, Object> taskConfig) {
        
        Long userId = UserContext.getCurrentUserId();
        if (taskConfig == null) {
            taskConfig = new HashMap<>();
        }
        
        ScheduledTask task = taskSchedulerService.createTask(taskName, cronExpression, taskType, taskConfig, userId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("taskId", task.getTaskId());
        response.put("taskName", task.getTaskName());
        response.put("cronExpression", task.getCronExpression());
        response.put("description", cronParser.parseCronDescription(cronExpression));
        response.put("nextExecuteTime", task.getNextExecuteTime());
        
        return ApiResponse.success(response);
    }
    
    @GetMapping("/list")
    public ApiResponse<List<ScheduledTask>> listTasks() {
        Long userId = UserContext.getCurrentUserId();
        List<ScheduledTask> tasks = taskSchedulerService.listTasks(userId);
        
        return ApiResponse.success(tasks);
    }
    
    @GetMapping("/{taskId}")
    public ApiResponse<ScheduledTask> getTask(@PathVariable String taskId) {
        ScheduledTask task = taskSchedulerService.getTask(taskId);
        
        return ApiResponse.success(task);
    }
    
    @PutMapping("/{taskId}")
    public ApiResponse<Map<String, Object>> updateTask(
            @PathVariable String taskId,
            @RequestParam(required = false) String cronExpression,
            @RequestBody(required = false) Map<String, Object> taskConfig) {
        
        taskSchedulerService.updateTask(taskId, cronExpression, taskConfig);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "任务更新成功");
        
        return ApiResponse.success(response);
    }
    
    @PostMapping("/{taskId}/pause")
    public ApiResponse<Map<String, Object>> pauseTask(@PathVariable String taskId) {
        taskSchedulerService.pauseTask(taskId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "任务暂停成功");
        
        return ApiResponse.success(response);
    }
    
    @PostMapping("/{taskId}/resume")
    public ApiResponse<Map<String, Object>> resumeTask(@PathVariable String taskId) {
        taskSchedulerService.resumeTask(taskId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "任务恢复成功");
        
        return ApiResponse.success(response);
    }
    
    @DeleteMapping("/{taskId}")
    public ApiResponse<Map<String, Object>> deleteTask(@PathVariable String taskId) {
        taskSchedulerService.deleteTask(taskId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "任务删除成功");
        
        return ApiResponse.success(response);
    }
    
    @PostMapping("/validate-cron")
    public ApiResponse<Map<String, Object>> validateCron(@RequestParam String cronExpression) {
        boolean valid = cronParser.isValidCron(cronExpression);
        String description = valid ? cronParser.parseCronDescription(cronExpression) : "无效的cron表达式";
        
        Map<String, Object> response = new HashMap<>();
        response.put("valid", valid);
        response.put("description", description);
        
        return ApiResponse.success(response);
    }
}
