package com.ecommerce.workflow.service.video;

import com.ecommerce.workflow.entity.AiVideoConfig;
import com.ecommerce.workflow.entity.AvoidanceRule;
import com.ecommerce.workflow.entity.ExplosiveRule;
import com.ecommerce.workflow.entity.Knowledge;
import com.ecommerce.workflow.entity.VideoTask;
import com.ecommerce.workflow.mapper.AvoidanceRuleMapper;
import com.ecommerce.workflow.mapper.ExplosiveRuleMapper;
import com.ecommerce.workflow.service.ai.AiProviderService;
import com.ecommerce.workflow.entity.ExpertRoleConfig;
import com.ecommerce.workflow.service.evolution.EvolutionCoreService;
import com.ecommerce.workflow.service.knowledge.KnowledgeService;
import com.ecommerce.workflow.service.learning.LearningCoreService;
import com.ecommerce.workflow.service.learning.ExpertRoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class VideoPostReviewService {
    private static final Logger log = LoggerFactory.getLogger(VideoPostReviewService.class);

    @Autowired
    private LearningCoreService learningCoreService;

    @Autowired
    private AiProviderService aiProviderService;

    @Autowired
    private ExpertRoleService expertRoleService;

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private EvolutionCoreService evolutionCoreService;

    @Autowired
    private VideoKnowledgeEnhancer knowledgeEnhancer;

    @Autowired
    private AvoidanceRuleMapper avoidanceRuleMapper;

    @Autowired
    private ExplosiveRuleMapper explosiveRuleMapper;

    public void reviewSuccess(VideoTask task, AiVideoConfig config) {
        try {
            log.info("开始成功视频复盘: taskId={}", task.getTaskId());

            Map<String, String> expertReviews = new HashMap<>();

            List<String> reviewExperts = Arrays.asList(
                    "product_manager", "visual_designer", "video_editor",
                    "data_analyst", "operations_expert", "marketing_planner");

            for (String expertCode : reviewExperts) {
                ExpertRoleConfig role = expertRoleService.getExpertRole(expertCode);
                if (role != null && "ACTIVE".equals(role.getStatus())) {
                    String reviewPrompt = buildSuccessReviewPrompt(role, task, config);
                    String review = analyzeWithExpert(expertCode, reviewPrompt);
                    if (review != null && !review.isEmpty()) {
                        expertReviews.put(role.getRoleName(), review);
                    }
                }
            }

            saveSuccessKnowledge(task, config, expertReviews);

            extractExplosiveFeatures(task, config);

            updateQTableReward(task, config, true);

            // 让10大角色从成功案例中自动学习进化
            learnFromSuccessCase(task, config);

            log.info("成功视频复盘完成: taskId={}, 专家复盘数={}", task.getTaskId(), expertReviews.size());

        } catch (Exception e) {
            log.error("成功视频复盘失败: taskId={}", task.getTaskId(), e);
        }
    }

    public void reviewFailure(VideoTask task, AiVideoConfig config, String errorMessage) {
        try {
            log.info("开始失败视频复盘: taskId={}, error={}", task.getTaskId(), errorMessage);

            Map<String, String> expertReviews = new HashMap<>();

            List<String> reviewExperts = Arrays.asList(
                    "data_analyst", "video_editor", "operations_expert");

            for (String expertCode : reviewExperts) {
                ExpertRoleConfig role = expertRoleService.getExpertRole(expertCode);
                if (role != null && "ACTIVE".equals(role.getStatus())) {
                    String reviewPrompt = buildFailureReviewPrompt(role, task, config, errorMessage);
                    String review = analyzeWithExpert(expertCode, reviewPrompt);
                    if (review != null && !review.isEmpty()) {
                        expertReviews.put(role.getRoleName(), review);
                    }
                }
            }

            updateAvoidanceRules(task, config, errorMessage);

            updateQTableReward(task, config, false);

            saveFailureKnowledge(task, config, errorMessage, expertReviews);

            // 让10大角色从失败案例中自动学习进化
            learnFromFailureCase(task, config, errorMessage);

            log.info("失败视频复盘完成: taskId={}, 专家复盘数={}", task.getTaskId(), expertReviews.size());

        } catch (Exception e) {
            log.error("失败视频复盘失败: taskId={}", task.getTaskId(), e);
        }
    }

    /**
     * 让10大角色从成功案例中自动学习进化
     */
    private void learnFromSuccessCase(VideoTask task, AiVideoConfig config) {
        try {
            log.info("开始10大角色成功案例学习: taskId={}", task.getTaskId());

            Map<String, Object> caseData = new HashMap<>();
            caseData.put("sceneType", config.getSceneType());
            caseData.put("frameType", config.getFrameType());
            caseData.put("topic", config.getTopic());
            caseData.put("race", config.getRace());
            caseData.put("role", config.getRole());
            caseData.put("videoUrl", task.getVideoUrl());
            caseData.put("taskId", task.getTaskId());

            // 调用专家学习服务，让10大角色从案例中学习
            learningCoreService.learnFromVideoCase(caseData, true);

            log.info("10大角色成功案例学习完成: taskId={}", task.getTaskId());

        } catch (Exception e) {
            log.error("10大角色成功案例学习失败: taskId={}", task.getTaskId(), e);
        }
    }

    /**
     * 让10大角色从失败案例中自动学习进化
     */
    private void learnFromFailureCase(VideoTask task, AiVideoConfig config, String errorMessage) {
        try {
            log.info("开始10大角色失败案例学习: taskId={}", task.getTaskId());

            Map<String, Object> caseData = new HashMap<>();
            caseData.put("sceneType", config.getSceneType());
            caseData.put("frameType", config.getFrameType());
            caseData.put("topic", config.getTopic());
            caseData.put("race", config.getRace());
            caseData.put("role", config.getRole());
            caseData.put("errorMessage", errorMessage);
            caseData.put("taskId", task.getTaskId());

            // 调用专家学习服务，让10大角色从案例中学习
            learningCoreService.learnFromVideoCase(caseData, false);

            log.info("10大角色失败案例学习完成: taskId={}", task.getTaskId());

        } catch (Exception e) {
            log.error("10大角色失败案例学习失败: taskId={}", task.getTaskId(), e);
        }
    }

    private String buildSuccessReviewPrompt(ExpertRoleConfig role, VideoTask task, AiVideoConfig config) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("一个视频生成成功了，请从你的专业角度分析成功因素:\n\n");
        prompt.append("视频配置:\n");
        prompt.append("- 主题: ").append(config.getTopic()).append("\n");
        prompt.append("- 人物: ").append(config.getRace()).append(" ").append(config.getRole()).append("\n");
        prompt.append("- 场景: ").append(config.getSceneType()).append(" ").append(config.getScene()).append("\n");
        prompt.append("- 运镜: ").append(config.getFrameType()).append("\n");
        prompt.append("- 时长: ").append(config.getDuration()).append("秒\n");
        prompt.append("- 风格强度: ").append(config.getStyleIntensity()).append("%\n");
        prompt.append("- 创意度: ").append(config.getCreativity()).append("%\n\n");

        prompt.append("请从以下维度分析:\n");
        List<String> dimensions = expertRoleService.getExpertCoreDimensions(role.getRoleCode());
        if (dimensions != null && !dimensions.isEmpty()) {
            for (String dimension : dimensions) {
                prompt.append("- ").append(dimension).append("\n");
            }
        }
        prompt.append("\n请总结成功的关键因素，并给出可复用的经验。");

        return prompt.toString();
    }

    private String buildFailureReviewPrompt(ExpertRoleConfig role, VideoTask task, AiVideoConfig config, String errorMessage) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("一个视频生成失败了，请从你的专业角度分析失败原因:\n\n");
        prompt.append("错误信息: ").append(errorMessage).append("\n\n");
        prompt.append("视频配置:\n");
        prompt.append("- 主题: ").append(config.getTopic()).append("\n");
        prompt.append("- 人物: ").append(config.getRace()).append(" ").append(config.getRole()).append("\n");
        prompt.append("- 场景: ").append(config.getSceneType()).append(" ").append(config.getScene()).append("\n");
        prompt.append("- 运镜: ").append(config.getFrameType()).append("\n");
        prompt.append("- 时长: ").append(config.getDuration()).append("秒\n\n");

        prompt.append("请分析:\n");
        prompt.append("1. 失败的根本原因是什么？\n");
        prompt.append("2. 哪些参数配置可能导致失败？\n");
        prompt.append("3. 如何避免类似的失败？\n");
        prompt.append("4. 应该建立什么样的避坑规则？");

        return prompt.toString();
    }

    private String analyzeWithExpert(String expertCode, String prompt) {
        try {
            ExpertRoleConfig role = expertRoleService.getExpertRole(expertCode);
            if (role == null || !"ACTIVE".equals(role.getStatus())) {
                return null;
            }

            String aiResponse = aiProviderService.chatWithHistory(
                    "你是世界顶级的" + role.getRoleName() + "，请进行专业复盘分析。",
                    prompt,
                    null,
                    0.7,
                    2000);

            return aiResponse;

        } catch (Exception e) {
            log.error("专家复盘失败: role={}", expertCode, e);
            return null;
        }
    }

    private void saveSuccessKnowledge(VideoTask task, AiVideoConfig config, Map<String, String> expertReviews) {
        try {
            StringBuilder content = new StringBuilder();
            content.append("【成功案例】\n\n");
            content.append("视频配置:\n");
            content.append("- 主题: ").append(config.getTopic()).append("\n");
            content.append("- 人物: ").append(config.getRace()).append(" ").append(config.getRole()).append("\n");
            content.append("- 场景: ").append(config.getSceneType()).append(" ").append(config.getScene()).append("\n");
            content.append("- 运镜: ").append(config.getFrameType()).append("\n");
            content.append("- 时长: ").append(config.getDuration()).append("秒\n\n");

            content.append("【专家复盘】\n");
            expertReviews.forEach((expert, review) -> {
                content.append("【").append(expert).append("】\n");
                content.append(review).append("\n\n");
            });

            content.append("【关键成功因素】\n");
            content.append("1. 参数配置合理，符合爆款规律\n");
            content.append("2. 专家建议被有效采纳\n");
            content.append("3. 未触发避坑规则\n");

            String tags = String.join(",",
                    "视频生成", "成功案例",
                    config.getSceneType() != null ? config.getSceneType() : "",
                    config.getFrameType() != null ? config.getFrameType() : "");

            Knowledge knowledge = new Knowledge();
            knowledge.setTitle("视频生成成功案例-" + config.getTopic());
            knowledge.setContent(content.toString());
            knowledge.setTags(tags);
            knowledge.setConfidence(0.9);
            knowledge.setType("VIDEO_CASE");
            knowledge.setSource("VIDEO_GENERATION");
            knowledge.setSourceId(task.getTaskId());

            knowledgeService.storeKnowledge(knowledge);

            log.info("成功知识已存入知识库: taskId={}", task.getTaskId());

        } catch (Exception e) {
            log.error("保存成功知识失败: taskId={}", task.getTaskId(), e);
        }
    }

    private void saveFailureKnowledge(VideoTask task, AiVideoConfig config, String errorMessage, Map<String, String> expertReviews) {
        try {
            StringBuilder content = new StringBuilder();
            content.append("【失败案例】\n\n");
            content.append("错误信息: ").append(errorMessage).append("\n\n");
            content.append("视频配置:\n");
            content.append("- 主题: ").append(config.getTopic()).append("\n");
            content.append("- 人物: ").append(config.getRace()).append(" ").append(config.getRole()).append("\n");
            content.append("- 场景: ").append(config.getSceneType()).append(" ").append(config.getScene()).append("\n");
            content.append("- 运镜: ").append(config.getFrameType()).append("\n");
            content.append("- 时长: ").append(config.getDuration()).append("秒\n\n");

            content.append("【专家复盘】\n");
            expertReviews.forEach((expert, review) -> {
                content.append("【").append(expert).append("】\n");
                content.append(review).append("\n\n");
            });

            String tags = String.join(",",
                    "视频生成", "失败案例",
                    config.getSceneType() != null ? config.getSceneType() : "");

            Knowledge knowledge = new Knowledge();
            knowledge.setTitle("视频生成失败案例-" + config.getTopic());
            knowledge.setContent(content.toString());
            knowledge.setTags(tags);
            knowledge.setConfidence(0.7);
            knowledge.setType("VIDEO_FAILURE_CASE");
            knowledge.setSource("VIDEO_GENERATION");
            knowledge.setSourceId(task.getTaskId());

            knowledgeService.storeKnowledge(knowledge);

            log.info("失败知识已存入知识库: taskId={}", task.getTaskId());

        } catch (Exception e) {
            log.error("保存失败知识失败: taskId={}", task.getTaskId(), e);
        }
    }

    private void extractExplosiveFeatures(VideoTask task, AiVideoConfig config) {
        try {
            log.info("提取爆款特征: taskId={}", task.getTaskId());

            String aiAnalysis = aiProviderService.chatWithHistory(
                    "你是世界顶级的数据分析师和视频爆款规律专家。请分析这个成功的视频配置，提取出可以复用的爆款特征。",
                    buildExplosiveFeaturePrompt(config),
                    null,
                    0.7,
                    1500);

            if (aiAnalysis != null && !aiAnalysis.isEmpty()) {
                ExplosiveRule newRule = new ExplosiveRule();
                newRule.setRuleCode("VIDEO_" + System.currentTimeMillis());
                newRule.setRuleName("视频爆款规律-" + config.getTopic() + "-" + System.currentTimeMillis());
                newRule.setCategory("视频");
                newRule.setDescription("从成功案例中自动提取的爆款规律");
                newRule.setRuleContent(aiAnalysis);
                newRule.setApplicableScenarios(config.getSceneType());
                newRule.setEffectiveness(new BigDecimal("0.8"));
                newRule.setSuccessRate(new BigDecimal("0.75"));
                newRule.setApplications(1);
                newRule.setTags("自动提取,视频生成,成功案例," + config.getSceneType());
                newRule.setStatus("ACTIVE");
                newRule.setPriority(5);
                newRule.setCreatedAt(LocalDateTime.now());
                newRule.setUpdatedAt(LocalDateTime.now());
                newRule.setDeleted(0);

                explosiveRuleMapper.insert(newRule);

                log.info("爆款规律已自动提取并入库: ruleId={}, taskId={}", newRule.getId(), task.getTaskId());
            }

        } catch (Exception e) {
            log.error("提取爆款特征失败: taskId={}", task.getTaskId(), e);
        }
    }

    private String buildExplosiveFeaturePrompt(AiVideoConfig config) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("一个视频生成成功了，请分析以下配置，提取爆款特征:\n\n");
        prompt.append("视频配置:\n");
        prompt.append("- 主题: ").append(config.getTopic()).append("\n");
        prompt.append("- 人物: ").append(config.getRace()).append(" ").append(config.getRole()).append("\n");
        prompt.append("- 场景: ").append(config.getSceneType()).append(" ").append(config.getScene()).append("\n");
        prompt.append("- 运镜: ").append(config.getFrameType()).append("\n");
        prompt.append("- 时长: ").append(config.getDuration()).append("秒\n");
        prompt.append("- 风格强度: ").append(config.getStyleIntensity()).append("%\n");
        prompt.append("- 创意度: ").append(config.getCreativity()).append("%\n\n");

        prompt.append("请从以下维度提取爆款规律:\n");
        prompt.append("1. 内容结构特征（开头、中间、结尾的节奏安排）\n");
        prompt.append("2. 视觉呈现特征（画面风格、色彩、构图）\n");
        prompt.append("3. 情感驱动特征（使用什么情感框架）\n");
        prompt.append("4. 产品展示特征（如何展示产品卖点）\n");
        prompt.append("5. 转化路径特征（如何引导用户行动）\n\n");

        prompt.append("请输出具体、可复用的规律描述，格式为条理清晰的文本。");

        return prompt.toString();
    }

    private void updateAvoidanceRules(VideoTask task, AiVideoConfig config, String errorMessage) {
        try {
            log.info("更新避坑规则: taskId={}, error={}", task.getTaskId(), errorMessage);

            String aiAnalysis = aiProviderService.chatWithHistory(
                    "你是世界顶级的视频制作专家和风险管控专家。请分析这个失败的视频生成案例，提取出应该避免的问题模式。",
                    buildAvoidanceRulePrompt(config, errorMessage),
                    null,
                    0.7,
                    1500);

            if (aiAnalysis != null && !aiAnalysis.isEmpty()) {
                AvoidanceRule newRule = new AvoidanceRule();
                newRule.setTitle("视频避坑规则-" + config.getTopic() + "-" + System.currentTimeMillis());
                newRule.setCategory("视频");
                newRule.setDescription("从失败案例中自动提取的避坑规则");
                newRule.setProblemPattern(extractProblemPattern(config, errorMessage));
                newRule.setSolution(aiAnalysis);
                newRule.setPrevention(buildPreventionAdvice(config, errorMessage));
                newRule.setSeverity(calculateSeverity(errorMessage));
                newRule.setApplyCount(0);
                newRule.setEffectiveCount(0);
                newRule.setTags("自动提取,视频生成,失败案例," + config.getSceneType());
                newRule.setRelatedCases(task.getTaskId());
                newRule.setCreatedBy(0);
                newRule.setCreatedAt(LocalDateTime.now());
                newRule.setUpdatedAt(LocalDateTime.now());
                newRule.setDeleted(0);

                avoidanceRuleMapper.insert(newRule);

                log.info("避坑规则已自动提取并入库: ruleId={}, taskId={}", newRule.getId(), task.getTaskId());
            }

        } catch (Exception e) {
            log.error("更新避坑规则失败: taskId={}", task.getTaskId(), e);
        }
    }

    private String buildAvoidanceRulePrompt(AiVideoConfig config, String errorMessage) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("一个视频生成失败了，请分析以下配置和错误信息，提取避坑规则:\n\n");
        prompt.append("错误信息: ").append(errorMessage).append("\n\n");
        prompt.append("视频配置:\n");
        prompt.append("- 主题: ").append(config.getTopic()).append("\n");
        prompt.append("- 人物: ").append(config.getRace()).append(" ").append(config.getRole()).append("\n");
        prompt.append("- 场景: ").append(config.getSceneType()).append(" ").append(config.getScene()).append("\n");
        prompt.append("- 运镜: ").append(config.getFrameType()).append("\n");
        prompt.append("- 时长: ").append(config.getDuration()).append("秒\n\n");

        prompt.append("请从以下维度分析:\n");
        prompt.append("1. 失败的根本原因是什么？\n");
        prompt.append("2. 哪些参数配置可能导致失败？\n");
        prompt.append("3. 如何避免类似的失败？\n");
        prompt.append("4. 应该建立什么样的避坑规则？\n\n");

        prompt.append("请输出具体、可操作的避坑建议。");

        return prompt.toString();
    }

    private String extractProblemPattern(AiVideoConfig config, String errorMessage) {
        StringBuilder pattern = new StringBuilder();

        if (config.getTopic() != null) {
            pattern.append(config.getTopic()).append(",");
        }
        if (config.getSceneType() != null) {
            pattern.append(config.getSceneType()).append(",");
        }
        if (errorMessage != null && !errorMessage.isEmpty()) {
            if (errorMessage.contains("timeout") || errorMessage.contains("超时")) {
                pattern.append("超时,");
            }
            if (errorMessage.contains("invalid") || errorMessage.contains("无效")) {
                pattern.append("无效配置,");
            }
            if (errorMessage.contains("rate limit") || errorMessage.contains("限流")) {
                pattern.append("限流,");
            }
        }

        return pattern.toString();
    }

    private String buildPreventionAdvice(AiVideoConfig config, String errorMessage) {
        StringBuilder advice = new StringBuilder();

        advice.append("预防措施:\n");
        advice.append("1. 生成前检查配置参数的合理性\n");
        advice.append("2. 参考相似场景的成功案例配置\n");
        advice.append("3. 避免使用已知的失败参数组合\n");
        advice.append("4. 监控生成过程，及时发现异常\n");

        return advice.toString();
    }

    private Integer calculateSeverity(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return 2;
        }

        String error = errorMessage.toLowerCase();

        if (error.contains("critical") || error.contains("fatal") || error.contains("系统错误")) {
            return 4;
        }
        if (error.contains("error") || error.contains("失败") || error.contains("无效")) {
            return 3;
        }
        if (error.contains("warning") || error.contains("警告") || error.contains("超时")) {
            return 2;
        }

        return 1;
    }

    private void updateQTableReward(VideoTask task, AiVideoConfig config, boolean success) {
        try {
            String stateHash = "video_generator_" + config.getSceneType() + "_" + config.getFrameType();

            double reward = success ? 1.0 : -1.0;

            evolutionCoreService.recordUsage(config.getSceneType(), success, null, null);

            log.info("Q-Table奖励更新: state={}, reward={}, success={}", stateHash, reward, success);

        } catch (Exception e) {
            log.error("更新Q-Table奖励失败: taskId={}", task.getTaskId(), e);
        }
    }
}
