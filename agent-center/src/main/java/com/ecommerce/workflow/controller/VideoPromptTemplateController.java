package com.ecommerce.workflow.controller;

import com.ecommerce.workflow.entity.VideoPromptTemplate;
import com.ecommerce.workflow.service.video.VideoPromptTemplateLearningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/video-prompt-templates")
public class VideoPromptTemplateController {

    private static final Logger log = LoggerFactory.getLogger(VideoPromptTemplateController.class);
    private final VideoPromptTemplateLearningService learningService;

    public VideoPromptTemplateController(VideoPromptTemplateLearningService learningService) {
        this.learningService = learningService;
    }

    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> getStatistics() {
        return ApiResponse.success(learningService.getTemplateStatistics());
    }

    @PostMapping("/create")
    public ApiResponse<VideoPromptTemplate> createTemplate(@RequestBody Map<String, String> request) {
        String templateCode = request.get("templateCode");
        String templateName = request.get("templateName");
        String category = request.get("category");
        String subCategory = request.get("subCategory");
        String rules = request.get("rules");
        String systemPrefix = request.get("systemPrefix");

        if (templateCode == null || templateName == null) {
            return ApiResponse.error("缺少必要参数: templateCode, templateName");
        }

        try {
            VideoPromptTemplate template = learningService.createTemplateFromLearning(
                    templateCode, templateName, category, subCategory, rules, systemPrefix);
            return ApiResponse.success(template);
        } catch (Exception e) {
            log.error("创建模板失败", e);
            return ApiResponse.error("创建模板失败: " + e.getMessage());
        }
    }

    @PostMapping("/record-usage")
    public ApiResponse<String> recordUsage(@RequestBody Map<String, Object> request) {
        Long templateId = request.get("templateId") != null ? Long.valueOf(request.get("templateId").toString()) : null;
        Boolean success = request.get("success") != null ? Boolean.valueOf(request.get("success").toString()) : null;
        Double qualityScore = request.get("qualityScore") != null ? Double.valueOf(request.get("qualityScore").toString()) : null;

        if (templateId == null || success == null || qualityScore == null) {
            return ApiResponse.error("缺少必要参数: templateId, success, qualityScore");
        }

        try {
            learningService.recordTemplateUsage(templateId, success, qualityScore);
            return ApiResponse.success("模板使用记录成功");
        } catch (Exception e) {
            log.error("记录模板使用失败", e);
            return ApiResponse.error("记录模板使用失败: " + e.getMessage());
        }
    }
}
