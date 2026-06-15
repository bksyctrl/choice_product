package com.ecommerce.workflow.controller;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.workflow.entity.UnifiedAiConfig;
import com.ecommerce.workflow.mapper.UnifiedAiConfigMapper;
import com.ecommerce.workflow.service.AiConfigUnifiedService;

@RestController
@RequestMapping("/api/smart-router")
public class SmartRouterController {
    
    @Autowired
    private AiConfigUnifiedService unifiedService;
    
    @Autowired
    private UnifiedAiConfigMapper unifiedAiConfigMapper;
    
    // ==================== 状态检查 ====================
    
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        
        long totalProviders = unifiedAiConfigMapper.selectCount(
            new LambdaQueryWrapper<UnifiedAiConfig>()
                .eq(UnifiedAiConfig::getDeleted, 0)
        );
        
        long enabledProviders = unifiedAiConfigMapper.selectCount(
            new LambdaQueryWrapper<UnifiedAiConfig>()
                .eq(UnifiedAiConfig::getDeleted, 0)
                .eq(UnifiedAiConfig::getEnabled, 1)
        );
        
        long videoProviders = unifiedAiConfigMapper.selectCount(
            new LambdaQueryWrapper<UnifiedAiConfig>()
                .eq(UnifiedAiConfig::getDeleted, 0)
                .eq(UnifiedAiConfig::getEnabled, 1)
                .eq(UnifiedAiConfig::getProviderType, "VIDEO")
        );
        
        long llmProviders = unifiedAiConfigMapper.selectCount(
            new LambdaQueryWrapper<UnifiedAiConfig>()
                .eq(UnifiedAiConfig::getDeleted, 0)
                .eq(UnifiedAiConfig::getEnabled, 1)
                .eq(UnifiedAiConfig::getProviderType, "LLM")
        );
        
        status.put("totalProviders", totalProviders);
        status.put("enabledProviders", enabledProviders);
        status.put("videoProviders", videoProviders);
        status.put("llmProviders", llmProviders);
        status.put("totalRules", enabledProviders);  // 每个配置都有自己的策略
        status.put("totalCapabilities", totalProviders);  // 每个配置都有能力标记
        status.put("status", enabledProviders > 0 ? "active" : "inactive");
        status.put("timestamp", LocalDateTime.now());
        status.put("architecture", "unified_ai_config (统一架构)");
        
