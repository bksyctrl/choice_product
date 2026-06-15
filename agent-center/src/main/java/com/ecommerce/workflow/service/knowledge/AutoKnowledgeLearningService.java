package com.ecommerce.workflow.service.knowledge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AutoKnowledgeLearningService {

    private static final Logger log = LoggerFactory.getLogger(AutoKnowledgeLearningService.class);

    private final KnowledgeService knowledgeService;
    private final JdbcTemplate jdbcTemplate;

    public AutoKnowledgeLearningService(KnowledgeService knowledgeService, JdbcTemplate jdbcTemplate) {
        this.knowledgeService = knowledgeService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedRateString = "${knowledge.learning.interval-ms:3600000}")
    public void autoLearnFromCases() {
        log.info("=== 开始自动知识学习 ===");
        learnFromSuccessCases();
        learnFromFailureCases();
        log.info("=== 自动知识学习完成 ===");
    }

    private void learnFromSuccessCases() {
        try {
            List<Map<String, Object>> unlearnedCases = jdbcTemplate.queryForList(
                    "SELECT vt.*, avc.topic, avc.scene_type, avc.scene, avc.race, avc.role " +
                    "FROM biz_video_task vt " +
                    "LEFT JOIN ai_video_config avc ON vt.config_id = avc.id " +
                    "WHERE vt.status = 'completed' " +
                    "AND vt.id NOT IN (SELECT source_id FROM sys_knowledge WHERE source LIKE 'video_task_%') " +
                    "ORDER BY vt.created_at DESC LIMIT 10");

            if (unlearnedCases.isEmpty()) {
                log.info("没有待学习的成功案例");
                return;
            }

            log.info("发现{}个待学习的成功案例", unlearnedCases.size());

            for (Map<String, Object> caseData : unlearnedCases) {
                extractKnowledgeFromCase(caseData, "SUCCESS_CASE");
            }

        } catch (Exception e) {
            log.error("从成功案例学习失败", e);
        }
    }

    private void learnFromFailureCases() {
        try {
            List<Map<String, Object>> unlearnedFailures = jdbcTemplate.queryForList(
                    "SELECT vt.*, avc.topic, avc.scene_type, avc.scene, avc.race, avc.role, vt.error_message " +
                    "FROM biz_video_task vt " +
                    "LEFT JOIN ai_video_config avc ON vt.config_id = avc.id " +
                    "WHERE vt.status = 'failed' " +
                    "AND vt.id NOT IN (SELECT source_id FROM sys_knowledge WHERE source LIKE 'video_task_%') " +
                    "AND vt.error_message IS NOT NULL " +
                    "ORDER BY vt.created_at DESC LIMIT 10");

            if (unlearnedFailures.isEmpty()) {
                log.info("没有待学习的失败案例");
                return;
            }

            log.info("发现{}个待学习的失败案例", unlearnedFailures.size());

            for (Map<String, Object> caseData : unlearnedFailures) {
                extractKnowledgeFromCase(caseData, "FAILURE_CASE");
            }

        } catch (Exception e) {
            log.error("从失败案例学习失败", e);
        }
    }

    private void extractKnowledgeFromCase(Map<String, Object> caseData, String caseType) {
        try {
            String taskId = (String) caseData.get("task_id");
            String topic = (String) caseData.get("topic");
            String sceneType = (String) caseData.get("scene_type");

            Map<String, Object> knowledgeData = new HashMap<>();
            knowledgeData.put("caseNo", taskId);
            knowledgeData.put("productName", topic);
            knowledgeData.put("category", sceneType);
            knowledgeData.put("caseType", caseType);
            knowledgeData.put("qualityTag", "SUCCESS".equals(caseType) ? "success" : "failure");
            knowledgeData.put("platform", "ai_video");

            if ("SUCCESS_CASE".equals(caseType)) {
                knowledgeData.put("lessonLearned", "视频生成成功，配置参数有效");
            } else {
                String errorMsg = (String) caseData.get("error_message");
                knowledgeData.put("lessonLearned", "视频生成失败: " + (errorMsg != null ? errorMsg : "未知错误"));
            }

            Map<String, Object> inputParams = new HashMap<>();
            inputParams.put("topic", topic);
            inputParams.put("sceneType", sceneType);
            inputParams.put("race", caseData.get("race"));
            inputParams.put("role", caseData.get("role"));
            knowledgeData.put("inputParams", inputParams);

            Map<String, Object> outputResult = new HashMap<>();
            outputResult.put("taskId", taskId);
            outputResult.put("status", caseData.get("status"));
            outputResult.put("videoUrl", caseData.get("video_url"));
            knowledgeData.put("outputResult", outputResult);

            knowledgeService.extractFromCase(knowledgeData, caseType);

            log.info("知识提取完成: taskId={}, type={}", taskId, caseType);

        } catch (Exception e) {
            log.info("提取知识失败: {}", caseData.get("task_id"), e);
        }
    }

    public void learnFromVideoCase(Map<String, Object> caseData, boolean isSuccess) {
        try {
            String taskId = (String) caseData.get("taskId");
            String caseType = isSuccess ? "SUCCESS_CASE" : "FAILURE_CASE";

            Map<String, Object> knowledgeData = new HashMap<>();
            knowledgeData.put("caseNo", taskId);
            knowledgeData.put("productName", caseData.get("topic"));
            knowledgeData.put("category", caseData.get("sceneType"));
            knowledgeData.put("caseType", caseType);
            knowledgeData.put("qualityTag", isSuccess ? "success" : "failure");
            knowledgeData.put("platform", "ai_video");

            if (isSuccess) {
                knowledgeData.put("lessonLearned", "视频生成成功，配置参数有效");
            } else {
                String errorMsg = (String) caseData.get("errorMessage");
                knowledgeData.put("lessonLearned", "视频生成失败: " + (errorMsg != null ? errorMsg : "未知错误"));
            }

            Map<String, Object> inputParams2 = new HashMap<>();
            inputParams2.put("topic", caseData.get("topic"));
            inputParams2.put("sceneType", caseData.get("sceneType"));
            inputParams2.put("frameType", caseData.get("frameType"));
            inputParams2.put("race", caseData.get("race"));
            inputParams2.put("role", caseData.get("role"));
            knowledgeData.put("inputParams", inputParams2);

            Map<String, Object> outputResult2 = new HashMap<>();
            outputResult2.put("taskId", taskId);
            outputResult2.put("status", isSuccess ? "completed" : "failed");
            outputResult2.put("videoUrl", caseData.get("videoUrl"));
            knowledgeData.put("outputResult", outputResult2);

            knowledgeService.extractFromCase(knowledgeData, caseType);

            log.info("视频案例知识提取完成: taskId={}, type={}", taskId, caseType);

        } catch (Exception e) {
            log.error("从视频案例提取知识失败", e);
        }
    }
}
