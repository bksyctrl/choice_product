package com.ecommerce.workflow.service.memory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ecommerce.workflow.entity.CaseMemory;
import com.ecommerce.workflow.entity.Knowledge;
import com.ecommerce.workflow.entity.SkillConfig;
import com.ecommerce.workflow.mapper.CaseMemoryMapper;
import com.ecommerce.workflow.entity.ExpertRoleConfig;
import com.ecommerce.workflow.service.config.SysConfigService;
import com.ecommerce.workflow.service.evolution.SkillConfigService;
import com.ecommerce.workflow.service.knowledge.KnowledgeService;
import com.ecommerce.workflow.service.learning.ExpertRoleService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class UnifiedMemoryService {
    private static final Logger log = LoggerFactory.getLogger(UnifiedMemoryService.class);

    private final WhiteBoxMemoryService whiteBoxMemory;
    private final PersistentLearningService persistentLearning;
    private final HeartbeatReflectionService heartbeatReflection;
    private final CaseMemoryMapper caseMemoryMapper;
    private final SkillConfigService skillConfigService;
    private final KnowledgeService knowledgeService;
    private final ObjectMapper objectMapper;
    private final SysConfigService sysConfigService;
    private final ExpertRoleService expertRoleService;

    public UnifiedMemoryService(WhiteBoxMemoryService whiteBoxMemory,
                                PersistentLearningService persistentLearning,
                                HeartbeatReflectionService heartbeatReflection,
                                CaseMemoryMapper caseMemoryMapper,
                                SkillConfigService skillConfigService,
                                KnowledgeService knowledgeService,
                                ObjectMapper objectMapper,
                                SysConfigService sysConfigService,
                                ExpertRoleService expertRoleService) {
        this.whiteBoxMemory = whiteBoxMemory;
        this.persistentLearning = persistentLearning;
        this.heartbeatReflection = heartbeatReflection;
        this.caseMemoryMapper = caseMemoryMapper;
        this.skillConfigService = skillConfigService;
        this.knowledgeService = knowledgeService;
        this.objectMapper = objectMapper;
        this.sysConfigService = sysConfigService;
        this.expertRoleService = expertRoleService;
    }

    public void recordBusinessAction(String skillCode, String actionType,
                                     Map<String, Object> inputParams,
                                     Map<String, Object> outputResult,
                                     boolean success, String errorMessage) {
        log.info("统一记忆记录: skill={}, action={}, success={}", skillCode, actionType, success);

        recordToCaseMemory(skillCode, actionType, inputParams, outputResult, success, errorMessage);

        skillConfigService.recordUsage(skillCode, success,
                outputParams(outputResult, "cvr"),
                outputParamsBigDecimal(outputResult, "gmv"));

        if (success) {
            whiteBoxMemory.recordLearning("SUCCESS", skillCode + ":" + actionType,
                    summarizeInput(inputParams),
                    summarizeOutput(outputResult),
                    "成功", "");
            heartbeatReflection.recordSuccess(skillCode, actionType,
                    summarizeOutput(outputResult), "自动记录");

            persistentLearning.saveEffectiveStrategy(skillCode, actionType,
                    extractImprovement(outputResult), true, inputParams);
        } else {
            whiteBoxMemory.recordLearning("FAILURE", skillCode + ":" + actionType,
                    summarizeInput(inputParams),
                    errorMessage != null ? errorMessage : "未知错误",
                    "失败", "");
            heartbeatReflection.recordFailure(skillCode, actionType,
                    summarizeInput(inputParams), errorMessage);

            persistentLearning.recordFailedPattern(skillCode, actionType, errorMessage);
        }

        if (shouldExtractKnowledge(success, inputParams, outputResult)) {
            extractKnowledgeFromAction(skillCode, actionType, inputParams, outputResult, success, errorMessage);
        }

        log.info("统一记忆记录完成: skill={}, action={}", skillCode, actionType);
    }

    private void recordToCaseMemory(String skillCode, String actionType,
                                    Map<String, Object> inputParams,
                                    Map<String, Object> outputResult,
                                    boolean success, String errorMessage) {
        try {
            CaseMemory caseMemory = new CaseMemory();
            caseMemory.setCaseNo("CASE_" + skillCode + "_" + System.currentTimeMillis());
            caseMemory.setCaseType(success ? "success" : "fail");
            caseMemory.setProductName(stringFrom(inputParams, "productName"));
            caseMemory.setCategory(stringFrom(inputParams, "category"));
            caseMemory.setPlatform(stringFrom(inputParams, "platform"));
            caseMemory.setSkillVersionSnapshot(skillCode);

            Map<String, Object> enrichedInput = new HashMap<>(inputParams);
            enrichedInput.put("actionType", actionType);
            enrichedInput.put("timestamp", LocalDateTime.now().toString());
            caseMemory.setInputParams(objectMapper.writeValueAsString(enrichedInput));

            Map<String, Object> enrichedOutput = new HashMap<>();
            if (outputResult != null) {
                enrichedOutput.putAll(outputResult);
            }
            enrichedOutput.put("success", success);
            if (errorMessage != null) {
                enrichedOutput.put("errorMessage", errorMessage);
            }
            caseMemory.setOutputResult(objectMapper.writeValueAsString(enrichedOutput));

            caseMemory.setPlayCount(bigDecimalFrom(outputResult, "playCount"));
            caseMemory.setLikeCount(bigDecimalFrom(outputResult, "likeCount"));
            caseMemory.setCommentCount(bigDecimalFrom(outputResult, "commentCount"));
            caseMemory.setShareCount(bigDecimalFrom(outputResult, "shareCount"));
            caseMemory.setCollectCount(bigDecimalFrom(outputResult, "collectCount"));
            caseMemory.setGmv(bigDecimalFrom(outputResult, "gmv"));
            caseMemory.setCvr(doubleFrom(outputResult, "cvr"));
            caseMemory.setQualityTag(success ? "success" : "fail");
            caseMemory.setFailureReason(success ? null : errorMessage);
            caseMemory.setLessonLearned(success ? "自动记录成功案例" : "自动记录失败案例: " + errorMessage);
            caseMemory.setCreatedAt(LocalDateTime.now());
            caseMemory.setDeleted(0);

            caseMemoryMapper.insert(caseMemory);
            log.debug("案例记忆已写入: caseNo={}", caseMemory.getCaseNo());
        } catch (Exception e) {
            log.error("写入案例记忆失败", e);
        }
    }

    private boolean shouldExtractKnowledge(boolean success,
                                           Map<String, Object> inputParams,
                                           Map<String, Object> outputResult) {
        if (success && outputResult != null) {
            double cvrThreshold = sysConfigService.getDoubleConfig("knowledge_extraction_cvr_threshold", 0.05);
            double gmvThreshold = sysConfigService.getDoubleConfig("knowledge_extraction_gmv_threshold", 100);
            Double cvr = doubleFrom(outputResult, "cvr");
            if (cvr != null && cvr > cvrThreshold) {
                return true;
            }
            BigDecimal gmv = bigDecimalFrom(outputResult, "gmv");
            if (gmv != null && gmv.doubleValue() > gmvThreshold) {
                return true;
            }
        }
        return false;
    }

    private void extractKnowledgeFromAction(String skillCode, String actionType,
                                            Map<String, Object> inputParams,
                                            Map<String, Object> outputResult,
                                            boolean success, String errorMessage) {
        try {
            Map<String, Object> caseData = new HashMap<>();
            caseData.putAll(inputParams);
            if (outputResult != null) {
                caseData.putAll(outputResult);
            }
            caseData.put("skillCode", skillCode);
            caseData.put("actionType", actionType);
            caseData.put("caseType", success ? "success" : "failure");

            knowledgeService.extractFromCase(caseData, success ? "SUCCESS_EXPERIENCE" : "FAILURE_LESSON");
            log.info("从业务操作中提取知识: skill={}, action={}", skillCode, actionType);
        } catch (Exception e) {
            log.error("提取知识失败", e);
        }
    }

    public Map<String, Object> getRelevantContext(String query, int topK) {
        Map<String, Object> context = new HashMap<>();

        try {
            List<Knowledge> relevantKnowledge = knowledgeService.searchSimilar(query, topK);
            if (!relevantKnowledge.isEmpty()) {
                StringBuilder kb = new StringBuilder();
                for (Knowledge k : relevantKnowledge) {
                    kb.append("- ").append(k.getTitle()).append(": ").append(k.getContent(), 0,
                            Math.min(k.getContent().length(), 200)).append("...\n");
                }
                context.put("relevantKnowledge", kb.toString());
            }
        } catch (Exception e) {
            log.debug("获取相关知识失败", e);
        }

        try {
            SkillConfig bestSkill = skillConfigService.getBestPerformingSkill(query);
            if (bestSkill != null) {
                context.put("recommendedSkill", bestSkill.getSkillCode());
                context.put("skillParams", bestSkill.getConfigParams());
            }
        } catch (Exception e) {
            log.debug("获取推荐Skill失败", e);
        }

        try {
            List<PersistentLearningService.FailedPattern> patterns = persistentLearning.getFailedPatterns();
            if (!patterns.isEmpty()) {
                StringBuilder fp = new StringBuilder();
                for (PersistentLearningService.FailedPattern p : patterns.stream().limit(3).toList()) {
                    fp.append("- 避免: ").append(p.getDescription()).append(" (原因: ").append(p.getFailureReason()).append(")\n");
                }
                context.put("avoidanceRules", fp.toString());
            }
        } catch (Exception e) {
            log.debug("获取避坑规则失败", e);
        }

        try {
            List<Map<String, Object>> viralPatterns = heartbeatReflection.getViralPatterns();
            if (!viralPatterns.isEmpty()) {
                StringBuilder vp = new StringBuilder();
                for (Map<String, Object> p : viralPatterns.stream().limit(3).toList()) {
                    vp.append("- 爆款规律: ").append(p.getOrDefault("pattern_name", ""))
                            .append(" (置信度: ").append(p.getOrDefault("confidence", 0)).append(")\n");
                }
                context.put("viralPatterns", vp.toString());
            }
        } catch (Exception e) {
            log.debug("获取爆款规律失败", e);
        }

        try {
            List<ExpertRoleConfig> relevantExperts =
                    expertRoleService.getAllExpertRoles().stream()
                            .filter(config -> "ACTIVE".equals(config.getStatus()))
                            .filter(r -> {
                                List<String> keywords = expertRoleService.getExpertTriggerKeywords(r.getRoleCode());
                                return keywords != null && keywords.stream()
                                        .anyMatch(kw -> query.contains(kw));
                            })
                            .limit(3)
                            .toList();
            if (!relevantExperts.isEmpty()) {
                StringBuilder ep = new StringBuilder();
                for (ExpertRoleConfig expert : relevantExperts) {
                    List<String> dimensions = expertRoleService.getExpertCoreDimensions(expert.getRoleCode());
                    ep.append("- ").append(expert.getRoleName()).append(": ")
                            .append(dimensions != null ? String.join("、", dimensions) : "").append("\n");
                }
                context.put("expertPerspectives", ep.toString());
            }
        } catch (Exception e) {
            log.debug("获取专家视角失败", e);
        }

        return context;
    }

    private String summarizeInput(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "无输入参数";
        return params.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .limit(5)
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + ", " + b)
                .orElse("无输入参数");
    }

    private String summarizeOutput(Map<String, Object> result) {
        if (result == null || result.isEmpty()) return "无输出结果";
        return result.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .limit(5)
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + ", " + b)
                .orElse("无输出结果");
    }

    private Double outputParams(Map<String, Object> result, String key) {
        if (result == null) return null;
        Object val = result.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return null;
    }

    private BigDecimal outputParamsBigDecimal(Map<String, Object> result, String key) {
        if (result == null) return null;
        Object val = result.get(key);
        if (val instanceof BigDecimal) return (BigDecimal) val;
        if (val instanceof Number) return BigDecimal.valueOf(((Number) val).doubleValue());
        return null;
    }

    private Double doubleFrom(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return null;
    }

    private BigDecimal bigDecimalFrom(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object val = map.get(key);
        if (val instanceof BigDecimal) return (BigDecimal) val;
        if (val instanceof Number) return BigDecimal.valueOf(((Number) val).doubleValue());
        return null;
    }

    private String stringFrom(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object val = map.get(key);
        if (val == null) return null;
        // 避免 JSON null 节点转为 "null" 字符串
        String str = val.toString();
        if ("null".equals(str) || str.isEmpty()) {
            return null;
        }
        return str;
    }

    private double extractImprovement(Map<String, Object> result) {
        if (result == null) return 0;
        Double cvr = doubleFrom(result, "cvr");
        return cvr != null ? cvr : 0;
    }
}
