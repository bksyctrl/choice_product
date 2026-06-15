package com.ecommerce.workflow.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.workflow.entity.WorkflowInstance;
import com.ecommerce.workflow.mapper.WorkflowInstanceMapper;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final WorkflowInstanceMapper workflowInstanceMapper;

    public DashboardController(WorkflowInstanceMapper workflowInstanceMapper) {
        this.workflowInstanceMapper = workflowInstanceMapper;
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        Long todayWorkflows = workflowInstanceMapper.selectCount(
            new QueryWrapper<WorkflowInstance>()
                .ge("created_at", todayStart)
                .eq("deleted", 0)
        );

        Long runningCount = workflowInstanceMapper.selectCount(
            new QueryWrapper<WorkflowInstance>()
                .eq("status", "RUNNING")
                .eq("deleted", 0)
        );

        Long totalCompleted = workflowInstanceMapper.selectCount(
            new QueryWrapper<WorkflowInstance>()
                .eq("status", "COMPLETED")
                .eq("deleted", 0)
        );

        Long totalFailed = workflowInstanceMapper.selectCount(
            new QueryWrapper<WorkflowInstance>()
                .eq("status", "FAILED")
                .eq("deleted", 0)
        );

        double successRate = 0.0;
        long total = totalCompleted + totalFailed;
        if (total > 0) {
            successRate = (double) totalCompleted / total;
        }

        stats.put("todayWorkflows", todayWorkflows != null ? todayWorkflows : 0);
        stats.put("runningCount", runningCount != null ? runningCount : 0);
        stats.put("successRate", successRate);
        stats.put("totalCompleted", totalCompleted != null ? totalCompleted : 0);
        stats.put("totalFailed", totalFailed != null ? totalFailed : 0);

        return ApiResponse.success(stats);
    }

    @GetMapping("/trend")
    public ApiResponse<List<Map<String, Object>>> getTrend(@RequestParam(defaultValue = "7") int days) {
        List<Map<String, Object>> trend = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

            Long total = workflowInstanceMapper.selectCount(
                new QueryWrapper<WorkflowInstance>()
                    .ge("created_at", dayStart)
                    .lt("created_at", dayEnd)
                    .eq("deleted", 0)
            );

            Long success = workflowInstanceMapper.selectCount(
                new QueryWrapper<WorkflowInstance>()
                    .ge("created_at", dayStart)
                    .lt("created_at", dayEnd)
                    .eq("status", "COMPLETED")
                    .eq("deleted", 0)
            );

            Long failed = workflowInstanceMapper.selectCount(
                new QueryWrapper<WorkflowInstance>()
                    .ge("created_at", dayStart)
                    .lt("created_at", dayEnd)
                    .eq("status", "FAILED")
                    .eq("deleted", 0)
            );

            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", date.format(DateTimeFormatter.ISO_DATE));
            dayData.put("total", total != null ? total : 0);
            dayData.put("success", success != null ? success : 0);
            dayData.put("failed", failed != null ? failed : 0);

            trend.add(dayData);
        }

        return ApiResponse.success(trend);
    }
}
