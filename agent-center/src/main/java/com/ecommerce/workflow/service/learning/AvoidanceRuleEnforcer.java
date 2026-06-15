package com.ecommerce.workflow.service.learning;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ecommerce.workflow.service.memory.PersistentLearningService;
import com.ecommerce.workflow.service.memory.PersistentLearningService.FailedPattern;

@Service
public class AvoidanceRuleEnforcer {
    private static final Logger log = LoggerFactory.getLogger(AvoidanceRuleEnforcer.class);

    private final PersistentLearningService persistentLearningService;

    public AvoidanceRuleEnforcer(PersistentLearningService persistentLearningService) {
        this.persistentLearningService = persistentLearningService;
    }

    public void enforceAvoidanceRules(String skillCode, Map<String, Object> params) {
        log.info("技能执行避坑规则检查: {}", skillCode);

        try {
            List<FailedPattern> rules = getRelevantAvoidanceRules(skillCode);

            if (rules.isEmpty()) {
                log.info("没有找到相关避坑规则");
                return;
            }

            List<FailedPattern> violations = checkViolations(params, rules);

            if (!violations.isEmpty()) {
                log.warn("检测到避坑规则违规: {}", violations.size());

                FailedPattern violation = violations.get(0);

                recordAvoidanceCheck(skillCode, params, violations);

                throw new AvoidanceRuleViolationException(
                    "技能参数触发避坑规则: " + violation.getDescription()
                );
            }

            recordAvoidanceCheck(skillCode, params, violations);

            log.info("避坑规则检查通过");

        } catch (AvoidanceRuleViolationException e) {
            throw e;
        } catch (Exception e) {
            log.error("避坑规则检查失败", e);
        }
    }

    private List<FailedPattern> getRelevantAvoidanceRules(String skillCode) {
        try {
            List<FailedPattern> allPatterns = persistentLearningService.getFailedPatterns();

            return allPatterns.stream()
                .filter(p -> p.getOccurrenceCount() >= 3)
                .sorted((p1, p2) -> Integer.compare(
                    p2.getOccurrenceCount(),
                    p1.getOccurrenceCount()
                ))
                .toList();

        } catch (Exception e) {
            log.error("获取避坑规则失败", e);
            return List.of();
        }
    }

    private List<FailedPattern> checkViolations(Map<String, Object> params, List<FailedPattern> rules) {
        return rules.stream()
            .filter(rule -> isViolation(params, rule))
            .toList();
    }

    private boolean isViolation(Map<String, Object> params, FailedPattern rule) {
        try {
            String description = rule.getDescription();

            if (description == null) {
                return false;
            }

            if (description.contains("失败") && params.containsKey("qualityTag")) {
                if ("FAIL".equals(params.get("qualityTag"))) {
                    return true;
                }
            }

            if (description.contains("低ROI") && params.containsKey("roi")) {
                double roi = ((Number) params.get("roi")).doubleValue();
                if (roi < 0.5) {
                    return true;
                }
            }

            if (description.contains("低CVR") && params.containsKey("cvr")) {
                double cvr = ((Number) params.get("cvr")).doubleValue();
                if (cvr < 1.0) {
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            log.error("检查违规失败", e);
            return false;
        }
    }

    private void recordAvoidanceCheck(String skillCode, Map<String, Object> params,
                                      List<FailedPattern> violations) {
        try {
            Map<String, Object> checkRecord = new HashMap<>();
            checkRecord.put("skillCode", skillCode);
            checkRecord.put("checkTime", LocalDateTime.now());
            checkRecord.put("hasViolation", !violations.isEmpty());
            checkRecord.put("violationCount", violations.size());

            if (!violations.isEmpty()) {
                checkRecord.put("violations", violations.stream()
                    .map(FailedPattern::getDescription)
                    .toList());
            }

            log.info("避坑检查记录: {}", checkRecord);

        } catch (Exception e) {
            log.error("记录避坑检查失败", e);
        }
    }

    public Map<String, Object> getAvoidanceStatistics() {
        Map<String, Object> stats = new HashMap<>();

        try {
            stats.put("totalChecks", 0);
            stats.put("passedChecks", 0);
            stats.put("blockedChecks", 0);
            stats.put("blockRate", 0.0);
            stats.put("lastCheckTime", LocalDateTime.now());

        } catch (Exception e) {
            log.error("获取避坑统计失败", e);
        }

        return stats;
    }

    public static class AvoidanceRuleViolationException extends RuntimeException {
        public AvoidanceRuleViolationException(String message) {
            super(message);
        }
    }
}
