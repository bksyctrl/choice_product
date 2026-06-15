package com.ecommerce.workflow.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.ecommerce.workflow.entity.UnifiedAiConfig;
import com.ecommerce.workflow.service.AiConfigUnifiedService;

@RestController
@RequestMapping("/api/ai/unified")
public class UnifiedAiConfigController {
    
    @Autowired
    private AiConfigUnifiedService unifiedService;
    
    /**
     * 获取所有配置
     */
    @GetMapping("/configs")
    public Map<String, Object> getAllConfigs() {
        List<UnifiedAiConfig> configs = unifiedService.getAllEnabledConfigs();
        return Map.of("code", 200, "data", configs, "message", "success");
    }
    
    /**
     * 按类型获取配置
     */
    @GetMapping("/configs/{type}")
    public Map<String, Object> getConfigsByType(@PathVariable String type) {
        List<UnifiedAiConfig> configs = unifiedService.getConfigsByType(type);
        return Map.of("code", 200, "data", configs, "message", "success");
    }
    
    /**
     * 根据ID获取配置
     */
    @GetMapping("/config/{id}")
    public Map<String, Object> getConfigById(@PathVariable Long id) {
        UnifiedAiConfig config = unifiedService.getConfigById(id);
        return Map.of("code", 200, "data", config, "message", "success");
    }
    
    /**
     * 创建配置
     */
    @PostMapping("/config")
    public Map<String, Object> createConfig(@RequestBody UnifiedAiConfig config) {
        UnifiedAiConfig created = unifiedService.createConfig(config);
        return Map.of("code", 200, "data", created, "message", "创建成功");
    }
    
    /**
     * 更新配置
     */
    @PutMapping("/config/{id}")
    public Map<String, Object> updateConfig(@PathVariable Long id, @RequestBody UnifiedAiConfig config) {
        UnifiedAiConfig updated = unifiedService.updateConfig(id, config);
        return Map.of("code", 200, "data", updated, "message", "更新成功");
    }
    
    /**
     * 删除配置
     */
    @DeleteMapping("/config/{id}")
    public Map<String, Object> deleteConfig(@PathVariable Long id) {
        unifiedService.deleteConfig(id);
        return Map.of("code", 200, "message", "删除成功");
    }
    
    /**
     * 智能选择最优配置
     */
    @PostMapping("/select-best")
    public Map<String, Object> selectBest(@RequestBody Map<String, String> request) {
        String taskType = request.getOrDefault("taskType", "chat");
        String strategy = request.getOrDefault("strategy", "balanced");
        UnifiedAiConfig selected = unifiedService.selectBestConfig(taskType, strategy);
        return Map.of("code", 200, "data", selected, "message", "选择成功");
    }
    
    /**
     * 清除缓存
     */
    @PostMapping("/cache/clear")
    public Map<String, Object> clearCache() {
        unifiedService.clearCache();
        return Map.of("code", 200, "message", "缓存已清除");
    }
}
