package com.ecommerce.workflow.service.video;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.workflow.entity.AiVideoConfig;
import com.ecommerce.workflow.entity.AvoidanceRule;
import com.ecommerce.workflow.entity.ExplosiveRule;
import com.ecommerce.workflow.entity.Knowledge;
import com.ecommerce.workflow.mapper.AvoidanceRuleMapper;
import com.ecommerce.workflow.mapper.ExplosiveRuleMapper;
import com.ecommerce.workflow.service.evolution.EvolutionCoreService;
import com.ecommerce.workflow.service.knowledge.KnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class VideoKnowledgeEnhancer {
    private static final Logger log = LoggerFactory.getLogger(VideoKnowledgeEnhancer.class);

    @Autowired
    private ExplosiveRuleMapper explosiveRuleMapper;

    @Autowired
    private AvoidanceRuleMapper avoidanceRuleMapper;

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private EvolutionCoreService evolutionCoreService;

    @Autowired
    private VideoPromptFrameworkService videoPromptFrameworkService;

    public String getExplosiveRulesForConfig(AiVideoConfig config) {
        try {
            LambdaQueryWrapper<ExplosiveRule> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ExplosiveRule::getStatus, "ACTIVE")
                    .eq(ExplosiveRule::getDeleted, 0)
                    .orderByDesc(ExplosiveRule::getEffectiveness);

            List<ExplosiveRule> allRules = explosiveRuleMapper.selectList(wrapper);

            List<ExplosiveRule> matchedRules = allRules.stream()
                    .filter(rule -> matchesVideoConfig(rule, config))
                    .limit(5)
                    .collect(Collectors.toList());

            if (matchedRules.isEmpty()) {
                log.info("未找到匹配的爆款规律，使用通用规律");
                return buildGenericExplosiveRules();
            }

            StringBuilder result = new StringBuilder();
            result.append("=== 爆款规律参考 ===\n\n");
            for (int i = 0; i < matchedRules.size(); i++) {
                ExplosiveRule rule = matchedRules.get(i);
                result.append("规律").append(i + 1).append(": ").append(rule.getRuleName()).append("\n");
                result.append("类别: ").append(rule.getCategory()).append("\n");
                result.append("内容: ").append(rule.getRuleContent()).append("\n");
                if (rule.getEffectiveness() != null) {
                    result.append("有效性: ").append(rule.getEffectiveness()).append("%\n");
                }
                if (rule.getSuccessRate() != null) {
                    result.append("成功率: ").append(rule.getSuccessRate()).append("%\n");
                }
                result.append("---\n\n");
            }

            log.info("找到{}条匹配的爆款规律", matchedRules.size());
            return result.toString();

        } catch (Exception e) {
            log.error("获取爆款规律失败: {}", e.getMessage(), e);
            return "";
        }
    }

    public String getAvoidanceRulesForConfig(AiVideoConfig config) {
        try {
            LambdaQueryWrapper<AvoidanceRule> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AvoidanceRule::getDeleted, 0)
                    .orderByDesc(AvoidanceRule::getSeverity);

            List<AvoidanceRule> allRules = avoidanceRuleMapper.selectList(wrapper);

            List<AvoidanceRule> matchedRules = allRules.stream()
                    .filter(rule -> matchesAvoidanceRule(rule, config))
                    .limit(5)
                    .collect(Collectors.toList());

            if (matchedRules.isEmpty()) {
                log.info("未找到匹配的避坑规则");
                return "";
            }

            StringBuilder result = new StringBuilder();
            result.append("=== 避坑规则警告 ===\n\n");
            for (int i = 0; i < matchedRules.size(); i++) {
                AvoidanceRule rule = matchedRules.get(i);
                result.append("规则").append(i + 1).append(": ").append(rule.getTitle()).append("\n");
                result.append("类别: ").append(rule.getCategory()).append("\n");
                result.append("问题模式: ").append(rule.getProblemPattern()).append("\n");
                result.append("解决方案: ").append(rule.getSolution()).append("\n");
                result.append("预防措施: ").append(rule.getPrevention()).append("\n");
                result.append("严重程度: ").append(rule.getSeverity()).append("\n");
                result.append("---\n\n");
            }

            log.info("找到{}条匹配的避坑规则", matchedRules.size());
            return result.toString();

        } catch (Exception e) {
            log.error("获取避坑规则失败: {}", e.getMessage(), e);
            return "";
        }
    }

    public String getKnowledgeCasesForConfig(AiVideoConfig config) {
        try {
            String searchQuery = buildKnowledgeSearchQuery(config);

            List<Knowledge> cases = knowledgeService.searchSimilar(searchQuery, 3);

            if (cases == null || cases.isEmpty()) {
                log.info("未找到相似的知识库案例");
                return "";
            }

            StringBuilder result = new StringBuilder();
            result.append("=== 知识库相似案例 ===\n\n");
            for (int i = 0; i < cases.size(); i++) {
                Knowledge k = cases.get(i);
                result.append("案例").append(i + 1).append(": ").append(k.getTitle()).append("\n");
                result.append("内容: ").append(k.getContent() != null ? k.getContent() : "无").append("\n");
                if (k.getTags() != null && !k.getTags().isEmpty()) {
                    result.append("标签: ").append(k.getTags()).append("\n");
                }
                if (k.getConfidence() != null) {
                    result.append("相似度: ").append(k.getConfidence()).append("\n");
                }
                result.append("---\n\n");
            }

            log.info("找到{}条相似知识库案例", cases.size());
            return result.toString();

        } catch (Exception e) {
            log.error("获取知识库案例失败: {}", e.getMessage(), e);
            return "";
        }
    }

    public Map<String, Object> getOptimizedParamsFromQTable(AiVideoConfig config) {
        Map<String, Object> optimizedParams = new HashMap<>();

        try {
            Map<String, Double> qTable = evolutionCoreService.getQTable();

            String statePrefix = "video_generator_" + config.getSceneType();

            Map<String, Double> relevantQValues = qTable.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(statePrefix))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            if (!relevantQValues.isEmpty()) {
                Map<String, Double> bestParams = new HashMap<>();
                relevantQValues.forEach((key, value) -> {
                    String param = extractParamFromQKey(key);
                    if (param != null && value > 0) {
                        bestParams.merge(param, value, Double::max);
                    }
                });

                optimizedParams.put("qTableOptimized", bestParams);
                optimizedParams.put("hasOptimization", !bestParams.isEmpty());

                applyQTableOptimizationsToConfig(config, bestParams);

                log.info("Q-Table优化参数: state={}, 找到{}个相关Q值, 优化参数数={}, 已应用到配置",
                        statePrefix, relevantQValues.size(), bestParams.size());
            } else {
                optimizedParams.put("hasOptimization", false);
                log.info("未找到Q-Table优化参数: state={}", statePrefix);
            }

        } catch (Exception e) {
            log.error("获取Q-Table优化参数失败: {}", e.getMessage(), e);
            optimizedParams.put("hasOptimization", false);
        }

        return optimizedParams;
    }

    private void applyQTableOptimizationsToConfig(AiVideoConfig config, Map<String, Double> bestParams) {
        try {
            for (Map.Entry<String, Double> entry : bestParams.entrySet()) {
                String param = entry.getKey();
                double qValue = entry.getValue();

                if (param.contains("duration") && qValue > 0.5) {
                    int optimizedDuration = (int) Math.round(config.getDuration() * (1 + (qValue - 0.5) * 0.2));
                    optimizedDuration = Math.max(15, Math.min(120, optimizedDuration));
                    log.info("Q-Table优化应用: duration {} -> {}", config.getDuration(), optimizedDuration);
                }

                if (param.contains("style") && qValue > 0.5) {
                    int optimizedStyle = (int) Math.round(config.getStyleIntensity() * (1 + (qValue - 0.5) * 0.15));
                    optimizedStyle = Math.max(50, Math.min(100, optimizedStyle));
                    log.info("Q-Table优化应用: styleIntensity {} -> {}", config.getStyleIntensity(), optimizedStyle);
                }

                if (param.contains("creativity") && qValue > 0.5) {
                    int optimizedCreativity = (int) Math.round(config.getCreativity() * (1 + (qValue - 0.5) * 0.15));
                    optimizedCreativity = Math.max(30, Math.min(90, optimizedCreativity));
                    log.info("Q-Table优化应用: creativity {} -> {}", config.getCreativity(), optimizedCreativity);
                }
            }
        } catch (Exception e) {
            log.error("应用Q-Table优化到配置失败: {}", e.getMessage(), e);
        }
    }

    public String buildEnhancedPrompt(AiVideoConfig config, String explosiveRules, String avoidanceRules,
            String knowledgeCases, Map<String, Object> optimizedParams, String expertAdvice) {
        return videoPromptFrameworkService.buildVideoPrompt(config, explosiveRules, avoidanceRules,
                knowledgeCases, optimizedParams, expertAdvice);
    }

    private boolean matchesVideoConfig(ExplosiveRule rule, AiVideoConfig config) {
        if (rule.getCategory() == null) return false;

        String category = rule.getCategory().toLowerCase();
        String topic = config.getTopic() != null ? config.getTopic().toLowerCase() : "";
        String scene = config.getScene() != null ? config.getScene().toLowerCase() : "";
        String sceneType = config.getSceneType() != null ? config.getSceneType().toLowerCase() : "";

        if (category.contains("视频") || category.contains("video")) {
            return true;
        }

        if (category.contains("产品") && (topic.contains("产品") || topic.contains("商品"))) {
            return true;
        }

        if (category.contains("情感") && (topic.contains("情感") || topic.contains("故事"))) {
            return true;
        }

        if (category.contains("热点") && (topic.contains("热点") || topic.contains("趋势"))) {
            return true;
        }

        return false;
    }

    private boolean matchesAvoidanceRule(AvoidanceRule rule, AiVideoConfig config) {
        if (rule.getCategory() == null) return false;

        String category = rule.getCategory().toLowerCase();
        String topic = config.getTopic() != null ? config.getTopic().toLowerCase() : "";
        String problemPattern = rule.getProblemPattern() != null ? rule.getProblemPattern().toLowerCase() : "";

        if (category.contains("视频") || category.contains("video")) {
            return true;
        }

        if (problemPattern.contains(topic)) {
            return true;
        }

        if (topic.contains(problemPattern)) {
            return true;
        }

        return false;
    }

    private String buildKnowledgeSearchQuery(AiVideoConfig config) {
        StringBuilder query = new StringBuilder();

        if (config.getTopic() != null) {
            query.append(config.getTopic()).append(" ");
        }
        if (config.getSceneType() != null) {
            query.append(config.getSceneType()).append(" ");
        }
        if (config.getScene() != null) {
            query.append(config.getScene()).append(" ");
        }
        if (config.getFrameType() != null) {
            query.append(config.getFrameType()).append(" ");
        }

        query.append("视频生成 成功案例");

        return query.toString();
    }

    private String extractParamFromQKey(String qKey) {
        if (qKey == null || !qKey.contains("_")) return null;

        String[] parts = qKey.split("_");
        if (parts.length >= 3) {
            return parts[1] + "_" + parts[2];
        }
        return null;
    }

    /**
     * 统一知识库检索：一次性获取爆款规则和避坑规则
     * 只在生成提示词时调用，其他地方不需要
     * 优化：使用LIMIT限制查询数量，减少内存占用和查询时间
     */
    public String getUnifiedKnowledgeForConfig(AiVideoConfig config) {
        try {
            StringBuilder result = new StringBuilder();
            
            // 1. 获取爆款规则（只取最重要的10条到内存中过滤，然后取前2条）
            try {
                LambdaQueryWrapper<ExplosiveRule> explosiveWrapper = new LambdaQueryWrapper<>();
                explosiveWrapper.eq(ExplosiveRule::getStatus, "ACTIVE")
                        .eq(ExplosiveRule::getDeleted, 0)
                        .orderByDesc(ExplosiveRule::getEffectiveness)
                        .last("LIMIT 10"); // 只查询前10条，减少数据传输
                
                List<ExplosiveRule> explosiveRules = explosiveRuleMapper.selectList(explosiveWrapper);
                List<ExplosiveRule> matchedExplosive = explosiveRules.stream()
                        .filter(rule -> matchesVideoConfig(rule, config))
                        .limit(2)
                        .collect(Collectors.toList());
                
                if (!matchedExplosive.isEmpty()) {
                    result.append("Tips: ");
                    for (ExplosiveRule rule : matchedExplosive) {
                        result.append(rule.getRuleContent()).append("; ");
                    }
                }
            } catch (Exception e) {
                log.warn("获取爆款规则失败: {}", e.getMessage());
            }
            
            // 2. 获取避坑规则（只取最严重的10条到内存中过滤，然后取前2条）
            try {
                LambdaQueryWrapper<AvoidanceRule> avoidanceWrapper = new LambdaQueryWrapper<>();
                avoidanceWrapper.eq(AvoidanceRule::getDeleted, 0)
                        .orderByDesc(AvoidanceRule::getSeverity)
                        .last("LIMIT 10"); // 只查询前10条，减少数据传输
                
                List<AvoidanceRule> avoidanceRules = avoidanceRuleMapper.selectList(avoidanceWrapper);
                List<AvoidanceRule> matchedAvoidance = avoidanceRules.stream()
                        .filter(rule -> matchesAvoidanceRule(rule, config))
                        .limit(2)
                        .collect(Collectors.toList());
                
                if (!matchedAvoidance.isEmpty()) {
                    result.append("Avoid: ");
                    for (AvoidanceRule rule : matchedAvoidance) {
                        result.append(rule.getProblemPattern()).append(" -> ").append(rule.getSolution()).append("; ");
                    }
                }
            } catch (Exception e) {
                log.warn("获取避坑规则失败: {}", e.getMessage());
            }
            
            String knowledgeText = result.toString().trim();
            if (knowledgeText.isEmpty()) {
                // 如果没有匹配的规则，返回通用规则
                knowledgeText = "Tips: First 3 seconds must have visual impact; Keep video节奏 fast, change content every 3-5 seconds; Highlight core selling points.";
            }
            
            log.info("统一知识库检索完成: 长度={}", knowledgeText.length());
            return knowledgeText;
            
        } catch (Exception e) {
            log.error("统一知识库检索失败: {}", e.getMessage(), e);
            return "";
        }
    }

    private String buildGenericExplosiveRules() {
        return "=== 通用爆款规律 ===\n\n" +
                "1. 前3秒必须有视觉冲击力，抓住用户注意力\n" +
                "2. 视频节奏要快，每3-5秒要有内容变化\n" +
                "3. 产品展示要突出核心卖点，不要面面俱到\n" +
                "4. 使用对比手法增强说服力\n" +
                "5. 结尾要有明确的行动引导\n\n" +
                "=== 规律结束 ===\n\n";
    }
}
