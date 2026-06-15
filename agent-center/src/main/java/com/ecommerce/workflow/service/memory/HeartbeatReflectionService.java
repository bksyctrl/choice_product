package com.ecommerce.workflow.service.memory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ecommerce.workflow.entity.ExpertRoleConfig;
import com.ecommerce.workflow.service.ai.GptChatService;
import com.ecommerce.workflow.service.config.SysConfigService;
import com.ecommerce.workflow.service.knowledge.KnowledgeService;
import com.ecommerce.workflow.service.learning.ExpertRoleService;
import com.ecommerce.workflow.service.memory.PersistentLearningService.EffectiveStrategy;
import com.ecommerce.workflow.service.memory.PersistentLearningService.FailedPattern;

@Service
public class HeartbeatReflectionService {
    private static final Logger log = LoggerFactory.getLogger(HeartbeatReflectionService.class);

    private final PersistentLearningService learningService;
    private final WhiteBoxMemoryService whiteBoxMemory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Lazy
    @Autowired
    private GptChatService gptChatService;

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private ExpertRoleService expertRoleService;

    private boolean enabled = true;
    private int intervalMinutes = 30;
    private boolean viralExtractionEnabled = true;

    private LocalDateTime lastReflectionTime;
    private String lastReflectionResult;
    private int reflectionCount = 0;
    private int viralExtractionCount = 0;

    public HeartbeatReflectionService(PersistentLearningService learningService,
                                       WhiteBoxMemoryService whiteBoxMemory) {
        this.learningService = learningService;
        this.whiteBoxMemory = whiteBoxMemory;
    }

