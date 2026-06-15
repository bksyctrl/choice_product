package com.ecommerce.workflow.service.knowledge;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ecommerce.workflow.entity.Knowledge;
import com.ecommerce.workflow.service.evolution.SkillConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class KnowledgeApplicationService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeApplicationService.class);

    private final KnowledgeService knowledgeService;
    private final SkillConfigService skillConfigService;
    private final ObjectMapper objectMapper;

    public KnowledgeApplicationService(KnowledgeService knowledgeService,
                                        SkillConfigService skillConfigService,
                                        ObjectMapper objectMapper) {
        this.knowledgeService = knowledgeService;
        this.skillConfigService = skillConfigService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> applyKnowledge(String skillCode, Map<String, Object> context) {
        log.info("应用知识到Skill: {}", skillCode);

        Map<String, Object> result = new HashMap<>();
        result.put("skillCode", skillCode);
        result.put("appliedAt", LocalDateTime.now());

        try {
            List<Knowledge> relevantKnowledge = retrieveRelevantKnowledge(skillCode, context);

            if (relevantKnowledge.isEmpty()) {
                log.info("没有找到相关知识");
                result.put("status", "NO_KNOWLEDGE");
                result.put("message", "没有找到相关知识");
                return result;
            }

            List<Knowledge> applicableKnowledge = filterByContext(relevantKnowledge, context);

            int appliedCount = 0;
            for (Knowledge knowledge : applicableKnowledge) {
                boolean applied = applySingleKnowledge(skillCode, knowledge, context);
                if (applied) {
                    appliedCount++;
                }
            }

            result.put("status", "SUCCESS");
            result.put("appliedCount", appliedCount);
            result.put("totalKnowledge", applicableKnowledge.size());

            log.info("成功应用{}条知识", appliedCount);

        } catch (Exception e) {
            log.error("应用知识失败", e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
        }

        return result;
    }

    private List<Knowledge> retrieveRelevantKnowledge(String skillCode, Map<String, Object> context) {
        try {
            String searchContent = buildSearchContent(skillCode, context);

            List<Knowledge> knowledge = knowledgeService.searchSimilar(searchContent, 5);

            log.info("检索到{}条相关知识", knowledge.size());
            return knowledge;

        } catch (Exception e) {
            log.error("检索知识失败", e);
            return List.of();
        }
    }

    private String buildSearchContent(String skillCode, Map<String, Object> context) {
        StringBuilder content = new StringBuilder();
        content.append(skillCode).append(" ");

        if (context.containsKey("category")) {
            content.append(context.get("category")).append(" ");
        }
        if (context.containsKey("platform")) {
            content.append(context.get("platform")).append(" ");
        }
        if (context.containsKey("productType")) {
            content.append(context.get("productType")).append(" ");
        }

        return content.toString();
    }

    private List<Knowledge> filterByContext(List<Knowledge> knowledge, Map<String, Object> context) {
        return knowledge.stream()
            .filter(k -> isApplicable(k, context))
            .sorted((k1, k2) -> Double.compare(
                calculateRelevanceScore(k2, context),
                calculateRelevanceScore(k1, context)
            ))
            .toList();
    }

    private boolean isApplicable(Knowledge knowledge, Map<String, Object> context) {
        String tags = knowledge.getTags();
        if (tags == null || tags.isEmpty()) {
            return true;
        }

        try {
            List<String> tagList = objectMapper.readValue(tags, new TypeReference<List<String>>() {});

            if (context.containsKey("category")) {
                String contextCategory = (String) context.get("category");
                if (tagList.stream().noneMatch(t -> t.contains(contextCategory))) {
                    return false;
                }
            }

            if (context.containsKey("platform")) {
                String contextPlatform = (String) context.get("platform");
                if (tagList.stream().noneMatch(t -> t.contains(contextPlatform))) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return true;
        }
    }

    private double calculateRelevanceScore(Knowledge knowledge, Map<String, Object> context) {
        double score = knowledge.getConfidence() != null ? knowledge.getConfidence() : 0.5;

        String tags = knowledge.getTags();
        if (tags != null && !tags.isEmpty()) {
            try {
                List<String> tagList = objectMapper.readValue(tags, new TypeReference<List<String>>() {});

                if (context.containsKey("category")) {
                    String contextCategory = (String) context.get("category");
                    if (tagList.stream().anyMatch(t -> t.contains(contextCategory))) {
                        score += 0.2;
                    }
                }

                if (context.containsKey("platform")) {
                    String contextPlatform = (String) context.get("platform");
                    if (tagList.stream().anyMatch(t -> t.contains(contextPlatform))) {
                        score += 0.2;
                    }
                }
            } catch (Exception e) {
                log.debug("解析标签失败", e);
            }
        }

        return score;
    }

    private boolean applySingleKnowledge(String skillCode, Knowledge knowledge, Map<String, Object> context) {
        try {
            String knowledgeType = knowledge.getType();

            switch (knowledgeType) {
                case "SUCCESS_PATTERN":
                case "SUCCESS_EXPERIENCE":
                    return applySuccessExperience(skillCode, knowledge, context);

                case "FAILURE_PATTERN":
                case "FAILURE_LESSON":
                    return applyFailureLesson(skillCode, knowledge, context);

                case "EXPLOSIVE_PATTERN":
                    return applyExplosivePattern(skillCode, knowledge, context);

                case "DELIVERY_SUCCESS":
                    return applyDeliverySuccess(skillCode, knowledge, context);

                default:
                    log.warn("未知知识类型: {}", knowledgeType);
                    return false;
            }

        } catch (Exception e) {
            log.error("应用单条知识失败", e);
            return false;
        }
    }

    private boolean applySuccessExperience(String skillCode, Knowledge knowledge, Map<String, Object> context) {
        try {
            log.info("应用成功经验: {}", knowledge.getTitle());

            String content = knowledge.getContent();
            if (content != null) {
                if (content.contains("CVR") || content.contains("转化率")) {
                    log.info("检测到转化率相关成功经验");
                }

                if (content.contains("GMV") || content.contains("成交额")) {
                    log.info("检测到GMV相关成功经验");
                }
            }

            knowledgeService.recordApplication(knowledge.getKnowledgeId(), true);

            return true;

        } catch (Exception e) {
            log.error("应用成功经验失败", e);
            return false;
        }
    }

    private boolean applyFailureLesson(String skillCode, Knowledge knowledge, Map<String, Object> context) {
        try {
            log.info("应用失败教训: {}", knowledge.getTitle());

            log.warn("已记录避坑规则: {}", knowledge.getTitle());

            knowledgeService.recordApplication(knowledge.getKnowledgeId(), true);

            return true;

        } catch (Exception e) {
            log.error("应用失败教训失败", e);
            return false;
        }
    }

    private boolean applyExplosivePattern(String skillCode, Knowledge knowledge, Map<String, Object> context) {
        try {
            log.info("应用爆款规律: {}", knowledge.getTitle());

            String content = knowledge.getContent();
            if (content != null) {
                log.info("爆款规律详情: {}", content);
            }

            knowledgeService.recordApplication(knowledge.getKnowledgeId(), true);

            return true;

        } catch (Exception e) {
            log.error("应用爆款规律失败", e);
            return false;
        }
    }

    private boolean applyDeliverySuccess(String skillCode, Knowledge knowledge, Map<String, Object> context) {
        try {
            log.info("应用投放成功经验: {}", knowledge.getTitle());

            String content = knowledge.getContent();
            if (content != null) {
                log.info("投放成功经验详情: {}", content);
            }

            knowledgeService.recordApplication(knowledge.getKnowledgeId(), true);

            return true;

        } catch (Exception e) {
            log.error("应用投放成功经验失败", e);
            return false;
        }
    }

    public Map<String, Object> getApplicationStatistics() {
        Map<String, Object> stats = new HashMap<>();

        try {
            stats.put("totalApplications", 0);
            stats.put("successApplications", 0);
            stats.put("failedApplications", 0);
            stats.put("lastApplicationTime", LocalDateTime.now());

        } catch (Exception e) {
            log.error("获取应用统计失败", e);
        }

        return stats;
    }
}
