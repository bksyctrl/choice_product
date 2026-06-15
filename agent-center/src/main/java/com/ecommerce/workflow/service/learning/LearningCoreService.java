package com.ecommerce.workflow.service.learning;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.workflow.entity.CaseMemory;
import com.ecommerce.workflow.entity.Knowledge;
import com.ecommerce.workflow.mapper.CaseMemoryMapper;
import com.ecommerce.workflow.service.ai.GptChatService;
import com.ecommerce.workflow.service.config.SysConfigService;
import com.ecommerce.workflow.service.knowledge.KnowledgeService;
import com.ecommerce.workflow.entity.ExpertRoleConfig;
import com.ecommerce.workflow.service.memory.PersistentLearningService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class LearningCoreService {
    private static final Logger log = LoggerFactory.getLogger(LearningCoreService.class);

    private final JdbcTemplate jdbcTemplate;
    private final CaseMemoryMapper caseMemoryMapper;
    private final KnowledgeService knowledgeService;
    private final PersistentLearningService persistentLearningService;
    private final GptChatService gptChatService;
    private final DimensionService dimensionService;
    private final ExpertRoleService expertRoleService;
    private final ObjectMapper objectMapper;
    private final SysConfigService sysConfigService;

    private final Map<String, Object> discoveredTables = new ConcurrentHashMap<>();
    private long lastDiscoveryTime = 0;
    private final Set<String> learnedCaseIds = ConcurrentHashMap.newKeySet();

    public LearningCoreService(JdbcTemplate jdbcTemplate,
                                CaseMemoryMapper caseMemoryMapper,
                                KnowledgeService knowledgeService,
                                PersistentLearningService persistentLearningService,
                                GptChatService gptChatService,
                                DimensionService dimensionService,
                                ExpertRoleService expertRoleService,
                                ObjectMapper objectMapper,
                                SysConfigService sysConfigService) {
        this.jdbcTemplate = jdbcTemplate;
        this.caseMemoryMapper = caseMemoryMapper;
        this.knowledgeService = knowledgeService;
        this.persistentLearningService = persistentLearningService;
        this.gptChatService = gptChatService;
        this.dimensionService = dimensionService;
        this.expertRoleService = expertRoleService;
        this.objectMapper = objectMapper;
        this.sysConfigService = sysConfigService;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void executeFullLearningCycle() {
        log.info("=== 开始统一学习周期 ===");
        long startTime = System.currentTimeMillis();

        try {
            int totalLearned = 0;

            totalLearned += learnFromUnlearnedCases();
            totalLearned += discoverAndLearnPatterns();
            totalLearned += performExpertAnalysis();

            long duration = System.currentTimeMillis() - startTime;
            log.info("=== 统一学习周期完成，共学习{}条数据，耗时{}ms ===", totalLearned, duration);

        } catch (Exception e) {
            log.error("统一学习周期发生异常", e);
        }
    }

    @Transactional
    public int learnFromUnlearnedCases() {
        log.info("=== 步骤1: 学习未学习的案例 ===");
        
        List<CaseMemory> unlearnedCases = getUnlearnedCases();
        int learnedCount = 0;

        for (CaseMemory caseMemory : unlearnedCases) {
            try {
                if (learnedCaseIds.contains(caseMemory.getCaseNo())) {
                    continue;
                }

                learnFromSingleCase(caseMemory);
                markCaseAsLearned(caseMemory);
                learnedCaseIds.add(caseMemory.getCaseNo());
                learnedCount++;

            } catch (Exception e) {
                log.warn("案例学习失败 {}: {}", caseMemory.getCaseNo(), e.getMessage());
            }
        }

        log.info("案例学习完成，成功学习{}个案例", learnedCount);
        return learnedCount;
    }

    private List<CaseMemory> getUnlearnedCases() {
        return caseMemoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CaseMemory>()
                        .eq("learned", 0)
                        .eq("deleted", 0)
                        .orderByDesc("created_at")
                        .last("LIMIT 100")
        );
    }

    public void learnFromSingleCase(CaseMemory caseMemory) throws JsonProcessingException {
        log.info("开始学习案例: {}", caseMemory.getCaseNo());

        String caseType = caseMemory.getCaseType();
        boolean isSuccess = "success".equalsIgnoreCase(caseType) || 
                           "excellent".equalsIgnoreCase(caseMemory.getQualityTag());

        if (isSuccess) {
            extractKnowledgeFromSuccessCase(caseMemory);
        } else {
            extractKnowledgeFromFailureCase(caseMemory);
        }

        extractDimensionsFromCase(caseMemory);
        recordLearningToMemory(caseMemory, isSuccess);

        log.info("案例学习完成: {}", caseMemory.getCaseNo());
    }

    private void extractKnowledgeFromSuccessCase(CaseMemory caseMemory) throws JsonProcessingException {
        log.info("提取成功案例知识: {}", caseMemory.getCaseNo());

        Knowledge knowledge = new Knowledge();
        knowledge.setType("SUCCESS_EXPERIENCE");
        knowledge.setTitle(generateKnowledgeTitle(caseMemory, "成功经验"));
        knowledge.setContent(generateDetailedCaseContent(caseMemory));
        knowledge.setSource("CASE_" + caseMemory.getCaseNo());
        knowledge.setSourceId(caseMemory.getCaseNo());
        knowledge.setTags(objectMapper.writeValueAsString(buildTags(caseMemory, Arrays.asList("成功经验", "案例分析"))));
        knowledge.setConfidence(calculateConfidence(caseMemory));

        knowledgeService.storeKnowledge(knowledge);
        log.info("成功案例知识已存储: {}", knowledge.getKnowledgeId());
    }

    private void extractKnowledgeFromFailureCase(CaseMemory caseMemory) throws JsonProcessingException {
        log.info("提取失败案例教训: {}", caseMemory.getCaseNo());

        Knowledge knowledge = new Knowledge();
        knowledge.setType("FAILURE_LESSON");
        knowledge.setTitle(generateKnowledgeTitle(caseMemory, "失败教训"));
        knowledge.setContent(generateFailureAnalysisContent(caseMemory));
        knowledge.setSource("CASE_" + caseMemory.getCaseNo());
        knowledge.setSourceId(caseMemory.getCaseNo());
        knowledge.setTags(objectMapper.writeValueAsString(buildTags(caseMemory, Arrays.asList("失败教训", "避坑指南"))));
        knowledge.setConfidence(0.8);

        knowledgeService.storeKnowledge(knowledge);
        log.info("失败案例教训已存储: {}", knowledge.getKnowledgeId());
    }

    private void extractDimensionsFromCase(CaseMemory caseMemory) {
        try {
            Map<String, String> dimensions = new HashMap<>();

            String videoTitle = caseMemory.getProductName();
            String videoDescription = caseMemory.getInputParams();

            if (videoTitle != null || videoDescription != null) {
                dimensions.putAll(dimensionService.extractFromVideo(
                    videoTitle,
                    videoDescription,
                    caseMemory.getCategory(),
                    caseMemory.getOutputResult()
                ));
            }

            if (!dimensions.isEmpty()) {
                String scenarioType = inferScenarioType(caseMemory);
                dimensionService.recordCaseDimensions(
                    caseMemory.getId(),
                    dimensions,
                    scenarioType,
                    "success".equalsIgnoreCase(caseMemory.getCaseType())
                );
                log.debug("案例维度已记录: caseId={}, 维度数={}", caseMemory.getId(), dimensions.size());
            }
        } catch (Exception e) {
            log.warn("提取案例维度失败: {}", caseMemory.getCaseNo(), e);
        }
    }

    private void recordLearningToMemory(CaseMemory caseMemory, boolean isSuccess) {
        try {
            log.info("记录学习结果: caseNo={}, isSuccess={}, cvr={}",
                    caseMemory.getCaseNo(), isSuccess, caseMemory.getCvr());
        } catch (Exception e) {
            log.warn("记录学习到记忆系统失败: {}", caseMemory.getCaseNo(), e);
        }
    }

    private Map<String, Object> parseInputParams(String inputParamsJson) {
        if (inputParamsJson == null || inputParamsJson.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(inputParamsJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private Map<String, Object> buildOutputResult(CaseMemory caseMemory) {
        Map<String, Object> result = new HashMap<>();
        result.put("caseNo", caseMemory.getCaseNo());
        result.put("cvr", caseMemory.getCvr());
        result.put("gmv", caseMemory.getGmv());
        result.put("playCount", caseMemory.getPlayCount());
        result.put("likeCount", caseMemory.getLikeCount());
        result.put("success", "success".equalsIgnoreCase(caseMemory.getCaseType()));
        return result;
    }

    public int discoverAndLearnPatterns() {
        log.info("=== 步骤2: 发现和学习规律 ===");
        
        int patternCount = 0;

        try {
            List<Map<String, Object>> explosivePatterns = discoverExplosivePatterns();
            for (Map<String, Object> pattern : explosivePatterns) {
                storePatternAsKnowledge(pattern, "EXPLOSIVE_PATTERN");
                patternCount++;
            }

            List<Map<String, Object>> failurePatterns = discoverFailurePatterns();
            for (Map<String, Object> pattern : failurePatterns) {
                storePatternAsKnowledge(pattern, "FAILURE_PATTERN");
                patternCount++;
            }

        } catch (Exception e) {
            log.error("发现规律失败", e);
        }

        log.info("规律发现完成，共发现{}个规律", patternCount);
        return patternCount;
    }

    private List<Map<String, Object>> discoverExplosivePatterns() {
        try {
            return jdbcTemplate.queryForList("""
                SELECT 
                    lc.id as case_id,
                    lc.product_name,
                    lc.category,
                    lc.video_title,
                    lc.cvr,
                    lc.gmv,
                    lc.play_count,
                    lc.input_params
                FROM biz_video_task lc
                WHERE lc.cvr >= 0.05 
                  AND lc.status = 'COMPLETED'
                  AND lc.created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
                  AND lc.deleted = 0
                ORDER BY lc.cvr DESC, lc.gmv DESC
                LIMIT 20
                """);
        } catch (Exception e) {
            log.error("查询爆款规律失败", e);
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> discoverFailurePatterns() {
        try {
            return jdbcTemplate.queryForList("""
                SELECT 
                    lc.id as case_id,
                    lc.product_name,
                    lc.category,
                    lc.video_title,
                    lc.cvr,
                    lc.error_message,
                    lc.input_params
                FROM biz_video_task lc
                WHERE (lc.status = 'FAILED' OR lc.cvr < 0.01)
                  AND lc.created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
                  AND lc.deleted = 0
                ORDER BY lc.created_at DESC
                LIMIT 20
                """);
        } catch (Exception e) {
            log.error("查询失败规律失败", e);
            return new ArrayList<>();
        }
    }

    private void storePatternAsKnowledge(Map<String, Object> pattern, String patternType) {
        try {
            Knowledge knowledge = new Knowledge();
            knowledge.setType(patternType);
            
            String productName = (String) pattern.get("product_name");
            String title = "EXPLOSIVE_PATTERN".equals(patternType) ? 
                "爆款规律: " + productName : "失败规律: " + productName;
            knowledge.setTitle(title);

            StringBuilder content = new StringBuilder();
            content.append("== 规律发现报告 ==\n\n");
            content.append("产品: ").append(productName).append("\n");
            content.append("分类: ").append(pattern.get("category")).append("\n");
            
            if ("EXPLOSIVE_PATTERN".equals(patternType)) {
                content.append("CVR: ").append(pattern.get("cvr")).append("\n");
                content.append("GMV: ").append(pattern.get("gmv")).append("\n");
                content.append("播放量: ").append(pattern.get("play_count")).append("\n");
            } else {
                content.append("错误信息: ").append(pattern.get("error_message")).append("\n");
            }
            
            content.append("\n== 关键参数 ==\n");
            content.append(pattern.getOrDefault("input_params", "无")).append("\n");

            knowledge.setContent(content.toString());
            knowledge.setSource("PATTERN_DISCOVERY");
            knowledge.setTags(objectMapper.writeValueAsString(
                Arrays.asList(patternType, productName != null ? productName : "未知")));
            knowledge.setConfidence("EXPLOSIVE_PATTERN".equals(patternType) ? 0.9 : 0.85);

            knowledgeService.storeKnowledge(knowledge);
            log.info("{}已存储: {}", patternType, title);

        } catch (Exception e) {
            log.error("存储规律知识失败", e);
        }
    }

    public int performExpertAnalysis() {
        log.info("=== 步骤3: 执行专家分析 ===");
        
        int analysisCount = 0;

        try {
            List<CaseMemory> recentCases = getRecentHighValueCases(10);

            for (CaseMemory caseMemory : recentCases) {
                try {
                    performMultiExpertAnalysis(caseMemory);
                    analysisCount++;
                } catch (Exception e) {
                    log.warn("专家分析失败: {}", caseMemory.getCaseNo(), e);
                }
            }

        } catch (Exception e) {
            log.error("专家分析批量执行失败", e);
        }

        log.info("专家分析完成，共分析{}个案例", analysisCount);
        return analysisCount;
    }

    private List<CaseMemory> getRecentHighValueCases(int limit) {
        return caseMemoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CaseMemory>()
                        .ge("cvr", 0.03)
                        .eq("deleted", 0)
                        .orderByDesc("cvr")
                        .last("LIMIT " + limit)
        );
    }

    private void performMultiExpertAnalysis(CaseMemory caseMemory) {
        log.info("对案例{}执行多专家分析", caseMemory.getCaseNo());

        String[] expertRoles = {"product_manager", "visual_designer", "video_editor", 
                               "operations_expert", "data_analyst"};

        for (String roleCode : expertRoles) {
            try {
                String analysis = analyzeWithExpert(roleCode, caseMemory);
                
                if (analysis != null && !analysis.isEmpty()) {
                    storeExpertAnalysis(roleCode, caseMemory, analysis);
                }
            } catch (Exception e) {
                log.warn("专家{}分析失败: {}", roleCode, e.getMessage());
            }
        }
    }

    private String analyzeWithExpert(String roleCode, CaseMemory caseMemory) {
        try {
            String systemPrompt = buildExpertSystemPrompt(roleCode);
            String userMessage = buildExpertUserMessage(caseMemory);

            return gptChatService.chat(systemPrompt, userMessage);
        } catch (Exception e) {
            log.error("调用AI进行专家分析失败: role={}", roleCode, e);
            return null;
        }
    }

    private String buildExpertSystemPrompt(String roleCode) {
        Map<String, String> expertPrompts = new HashMap<>();
        expertPrompts.put("product_manager", "你是一位资深产品经理，擅长分析产品的市场定位、用户痛点和转化路径。请从产品经理的专业视角分析这个案例。");
        expertPrompts.put("visual_designer", "你是一位顶级视觉设计师，精通色彩心理学、构图美学和品牌视觉。请从视觉设计的专业视角分析这个案例。");
        expertPrompts.put("video_editor", "你是一位专业视频剪辑师，熟悉节奏控制、转场设计和后期制作。请从视频制作的专业视角分析这个案例。");
        expertPrompts.put("operations_expert", "你是一位数据驱动的运营专家，擅长ROI优化和增长策略。请从运营优化的专业视角分析这个案例。");
        expertPrompts.put("data_analyst", "你是一位高级数据分析师，擅长通过数据发现业务洞察。请从数据分析的专业视角分析这个案例。");

        return expertPrompts.getOrDefault(roleCode, "你是一位专业的商业分析师，请分析这个案例。");
    }

    private String buildExpertUserMessage(CaseMemory caseMemory) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析以下视频案例:\n\n");
        sb.append("产品名称: ").append(caseMemory.getProductName()).append("\n");
        sb.append("案例编号: ").append(caseMemory.getCaseNo()).append("\n");
        sb.append("CVR: ").append(caseMemory.getCvr()).append("\n");
        sb.append("GMV: ").append(caseMemory.getGmv()).append("\n");
        sb.append("播放量: ").append(caseMemory.getPlayCount()).append("\n");
        sb.append("点赞数: ").append(caseMemory.getLikeCount()).append("\n");
        sb.append("视频标题: ").append(caseMemory.getProductName()).append("\n");

        String outputResult = caseMemory.getOutputResult();
        if (outputResult != null && outputResult.length() < 2000) {
            sb.append("内容摘要: ").append(outputResult).append("\n");
        }

        sb.append("\n请提供:\n");
        sb.append("1. 核心发现（3-5个要点）\n");
        sb.append("2. 成功/失败原因分析\n");
        sb.append("3. 可复用的策略建议\n");
        sb.append("4. 需要避免的坑点\n");

        return sb.toString();
    }

    private void storeExpertAnalysis(String roleCode, CaseMemory caseMemory, String analysis) {
        try {
            Knowledge knowledge = new Knowledge();
            knowledge.setType("EXPERT_ANALYSIS_" + roleCode.toUpperCase());

            Map<String, String> roleNames = new HashMap<>();
            roleNames.put("product_manager", "产品经理");
            roleNames.put("visual_designer", "视觉设计师");
            roleNames.put("video_editor", "视频剪辑");
            roleNames.put("operations_expert", "运营专家");
            roleNames.put("data_analyst", "数据分析师");

            String roleName = roleNames.getOrDefault(roleCode, "专家");
            knowledge.setTitle(roleName + "分析: " + caseMemory.getProductName());

            StringBuilder content = new StringBuilder();
            content.append("== ").append(roleName).append("分析报告 ==\n\n");
            content.append("案例: ").append(caseMemory.getCaseNo()).append("\n");
            content.append("产品: ").append(caseMemory.getProductName()).append("\n");
            content.append("分析时间: ").append(LocalDateTime.now()).append("\n\n");
            content.append(analysis);

            knowledge.setContent(content.toString());
            knowledge.setSource("EXPERT_ANALYSIS");
            knowledge.setSourceId(caseMemory.getCaseNo());
            knowledge.setTags(objectMapper.writeValueAsString(
                Arrays.asList("专家分析", roleName, caseMemory.getProductName())));
            knowledge.setConfidence(0.88);

            knowledgeService.storeKnowledge(knowledge);
            log.info("{}分析已存储: {}", roleName, caseMemory.getCaseNo());

        } catch (Exception e) {
            log.error("存储专家分析失败: role={}", roleCode, e);
        }
    }

    private void markCaseAsLearned(CaseMemory caseMemory) {
        try {
            caseMemory.setLearned(1);
            caseMemoryMapper.updateById(caseMemory);
        } catch (Exception e) {
            log.warn("标记案例为已学习失败: {}", caseMemory.getCaseNo(), e);
        }
    }

    private String generateKnowledgeTitle(CaseMemory caseMemory, String prefix) {
        String productName = caseMemory.getProductName() != null ? 
            caseMemory.getProductName() : caseMemory.getCaseNo();
        return prefix + ": " + productName;
    }

    private String generateDetailedCaseContent(CaseMemory caseMemory) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("== 成功案例详细分析 ==\n\n");
        sb.append("基本信息:\n");
        sb.append("- 编号: ").append(caseMemory.getCaseNo()).append("\n");
        sb.append("- 产品: ").append(caseMemory.getProductName()).append("\n");
        sb.append("- 分类: ").append(caseMemory.getCategory()).append("\n");
        sb.append("- 平台: ").append(caseMemory.getPlatform()).append("\n\n");

        sb.append("核心指标:\n");
        if (caseMemory.getCvr() != null) {
            sb.append("- CVR: ").append(String.format("%.2f%%", caseMemory.getCvr())).append("\n");
        }
        if (caseMemory.getGmv() != null) {
            sb.append("- GMV: ").append(formatNumber(caseMemory.getGmv())).append("\n");
        }
        if (caseMemory.getPlayCount() != null) {
            sb.append("- 播放: ").append(formatNumber(caseMemory.getPlayCount())).append("\n");
        }
        if (caseMemory.getLikeCount() != null) {
            sb.append("- 点赞: ").append(formatNumber(caseMemory.getLikeCount())).append("\n");
        }
        sb.append("\n");

        sb.append("成功要素:\n");
        sb.append("- 内容吸引力: [待AI补充分析]\n");
        sb.append("- 转化路径: [待AI补充分析]\n");
        sb.append("- 用户心理把握: [待AI补充分析]\n");
        sb.append("- 产品展示方式: [待AI补充分析]\n\n");

        if (caseMemory.getLessonLearned() != null && !caseMemory.getLessonLearned().isEmpty()) {
            sb.append("经验总结:\n");
            String[] lessons = caseMemory.getLessonLearned().split("[,\\n]");
            for (String lesson : lessons) {
                if (!lesson.trim().isEmpty()) {
                    sb.append("- ").append(lesson.trim()).append("\n");
                }
            }
        }

        return sb.toString();
    }

    private String generateFailureAnalysisContent(CaseMemory caseMemory) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("== 失败案例深度分析 ==\n\n");
        sb.append("基本信息:\n");
        sb.append("- 编号: ").append(caseMemory.getCaseNo()).append("\n");
        sb.append("- 产品: ").append(caseMemory.getProductName()).append("\n");
        sb.append("- 错误信息: ").append(caseMemory.getFailureReason()).append("\n\n");

        sb.append("失败原因分析:\n");
        sb.append("- 内容质量问题: [待AI补充分析]\n");
        sb.append("- 定位偏差: [待AI补充分析]\n");
        sb.append("- 执行失误: [待AI补充分析]\n");
        sb.append("- 外部因素: [待AI补充分析]\n\n");

        sb.append("改进建议:\n");
        sb.append("- 短期优化: [待AI补充]\n");
        sb.append("- 中期调整: [待AI补充]\n");
        sb.append("- 长期策略: [待AI补充]\n");

        return sb.toString();
    }

    private List<String> buildTags(CaseMemory caseMemory, List<String> baseTags) {
        List<String> tags = new ArrayList<>(baseTags);
        
        if (caseMemory.getProductName() != null) {
            tags.add(caseMemory.getProductName());
        }
        if (caseMemory.getCategory() != null) {
            tags.add(caseMemory.getCategory());
        }
        if (caseMemory.getPlatform() != null) {
            tags.add(caseMemory.getPlatform());
        }

        return tags;
    }

    private double calculateConfidence(CaseMemory caseMemory) {
        double confidence = 0.7;

        if (caseMemory.getCvr() != null && caseMemory.getCvr().compareTo(0.05) > 0) {
            confidence += 0.1;
        }
        if (caseMemory.getGmv() != null && caseMemory.getGmv().compareTo(new java.math.BigDecimal("10000")) > 0) {
            confidence += 0.1;
        }
        if (caseMemory.getPlayCount() != null && caseMemory.getPlayCount().compareTo(new java.math.BigDecimal("100000")) > 0) {
            confidence += 0.1;
        }

        return Math.min(confidence, 0.99);
    }

    private String inferScenarioType(CaseMemory caseMemory) {
        if (caseMemory.getPlatform() != null) {
            return caseMemory.getPlatform().toUpperCase();
        }
        if (caseMemory.getCategory() != null) {
            return caseMemory.getCategory().toUpperCase();
        }
        return "GENERAL";
    }

    private String formatNumber(Number number) {
        if (number == null) return "0";
        
        double value = number.doubleValue();
        if (value >= 10000) {
            return String.format("%.1f万", value / 10000);
        } else if (value >= 1000) {
            return String.format("%.1fK", value / 1000);
        } else {
            return String.format("%.0f", value);
        }
    }

    /**
     * 从视频生成案例中自动学习
     * 迁移自 ExpertPerspectiveLearningService
     */
    public void learnFromVideoCase(Map<String, Object> caseData, boolean isSuccess) {
        try {
            log.info("开始从视频案例中学习: success={}, 数据={}", isSuccess, caseData.keySet());

            String sceneType = (String) caseData.get("sceneType");
            String frameType = (String) caseData.get("frameType");
            String topic = (String) caseData.get("topic");
            String race = (String) caseData.get("race");
            String role = (String) caseData.get("role");
            String videoUrl = (String) caseData.get("videoUrl");
            String errorMessage = (String) caseData.get("errorMessage");

            // 构建案例描述
            StringBuilder caseDescription = new StringBuilder();
            caseDescription.append("视频生成案例\n");
            caseDescription.append("场景类型: ").append(sceneType).append("\n");
            caseDescription.append("运镜类型: ").append(frameType).append("\n");
            caseDescription.append("主题: ").append(topic).append("\n");
            caseDescription.append("人物: ").append(race).append(" ").append(role).append("\n");
            if (isSuccess) {
                caseDescription.append("结果: 成功\n");
                caseDescription.append("视频URL: ").append(videoUrl).append("\n");
            } else {
                caseDescription.append("结果: 失败\n");
                caseDescription.append("错误信息: ").append(errorMessage).append("\n");
            }

            // 匹配相关专家角色
            List<String> matchedExperts = matchExpertsForVideoCase(sceneType, frameType, topic);
            log.info("匹配到{}位相关专家: {}", matchedExperts.size(), matchedExperts);

            // 让每位专家从案例中学习
            for (String expertCode : matchedExperts) {
                try {
                    ExpertRoleConfig expert = expertRoleService.getExpertRole(expertCode);
                    if (expert == null || !"ACTIVE".equals(expert.getStatus())) {
                        continue;
                    }

                    log.info("{}专家开始从案例中学习", expert.getRoleName());

                    // 构建学习提示词
                    String learningPrompt = buildVideoCaseLearningPrompt(expert, caseDescription.toString(), isSuccess);

                    // 调用AI进行深度学习
                    String aiResponse = gptChatService.chat(learningPrompt);

                    // 存储学习结果作为知识
                    storeVideoCaseLearningKnowledge(expert, caseData, aiResponse, isSuccess);

                    log.info("{}专家学习完成", expert.getRoleName());
                } catch (Exception e) {
                    log.warn("专家{}学习失败: {}", expertCode, e.getMessage());
                }
            }

            log.info("视频案例学习完成: success={}, 专家数={}", isSuccess, matchedExperts.size());

        } catch (Exception e) {
            log.error("从视频案例学习失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 匹配视频案例相关的专家角色
     */
    private List<String> matchExpertsForVideoCase(String sceneType, String frameType, String topic) {
        List<String> matched = new ArrayList<>();

        // 根据场景类型匹配
        if (sceneType != null) {
            String sceneLower = sceneType.toLowerCase();
            if (sceneLower.contains("产品") || sceneLower.contains("商品") || sceneLower.contains("product")) {
                matched.add("product_manager");
            }
            if (sceneLower.contains("视觉") || sceneLower.contains("设计") || sceneLower.contains("visual")) {
                matched.add("visual_designer");
            }
            if (sceneLower.contains("视频") || sceneLower.contains("剪辑") || sceneLower.contains("video")) {
                matched.add("video_editor");
            }
            if (sceneLower.contains("图片") || sceneLower.contains("图像") || sceneLower.contains("image")) {
                matched.add("image_analyst");
            }
            if (sceneLower.contains("运营") || sceneLower.contains("operation")) {
                matched.add("operations_expert");
            }
            if (sceneLower.contains("数据") || sceneLower.contains("data")) {
                matched.add("data_analyst");
            }
        }

        // 根据主题匹配
        if (topic != null) {
            String topicLower = topic.toLowerCase();
            if (topicLower.contains("文案") || topicLower.contains("脚本") || topicLower.contains("copy")) {
                matched.add("copywriter");
            }
            if (topicLower.contains("导演") || topicLower.contains("编导") || topicLower.contains("director")) {
                matched.add("director");
            }
            if (topicLower.contains("营销") || topicLower.contains("推广") || topicLower.contains("marketing")) {
                matched.add("marketing_planner");
            }
            if (topicLower.contains("提示词") || topicLower.contains("prompt") || topicLower.contains("ai")) {
                matched.add("prompt_engineer");
            }
        }

        // 根据运镜类型匹配
        if (frameType != null) {
            matched.add("video_editor");
            matched.add("director");
        }

        // 去重
        return new ArrayList<>(new LinkedHashSet<>(matched));
    }

    /**
     * 构建视频案例学习提示词
     */
    private String buildVideoCaseLearningPrompt(ExpertRoleConfig expert, String caseDescription, boolean isSuccess) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你现在是").append(expert.getRoleName()).append("，需要从这个视频生成案例中学习经验教训。\n\n");
        prompt.append("案例描述:\n").append(caseDescription).append("\n\n");

        if (isSuccess) {
            prompt.append("这是一个成功案例，请分析:\n");
            prompt.append("1. 成功关键因素是什么？\n");
            prompt.append("2. 哪些策略或方法值得复用？\n");
            prompt.append("3. 对你的专业领域有什么启发？\n");
        } else {
            prompt.append("这是一个失败案例，请分析:\n");
            prompt.append("1. 失败的主要原因是什么？\n");
            prompt.append("2. 如何避免类似问题？\n");
            prompt.append("3. 有什么改进建议？\n");
        }

        prompt.append("\n请用简洁的语言总结你的学习心得，便于后续应用到类似案例中。");
        return prompt.toString();
    }

    /**
     * 存储视频案例学习知识
     */
    private void storeVideoCaseLearningKnowledge(ExpertRoleConfig expert, Map<String, Object> caseData, 
                                                  String analysis, boolean isSuccess) {
        try {
            Knowledge knowledge = new Knowledge();
            knowledge.setType(isSuccess ? "VIDEO_CASE_SUCCESS" : "VIDEO_CASE_FAILURE");
            knowledge.setTitle(expert.getRoleName() + "学习: " + caseData.get("sceneType"));

            StringBuilder content = new StringBuilder();
            content.append("== ").append(expert.getRoleName()).append("学习报告 ==\n\n");
            content.append("场景: ").append(caseData.get("sceneType")).append("\n");
            content.append("运镜: ").append(caseData.get("frameType")).append("\n");
            content.append("主题: ").append(caseData.get("topic")).append("\n");
            content.append("结果: ").append(isSuccess ? "成功" : "失败").append("\n\n");
            content.append("学习心得:\n").append(analysis);

            knowledge.setContent(content.toString());
            knowledge.setSource("VIDEO_CASE_LEARNING");
            knowledge.setTags(objectMapper.writeValueAsString(
                Arrays.asList("视频案例学习", expert.getRoleName(), caseData.get("sceneType"))));
            knowledge.setConfidence(isSuccess ? 0.9 : 0.85);

            knowledgeService.storeKnowledge(knowledge);
            log.info("{}学习知识已存储", expert.getRoleName());
        } catch (Exception e) {
            log.error("存储视频案例学习知识失败: {}", e.getMessage());
        }
    }
}
