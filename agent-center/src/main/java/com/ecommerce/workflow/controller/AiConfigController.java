package com.ecommerce.workflow.controller;

import com.ecommerce.workflow.entity.AiProviderConfig;
import com.ecommerce.workflow.service.ai.AiProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/config/ai")
public class AiConfigController {

    private static final Logger log = LoggerFactory.getLogger(AiConfigController.class);
    private final AiProviderService aiProviderService;

    public AiConfigController(AiProviderService aiProviderService) {
        this.aiProviderService = aiProviderService;
    }

    @GetMapping("/providers")
    public ApiResponse<List<AiProviderConfig>> listProviders() {
        return ApiResponse.success(aiProviderService.getAllProviders());
    }

    @GetMapping("/providers/{id}")
    public ApiResponse<AiProviderConfig> getProvider(@PathVariable Long id) {
        AiProviderConfig provider = aiProviderService.getProviderById(id);
        if (provider == null) {
            return ApiResponse.error(404, "配置不存在");
        }
        return ApiResponse.success(provider);
    }

    @GetMapping("/providers/type/{type}")
    public ApiResponse<List<AiProviderConfig>> getProvidersByType(@PathVariable String type) {
        return ApiResponse.success(aiProviderService.getProvidersByType(type));
    }

    @PostMapping("/providers")
    public ApiResponse<AiProviderConfig> createProvider(@Valid @RequestBody AiProviderConfig config) {
        try {
            return ApiResponse.success(aiProviderService.createProvider(config));
        } catch (Exception e) {
            log.error("创建AI提供商配置失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/providers/{id}")
    public ApiResponse<AiProviderConfig> updateProvider(@PathVariable Long id, @Valid @RequestBody AiProviderConfig config) {
        config.setId(id);
        try {
            return ApiResponse.success(aiProviderService.updateProvider(config));
        } catch (Exception e) {
            log.error("更新AI提供商配置失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/providers/{id}")
    public ApiResponse<Void> deleteProvider(@PathVariable Long id) {
        try {
            aiProviderService.deleteProvider(id);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("删除AI提供商配置失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/providers/{id}/test")
    public ApiResponse<Map<String, Object>> testConnection(@PathVariable Long id) {
        AiProviderConfig provider = aiProviderService.getProviderById(id);
        if (provider == null) {
            return ApiResponse.error(404, "配置不存在");
        }
        boolean success = aiProviderService.testConnection(provider);
        return ApiResponse.success(Map.of(
                "providerId", id,
                "providerName", provider.getProviderName(),
                "success", success,
                "message", success ? "连接成功" : "连接失败"
        ));
    }

    @PostMapping("/init")
    public ApiResponse<Map<String, Object>> initDefault() {
        try {
            aiProviderService.initDefaultProviders();
            return ApiResponse.success(Map.of("message", "初始化成功"));
        } catch (Exception e) {
            log.error("初始化默认AI提供商失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/cache/refresh")
    public ApiResponse<Map<String, Object>> refreshCache() {
        aiProviderService.refreshCache();
        return ApiResponse.success(Map.of("message", "缓存已刷新"));
    }
}
