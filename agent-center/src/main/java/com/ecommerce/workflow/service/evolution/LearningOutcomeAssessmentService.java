package com.ecommerce.workflow.service.evolution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class LearningOutcomeAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(LearningOutcomeAssessmentService.class);

    private final JdbcTemplate jdbcTemplate;

    public LearningOutcomeAssessmentService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedRateString = "${learning.assessment.interval-ms:86400000}")
    public void assessLearningOutcomes() {
        log.info("=== 开始学习成果量化评估 ===");

        Map<String, Object> assessment = new HashMap<>();

        assessment.put("qTableLearning", assessQTableLearning());
        assessment.put("skillEvolution", assessSkillEvolution());
        assessment.put("templateLearning", assessTemplateLearning());
        assessment.put("expertLearning", assessExpertLearning());
        assessment.put("knowledgeExtraction", assessKnowledgeExtraction());
        assessment.put("mcpOptimization", assessMcpOptimization());
        assessment.put("overallLearningDepth", calculateOverallLearningDepth(assessment));

        persistAssessmentResults(assessment);

        log.info("=== 学习成果量化评估完成 ===");
        log.info("总体学习深度评分: {}/100", assessment.get("overallLearningDepth"));
    }

    private Map<String, Object> assessQTableLearning() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            Integer qTableSize = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_q_table", Integer.class);
            metrics.put("qTableSize", qTableSize != null ? qTableSize : 0);

            Double avgQValue = jdbcTemplate.queryForObject(
                    "SELECT AVG(q_value) FROM sys_q_table WHERE q_value > 0", Double.class);
            metrics.put("avgQValue", avgQValue != null ? avgQValue : 0.0);

            Integer highQValueCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_q_table WHERE q_value > 0.7", Integer.class);
            metrics.put("highQValueCount", highQValueCount != null ? highQValueCount : 0);

            Integer videoTasksWithOptimization = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM biz_video_task WHERE q_table_applied = 1", Integer.class);
            metrics.put("videoTasksWithOptimization", videoTasksWithOptimization != null ? videoTasksWithOptimization : 0);

            double depthScore = calculateQTableDepthScore(metrics);
            metrics.put("depthScore", depthScore);

            log.info("Q-Table学习评估: 大小={}, 平均Q值={:.3f}, 高价值Q值={}, 已应用优化任务={}, 深度评分={:.1f}/100",
                    metrics.get("qTableSize"), metrics.get("avgQValue"), metrics.get("highQValueCount"),
                    metrics.get("videoTasksWithOptimization"), depthScore);

        } catch (Exception e) {
            log.error("Q-Table学习评估失败", e);
            metrics.put("depthScore", 0.0);
        }

        return metrics;
    }

    private Map<String, Object> assessSkillEvolution() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            Integer totalEvolutions = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_skill_evolution_log", Integer.class);
            metrics.put("totalEvolutions", totalEvolutions != null ? totalEvolutions : 0);

            Integer successfulEvolutions = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_skill_evolution_log WHERE status = 'VALIDATED'", Integer.class);
            metrics.put("successfulEvolutions", successfulEvolutions != null ? successfulEvolutions : 0);

            Integer rolledBackEvolutions = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_skill_evolution_log WHERE is_rolled_back = 1", Integer.class);
            metrics.put("rolledBackEvolutions", rolledBackEvolutions != null ? rolledBackEvolutions : 0);

            Double avgImprovement = jdbcTemplate.queryForObject(
                    "SELECT AVG(improvement_pct) FROM sys_skill_evolution_log " +
                    "WHERE status = 'VALIDATED' AND metric_before IS NOT NULL AND metric_after IS NOT NULL", Double.class);
            metrics.put("avgImprovement", avgImprovement != null ? avgImprovement : 0.0);

            double depthScore = calculateSkillEvolutionDepthScore(metrics);
            metrics.put("depthScore", depthScore);

            log.info("Skill进化评估: 总进化数={}, 成功={}, 回滚={}, 平均提升={:.1f}%, 深度评分={:.1f}/100",
                    metrics.get("totalEvolutions"), metrics.get("successfulEvolutions"),
                    metrics.get("rolledBackEvolutions"), ((Double)metrics.get("avgImprovement")) * 100, depthScore);

        } catch (Exception e) {
            log.error("Skill进化评估失败", e);
            metrics.put("depthScore", 0.0);
        }

        return metrics;
    }

    private Map<String, Object> assessTemplateLearning() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            Integer totalTemplates = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM video_prompt_template", Integer.class);
            metrics.put("totalTemplates", totalTemplates != null ? totalTemplates : 0);

            Double avgSuccessRate = jdbcTemplate.queryForObject(
                    "SELECT AVG(success_rate) FROM video_prompt_template WHERE usage_count > 0", Double.class);
            metrics.put("avgSuccessRate", avgSuccessRate != null ? avgSuccessRate : 0.0);

            Integer highPerformingTemplates = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM video_prompt_template WHERE success_rate > 0.8 AND usage_count > 10", Integer.class);
            metrics.put("highPerformingTemplates", highPerformingTemplates != null ? highPerformingTemplates : 0);

            Integer abTestsPassed = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_ab_test WHERE test_type = 'template_evolution' AND status = 'PASSED'", Integer.class);
            metrics.put("abTestsPassed", abTestsPassed != null ? abTestsPassed : 0);

            double depthScore = calculateTemplateLearningDepthScore(metrics);
            metrics.put("depthScore", depthScore);

            log.info("模板学习评估: 总模板数={}, 平均成功率={:.1f}%, 高性能模板={}, A/B测试通过={}, 深度评分={:.1f}/100",
                    metrics.get("totalTemplates"), ((Double)metrics.get("avgSuccessRate")) * 100,
                    metrics.get("highPerformingTemplates"), metrics.get("abTestsPassed"), depthScore);

        } catch (Exception e) {
            log.error("模板学习评估失败", e);
            metrics.put("depthScore", 0.0);
        }

        return metrics;
    }

    private Map<String, Object> assessExpertLearning() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            Integer totalExpertLearnings = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_expert_learning_log", Integer.class);
            metrics.put("totalExpertLearnings", totalExpertLearnings != null ? totalExpertLearnings : 0);

            Integer templateUpdatesFromExperts = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_prompt_template_update_log WHERE source = 'EXPERT_LEARNING'", Integer.class);
            metrics.put("templateUpdatesFromExperts", templateUpdatesFromExperts != null ? templateUpdatesFromExperts : 0);

            Integer knowledgeFromExperts = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_knowledge WHERE type = 'EXPERT_LEARNING' AND status = 'ACTIVE'", Integer.class);
            metrics.put("knowledgeFromExperts", knowledgeFromExperts != null ? knowledgeFromExperts : 0);

            double depthScore = calculateExpertLearningDepthScore(metrics);
            metrics.put("depthScore", depthScore);

            log.info("专家学习评估: 总学习数={}, 模板更新数={}, 知识提取数={}, 深度评分={:.1f}/100",
                    metrics.get("totalExpertLearnings"), metrics.get("templateUpdatesFromExperts"),
                    metrics.get("knowledgeFromExperts"), depthScore);

        } catch (Exception e) {
            log.error("专家学习评估失败", e);
            metrics.put("depthScore", 0.0);
        }

        return metrics;
    }

    private Map<String, Object> assessKnowledgeExtraction() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            Integer totalKnowledge = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_knowledge WHERE status = 'ACTIVE'", Integer.class);
            metrics.put("totalKnowledge", totalKnowledge != null ? totalKnowledge : 0);

            Integer explosiveRules = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM explosive_rule WHERE is_active = 1", Integer.class);
            metrics.put("explosiveRules", explosiveRules != null ? explosiveRules : 0);

            Integer avoidanceRules = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM avoidance_rule WHERE is_active = 1", Integer.class);
            metrics.put("avoidanceRules", avoidanceRules != null ? avoidanceRules : 0);

            Integer knowledgeAppliedTasks = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM biz_video_task WHERE knowledge_applied = 1", Integer.class);
            metrics.put("knowledgeAppliedTasks", knowledgeAppliedTasks != null ? knowledgeAppliedTasks : 0);

            double depthScore = calculateKnowledgeExtractionDepthScore(metrics);
            metrics.put("depthScore", depthScore);

            log.info("知识提取评估: 总知识={}, 爆款规则={}, 避坑规则={}, 已应用知识任务={}, 深度评分={:.1f}/100",
                    metrics.get("totalKnowledge"), metrics.get("explosiveRules"),
                    metrics.get("avoidanceRules"), metrics.get("knowledgeAppliedTasks"), depthScore);

        } catch (Exception e) {
            log.error("知识提取评估失败", e);
            metrics.put("depthScore", 0.0);
        }

        return metrics;
    }

    private Map<String, Object> assessMcpOptimization() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            Integer totalMcpServers = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM mcp_server", Integer.class);
            metrics.put("totalMcpServers", totalMcpServers != null ? totalMcpServers : 0);

            Integer activeMcpServers = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM mcp_server WHERE status = 'active'", Integer.class);
            metrics.put("activeMcpServers", activeMcpServers != null ? activeMcpServers : 0);

            Integer autoConfiguredServers = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM mcp_server WHERE auto_configured = 1", Integer.class);
            metrics.put("autoConfiguredServers", autoConfiguredServers != null ? autoConfiguredServers : 0);

            Integer mcpToolsTriggered = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_mcp_tool_trigger_log", Integer.class);
            metrics.put("mcpToolsTriggered", mcpToolsTriggered != null ? mcpToolsTriggered : 0);

            double depthScore = calculateMcpOptimizationDepthScore(metrics);
            metrics.put("depthScore", depthScore);

            log.info("MCP优化评估: 总服务器={}, 活跃={}, 自动配置={}, 工具触发={}, 深度评分={:.1f}/100",
                    metrics.get("totalMcpServers"), metrics.get("activeMcpServers"),
                    metrics.get("autoConfiguredServers"), metrics.get("mcpToolsTriggered"), depthScore);

        } catch (Exception e) {
            log.error("MCP优化评估失败", e);
            metrics.put("depthScore", 0.0);
        }

        return metrics;
    }

    private double calculateQTableDepthScore(Map<String, Object> metrics) {
        double score = 0.0;

        Integer qTableSize = (Integer) metrics.get("qTableSize");
        if (qTableSize != null && qTableSize > 0) {
            score += Math.min(30, qTableSize * 0.3);
        }

        Double avgQValue = (Double) metrics.get("avgQValue");
        if (avgQValue != null && avgQValue > 0) {
            score += avgQValue * 30;
        }

        Integer videoTasksWithOptimization = (Integer) metrics.get("videoTasksWithOptimization");
        if (videoTasksWithOptimization != null && videoTasksWithOptimization > 0) {
            score += Math.min(40, videoTasksWithOptimization * 2);
        }

        return Math.min(100, score);
    }

    private double calculateSkillEvolutionDepthScore(Map<String, Object> metrics) {
        double score = 0.0;

        Integer totalEvolutions = (Integer) metrics.get("totalEvolutions");
        if (totalEvolutions != null && totalEvolutions > 0) {
            score += Math.min(25, totalEvolutions * 5);
        }

        Integer successfulEvolutions = (Integer) metrics.get("successfulEvolutions");
        Integer rolledBackEvolutions = (Integer) metrics.get("rolledBackEvolutions");
        if (successfulEvolutions != null && successfulEvolutions > 0) {
            int total = successfulEvolutions + (rolledBackEvolutions != null ? rolledBackEvolutions : 0);
            double successRate = total > 0 ? (successfulEvolutions * 1.0 / total) : 0;
            score += successRate * 35;
        }

        Double avgImprovement = (Double) metrics.get("avgImprovement");
        if (avgImprovement != null && avgImprovement > 0) {
            score += Math.min(40, avgImprovement * 400);
        }

        return Math.min(100, score);
    }

    private double calculateTemplateLearningDepthScore(Map<String, Object> metrics) {
        double score = 0.0;

        Double avgSuccessRate = (Double) metrics.get("avgSuccessRate");
        if (avgSuccessRate != null && avgSuccessRate > 0) {
            score += avgSuccessRate * 40;
        }

        Integer highPerformingTemplates = (Integer) metrics.get("highPerformingTemplates");
        if (highPerformingTemplates != null && highPerformingTemplates > 0) {
            score += Math.min(30, highPerformingTemplates * 5);
        }

        Integer abTestsPassed = (Integer) metrics.get("abTestsPassed");
        if (abTestsPassed != null && abTestsPassed > 0) {
            score += Math.min(30, abTestsPassed * 10);
        }

        return Math.min(100, score);
    }

    private double calculateExpertLearningDepthScore(Map<String, Object> metrics) {
        double score = 0.0;

        Integer totalExpertLearnings = (Integer) metrics.get("totalExpertLearnings");
        if (totalExpertLearnings != null && totalExpertLearnings > 0) {
            score += Math.min(30, totalExpertLearnings * 3);
        }

        Integer templateUpdatesFromExperts = (Integer) metrics.get("templateUpdatesFromExperts");
        if (templateUpdatesFromExperts != null && templateUpdatesFromExperts > 0) {
            score += Math.min(40, templateUpdatesFromExperts * 8);
        }

        Integer knowledgeFromExperts = (Integer) metrics.get("knowledgeFromExperts");
        if (knowledgeFromExperts != null && knowledgeFromExperts > 0) {
            score += Math.min(30, knowledgeFromExperts * 2);
        }

        return Math.min(100, score);
    }

    private double calculateKnowledgeExtractionDepthScore(Map<String, Object> metrics) {
        double score = 0.0;

        Integer totalKnowledge = (Integer) metrics.get("totalKnowledge");
        if (totalKnowledge != null && totalKnowledge > 0) {
            score += Math.min(25, totalKnowledge * 0.5);
        }

        Integer explosiveRules = (Integer) metrics.get("explosiveRules");
        Integer avoidanceRules = (Integer) metrics.get("avoidanceRules");
        if (explosiveRules != null || avoidanceRules != null) {
            int totalRules = (explosiveRules != null ? explosiveRules : 0) + 
                           (avoidanceRules != null ? avoidanceRules : 0);
            score += Math.min(35, totalRules * 5);
        }

        Integer knowledgeAppliedTasks = (Integer) metrics.get("knowledgeAppliedTasks");
        if (knowledgeAppliedTasks != null && knowledgeAppliedTasks > 0) {
            score += Math.min(40, knowledgeAppliedTasks * 2);
        }

        return Math.min(100, score);
    }

    private double calculateMcpOptimizationDepthScore(Map<String, Object> metrics) {
        double score = 0.0;

        Integer activeMcpServers = (Integer) metrics.get("activeMcpServers");
        if (activeMcpServers != null && activeMcpServers > 0) {
            score += Math.min(30, activeMcpServers * 10);
        }

        Integer autoConfiguredServers = (Integer) metrics.get("autoConfiguredServers");
        if (autoConfiguredServers != null && autoConfiguredServers > 0) {
            score += Math.min(30, autoConfiguredServers * 15);
        }

        Integer mcpToolsTriggered = (Integer) metrics.get("mcpToolsTriggered");
        if (mcpToolsTriggered != null && mcpToolsTriggered > 0) {
            score += Math.min(40, mcpToolsTriggered * 2);
        }

        return Math.min(100, score);
    }

    private double calculateOverallLearningDepth(Map<String, Object> assessment) {
        double totalScore = 0.0;
        int componentCount = 0;

        @SuppressWarnings("unchecked")
        Map<String, Object> qTableMetrics = (Map<String, Object>) assessment.get("qTableLearning");
        if (qTableMetrics != null && qTableMetrics.containsKey("depthScore")) {
            totalScore += (Double) qTableMetrics.get("depthScore");
            componentCount++;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> skillMetrics = (Map<String, Object>) assessment.get("skillEvolution");
        if (skillMetrics != null && skillMetrics.containsKey("depthScore")) {
            totalScore += (Double) skillMetrics.get("depthScore");
            componentCount++;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> templateMetrics = (Map<String, Object>) assessment.get("templateLearning");
        if (templateMetrics != null && templateMetrics.containsKey("depthScore")) {
            totalScore += (Double) templateMetrics.get("depthScore");
            componentCount++;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> expertMetrics = (Map<String, Object>) assessment.get("expertLearning");
        if (expertMetrics != null && expertMetrics.containsKey("depthScore")) {
            totalScore += (Double) expertMetrics.get("depthScore");
            componentCount++;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> knowledgeMetrics = (Map<String, Object>) assessment.get("knowledgeExtraction");
        if (knowledgeMetrics != null && knowledgeMetrics.containsKey("depthScore")) {
            totalScore += (Double) knowledgeMetrics.get("depthScore");
            componentCount++;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> mcpMetrics = (Map<String, Object>) assessment.get("mcpOptimization");
        if (mcpMetrics != null && mcpMetrics.containsKey("depthScore")) {
            totalScore += (Double) mcpMetrics.get("depthScore");
            componentCount++;
        }

        return componentCount > 0 ? Math.round(totalScore / componentCount * 10.0) / 10.0 : 0.0;
    }

    private void persistAssessmentResults(Map<String, Object> assessment) {
        try {
            Double overallDepth = (Double) assessment.get("overallLearningDepth");
            LocalDateTime now = LocalDateTime.now();

            jdbcTemplate.update(
                    "INSERT INTO sys_learning_assessment (assessment_id, overall_depth_score, " +
                    "q_table_metrics, skill_evolution_metrics, template_learning_metrics, " +
                    "expert_learning_metrics, knowledge_extraction_metrics, mcp_optimization_metrics, " +
                    "assessed_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    "ASSESSMENT_" + System.currentTimeMillis(),
                    overallDepth,
                    serializeMetrics((Map<String, Object>) assessment.get("qTableLearning")),
                    serializeMetrics((Map<String, Object>) assessment.get("skillEvolution")),
                    serializeMetrics((Map<String, Object>) assessment.get("templateLearning")),
                    serializeMetrics((Map<String, Object>) assessment.get("expertLearning")),
                    serializeMetrics((Map<String, Object>) assessment.get("knowledgeExtraction")),
                    serializeMetrics((Map<String, Object>) assessment.get("mcpOptimization")),
                    now,
                    now);

            log.info("学习成果评估结果已持久化: 总体深度评分={}/100", overallDepth);

        } catch (Exception e) {
            log.error("持久化学习成果评估结果失败", e);
        }
    }

    private String serializeMetrics(Map<String, Object> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return "{}";
        }

        try {
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : metrics.entrySet()) {
                if (!first) {
                    json.append(",");
                }
                json.append("\"").append(entry.getKey()).append("\":");
                Object value = entry.getValue();
                if (value instanceof String) {
                    json.append("\"").append(value).append("\"");
                } else if (value instanceof Number) {
                    json.append(value);
                } else {
                    json.append("\"").append(value.toString()).append("\"");
                }
                first = false;
            }
            json.append("}");
            return json.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    public Map<String, Object> getLatestAssessment() {
        try {
            Map<String, Object> assessment = jdbcTemplate.queryForMap(
                    "SELECT * FROM sys_learning_assessment ORDER BY assessed_at DESC LIMIT 1");
            return assessment;
        } catch (Exception e) {
            log.error("获取最新学习成果评估失败", e);
            return new HashMap<>();
        }
    }

    public Map<String, Object> getLearningDepthTrend(int days) {
        try {
            List<Map<String, Object>> trend = jdbcTemplate.queryForList(
                    "SELECT assessed_at, overall_depth_score FROM sys_learning_assessment " +
                    "WHERE assessed_at > DATE_SUB(NOW(), INTERVAL ? DAY) ORDER BY assessed_at ASC",
                    days);
            
            Map<String, Object> result = new HashMap<>();
            result.put("trend", trend);
            result.put("dataPoints", trend.size());
            
            if (!trend.isEmpty()) {
                Double firstScore = ((Number) trend.get(0).get("overall_depth_score")).doubleValue();
                Double lastScore = ((Number) trend.get(trend.size() - 1).get("overall_depth_score")).doubleValue();
                result.put("improvement", lastScore - firstScore);
            }
            
            return result;
        } catch (Exception e) {
            log.error("获取学习深度趋势失败", e);
            return new HashMap<>();
        }
    }
}
