package com.ecommerce.workflow.controller;

import com.ecommerce.workflow.service.video.VideoPromptFrameworkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/config/video-prompt-framework")
public class VideoPromptFrameworkController {

    private static final Logger log = LoggerFactory.getLogger(VideoPromptFrameworkController.class);
    private final VideoPromptFrameworkService frameworkService;

    public VideoPromptFrameworkController(VideoPromptFrameworkService frameworkService) {
        this.frameworkService = frameworkService;
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> getTemplateInfo() {
        return ApiResponse.success(frameworkService.getTemplateInfo());
    }

    @PutMapping("/update")
    public ApiResponse<String> updateTemplate(@RequestBody Map<String, String> request) {
        String field = request.get("field");
        String value = request.get("value");

        if (field == null || value == null) {
            return ApiResponse.error("缺少必要参数: field, value");
        }

        try {
            frameworkService.updateTemplate(field, value);
            return ApiResponse.success("提示词框架已更新");
        } catch (Exception e) {
            log.error("更新提示词框架失败", e);
            return ApiResponse.error("更新提示词框架失败: " + e.getMessage());
        }
    }

    @PutMapping("/version")
    public ApiResponse<String> updateVersion(@RequestBody Map<String, String> request) {
        String version = request.get("version");

        if (version == null) {
            return ApiResponse.error("缺少必要参数: version");
        }

        try {
            frameworkService.updateVersion(version);
            return ApiResponse.success("版本号已更新为: " + version);
        } catch (Exception e) {
            log.error("更新版本号失败", e);
            return ApiResponse.error("更新版本号失败: " + e.getMessage());
        }
    }

    @PostMapping("/reset")
    public ApiResponse<String> resetToDefault(@RequestBody Map<String, String> request) {
        String field = request.get("field");

        if (field == null) {
            return ApiResponse.error("缺少必要参数: field");
        }

        try {
            frameworkService.updateTemplate(field, "");
            log.info("提示词框架字段已重置为默认值: {}", field);
            return ApiResponse.success("已重置为默认值");
        } catch (Exception e) {
            log.error("重置提示词框架失败", e);
            return ApiResponse.error("重置提示词框架失败: " + e.getMessage());
        }
    }
}
