package com.ecommerce.workflow.service.video;

import com.ecommerce.workflow.entity.AiVideoConfig;
import com.ecommerce.workflow.entity.VideoPromptTemplate;
import com.ecommerce.workflow.mapper.VideoPromptTemplateMapper;
import com.ecommerce.workflow.service.config.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VideoPromptTemplateLearningService {

    private static final Logger log = LoggerFactory.getLogger(VideoPromptTemplateLearningService.class);

    private final VideoPromptTemplateMapper templateMapper;
    private final SysConfigService configService;

    public VideoPromptTemplateLearningService(VideoPromptTemplateMapper templateMapper,
            SysConfigService configService) {
        this.templateMapper = templateMapper;
        this.configService = configService;
    }

    public VideoPromptTemplate selectBestTemplate(AiVideoConfig config) {
        String category = inferCategoryFromConfig(config);
        String subCategory = inferSubCategoryFromConfig(config);

        List<VideoPromptTemplate> candidates = templateMapper.selectByCategoryAndSubCategory(category, subCategory);

        if (candidates.isEmpty()) {
            candidates = templateMapper.selectByCategory(category);
        }

        if (candidates.isEmpty()) {
            candidates = templateMapper.selectAllActive();
        }

        if (candidates.isEmpty()) {
            log.info("未找到匹配的模板，使用默认模板");
            return null;
        }

        VideoPromptTemplate bestTemplate = candidates.stream()
                .max((t1, t2) -> {
                    double score1 = calculateTemplateScore(t1);
                    double score2 = calculateTemplateScore(t2);
                    return Double.compare(score1, score2);
                })
                .orElse(candidates.get(0));

        log.info("选择模板: code={}, category={}, score={}",
                bestTemplate.getTemplateCode(),
                bestTemplate.getCategory(),
                calculateTemplateScore(bestTemplate));

        return bestTemplate;
    }

    public void recordTemplateUsage(Long templateId, boolean success, double qualityScore) {
        templateMapper.incrementUsageCount(templateId);

        VideoPromptTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            return;
        }

        double currentSuccessRate = template.getSuccessRate() != null ? template.getSuccessRate().doubleValue() : 0.5;
        double currentAvgQuality = template.getAvgQualityScore() != null ? template.getAvgQualityScore().doubleValue() : 0.5;
        int usageCount = template.getUsageCount() != null ? template.getUsageCount() : 1;

        if (usageCount <= 0) {
            usageCount = 1;
        }

        double newSuccessRate = (currentSuccessRate * (usageCount - 1) + (success ? 1.0 : 0.0)) / usageCount;
        double newAvgQuality = (currentAvgQuality * (usageCount - 1) + qualityScore) / usageCount;

        newSuccessRate = Math.max(0.0, Math.min(1.0, newSuccessRate));
        newAvgQuality = Math.max(0.0, Math.min(100.0, newAvgQuality));

        templateMapper.updateLearningMetrics(templateId, newSuccessRate, newAvgQuality);

        log.info("模板学习更新: id={}, successRate={}, avgQuality={}, usageCount={}",
                templateId, newSuccessRate, newAvgQuality, usageCount);
    }

    public VideoPromptTemplate createTemplateFromLearning(String templateCode, String templateName,
            String category, String subCategory, String rules, String systemPrefix) {
        VideoPromptTemplate template = new VideoPromptTemplate();
        template.setTemplateCode(templateCode);
        template.setTemplateName(templateName);
        template.setCategory(category);
        template.setSubCategory(subCategory);
        template.setRules(rules);
        template.setSystemPrefix(systemPrefix);
        template.setDescription("自动学习生成的模板");
        template.setVersion(1);
        template.setSuccessRate(java.math.BigDecimal.valueOf(0.5));
        template.setUsageCount(0);
        template.setAvgQualityScore(java.math.BigDecimal.valueOf(0.5));
        template.setLearnedFromCases(1);
        template.setIsActive(true);
        template.setDeleted(0);

        templateMapper.insert(template);

        log.info("创建学习模板: code={}, name={}, category={}", templateCode, templateName, category);

        return template;
    }

    public Map<String, Object> getTemplateStatistics() {
        List<VideoPromptTemplate> allTemplates = templateMapper.selectAllActive();

        Map<String, Long> categoryCount = allTemplates.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getCategory() != null ? t.getCategory() : "未分类",
                        Collectors.counting()));

        double avgSuccessRate = allTemplates.stream()
                .filter(t -> t.getSuccessRate() != null)
                .mapToDouble(t -> t.getSuccessRate().doubleValue())
                .average()
                .orElse(0.0);

        int totalUsage = allTemplates.stream()
                .mapToInt(t -> t.getUsageCount() != null ? t.getUsageCount() : 0)
                .sum();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTemplates", allTemplates.size());
        stats.put("categoryCount", categoryCount);
        stats.put("avgSuccessRate", avgSuccessRate);
        stats.put("totalUsage", totalUsage);
        stats.put("templates", allTemplates);

        return stats;
    }

    private double calculateTemplateScore(VideoPromptTemplate template) {
        double successRate = template.getSuccessRate() != null ? template.getSuccessRate().doubleValue() : 0.5;
        double qualityScore = template.getAvgQualityScore() != null ? template.getAvgQualityScore().doubleValue() : 0.5;
        int usageCount = template.getUsageCount() != null ? template.getUsageCount() : 0;

        double usageBonus = Math.min(usageCount / 100.0, 0.2);

        return successRate * 0.4 + qualityScore * 0.4 + usageBonus;
    }

    private String inferCategoryFromConfig(AiVideoConfig config) {
        String frameType = config.getFrameType();
        String topic = config.getTopic();
        String scene = config.getScene();
        String sceneType = config.getSceneType();

        if (frameType != null && (frameType.contains("tech") || frameType.contains("unbox"))) {
            return "数码";
        }

        if (topic != null && (topic.contains("服装") || topic.contains("服饰") || 
                topic.contains("手机壳") || topic.contains("配饰"))) {
            return "服装";
        }

        if (scene != null && (scene.contains("数码") || scene.contains("科技") || scene.contains("电子"))) {
            return "数码";
        }

        if (sceneType != null && (sceneType.contains("户外") || sceneType.contains("自然"))) {
            return "户外";
        }

        if (sceneType != null && (sceneType.contains("职场") || sceneType.contains("商务"))) {
            return "职场";
        }

        return "通用";
    }

    private String inferSubCategoryFromConfig(AiVideoConfig config) {
        String topic = config.getTopic();
        String role = config.getRole();
        String scene = config.getScene();

        if (topic != null && (topic.contains("手机") || topic.contains("壳") || topic.contains("配件"))) {
            return "配件";
        }

        if (role != null && (role.contains("女性") || role.contains("女士"))) {
            return "女性";
        }

        if (role != null && (role.contains("男性") || role.contains("男士"))) {
            return "男性";
        }

        if (scene != null && (scene.contains("居家") || scene.contains("室内"))) {
            return "居家";
        }

        if (scene != null && (scene.contains("户外") || scene.contains("室外"))) {
            return "户外";
        }

        return null;
    }

    public VideoPromptTemplate selectBestTemplateFromParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }

        String category = inferCategoryFromParams(params);
        String subCategory = inferSubCategoryFromParams(params);

        List<VideoPromptTemplate> candidates = templateMapper.selectByCategoryAndSubCategory(category, subCategory);

        if (candidates.isEmpty()) {
            candidates = templateMapper.selectByCategory(category);
        }

        if (candidates.isEmpty()) {
            candidates = templateMapper.selectAllActive();
        }

        if (candidates.isEmpty()) {
            log.info("未找到匹配的模板(参数模式)，使用默认模板");
            return null;
        }

        VideoPromptTemplate bestTemplate = candidates.stream()
                .max((t1, t2) -> {
                    double score1 = calculateTemplateScore(t1);
                    double score2 = calculateTemplateScore(t2);
                    return Double.compare(score1, score2);
                })
                .orElse(candidates.get(0));

        log.info("选择模板(参数模式): code={}, category={}, score={}",
                bestTemplate.getTemplateCode(),
                bestTemplate.getCategory(),
                calculateTemplateScore(bestTemplate));

        return bestTemplate;
    }

    private String inferCategoryFromParams(Map<String, Object> params) {
        String framework = extractParamString(params.get("framework"));
        String product = extractMapParamValue(params.get("product"));
        String scene = extractMapParamValue(params.get("scene"));

        if (framework != null && (framework.contains("tech") || framework.contains("unbox"))) {
            return "数码";
        }

        if (product != null && (product.contains("服装") || product.contains("服饰") || 
                product.contains("手机壳") || product.contains("配饰"))) {
            return "服装";
        }

        if (scene != null && (scene.contains("数码") || scene.contains("科技") || scene.contains("电子"))) {
            return "数码";
        }

        return "通用";
    }

    private String inferSubCategoryFromParams(Map<String, Object> params) {
        String product = extractMapParamValue(params.get("product"));
        
        if (product != null && (product.contains("手机") || product.contains("壳") || product.contains("配件"))) {
            return "配件";
        }

        return null;
    }

    private String extractParamString(Object value) {
        if (value == null) return null;
        if (value instanceof String) return (String) value;
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Object v = map.get("value");
            return v != null ? v.toString() : null;
        }
        return value.toString();
    }

    private String extractMapParamValue(Object value) {
        if (value == null) return null;
        if (value instanceof String) return (String) value;
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Object v = map.get("value");
            return v != null ? v.toString() : null;
        }
        return null;
    }
}
