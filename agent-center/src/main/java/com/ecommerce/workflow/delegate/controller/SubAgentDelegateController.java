package com.ecommerce.workflow.delegate.controller;

import com.ecommerce.workflow.controller.ApiResponse;
import com.ecommerce.workflow.delegate.entity.SubAgentTask;
import com.ecommerce.workflow.delegate.service.EnvironmentIsolationManager;
import com.ecommerce.workflow.delegate.service.ParallelTaskExecutor;
import com.ecommerce.workflow.delegate.service.ToolRpcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/delegate")
public class SubAgentDelegateController {
    
    @Autowired
    private ParallelTaskExecutor taskExecutor;
    
    @Autowired
    private ToolRpcService toolRpcService;
    
    @Autowired
    private EnvironmentIsolationManager environmentManager;
    
    @PostMapping("/tasks/create")
    public ApiResponse<Map<String, Object>> createTask(
            @RequestParam String parentAgentId,
            @RequestParam String subAgentId,
            @RequestParam String taskType,
            @RequestParam String taskDescription) {
        
        SubAgentTask task = taskExecutor.createTask(parentAgentId, subAgentId, taskType, taskDescription);
        
        Map<String, Object> response = new HashMap<>();
        response.put("taskId", task.getTaskId());
        response.put("status", task.getStatus());
        
        return ApiResponse.success(response);
    }
    
    @PostMapping("/tasks/execute-parallel")
    public ApiResponse<List<SubAgentTask>> executeTasksInParallel(
            @RequestBody List<SubAgentTask> tasks) {
        
        List<SubAgentTask> results = taskExecutor.executeTasksInParallel(tasks);
        
        return ApiResponse.success(results);
    }
    
    @GetMapping("/tasks/{taskId}")
    public ApiResponse<SubAgentTask> getTask(@PathVariable String taskId) {
        SubAgentTask task = taskExecutor.getTask(taskId);
        
        return ApiResponse.success(task);
    }
    
    @GetMapping("/tasks")
    public ApiResponse<List<SubAgentTask>> listAllTasks() {
        List<SubAgentTask> tasks = taskExecutor.listAllTasks();
        
        return ApiResponse.success(tasks);
    }
    
    @GetMapping("/tasks/parent/{parentAgentId}")
    public ApiResponse<List<SubAgentTask>> listTasksByParent(@PathVariable String parentAgentId) {
        List<SubAgentTask> tasks = taskExecutor.listTasksByParent(parentAgentId);
        
        return ApiResponse.success(tasks);
    }
    
    @PostMapping("/tasks/{taskId}/retry")
    public ApiResponse<Map<String, Object>> retryTask(@PathVariable String taskId) {
        taskExecutor.retryTask(taskId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "任务已重新提交");
        
        return ApiResponse.success(response);
    }
    
    @PostMapping("/tools/{toolName}/call")
    public ApiResponse<Map<String, Object>> callTool(
            @PathVariable String toolName,
            @RequestBody Map<String, Object> parameters) {
        
        Map<String, Object> result = toolRpcService.callTool(toolName, parameters);
        
        return ApiResponse.success(result);
    }
    
    @PostMapping("/environments/create")
    public ApiResponse<Map<String, Object>> createEnvironment(
            @RequestParam String parentAgentId,
            @RequestParam String subAgentId,
            @RequestBody(required = false) Map<String, Object> config) {
        
        String envId = environmentManager.createEnvironment(parentAgentId, subAgentId, config);
        
        Map<String, Object> response = new HashMap<>();
        response.put("envId", envId);
        
        return ApiResponse.success(response);
    }
    
    @PostMapping("/environments/{envId}/variables")
    public ApiResponse<Map<String, Object>> setVariable(
            @PathVariable String envId,
            @RequestParam String key,
            @RequestBody Object value) {
        
        environmentManager.setVariable(envId, key, value);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "变量设置成功");
        
        return ApiResponse.success(response);
    }
    
    @GetMapping("/environments/{envId}/variables/{key}")
    public ApiResponse<Object> getVariable(
            @PathVariable String envId,
            @PathVariable String key) {
        
        Object value = environmentManager.getVariable(envId, key);
        
        return ApiResponse.success(value);
    }
    
    @GetMapping("/environments/{envId}/variables")
    public ApiResponse<Map<String, Object>> getAllVariables(@PathVariable String envId) {
        Map<String, Object> variables = environmentManager.getAllVariables(envId);
        
        return ApiResponse.success(variables);
    }
    
    @DeleteMapping("/environments/{envId}")
    public ApiResponse<Map<String, Object>> destroyEnvironment(@PathVariable String envId) {
        environmentManager.destroyEnvironment(envId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "环境销毁成功");
        
        return ApiResponse.success(response);
    }
}
