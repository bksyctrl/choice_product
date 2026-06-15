package com.ecommerce.workflow.service.video;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.workflow.entity.AvoidanceRule;
import com.ecommerce.workflow.entity.AiVideoConfig;
import com.ecommerce.workflow.mapper.AvoidanceRuleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VideoAvoidanceChecker {
    private static final Logger log = LoggerFactory.getLogger(VideoAvoidanceChecker.class);

    @Autowired
    private AvoidanceRuleMapper avoidanceRuleMapper;

    public Map<String, Object> checkBeforeGenerate(AiVideoConfig config) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> triggeredRules = new ArrayList<>();
        boolean hasBlocker = false;

        try {
            LambdaQueryWrapper<AvoidanceRule> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AvoidanceRule::getDeleted, 0)
                    .in(AvoidanceRule::getCategory, "视频", "video", "通用", "general")
                    .orderByDesc(AvoidanceRule::getSeverity);

            List<AvoidanceRule> allRules = avoidanceRuleMapper.selectList(wrapper);

            for (AvoidanceRule rule : allRules) {
                Map<String, Object> matchResult = checkRuleMatch(rule, config);
                if (matchResult != null) {
                    triggeredRules.add(matchResult);

                    if ("BLOCK".equals(matchResult.get("action"))) {
                        hasBlocker = true;
                    }

                    log.warn("触发避坑规则: rule={}, severity={}, action={}",
                            rule.getTitle(), rule.getSeverity(), matchResult.get("action"));
                }
            }

            result.put("passed", !hasBlocker);
            result.put("triggeredRules", triggeredRules);
            result.put("ruleCount", triggeredRules.size());
            result.put("hasBlocker", hasBlocker);

            if (hasBlocker) {
                String blockerMsg = triggeredRules.stream()
                        .filter(r -> "BLOCK".equals(r.get("action")))
                        .map(r -> (String) r.get("title"))
                        .collect(Collectors.joining("; "));
                result.put("blockerMessage", "以下避坑规则被触发，阻止生成: " + blockerMsg);
            }

            log.info("避坑检查完成: 触发规则数={}, 是否阻止={}", triggeredRules.size(), hasBlocker);

        } catch (Exception e) {
            log.error("避坑检查失败: {}", e.getMessage(), e);
            result.put("passed", true);
            result.put("error", "避坑检查失败，继续生成: " + e.getMessage());
        }

        return result;
    }

    private Map<String, Object> checkRuleMatch(AvoidanceRule rule, AiVideoConfig config) {
        String problemPattern = rule.getProblemPattern();
        if (problemPattern == null || problemPattern.isEmpty()) {
            return null;
        }

        problemPattern = problemPattern.toLowerCase();

        String topic = config.getTopic() != null ? config.getTopic().toLowerCase() : "";
        String scene = config.getScene() != null ? config.getScene().toLowerCase() : "";
        String sceneType = config.getSceneType() != null ? config.getSceneType().toLowerCase() : "";
        String role = config.getRole() != null ? config.getRole().toLowerCase() : "";

        boolean matched = false;

        if (problemPattern.contains(topic) || topic.contains(problemPattern)) {
            matched = true;
        }

        if (problemPattern.contains(scene) || scene.contains(problemPattern)) {
            matched = true;
        }

        if (problemPattern.contains(sceneType) || sceneType.contains(problemPattern)) {
            matched = true;
        }

        if (problemPattern.contains(role) || role.contains(problemPattern)) {
            matched = true;
        }

        String[] keywords = problemPattern.split("[,，;；|]");
        for (String keyword : keywords) {
            keyword = keyword.trim();
            if (!keyword.isEmpty()) {
                if (topic.contains(keyword) || scene.contains(keyword) ||
                        sceneType.contains(keyword) || role.contains(keyword)) {
                    matched = true;
                    break;
                }
            }
        }

        if (!matched) {
            return null;
        }

        Map<String, Object> matchResult = new HashMap<>();
        matchResult.put("ruleId", rule.getId());
        matchResult.put("title", rule.getTitle());
        matchResult.put("category", rule.getCategory());
        matchResult.put("problemPattern", rule.getProblemPattern());
        matchResult.put("solution", rule.getSolution());
        matchResult.put("prevention", rule.getPrevention());
        matchResult.put("severity", rule.getSeverity());

        if (rule.getSeverity() != null && rule.getSeverity() >= 3) {
            matchResult.put("action", "BLOCK");
        } else if (rule.getSeverity() != null && rule.getSeverity() >= 2) {
            matchResult.put("action", "WARN");
        } else {
            matchResult.put("action", "ADJUST");
        }

        return matchResult;
    }

    public void recordRuleApplication(Long ruleId, boolean effective) {
        try {
            AvoidanceRule rule = avoidanceRuleMapper.selectById(ruleId);
            if (rule != null) {
                rule.setApplyCount(rule.getApplyCount() != null ? rule.getApplyCount() + 1 : 1);
                if (effective) {
                    rule.setEffectiveCount(rule.getEffectiveCount() != null ? rule.getEffectiveCount() + 1 : 1);
                }
                avoidanceRuleMapper.updateById(rule);
                log.info("避坑规则应用记录更新: ruleId={}, effective={}", ruleId, effective);
            }
        } catch (Exception e) {
            log.error("更新避坑规则应用记录失败: ruleId={}", ruleId, e);
        }
    }
}