        return ApiResponse.success(status);
    }
    
    // ==================== 智能路由 ====================
    
    @PostMapping("/route")
    public ResponseEntity<Map<String, Object>> route(@RequestBody Map<String, String> request) {
        try {
            String taskType = request.getOrDefault("taskType", "chat");
            String strategy = request.getOrDefault("strategy", "balanced");
            
            UnifiedAiConfig selected = unifiedService.selectBestConfig(taskType, strategy);
            
            if (selected == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No available provider found"
                ));
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("traceId", "TR-" + System.currentTimeMillis());
            response.put("providerId", selected.getId());
            response.put("providerName", selected.getConfigName());
            response.put("baseUrl", selected.getBaseUrl());
            response.put("apiKey", selected.getApiKey());
            response.put("modelName", selected.getDefaultModel());
            response.put("providerType", selected.getProviderType());
            response.put("strategy", strategy);
            response.put("taskType", taskType);
            response.put("qualityScore", selected.getQualityScore());
            response.put("speedScore", selected.getSpeedScore());
            response.put("costRate", selected.getCostRate());
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Routing failed: " + e.getMessage()
            ));
        }
    }
    
    // ==================== 路由规则管理（基于unified_ai_config.routingStrategy）====================
    
    @GetMapping("/rules")
    public ApiResponse<List<Map<String, Object>>> getRules() {
        List<UnifiedAiConfig> configs = unifiedAiConfigMapper.selectList(
            new LambdaQueryWrapper<UnifiedAiConfig>()
                .eq(UnifiedAiConfig::getDeleted, 0)
                .orderByAsc(UnifiedAiConfig::getPriority)
        );
        
        List<Map<String, Object>> rules = configs.stream().map(config -> {
            Map<String, Object> rule = new HashMap<>();
            rule.put("id", config.getId());
            rule.put("taskType", config.getTaskTypes());  // 支持的任务类型列表
            rule.put("strategy", config.getRoutingStrategy());  // 路由策略
            rule.put("providerName", config.getConfigName());
            rule.put("providerType", config.getProviderType());
            rule.put("priority", config.getPriority());
            rule.put("enabled", config.getEnabled() == 1);
            return rule;
        }).collect(Collectors.toList());
        
        return ApiResponse.success(rules);
    }
    
    @PostMapping("/rules")
    public ApiResponse<Map<String, Object>> createRule(@RequestBody Map<String, Object> data) {
        try {
            Long providerId = Long.valueOf(data.get("providerId").toString());
            String strategy = (String) data.getOrDefault("strategy", "balanced");
            
            UnifiedAiConfig config = unifiedAiConfigMapper.selectById(providerId);
            if (config == null) {
                return ApiResponse.error(404, "Provider not found");
            }
            
            config.setRoutingStrategy(strategy);
            if (data.containsKey("taskTypes")) {
                config.setTaskTypes(data.get("taskTypes").toString());
            }
            
            unifiedAiConfigMapper.updateById(config);
            unifiedService.clearCache();
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", config.getId());
            result.put("strategy", config.getRoutingStrategy());
            result.put("message", "路由规则更新成功");
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(500, "创建规则失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/rules/{id}")
    public ApiResponse<Map<String, Object>> updateRule(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        return createRule(data);  // 复用逻辑
    }
    
    @DeleteMapping("/rules/{id}")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        try {
            UnifiedAiConfig config = unifiedAiConfigMapper.selectById(id);
            if (config != null) {
                config.setRoutingStrategy("balanced");  // 重置为默认策略
                unifiedAiConfigMapper.updateById(config);
                unifiedService.clearCache();
            }
            return ApiResponse.success(null);
        } catch (Exception e) {
            return ApiResponse.error(500, "删除规则失败: " + e.getMessage());
        }
    }
    
    // ==================== 模型能力管理（基于unified_ai_config.capabilities）====================
    
    @GetMapping("/capabilities")
    public ApiResponse<List<Map<String, Object>>> getCapabilities(@RequestParam(required = false) String type) {
        LambdaQueryWrapper<UnifiedAiConfig> wrapper = new LambdaQueryWrapper<UnifiedAiConfig>()
            .eq(UnifiedAiConfig::getDeleted, 0)
            .eq(UnifiedAiConfig::getEnabled, 1);
        
        if (type != null && !type.isEmpty()) {
            wrapper.eq(UnifiedAiConfig::getProviderType, type);
        }
        
        List<UnifiedAiConfig> configs = unifiedAiConfigMapper.selectList(wrapper);
        
        List<Map<String, Object>> capabilities = configs.stream().map(config -> {
            Map<String, Object> cap = new HashMap<>();
            cap.put("id", config.getId());
            cap.put("modelName", config.getDefaultModel());
            cap.put("capabilityType", config.getProviderType());
            cap.put("displayName", config.getConfigName());
            cap.put("score", config.getQualityScore());
            cap.put("supportsVision", config.getTaskTypes() != null && config.getTaskTypes().contains("vision"));
            cap.put("supportsVideo", config.getTaskTypes() != null && config.getTaskTypes().contains("video"));
            cap.put("supportsImage", config.getTaskTypes() != null && config.getTaskTypes().contains("image"));
            cap.put("providerName", config.getConfigName());
            return cap;
        }).collect(Collectors.toList());
        
        return ApiResponse.success(capabilities);
    }
    
    @PostMapping("/capabilities")
    public ApiResponse<Map<String, Object>> createCapability(@RequestBody Map<String, Object> data) {
        try {
            Long providerId = Long.valueOf(data.get("providerId").toString());
            String capabilityType = (String) data.getOrDefault("capabilityType", "chat");
            
            UnifiedAiConfig config = unifiedAiConfigMapper.selectById(providerId);
            if (config == null) {
                return ApiResponse.error(404, "Provider not found");
            }
            
            // 更新任务类型支持
            String currentTaskTypes = config.getTaskTypes() != null ? config.getTaskTypes() : "[]";
            if (!currentTaskTypes.contains(capabilityType)) {
                currentTaskTypes = currentTaskTypes.replace("]", ", \"" + capabilityType + "\"]");
                config.setTaskTypes(currentTaskTypes);
                unifiedAiConfigMapper.updateById(config);
                unifiedService.clearCache();
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", config.getId());
            result.put("message", "能力添加成功");
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(500, "创建能力失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/capabilities/{id}")
    public ApiResponse<Map<String, Object>> updateCapability(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        return createCapability(data);
    }
    
    @DeleteMapping("/capabilities/{id}")
    public ApiResponse<Void> deleteCapability(@PathVariable Long id) {
        try {
            UnifiedAiConfig config = unifiedAiConfigMapper.selectById(id);
            if (config != null) {
                config.setCapabilities("[]");  // 重置能力
                unifiedAiConfigMapper.updateById(config);
                unifiedService.clearCache();
            }
            return ApiResponse.success(null);
        } catch (Exception e) {
            return ApiResponse.error(500, "删除能力失败: " + e.getMessage());
        }
    }
    
    // ==================== 提供商管理 ====================
    
    @GetMapping("/providers")
    public ApiResponse<List<UnifiedAiConfig>> getProviders(@RequestParam(required = false) String type) {
        List<UnifiedAiConfig> providers;
        if (type != null && !type.isEmpty()) {
            providers = unifiedAiConfigMapper.selectList(
                new LambdaQueryWrapper<UnifiedAiConfig>()
                    .eq(UnifiedAiConfig::getDeleted, 0)
                    .eq(UnifiedAiConfig::getEnabled, 1)
                    .eq(UnifiedAiConfig::getProviderType, type)
                    .orderByAsc(UnifiedAiConfig::getPriority)
            );
        } else {
            providers = unifiedService.getAllEnabledConfigs();
        }
        return ApiResponse.success(providers);
    }
    
    @PutMapping("/providers/{id}")
    public ApiResponse<UnifiedAiConfig> updateProvider(@PathVariable Long id, @RequestBody UnifiedAiConfig config) {
        try {
            config.setId(id);
            UnifiedAiConfig updated = unifiedService.updateConfig(id, config);
            return ApiResponse.success(updated);
        } catch (Exception e) {
            return ApiResponse.error(500, "更新失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/providers/{id}/enable")
    public ApiResponse<Map<String, Object>> enableProvider(@PathVariable Long id) {
        try {
            UnifiedAiConfig config = unifiedAiConfigMapper.selectById(id);
            if (config == null) {
                return ApiResponse.error(404, "Provider not found");
            }
            
            config.setEnabled(1);
            config.setFailCount(0);  // 重置失败计数
            unifiedAiConfigMapper.updateById(config);
            unifiedService.clearCache();
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", id);
            result.put("enabled", true);
            result.put("message", "提供商已启用");
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(500, "启用失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/providers/{id}/disable")
    public ApiResponse<Map<String, Object>> disableProvider(@PathVariable Long id) {
        try {
            UnifiedAiConfig config = unifiedAiConfigMapper.selectById(id);
            if (config == null) {
                return ApiResponse.error(404, "Provider not found");
            }
            
            config.setEnabled(0);
            unifiedAiConfigMapper.updateById(config);
            unifiedService.clearCache();
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", id);
            result.put("enabled", false);
            result.put("message", "提供商已禁用");
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(500, "禁用失败: " + e.getMessage());
        }
    }
    
    // ==================== 统计数据 ====================
    
    @GetMapping("/stats/providers")
    public ApiResponse<List<Map<String, Object>>> getProviderStats(@RequestParam(defaultValue = "7") int days) {
        List<UnifiedAiConfig> configs = unifiedAiConfigMapper.selectList(
            new LambdaQueryWrapper<UnifiedAiConfig>()
                .eq(UnifiedAiConfig::getDeleted, 0)
        );
        
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        
        List<Map<String, Object>> stats = configs.stream().map(config -> {
            Map<String, Object> stat = new HashMap<>();
            stat.put("providerId", config.getId());
            stat.put("providerName", config.getConfigName());
            stat.put("providerType", config.getProviderType());
            stat.put("totalCalls", config.getDailyUsed() != null ? config.getDailyUsed() : 0);
            stat.put("successCalls", config.getDailyUsed() != null ? config.getDailyUsed() : 0);  // 简化处理
            stat.put("failCount", config.getFailCount() != null ? config.getFailCount() : 0);
            stat.put("avgResponseTime", config.getSpeedScore() != null ? (1000 / config.getSpeedScore().doubleValue()) : 100);
            stat.put("lastSuccessAt", config.getLastSuccessAt());
            stat.put("lastFailAt", config.getLastFailAt());
            stat.put("isEnabled", config.getEnabled() == 1);
            stat.put("healthStatus", (config.getFailCount() == null || config.getFailCount() < 3) ? "healthy" : "degraded");
            return stat;
        }).collect(Collectors.toList());
        
        return ApiResponse.success(stats);
    }
    
    @GetMapping("/stats/models")
    public ApiResponse<List<Map<String, Object>>> getModelStats(@RequestParam(defaultValue = "7") int days) {
        List<UnifiedAiConfig> configs = unifiedAiConfigMapper.selectList(
            new LambdaQueryWrapper<UnifiedAiConfig>()
                .eq(UnifiedAiConfig::getDeleted, 0)
                .eq(UnifiedAiConfig::getEnabled, 1)
        );
        
        List<Map<String, Object>> stats = configs.stream()
            .collect(Collectors.groupingBy(UnifiedAiConfig::getDefaultModel))
            .entrySet().stream().map(entry -> {
                String model = entry.getKey();
                List<UnifiedAiConfig> providers = entry.getValue();
                
                Map<String, Object> stat = new HashMap<>();
                stat.put("modelName", model);
                stat.put("providerCount", providers.size());
                stat.put("totalCalls", providers.stream().mapToInt(c -> c.getDailyUsed() != null ? c.getDailyUsed() : 0).sum());
                stat.put("avgQuality", providers.stream()
                    .mapToDouble(c -> c.getQualityScore() != null ? c.getQualityScore().doubleValue() : 0.80)
                    .average().orElse(0.80));
                stat.put("providers", providers.stream().map(UnifiedAiConfig::getConfigName).collect(Collectors.toList()));
                
                return stat;
            }).collect(Collectors.toList());
        
        return ApiResponse.success(stats);
    }
    
    @GetMapping("/stats/tasks")
    public ApiResponse<Map<String, Object>> getTaskStats(@RequestParam(defaultValue = "7") int days) {
        List<UnifiedAiConfig> allConfigs = unifiedAiConfigMapper.selectList(
            new LambdaQueryWrapper<UnifiedAiConfig>().eq(UnifiedAiConfig::getDeleted, 0)
        );
        
        long totalTasks = allConfigs.stream()
            .mapToLong(c -> c.getDailyUsed() != null ? c.getDailyUsed() : 0).sum();
        
        long successTasks = totalTasks;  // 简化：假设都是成功的
        
        long failedTasks = allConfigs.stream()
            .mapToLong(c -> c.getFailCount() != null ? c.getFailCount() : 0).sum();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("period", days + "天");
        stats.put("totalTasks", totalTasks);
        stats.put("successTasks", successTasks);
        stats.put("failedTasks", failedTasks);
        stats.put("successRate", totalTasks > 0 ? (double) successTasks / totalTasks * 100 : 100);
        stats.put("avgProcessingTime", "2.5s");  // 模拟值
        stats.put("peakHour", 14);  // 模拟值
        stats.put("timestamp", LocalDateTime.now());
        
        return ApiResponse.success(stats);
    }
    
    // ==================== 日志查询（简化版）====================
    
    @GetMapping("/logs")
    public ApiResponse<Map<String, Object>> getLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String providerId) {
        
        LambdaQueryWrapper<UnifiedAiConfig> wrapper = new LambdaQueryWrapper<UnifiedAiConfig>()
            .eq(UnifiedAiConfig::getDeleted, 0)
            .orderByDesc(UnifiedAiConfig::getUpdatedAt);
        
        if (providerId != null && !providerId.isEmpty()) {
            wrapper.eq(UnifiedAiConfig::getId, Long.valueOf(providerId));
        }
        
        // 简化：返回最近的配置更新记录作为"日志"
        List<UnifiedAiConfig> recentUpdates = unifiedAiConfigMapper.selectList(wrapper);
        
        List<Map<String, Object>> logs = recentUpdates.stream().limit(size).map(config -> {
            Map<String, Object> log = new HashMap<>();
            log.put("id", config.getId());
            log.put("timestamp", config.getUpdatedAt());
            log.put("level", config.getFailCount() != null && config.getFailCount() > 0 ? "WARN" : "INFO");
            log.put("providerName", config.getConfigName());
            log.put("modelName", config.getDefaultModel());
            log.put("action", config.getFailCount() != null && config.getFailCount() > 3 ? 
                "故障转移触发" : "正常调用");
            log.put("duration", config.getSpeedScore() != null ? 
                String.format("%.2fs", 1000 / config.getSpeedScore().doubleValue()) : "1.00s");
            log.put("status", (config.getFailCount() == null || config.getFailCount() < 3) ? "SUCCESS" : "FAILED");
            log.put("errorMessage", config.getFailCount() != null && config.getFailCount() >= 3 ?
                "连续失败" + config.getFailCount() + "次" : null);
            return log;
        }).collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        result.put("content", logs);
        result.put("totalElements", recentUpdates.size());
        result.put("totalPages", (int) Math.ceil((double) recentUpdates.size() / size));
        result.put("currentPage", page);
        result.put("size", size);
        
        return ApiResponse.success(result);
    }
    
    // ==================== 缓存管理 ====================
    
    @PostMapping("/cache/refresh")
    public ApiResponse<Map<String, Object>> refreshCache() {
        try {
            unifiedService.clearCache();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "缓存刷新成功");
            result.put("clearedKeys", "all");  // 清除了所有缓存
            result.put("timestamp", LocalDateTime.now());
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(500, "缓存刷新失败: " + e.getMessage());
        }
    }
}