    @Scheduled(fixedRateString = "${heartbeat.reflection.interval-ms:1800000}")
    public void performHeartbeatReflection() {
        loadConfigFromDb();
        if (!enabled) return;

        log.info("=== 开始心跳反思 ===");
        reflectionCount++;
        lastReflectionTime = LocalDateTime.now();

        StringBuilder reflection = new StringBuilder();
        reflection.append("# 心跳反思报告\n\n");
        reflection.append("时间: ").append(lastReflectionTime.format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        reflection.append("第").append(reflectionCount).append(" 次反思\n\n");

        analyzeLearningProgress(reflection);
        analyzeFailedPatterns(reflection);
        analyzeEffectiveStrategies(reflection);
        analyzeViralPatterns(reflection);
        generateActionItems(reflection);

        lastReflectionResult = reflection.toString();

        whiteBoxMemory.recordDailyLog("## 心跳反思报告\n" + reflection);

        if (viralExtractionEnabled) {
            extractViralPatternsFromCases();
        }

        log.info("=== 心跳反思完成 ===");
    }

    private void loadConfigFromDb() {
        try {
            enabled = sysConfigService.getBooleanConfig("reflection_enabled", true);
            intervalMinutes = sysConfigService.getIntConfig("reflection_interval_ms", 1800000) / 60000;
            viralExtractionEnabled = sysConfigService.getBooleanConfig("viral_extraction_enabled", true);
        } catch (Exception e) {
            log.debug("加载心跳配置失败，使用默认值", e);
        }
    }

    private void analyzeLearningProgress(StringBuilder reflection) {
        Map<String, Object> stats = learningService.getLearningStats();

        reflection.append("## 学习进度概览\n\n");
        reflection.append("- 有效策略数: ").append(stats.get("totalStrategies")).append("\n");
        reflection.append("- 记忆总数: ").append(stats.get("totalMemories")).append("\n");
        reflection.append("- 失败模式数: ").append(stats.get("totalFailedPatterns")).append("\n");

        double successRate = 0.0;
        Object rateObj = stats.get("successRate");
        if (rateObj instanceof Number) {
            successRate = ((Number) rateObj).doubleValue();
        } else if (rateObj != null) {
            try {
                successRate = Double.parseDouble(rateObj.toString());
            } catch (NumberFormatException ignored) {}
        }
        reflection.append("- 整体成功率: ").append(String.format("%.1f%%", successRate)).append("\n\n");

        analyzeVideoGenerationStats(reflection);

        if (successRate < 50) {
            reflection.append("**警告**: 成功率偏低，建议检查策略配置和参数设置\n\n");
        } else if (successRate > 80) {
            reflection.append("**良好**: 学习效果显著，继续保持\n\n");
        }

        analyzeExpertRoleCoverage(reflection);
    }

    private void analyzeVideoGenerationStats(StringBuilder reflection) {
        try {
            Integer totalTasks = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM biz_video_task", Integer.class);
            if (totalTasks == null || totalTasks == 0) {
                reflection.append("## 视频生成统计\n\n");
                reflection.append("暂无视频生成记录\n\n");
                return;
            }

            Integer completedTasks = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM biz_video_task WHERE status = 'completed'", Integer.class);
            Integer failedTasks = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM biz_video_task WHERE status = 'failed'", Integer.class);

            double videoSuccessRate = totalTasks > 0 ? (completedTasks * 100.0 / totalTasks) : 0;

            reflection.append("## 视频生成统计\n\n");
            reflection.append("- 总任务数: ").append(totalTasks).append("\n");
            reflection.append("- 成功: ").append(completedTasks).append("\n");
            reflection.append("- 失败: ").append(failedTasks).append("\n");
            reflection.append("- 成功率: ").append(String.format("%.1f%%", videoSuccessRate)).append("\n\n");

            List<Map<String, Object>> topFailures = jdbcTemplate.queryForList(
                    "SELECT error_message, COUNT(*) as cnt FROM biz_video_task " +
                    "WHERE status = 'failed' AND error_message IS NOT NULL " +
                    "GROUP BY error_message ORDER BY cnt DESC LIMIT 5");

            if (!topFailures.isEmpty()) {
                reflection.append("### 常见失败原因\n\n");
                for (Map<String, Object> failure : topFailures) {
                    String errorMsg = (String) failure.get("error_message");
                    Long count = ((Number) failure.get("cnt")).longValue();
                    if (errorMsg != null && errorMsg.length() > 100) {
                        errorMsg = errorMsg.substring(0, 100) + "...";
                    }
                    reflection.append("- ").append(errorMsg).append(" (").append(count).append("次)\n");
                }
                reflection.append("\n");
            }

            List<Map<String, Object>> topTopics = jdbcTemplate.queryForList(
                    "SELECT topic, COUNT(*) as cnt FROM ai_video_config " +
                    "WHERE id IN (SELECT config_id FROM biz_video_task WHERE status = 'completed') " +
                    "GROUP BY topic ORDER BY cnt DESC LIMIT 5");

            if (!topTopics.isEmpty()) {
                reflection.append("### 热门主题\n\n");
                for (Map<String, Object> topic : topTopics) {
                    reflection.append("- ").append(topic.get("topic")).append(" (").append(topic.get("cnt")).append("次)\n");
                }
                reflection.append("\n");
            }

        } catch (Exception e) {
            reflection.append("视频生成统计暂时不可用\n\n");
            log.debug("视频生成统计分析失败", e);
        }
    }

    private void analyzeExpertRoleCoverage(StringBuilder reflection) {
        reflection.append("## 专家角色覆盖度\n\n");
        try {
            List<ExpertRoleConfig> roles = expertRoleService.getAllExpertRoles();
            reflection.append("已激活角色: ").append(roles.stream().filter(r -> "ACTIVE".equals(r.getStatus())).count())
                    .append("/").append(roles.size()).append("\n");
            for (ExpertRoleConfig role : roles) {
                String status = "ACTIVE".equals(role.getStatus()) ? "✅" : "❌";
                reflection.append("- ").append(status).append(" ").append(role.getRoleName())
                        .append("\n");
            }
            reflection.append("\n");
        } catch (Exception e) {
            reflection.append("专家角色分析暂时不可用\n\n");
            log.debug("专家角色覆盖度分析失败", e);
        }
    }

    private void analyzeFailedPatterns(StringBuilder reflection) {
        List<FailedPattern> patterns = learningService.getFailedPatterns();

        if (patterns.isEmpty()) {
            reflection.append("## 失败模式概览\n\n");
            reflection.append("暂无失败模式记录\n\n");
            return;
        }

        reflection.append("## 失败模式概览\n\n");
        reflection.append("共发现").append(patterns.size()).append(" 个高频失败模式\n");

        patterns.stream()
                .sorted((a, b) -> Integer.compare(b.getOccurrenceCount(), a.getOccurrenceCount()))
                .limit(5)
                .forEach(p -> {
                    reflection.append("### ").append(p.getIssueType()).append("\n");
                    reflection.append("- 问题描述: ").append(p.getDescription()).append("\n");
                    reflection.append("- 失败原因: ").append(p.getFailureReason()).append("\n");
                    reflection.append("- 发生次数: ").append(p.getOccurrenceCount()).append("\n\n");
                });
    }

    private void analyzeEffectiveStrategies(StringBuilder reflection) {
        Map<String, EffectiveStrategy> strategies = learningService.getAllStrategies();

        reflection.append("## 有效策略概览\n\n");

        if (strategies.isEmpty()) {
            reflection.append("暂无有效策略记录\n\n");
            return;
        }

        strategies.entrySet().stream()
                .filter(e -> e.getValue().getSuccessCount() > 2)
                .sorted((e1, e2) -> Double.compare(
                        e2.getValue().getAvgImprovement(),
                        e1.getValue().getAvgImprovement()))
                .limit(5)
                .forEach(e -> {
                    EffectiveStrategy s = e.getValue();
                    reflection.append("### ").append(s.getSkillCode()).append("\n");
                    reflection.append("- 策略类型: ").append(s.getStrategyType()).append("\n");
                    reflection.append("- 平均提升: ").append(String.format("%.2f", s.getAvgImprovement())).append("\n");
                    reflection.append("- 成功/失败: ").append(s.getSuccessCount()).append("/").append(s.getFailCount()).append("\n\n");
                });
    }

    private void analyzeViralPatterns(StringBuilder reflection) {
        reflection.append("## 爆款规律分析\n\n");

        try {
            List<Map<String, Object>> topCases = jdbcTemplate.queryForList(
                    "SELECT * FROM biz_case_memory WHERE case_type = 'success' AND quality_tag = 'success' " +
                    "ORDER BY play_count DESC, like_count DESC LIMIT 10");

            if (topCases.isEmpty()) {
                reflection.append("暂无爆款案例数据\n\n");
                return;
            }

            reflection.append("分析Top").append(topCases.size()).append("成功案例:\n\n");

            Map<String, Integer> platformDist = new HashMap<>();
            Map<String, Integer> categoryDist = new HashMap<>();
            double avgCvr = 0;
            int cvrCount = 0;

            for (Map<String, Object> c : topCases) {
                String platform = (String) c.getOrDefault("platform", "unknown");
                String category = (String) c.getOrDefault("category", "unknown");
                platformDist.merge(platform, 1, Integer::sum);
                categoryDist.merge(category, 1, Integer::sum);

                if (c.get("cvr") != null) {
                    try {
                        avgCvr += Double.parseDouble(c.get("cvr").toString());
                        cvrCount++;
                    } catch (NumberFormatException ignored) {}
                }
            }

            if (cvrCount > 0) {
                avgCvr /= cvrCount;
                reflection.append("- 平均转化率: ").append(String.format("%.2f%%", avgCvr * 100)).append("\n");
            }

            String topPlatform = platformDist.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse("无");
            reflection.append("- 最成功平台: ").append(topPlatform).append("\n");

            String topCategory = categoryDist.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse("无");
            reflection.append("- 最成功品类: ").append(topCategory).append("\n\n");

        } catch (Exception e) {
            reflection.append("爆款规律分析暂时不可用\n\n");
            log.debug("爆款规律分析失败", e);
        }
    }

    private void extractViralPatternsFromCases() {
        try {
            double cvrThreshold = sysConfigService.getDoubleConfig("viral_cvr_threshold", 0.03);
            int minCases = sysConfigService.getIntConfig("viral_min_cases", 3);

            List<Map<String, Object>> successCases = jdbcTemplate.queryForList(
                    "SELECT * FROM biz_case_memory WHERE case_type = 'success' " +
                    "AND quality_tag = 'success' AND cvr > ? ORDER BY cvr DESC LIMIT 20", cvrThreshold);

            if (successCases.size() < minCases) {
                log.debug("成功案例不足{}个，跳过爆款规律提取", minCases);
                return;
            }

            StringBuilder caseSummary = new StringBuilder();
            for (Map<String, Object> c : successCases) {
                caseSummary.append(String.format(
                        "平台:%s, 品类:%s, CVR:%s, 播放:%s, 点赞:%s, 经验:%s\n",
                        c.getOrDefault("platform", ""),
                        c.getOrDefault("category", ""),
                        c.getOrDefault("cvr", "0"),
                        c.getOrDefault("play_count", "0"),
                        c.getOrDefault("like_count", "0"),
                        c.getOrDefault("lesson_learned", "")
                ));
            }

            List<ExpertRoleConfig> relevantExperts =
                    expertRoleService.getAllExpertRoles().stream()
                            .filter(r -> "ACTIVE".equals(r.getStatus()))
                            .filter(r -> {
                                List<String> keywords = expertRoleService.getExpertTriggerKeywords(r.getRoleCode());
                                return keywords != null && keywords.stream()
                                        .anyMatch(kw -> caseSummary.toString().contains(kw));
                            })
                            .limit(3)
                            .toList();

            if (relevantExperts.isEmpty()) {
                relevantExperts = expertRoleService.getAllExpertRoles().stream()
                        .filter(r -> "ACTIVE".equals(r.getStatus()))
                        .limit(2)
                        .toList();
            }

            StringBuilder expertContext = new StringBuilder();
            for (ExpertRoleConfig expert : relevantExperts) {
                List<String> dimensions = expertRoleService.getExpertCoreDimensions(expert.getRoleCode());
                expertContext.append(String.format("\n【%s视角分析维度】%s\n",
                        expert.getRoleName(),
                        dimensions != null ? String.join("。", dimensions) : ""));
            }

            String extractionPrompt = String.format("""
                    你是电商爆款规律分析专家。请从以下成功案例中提取爆款规律，返回JSON格式:
                    {
                      "viral_patterns": [
                        {
                          "pattern_name": "规律名称",
                          "description": "规律描述",
                          "conditions": "触发条件",
                          "key_factors": ["关键因素1", "关键因素2"],
                          "confidence": 0.85,
                          "applicable_scenarios": "适用场景"
                        }
                      ],
                      "summary": "总体规律总结"
                    }
                    
                    %s
                    
                    成功案例数据:
                    %s
                    """, expertContext.toString(), caseSummary.toString());

            String response = gptChatService.chat(extractionPrompt, "提取爆款规律");

            try {
                Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(response, Map.class);
                List<Map<String, Object>> patterns = (List<Map<String, Object>>) parsed.get("viral_patterns");

                if (patterns != null) {
                    for (Map<String, Object> pattern : patterns) {
                        saveViralPattern(pattern);
                    }
                    viralExtractionCount++;
                    log.info("爆款规律提取完成: 发现{}条规律, 使用{}个专家角色", patterns.size(), relevantExperts.size());
                }
            } catch (Exception e) {
                log.debug("解析爆款规律结果失败", e);
            }

        } catch (Exception e) {
            log.error("爆款规律自动提取失败", e);
        }
    }

    private void saveViralPattern(Map<String, Object> pattern) {
        try {
            jdbcTemplate.update("""
                CREATE TABLE IF NOT EXISTS sys_viral_pattern (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    pattern_name VARCHAR(200),
                    description TEXT,
                    conditions TEXT,
                    key_factors TEXT,
                    confidence DOUBLE DEFAULT 0.0,
                    applicable_scenarios TEXT,
                    occurrence_count INT DEFAULT 1,
                    last_verified TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_confidence (confidence),
                    INDEX idx_created (created_at)
                )
                """);

            String patternName = (String) pattern.getOrDefault("pattern_name", "未命名规律");
            String existingId = null;
            try {
                Map<String, Object> existing = jdbcTemplate.queryForMap(
                        "SELECT id, occurrence_count FROM sys_viral_pattern WHERE pattern_name = ? LIMIT 1",
                        patternName);
                existingId = existing.get("id").toString();
                int count = ((Number) existing.get("occurrence_count")).intValue() + 1;
                jdbcTemplate.update(
                        "UPDATE sys_viral_pattern SET occurrence_count = ?, last_verified = NOW(), confidence = ? WHERE id = ?",
                        count, pattern.getOrDefault("confidence", 0.5), existingId);
                return;
            } catch (Exception ignored) {}

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            jdbcTemplate.update("""
                INSERT INTO sys_viral_pattern (pattern_name, description, conditions, key_factors, confidence, applicable_scenarios, occurrence_count, last_verified, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 1, NOW(), NOW())
                """, patternName,
                    pattern.getOrDefault("description", ""),
                    pattern.getOrDefault("conditions", ""),
                    pattern.get("key_factors") != null ? mapper.writeValueAsString(pattern.get("key_factors")) : "[]",
                    pattern.get("confidence") != null ? pattern.get("confidence") : 0.5,
                    pattern.getOrDefault("applicable_scenarios", ""));

            knowledgeService.extractFromCase(Map.of(
                    "patternName", patternName,
                    "description", pattern.getOrDefault("description", ""),
                    "type", "VIRAL_PATTERN"
            ), "VIRAL_PATTERN");

        } catch (Exception e) {
            log.debug("保存爆款规律失败", e);
        }
    }

    private void generateActionItems(StringBuilder reflection) {
        reflection.append("## 行动项\n\n");

        List<FailedPattern> patterns = learningService.getFailedPatterns();
        long highFrequencyFailures = patterns.stream()
                .filter(p -> p.getOccurrenceCount() >= 3)
                .count();

        if (highFrequencyFailures > 0) {
            reflection.append("- [ ] 处理").append(highFrequencyFailures).append(" 个高频失败模式\n");
        }

        Map<String, Object> stats = learningService.getLearningStats();
        double successRate = 0.0;
        Object rateObj = stats.get("successRate");
        if (rateObj instanceof Number) {
            successRate = ((Number) rateObj).doubleValue();
        } else if (rateObj != null) {
            try {
                successRate = Double.parseDouble(rateObj.toString());
            } catch (NumberFormatException ignored) {}
        }
        if (successRate < 60) {
            reflection.append("- [ ] 优化策略参数以提高整体成功率\n");
        }

        reflection.append("- [ ] 定期回顾有效策略的适用范围\n");
        reflection.append("- [ ] 更新失败模式库并优化避坑规则\n");

        try {
            Long unverifiedPatterns = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_viral_pattern WHERE occurrence_count = 1", Long.class);
            if (unverifiedPatterns != null && unverifiedPatterns > 0) {
                reflection.append("- [ ] 验证").append(unverifiedPatterns).append(" 条待验证爆款规律\n");
            }
        } catch (Exception ignored) {}
    }

    public void recordUserCorrection(String scenario, String originalOutput,
                                      String userCorrection, String learningPoint) {
        whiteBoxMemory.recordLearning("CORRECTION", scenario,
                originalOutput, userCorrection, learningPoint, "");

        learningService.recordFailedPattern("USER_CORRECTION", scenario,
                "原始输出: " + originalOutput + " -> 用户修正: " + userCorrection);

        log.info("用户修正已记录: {}", scenario);
    }

    public void recordSuccess(String skillCode, String scenario,
                               String solution, String effect) {
        whiteBoxMemory.recordLearning("SUCCESS", scenario,
                "技能:" + skillCode, solution, effect, "");
        whiteBoxMemory.updateSkillUsage(skillCode, true);
    }

    public void recordFailure(String skillCode, String scenario,
                               String attempt, String reason) {
        whiteBoxMemory.recordLearning("FAILURE", scenario,
                "技能:" + skillCode, attempt, reason, "");
        whiteBoxMemory.updateSkillUsage(skillCode, false);
        learningService.recordFailedPattern(skillCode, scenario, reason);
    }

    public LocalDateTime getLastReflectionTime() {
        return lastReflectionTime;
    }

    public String getLastReflectionResult() {
        return lastReflectionResult != null ? lastReflectionResult : "暂无心跳反思结果";
    }

    public int getReflectionCount() {
        return reflectionCount;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getViralExtractionCount() {
        return viralExtractionCount;
    }

    public List<Map<String, Object>> getViralPatterns() {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT * FROM sys_viral_pattern ORDER BY confidence DESC, occurrence_count DESC LIMIT 20");
        } catch (Exception e) {
            return List.of();
        }
    }
}
