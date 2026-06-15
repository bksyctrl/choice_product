package com.ecommerce.workflow.service.evolution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.workflow.entity.AutoOptimizationLog;
import com.ecommerce.workflow.entity.ExpertRoleConfig;
import com.ecommerce.workflow.entity.PromptTemplate;
import com.ecommerce.workflow.entity.SkillConfig;
import com.ecommerce.workflow.mapper.AutoOptimizationLogMapper;
import com.ecommerce.workflow.mapper.ExpertRoleConfigMapper;
import com.ecommerce.workflow.mapper.PromptTemplateMapper;
import com.ecommerce.workflow.mapper.SkillConfigMapper;
import com.ecommerce.workflow.service.ai.AiProviderService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AutoOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(AutoOptimizationService.class);

    @Autowired
    private AutoOptimizationLogMapper optimizationLogMapper;

    @Autowired
    private SkillConfigMapper skillConfigMapper;

    @Autowired
    private PromptTemplateMapper promptTemplateMapper;

    @Autowired
    private ExpertRoleConfigMapper expertRoleConfigMapper;

    @Autowired
    private SkillUsageTracker skillUsageTracker;

    @Autowired
    private SkillEvolutionService skillEvolutionService;

    @Autowired
    private ABTestFramework abTestFramework;

    @Autowired(required = false)
    private AiProviderService aiProviderService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${optimization.auto-apply:false}")
    private boolean autoApply;

    @Value("${optimization.confidence-threshold:0.8}")
    private double confidenceThreshold;

    public Map<String, Object> optimizeSkillParams(String skillCode) {
        Map<String, Object> result = new HashMap<>();
        result.put("skillCode", skillCode);

        SkillConfig skill = skillConfigMapper.selectBySkillCode(skillCode);
        if (skill == null) {
            result.put("success", false);
            result.put("error", "Skill不存在");
            return result;
        }

        SkillUsageTracker.UsageStats stats = skillUsageTracker.getUsageStats(skillCode);
        if (stats.getTotalUsage() < 10) {
            result.put("success", false);
            result.put("error", "样本不足");
            return result;
        }

        Map<String, Object> currentParams = skill.getParamsJson();
        Map<String, Object> optimizedParams = generateOptimizedParams(skillCode, currentParams, stats);

        if (optimizedParams != null && !optimizedParams.equals(currentParams)) {
            result.put("beforeParams", currentParams);
            result.put("afterParams", optimizedParams);
            result.put("confidence", calculateConfidence(stats));

            Long logId = logOptimization("SKILL_PARAM", skillCode, currentParams, optimizedParams, "STATISTICAL");
            result.put("optimizationLogId", logId);

            if (autoApply && calculateConfidence(stats) >= confidenceThreshold) {
                applyOptimization(logId);
                result.put("applied", true);
            } else {
                result.put("applied", false);
                result.put("reason", autoApply ? "置信度不足" : "自动应用未启用");
            }
        } else {
            result.put("success", true);
            result.put("message", "无需优化");
        }

        return result;
    }

    private Map<String, Object> generateOptimizedParams(String skillCode,
            Map<String, Object> currentParams,
            SkillUsageTracker.UsageStats stats) {
        Map<String, Object> optimized = new HashMap<>(currentParams);

        if (stats.getSuccessRate() < 0.7) {
            if (optimized.containsKey("threshold")) {
                double currentThreshold = ((Number) optimized.get("threshold")).doubleValue();
                optimized.put("threshold", currentThreshold * 0.9);
            }
            if (optimized.containsKey("confidence")) {
                double currentConfidence = ((Number) optimized.get("confidence")).doubleValue();
                optimized.put("confidence", Math.max(0.5, currentConfidence * 0.9));
            }
        }

        if (stats.getAvgExecutionTimeMs() > 5000) {
            optimized.put("maxRetries", 1);
            optimized.put("timeout", 30000);
        }

        return optimized;
    }

    public Map<String, Object> optimizePromptTemplate(String templateId) {
        Map<String, Object> result = new HashMap<>();
        result.put("templateId", templateId);

        PromptTemplate template = promptTemplateMapper.selectByTemplateId(templateId);
        if (template == null) {
            result.put("success", false);
            result.put("error", "模板不存在");
            return result;
        }

        if (aiProviderService == null) {
            result.put("success", false);
            result.put("error", "AI服务不可用");
            return result;
        }

        try {
            String currentContent = template.getTemplateContent();
            String optimizedContent = generateOptimizedPrompt(template, currentContent);

            if (optimizedContent != null && !optimizedContent.equals(currentContent)) {
                result.put("beforeContent", currentContent);
                result.put("afterContent", optimizedContent);
                result.put("confidence", 0.7);

                Long logId = logOptimization("PROMPT_TEMPLATE", templateId,
                        currentContent, optimizedContent, "LLM_SUGGEST");
                result.put("optimizationLogId", logId);

                result.put("success", true);
            } else {
                result.put("success", true);
                result.put("message", "无需优化");
            }

        } catch (Exception e) {
            log.error("优化提示词模板失败: templateId={}", templateId, e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    private String generateOptimizedPrompt(PromptTemplate template, String currentContent) {
        try {
            String systemPrompt = "你是一个专业的提示词优化专家。请分析以下提示词模板，并提供优化建议。" +
                    "优化目标：提高生成内容的质量、准确性和一致性。\n\n" +
                    "优化方向：\n" +
                    "1. 增强指令的清晰度\n" +
                    "2. 添加具体的约束条件\n" +
                    "3. 改进输出格式\n" +
                    "4. 消除歧义\n" +
                    "请直接返回优化后的提示词内容。";

            String optimized = aiProviderService.chatWithFallback(systemPrompt, currentContent, 0.7, 2000);

            return optimized.trim();

        } catch (Exception e) {
            log.warn("AI优化提示词失败", e);
            return null;
        }
    }

    public Long logOptimization(String optimizationType, String targetCode,
            Object beforeValue, Object afterValue, String method) {
        AutoOptimizationLog optLog = new AutoOptimizationLog();
        optLog.setOptimizationType(optimizationType);
        optLog.setTargetCode(targetCode);
        optLog.setBeforeValue(serializeValue(beforeValue));
        optLog.setAfterValue(serializeValue(afterValue));
        optLog.setOptimizationMethod(method);
        optLog.setStatus("PENDING");
        optLog.setCreatedAt(LocalDateTime.now());

        optimizationLogMapper.insert(optLog);

        log.info("记录优化日志: type={}, code={}, method={}",
                optimizationType, targetCode, method);

        return optLog.getId();
    }

    public boolean applyOptimization(Long logId) {
        AutoOptimizationLog optLog = optimizationLogMapper.selectById(logId);
        if (optLog == null || !"PENDING".equals(optLog.getStatus())) {
            return false;
        }

        try {
            switch (optLog.getOptimizationType()) {
                case "SKILL_PARAM":
                    applySkillParamOptimization(optLog);
                    break;
                case "PROMPT_TEMPLATE":
                    applyPromptTemplateOptimization(optLog);
                    break;
                case "EXPERT_ROLE":
                    applyExpertRoleOptimization(optLog);
                    break;
                default:
                    return false;
            }

            optLog.setStatus("APPLIED");
            optLog.setAppliedAt(LocalDateTime.now());
            optimizationLogMapper.updateById(optLog);

            try {
                abTestFramework.collectTestResult(
                        "opt_" + optLog.getOptimizationType() + "_" + optLog.getTargetCode(),
                        0L,
                        "treatment",
                        true,
                        null,
                        Map.of("logId", logId, "type", optLog.getOptimizationType(), "target", optLog.getTargetCode()));
            } catch (Exception e) {
                log.warn("AB测试记录失败: {}", e.getMessage());
            }

            log.info("应用优化成功: logId={}", logId);
            return true;

        } catch (Exception e) {
            log.error("应用优化失败: logId={}", logId, e);
            return false;
        }
    }

    private void applySkillParamOptimization(AutoOptimizationLog optLog) throws Exception {
        SkillConfig skill = skillConfigMapper.selectBySkillCode(optLog.getTargetCode());
        if (skill != null) {
            Map<String, Object> newParams = objectMapper.readValue(
                    optLog.getAfterValue(),
                    new TypeReference<Map<String, Object>>() {
                    });
            skill.setParamsJson(newParams);
            skill.setUpdatedAt(LocalDateTime.now());
            skillConfigMapper.updateById(skill);
        }
    }

    private void applyPromptTemplateOptimization(AutoOptimizationLog optLog) throws Exception {
        PromptTemplate template = promptTemplateMapper.selectByTemplateId(optLog.getTargetCode());
        if (template != null) {
            template.setTemplateContent(optLog.getAfterValue());
            template.setVersion(template.getVersion() + 1);
            template.setUpdatedAt(LocalDateTime.now());
            promptTemplateMapper.updateById(template);
        }
    }

    private void applyExpertRoleOptimization(AutoOptimizationLog optLog) throws Exception {
        ExpertRoleConfig role = expertRoleConfigMapper.selectByRoleCode(optLog.getTargetCode());
        if (role != null) {
            Map<String, Object> newConfig = objectMapper.readValue(
                    optLog.getAfterValue(),
                    new TypeReference<Map<String, Object>>() {
                    });
            role.setConfigJson(newConfig);
            role.setUpdatedAt(LocalDateTime.now());
            expertRoleConfigMapper.updateById(role);
        }
    }

    public boolean rollbackOptimization(Long logId) {
        AutoOptimizationLog optLog = optimizationLogMapper.selectById(logId);
        if (optLog == null || !"APPLIED".equals(optLog.getStatus())) {
            return false;
        }

        try {
            String temp = optLog.getBeforeValue();
            optLog.setBeforeValue(optLog.getAfterValue());
            optLog.setAfterValue(temp);

            switch (optLog.getOptimizationType()) {
                case "SKILL_PARAM":
                    applySkillParamOptimization(optLog);
                    break;
                case "PROMPT_TEMPLATE":
                    applyPromptTemplateOptimization(optLog);
                    break;
                case "EXPERT_ROLE":
                    applyExpertRoleOptimization(optLog);
                    break;
            }

            optLog.setStatus("ROLLED_BACK");
            optLog.setRolledBackAt(LocalDateTime.now());
            optimizationLogMapper.updateById(optLog);

            log.info("回滚优化成功: logId={}", logId);
            return true;

        } catch (Exception e) {
            log.error("回滚优化失败: logId={}", logId, e);
            return false;
        }
    }

    public boolean deployIfEffective(Long logId, int testDays) {
        AutoOptimizationLog optLog = optimizationLogMapper.selectById(logId);
        if (optLog == null) {
            return false;
        }

        LocalDateTime testStart = optLog.getAppliedAt();
        if (testStart == null) {
            return false;
        }

        LocalDateTime testEnd = testStart.plusDays(testDays);
        if (LocalDateTime.now().isBefore(testEnd)) {
            return false;
        }

        Map<String, Object> analysis = skillEvolutionService.analyzeForEvolution(
                optLog.getOptimizationType().replace("_", ""),
                optLog.getTargetCode());

        if (analysis.containsKey("metrics7Days")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metrics = analysis.get("metrics7Days") instanceof Map
                    ? (Map<String, Object>) analysis.get("metrics7Days")
                    : new HashMap<>();
            if (metrics.containsKey("successRate")) {
                double successRate = ((Number) metrics.get("successRate")).doubleValue();
                if (successRate >= 0.7) {
                    optLog.setEffectMetrics(metrics);
                    optimizationLogMapper.updateById(optLog);
                    return true;
                }
            }
        }

        return false;
    }

    private double calculateConfidence(SkillUsageTracker.UsageStats stats) {
        double confidence = 0.5;

        if (stats.getTotalUsage() >= 100) {
            confidence += 0.2;
        } else if (stats.getTotalUsage() >= 50) {
            confidence += 0.1;
        }

        if (stats.getAvgUserRating() >= 4.0) {
            confidence += 0.1;
        }

        return Math.min(1.0, confidence);
    }

    private String serializeValue(Object value) {
        try {
            if (value instanceof String) {
                return (String) value;
            }
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    public List<AutoOptimizationLog> getPendingOptimizations() {
        LambdaQueryWrapper<AutoOptimizationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AutoOptimizationLog::getStatus, "PENDING")
                .orderByAsc(AutoOptimizationLog::getCreatedAt);
        return optimizationLogMapper.selectList(wrapper);
    }

    public List<AutoOptimizationLog> getAppliedOptimizations() {
        LambdaQueryWrapper<AutoOptimizationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AutoOptimizationLog::getStatus, "APPLIED")
                .orderByDesc(AutoOptimizationLog::getAppliedAt);
        return optimizationLogMapper.selectList(wrapper);
    }
}
