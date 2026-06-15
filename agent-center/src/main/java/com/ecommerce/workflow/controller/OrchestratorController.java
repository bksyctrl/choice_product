package com.ecommerce.workflow.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.ecommerce.workflow.service.orchestrator.IntelligentOrchestrator;
import com.ecommerce.workflow.service.orchestrator.IntelligentOrchestrator.OrchestratedTask;
import com.ecommerce.workflow.service.orchestrator.IntelligentOrchestrator.TaskResult;
import com.ecommerce.workflow.service.orchestrator.IntelligentOrchestrator.ToolDescriptor;

@RestController
@RequestMapping("/api/orchestrator")
public class OrchestratorController {

    @Autowired
    private IntelligentOrchestrator orchestrator;

    @PostMapping("/tasks/submit")
    public ApiResponse<Map<String, Object>> submitTask(
            @RequestParam String toolName,
            @RequestBody Map<String, Object> params,
            @RequestParam(defaultValue = "5") int priority) {

        String taskId = orchestrator.submitTask(toolName, params, priority);

        return ApiResponse.success(Map.of("taskId", taskId, "status", "QUEUED"));
    }

    @PostMapping("/tasks/execute")
    public ApiResponse<TaskResult> executeDirect(
            @RequestParam String toolName,
            @RequestBody Map<String, Object> params) {

        TaskResult result = orchestrator.executeDirect(toolName, params);
        return ApiResponse.success(result);
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<TaskResult> getTaskResult(@PathVariable String taskId) {
        TaskResult result = orchestrator.getTaskResult(taskId);
        return ApiResponse.success(result);
    }

    @GetMapping("/tasks/queued")
    public ApiResponse<List<OrchestratedTask>> getQueuedTasks() {
        return ApiResponse.success(orchestrator.getQueuedTasks());
    }

    @GetMapping("/tasks/running")
    public ApiResponse<List<OrchestratedTask>> getRunningTasks() {
        return ApiResponse.success(orchestrator.getRunningTasks());
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        return ApiResponse.success(orchestrator.getOrchestratorStats());
    }

    @GetMapping("/tools")
    public ApiResponse<List<ToolDescriptor>> getRegisteredTools() {
        return ApiResponse.success(orchestrator.getRegisteredTools());
    }

    @PutMapping("/config")
    public ApiResponse<Map<String, Object>> updateConfig(
            @RequestParam String key,
            @RequestParam String value) {

        orchestrator.updateConfig(key, value);
        return ApiResponse.success(Map.of("key", key, "value", value, "updated", true));
    }
}
