package com.ecommerce.workflow.agent.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.workflow.agent.*;
import com.ecommerce.workflow.entity.CaseMemory;
import com.ecommerce.workflow.entity.DeliveryData;
import com.ecommerce.workflow.engine.WorkflowEngine;
import com.ecommerce.workflow.entity.SkillConfig;
import com.ecommerce.workflow.entity.WorkflowDefinition;
import com.ecommerce.workflow.mapper.CaseMemoryMapper;
import com.ecommerce.workflow.mapper.DeliveryDataMapper;
import com.ecommerce.workflow.mapper.WorkflowDefinitionMapper;
import com.ecommerce.workflow.service.ai.GptChatService;
import com.ecommerce.workflow.service.evolution.EvolutionCoreService;
import com.ecommerce.workflow.service.evolution.SkillConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class SelfEvolutionAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(SelfEvolutionAgent.class);
    private final GptChatService gptChatService;
    private final SkillConfigService skillConfigService;
    private final EvolutionCoreService evolutionCoreService;
    private final WorkflowEngine workflowEngine;
    private final CaseMemoryMapper caseMemoryMapper;
    private final DeliveryDataMapper deliveryDataMapper;
    private final WorkflowDefinitionMapper workflowDefinitionMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SelfEvolutionAgent(GptChatService gptChatService, SkillConfigService skillConfigService, 
                             EvolutionCoreService evolutionCoreService, WorkflowEngine workflowEngine, 
                             CaseMemoryMapper caseMemoryMapper, DeliveryDataMapper deliveryDataMapper, 
                             WorkflowDefinitionMapper workflowDefinitionMapper) {
        this.gptChatService = gptChatService;
        this.skillConfigService = skillConfigService;
        this.evolutionCoreService = evolutionCoreService;
        this.workflowEngine = workflowEngine;
        this.caseMemoryMapper = caseMemoryMapper;
        this.deliveryDataMapper = deliveryDataMapper;
        this.workflowDefinitionMapper = workflowDefinitionMapper;
    }

    @Override
    public String getAgentType() {
        return "self_evolution";
    }

    @Override
    public String getName() {
        return "自进化优化Agent";
    }

    @Override
    public AgentResponse process(AgentRequest request) throws Exception {
        log.info("自进化Agent处理请求: userId={}, message={}", request.getUserId(), request.getMessage());

        String action = extractAction(request);

        return switch (action) {
            case "configure_skill" -> configureSkillFromConversation(request);
            case "analyze_performance" -> analyzePerformance(request);
            case "evolve_now" -> triggerEvolution(request);
            case "record_result" -> recordExecutionResult(request);
            case "ingest_delivery" -> ingestDeliveryData(request);
            case "query_memory" -> queryCaseMemory(request);
            default -> handleGeneralEvolution(request);
        };
    }

    private AgentResponse configureSkillFromConversation(AgentRequest request) {
        log.info("从对话中自动配置Skill: {}", request.getMessage());

        String analysisPrompt = String.format("""
                你是系统配置专家。用户想要配置或调整系统的能力(Skill)。
                
                当前可用的Skill:
                1. scorer_6d - 6维爆品评分器(选品权重/阈值)
                2. script_generator - AI脚本生成器(风格/长度/平台)
                3. video_generator - VEO视频生成器(模型/参数)
                4. compliance_checker - 合规检测器(严格度/规则)
                5. causal_analyzer - 因果归因分析器(方法/因子数)

                用户需求: %s

                请分析用户想配置哪个Skill，以及具体要怎么配置。
                返回JSON格式:
                {
                  "skill_code": "scorer_6d",
                  "skill_name": "6维爆品评分器",
                  "action": "create_or_upgrade",
                  "params": {
                    "weights": [0.25,0.20,0.15,0.15,0.15,0.10],
                    "sThreshold": 85,
                    "其他参数": "值"
                  },
                  "reason": "为什么这样配置",
                  "confidence": 0.9,
                  "suggested_workflow": "smart_selection_workflow"
                }
                
                如果用户只是询问而不需要配置，action设为"info_only"。
                """, request.getMessage());

        String aiResult = gptChatService.chatWithThinking(analysisPrompt, request.getMessage());

        Map<String, Object> config;
        try {
            config = objectMapper.readValue(aiResult, Map.class);
        } catch (Exception e) {
            log.warn("解析AI配置结果失败", e);
            return AgentResponse.success("我理解您的需求是: " + request.getMessage() + "\n\n让我帮您分析和配置最合适的方案...");
        }

        String skillCode = (String) config.getOrDefault("skill_code", "");
        String action = (String) config.getOrDefault("action", "info_only");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) config.getOrDefault("params", new HashMap<>());
        String reason = (String) config.getOrDefault("reason", "用户需求驱动");

        if ("info_only".equals(action)) {
            return AgentResponse.success(generateInfoResponse(config, params));
        }

        SkillConfig updatedSkill = skillConfigService.createOrUpdateSkill(
                skillCode,
                (String) config.getOrDefault("skill_name", skillCode),
                inferCategory(skillCode),
                params,
                reason
        );

        String suggestedWorkflow = (String) config.get("suggested_workflow");
        StringBuilder reply = new StringBuilder();
        reply.append(String.format("✅ **Skill配置完成!**\n\n"));
        reply.append(String.format("- **Skill**: %s v%s\n", updatedSkill.getSkillName(), updatedSkill.getVersion()));
        reply.append(String.format("- **原因**: %s\n", reason));
        reply.append(String.format("- **参数**: %s\n", formatParams(params)));

        if (suggestedWorkflow != null && !suggestedWorkflow.isEmpty()) {
            WorkflowDefinition wf = findWorkflowByCode(suggestedWorkflow);
            if (wf != null) {
                reply.append(String.format("\n- 🚀 已为您准备好工作流: **%s**\n", wf.getWorkflowName()));
                reply.append("   是否现在执行? 我可以立即启动。\n");
                reply.append(String.format("\n💡 **记忆已更新**: 下次类似需求我会自动使用这套配置"));
            }
        }

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("skillConfig", updatedSkill);
        responseData.put("configuredParams", params);
        responseData.put("suggestedWorkflow", suggestedWorkflow);

        return AgentResponse.success(reply.toString(), responseData);
    }

    private AgentResponse analyzePerformance(AgentRequest request) {
        log.info("分析系统性能和进化状态...");

        evolutionCoreService.executeEvolutionCycle();

        List<SkillConfig> allSkills = skillConfigService.getAllActiveSkills();
        long totalCases = caseMemoryMapper.selectCount(
                new QueryWrapper<CaseMemory>().eq("deleted", 0));
        long successCases = caseMemoryMapper.selectCount(
                new QueryWrapper<CaseMemory>().eq("quality_tag", "success").eq("deleted", 0));

        StringBuilder report = new StringBuilder();
        report.append("📊 **系统进化状态报告**\n\n");
        report.append(String.format("**记忆库**: %d个案例 (成功%d / 失败%d)\n\n",
                totalCases, successCases, totalCases - successCases));

        report.append("**当前活跃Skills:**\n");
        for (SkillConfig skill : allSkills) {
            String rateStr = skill.getSuccessRate() != null ? skill.getSuccessRate().toString() : "--";
            report.append(String.format("- `%s` v%s | 使用%d次 | 成功率%s | CVR %.2f%%\n",
                    skill.getSkillName(), skill.getVersion(),
                    skill.getUsageCount(), rateStr,
                    skill.getAvgCvr() != null ? skill.getAvgCvr() * 100 : 0.0));
        }

        report.append("\n🔄 **进化周期已执行**\n");

        return AgentResponse.success(report.toString(),
                Map.of("skills", allSkills));
    }

    private AgentResponse triggerEvolution(AgentRequest request) {
        log.info("手动触发自进化...");

        evolutionCoreService.executeEvolutionCycle();

        String summary = "✅ 进化周期已执行完成";

        return AgentResponse.success(summary, Map.of("status", "completed"));
    }

    private AgentResponse recordExecutionResult(AgentRequest request) {
        log.info("记录执行结果到记忆库...");

        Map<String, Object> context = request.getContext() != null ? request.getContext() : new HashMap<>();
        String skillCode = (String) context.getOrDefault("skillCode", "unknown");
        boolean success = Boolean.TRUE.equals(context.get("success"));
        Double cvr = context.containsKey("cvr") ? ((Number) context.get("cvr")).doubleValue() : null;
        BigDecimal gmv = context.containsKey("gmv")
                ? new BigDecimal(context.get("gmv").toString()) : null;

        CaseMemory caseMemory = new CaseMemory();
        caseMemory.setCaseNo("CASE_" + System.currentTimeMillis());
        caseMemory.setCaseType(success ? "success" : "fail");
        caseMemory.setProductId(context.containsKey("productId")
                ? ((Number) context.get("productId")).longValue() : null);
        caseMemory.setProductName((String) context.getOrDefault("productName", ""));
        caseMemory.setCategory((String) context.getOrDefault("category", ""));
        caseMemory.setPlatform((String) context.getOrDefault("platform", "douyin"));
        caseMemory.setSkillVersionSnapshot(skillCode);
        try {
            Map<String, Object> enhancedInputParams = new HashMap<>(context);
            if (!context.containsKey("personDetails")) {
                enhancedInputParams.put("personDetails", extractPersonDetails(context));
            }
            if (!context.containsKey("sceneDetails")) {
                enhancedInputParams.put("sceneDetails", extractSceneDetails(context));
            }
            if (!context.containsKey("cameraDetails")) {
                enhancedInputParams.put("cameraDetails", extractCameraDetails(context));
            }
            if (!context.containsKey("actionDetails")) {
                enhancedInputParams.put("actionDetails", extractActionDetails(context));
            }
            if (!context.containsKey("productDetails")) {
                enhancedInputParams.put("productDetails", extractProductDetails(context));
            }
            caseMemory.setInputParams(objectMapper.writeValueAsString(enhancedInputParams));
            
            Map<String, Object> enhancedOutputResult = new HashMap<>();
            if (context.containsKey("outputResult")) {
                enhancedOutputResult.putAll((Map<String, Object>) context.get("outputResult"));
            }
            enhancedOutputResult.put("successMetrics", extractSuccessMetrics(context, success, cvr, gmv));
            caseMemory.setOutputResult(objectMapper.writeValueAsString(enhancedOutputResult));
        } catch (Exception e) {
            try {
                caseMemory.setInputParams(objectMapper.writeValueAsString(context));
                caseMemory.setOutputResult("{}");
            } catch (Exception ex) {
                caseMemory.setInputParams("{}");
                caseMemory.setOutputResult("{}");
            }
        }
        caseMemory.setPlayCount(context.containsKey("playCount")
                ? new BigDecimal(context.get("playCount").toString()) : null);
        caseMemory.setLikeCount(context.containsKey("likeCount")
                ? new BigDecimal(context.get("likeCount").toString()) : null);
        caseMemory.setCommentCount(context.containsKey("commentCount")
                ? new BigDecimal(context.get("commentCount").toString()) : null);
        caseMemory.setShareCount(context.containsKey("shareCount")
                ? new BigDecimal(context.get("shareCount").toString()) : null);
        caseMemory.setCollectCount(context.containsKey("collectCount")
                ? new BigDecimal(context.get("collectCount").toString()) : null);
        caseMemory.setGmv(gmv);
        caseMemory.setCvr(cvr);
        caseMemory.setConversionCount(context.containsKey("conversionCount")
                ? ((Number) context.get("conversionCount")).intValue() : null);
        caseMemory.setQualityTag(success ? "success" : "fail");
        caseMemory.setFailureReason(success ? null : (String) context.getOrDefault("error", "未知错误"));
        caseMemory.setLessonLearned(generateDetailedLesson(context, success));
        caseMemory.setCreatedAt(LocalDateTime.now());
        caseMemory.setDeleted(0);

        caseMemoryMapper.insert(caseMemory);

        skillConfigService.recordUsage(skillCode, success, cvr, gmv);

        log.info("案例记录完成: caseNo={}, type={}", caseMemory.getCaseNo(), caseMemory.getCaseType());

        String reply = generateDetailedRecordReply(caseMemory, skillCode, success, cvr);

        return AgentResponse.success(reply, Map.of("caseMemoryId", caseMemory.getId()));
    }
    
    private Map<String, Object> extractPersonDetails(Map<String, Object> context) {
        Map<String, Object> details = new HashMap<>();
        details.put("ageRange", context.getOrDefault("personAgeRange", "未指定"));
        details.put("faceFeatures", context.getOrDefault("personFaceFeatures", "未指定"));
        details.put("skinCondition", context.getOrDefault("personSkinCondition", "未指定"));
        details.put("hairStyle", context.getOrDefault("personHairStyle", "未指定"));
        details.put("expression", context.getOrDefault("personExpression", "未指定"));
        details.put("makeup", context.getOrDefault("personMakeup", "未指定"));
        details.put("outfit", context.getOrDefault("personOutfit", "未指定"));
        details.put("bodyLanguage", context.getOrDefault("personBodyLanguage", "未指定"));
        return details;
    }
    
    private Map<String, Object> extractSceneDetails(Map<String, Object> context) {
        Map<String, Object> details = new HashMap<>();
        details.put("layout", context.getOrDefault("sceneLayout", "未指定"));
        details.put("lighting", context.getOrDefault("sceneLighting", "未指定"));
        details.put("colorTone", context.getOrDefault("sceneColorTone", "未指定"));
        details.put("environmentDetails", context.getOrDefault("sceneEnvironmentDetails", "未指定"));
        details.put("props", context.getOrDefault("sceneProps", "未指定"));
        return details;
    }
    
    private Map<String, Object> extractCameraDetails(Map<String, Object> context) {
        Map<String, Object> details = new HashMap<>();
        details.put("composition", context.getOrDefault("cameraComposition", "未指定"));
        details.put("shotSize", context.getOrDefault("cameraShotSize", "未指定"));
        details.put("cameraHeight", context.getOrDefault("cameraHeight", "未指定"));
        details.put("cameraMovement", context.getOrDefault("cameraMovement", "未指定"));
        details.put("distance", context.getOrDefault("cameraDistance", "未指定"));
        return details;
    }
    
    private Map<String, Object> extractActionDetails(Map<String, Object> context) {
        Map<String, Object> details = new HashMap<>();
        details.put("bodyMovement", context.getOrDefault("actionBodyMovement", "未指定"));
        details.put("expressionChange", context.getOrDefault("actionExpressionChange", "未指定"));
        details.put("rhythm", context.getOrDefault("actionRhythm", "未指定"));
        details.put("voiceover", context.getOrDefault("actionVoiceover", "未指定"));
        return details;
    }
    
    private Map<String, Object> extractProductDetails(Map<String, Object> context) {
        Map<String, Object> details = new HashMap<>();
        details.put("coreSellingPoints", context.getOrDefault("productCoreSellingPoints", "未指定"));
        details.put("painPoints", context.getOrDefault("productPainPoints", "未指定"));
        details.put("pricingStrategy", context.getOrDefault("productPricingStrategy", "未指定"));
        details.put("usageScenarios", context.getOrDefault("productUsageScenarios", "未指定"));
        return details;
    }
    
    private Map<String, Object> extractSuccessMetrics(Map<String, Object> context, boolean success, Double cvr, BigDecimal gmv) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("success", success);
        metrics.put("cvr", cvr);
        metrics.put("gmv", gmv);
        metrics.put("playCount", context.getOrDefault("playCount", 0));
        metrics.put("likeCount", context.getOrDefault("likeCount", 0));
        metrics.put("commentCount", context.getOrDefault("commentCount", 0));
        metrics.put("shareCount", context.getOrDefault("shareCount", 0));
        metrics.put("collectCount", context.getOrDefault("collectCount", 0));
        if (context.containsKey("playCount") && context.get("playCount") != null) {
            double playCount = ((Number) context.get("playCount")).doubleValue();
            if (playCount > 0) {
                double totalEngagement = 0;
                if (context.containsKey("likeCount")) totalEngagement += ((Number) context.get("likeCount")).doubleValue();
                if (context.containsKey("commentCount")) totalEngagement += ((Number) context.get("commentCount")).doubleValue();
                if (context.containsKey("shareCount")) totalEngagement += ((Number) context.get("shareCount")).doubleValue();
                if (context.containsKey("collectCount")) totalEngagement += ((Number) context.get("collectCount")).doubleValue();
                metrics.put("engagementRate", (totalEngagement / playCount) * 100);
            }
        }
        return metrics;
    }
    
    private String generateDetailedLesson(Map<String, Object> context, boolean success) {
        StringBuilder lesson = new StringBuilder();
        if (success) {
            lesson.append("【成功要素】\n");
            lesson.append("• 核心成功因子: ").append(context.getOrDefault("successFactor", "需AI深度分析")).append("\n");
            lesson.append("• 可复制模式: ").append(context.getOrDefault("replicablePattern", "需AI总结")).append("\n");
            lesson.append("• 关键决策点: ").append(context.getOrDefault("keyDecision", "需专家视角评估")).append("\n");
        } else {
            lesson.append("【失败教训】\n");
            lesson.append("• 根本原因: ").append(context.getOrDefault("rootCause", context.getOrDefault("error", "需深度分析"))).append("\n");
            lesson.append("• 避坑建议: ").append(context.getOrDefault("avoidanceAdvice", "需专家总结")).append("\n");
            lesson.append("• 改进方向: ").append(context.getOrDefault("improvementDirection", "需多维度扫描")).append("\n");
        }
        return lesson.toString();
    }
    
    private String generateDetailedRecordReply(CaseMemory caseMemory, String skillCode, boolean success, Double cvr) {
        StringBuilder reply = new StringBuilder();
        reply.append("═══════════════════════════════════════════════════════════════\n");
        reply.append("📝 **精细化记忆沉淀报告**\n");
        reply.append("═══════════════════════════════════════════════════════════════\n\n");
        
        reply.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        reply.append("【案例基础信息】\n");
        reply.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        reply.append("• 案例编号: ").append(caseMemory.getCaseNo()).append("\n");
        reply.append("• 类型: ").append(success ? "✅ 成功案例" : "❌ 失败案例").append("\n");
        reply.append("• Skill: ").append(skillCode).append("\n");
        reply.append("• 产品: ").append(caseMemory.getProductName() != null ? caseMemory.getProductName() : "未指定").append("\n");
        reply.append("• 平台: ").append(caseMemory.getPlatform()).append("\n\n");
        
        reply.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        reply.append("【关键业务指标】\n");
        reply.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        if (caseMemory.getPlayCount() != null) {
            reply.append("• 播放量: ").append(caseMemory.getPlayCount()).append("\n");
        }
        if (caseMemory.getLikeCount() != null) {
            reply.append("• 点赞数: ").append(caseMemory.getLikeCount()).append("\n");
        }
        if (caseMemory.getCommentCount() != null) {
            reply.append("• 评论数: ").append(caseMemory.getCommentCount()).append("\n");
        }
        if (cvr != null) {
            reply.append("• 转化率(CVR): ").append(String.format("%.2f%%", cvr * 100)).append("\n");
        }
        if (caseMemory.getGmv() != null) {
            reply.append("• GMV: ¥").append(caseMemory.getGmv()).append("\n");
        }
        reply.append("\n");
        
        reply.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        reply.append("【经验教训】\n");
        reply.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        reply.append(caseMemory.getLessonLearned()).append("\n\n");
        
        reply.append("═══════════════════════════════════════════════════════════════\n");
        reply.append("💡 这条精细化经验将用于后续进化分析，帮助系统持续优化。\n");
        reply.append("═══════════════════════════════════════════════════════════════\n");
        
        return reply.toString();
    }

    private AgentResponse ingestDeliveryData(AgentRequest request) {
        log.info("录入投放数据...");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = request.getContext() != null ?
                (Map<String, Object>) request.getContext() : new HashMap<>();

        DeliveryData dd = new DeliveryData();
        dd.setTaskNo((String) data.getOrDefault("taskNo", "DELIVERY_" + System.currentTimeMillis()));
        dd.setVideoTaskId(data.containsKey("videoTaskId")
                ? ((Number) data.get("videoTaskId")).longValue() : null);
        dd.setPlatform((String) data.getOrDefault("platform", "douyin"));
        dd.setPlayCount(data.containsKey("playCount")
                ? new BigDecimal(data.get("playCount").toString()) : null);
        dd.setLikeCount(data.containsKey("likeCount")
                ? new BigDecimal(data.get("likeCount").toString()) : null);
        dd.setCommentCount(data.containsKey("commentCount")
                ? new BigDecimal(data.get("commentCount").toString()) : null);
        dd.setShareCount(data.containsKey("shareCount")
                ? new BigDecimal(data.get("shareCount").toString()) : null);
        dd.setGmv(data.containsKey("gmv")
                ? new BigDecimal(data.get("gmv").toString()) : null);
        dd.setOrderCount(data.containsKey("orderCount")
                ? new BigDecimal(data.get("orderCount").toString()) : null);
        dd.setCvr(data.containsKey("cvr")
                ? ((Number) data.get("cvr")).doubleValue() : null);
        dd.setStatus((String) data.getOrDefault("status", "COMPLETED"));
        try {
            dd.setRawDataJson(objectMapper.writeValueAsString(data));
        } catch (Exception e) {
            dd.setRawDataJson("{}");
        }
        dd.setDataDate(LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE));
        dd.setCreatedAt(LocalDateTime.now());
        dd.setDeleted(0);

        deliveryDataMapper.insert(dd);

        log.info("投放数据录入完成: taskNo={}, cvr={}%", dd.getTaskNo(),
                dd.getCvr() != null ? String.format("%.2f", dd.getCvr() * 100) : "N/A");

        return AgentResponse.success(
                String.format("📥 **投放数据已入库**\n- 任务号: %s\n- 平台: %s\n- 播放: %s\n- CVR: %s%%\n- GMV: %s\n\n数据将参与下次进化分析。",
                        dd.getTaskNo(), dd.getPlatform(),
                        dd.getPlayCount() != null ? dd.getPlayCount().toString() : "N/A",
                        dd.getCvr() != null ? String.format("%.2f", dd.getCvr() * 100) : "N/A",
                        dd.getGmv() != null ? dd.getGmv().toString() : "N/A"),
                Map.of("deliveryDataId", dd.getId()));
    }

    private AgentResponse queryCaseMemory(AgentRequest request) {
        String query = request.getMessage();

        QueryWrapper<CaseMemory> queryWrapper = new QueryWrapper<CaseMemory>()
                .eq("deleted", 0)
                .ge("created_at", LocalDateTime.now().minusDays(30));
        
        // Filter by query keywords for relevance
        if (query != null && !query.isEmpty()) {
            queryWrapper.and(w -> w
                    .like("product_name", query)
                    .or().like("lesson_learned", query)
                    .or().like("failure_reason", query)
                    .or().like("category", query)
            );
        }
        
        queryWrapper.orderByDesc("created_at").last("LIMIT 20");

        List<CaseMemory> cases = caseMemoryMapper.selectList(queryWrapper);

        if (cases.isEmpty()) {
            return AgentResponse.success("🧠 记忆库暂无数据。随着系统使用，成功/失败案例会自动沉淀。");
        }

        long successCount = cases.stream().filter(c -> "success".equals(c.getQualityTag())).count();
        long failCount = cases.size() - successCount;

        StringBuilder reply = new StringBuilder();
        reply.append(String.format("🧠 **记忆库概览** (近30天)\n\n"));
        reply.append(String.format("- 总案例: %d (成功%d / 失败%d)\n\n", cases.size(), successCount, failCount));
        reply.append("**最近案例:**\n");

        for (int i = 0; i < Math.min(cases.size(), 8); i++) {
            CaseMemory c = cases.get(i);
            String icon = "success".equals(c.getQualityTag()) ? "✅" : "❌";
            reply.append(String.format("%s [%s] %s | %s | CVR: %s\n",
                    icon,
                    c.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd")),
                    c.getProductName() != null ? c.getProductName() : c.getCategory(),
                    c.getPlatform(),
                    c.getCvr() != null ? String.format("%.2f%%", c.getCvr() * 100) : "-"
            ));
        }

        return AgentResponse.success(reply.toString(), Map.of("cases", cases));
    }

    private AgentResponse handleGeneralEvolution(AgentRequest request) {
        String systemPrompt = """
                你是不可思议系统的自进化助手，专注于**极度精细化**的学习和优化。

                ═══════════════════════════════════════════════════════════════
                【核心能力】
                ═══════════════════════════════════════════════════════════════

                1. **Skill自动配置** - 用户说需求，你自动配置最优的Skill参数
                2. **记忆管理** - 沉淀成功/失败案例，形成经验库
                3. **数据分析** - 分析投放效果，发现优化机会
                4. **进化触发** - 基于数据自动调优Skill参数
                5. **A/B验证** - 对比不同策略的效果差异

                ═══════════════════════════════════════════════════════════════
                【精细化学习标准】
                ═══════════════════════════════════════════════════════════════

                所有学习内容必须**极度精细化**，包含以下维度：

                ▶ **人物维度（必须全部覆盖）**:
                - 年龄精确区间（如25-30岁）
                - 脸型五官详细描述（脸型、五官比例、眉眼结构、鼻唇形态）
                - 皮肤状态（毛孔、瑕疵、质感）
                - 发型（长度、颜色、造型、凌乱度）
                - 表情神态（眼神、微笑、情绪张力）
                - 妆容状态（素颜/淡妆/浓妆）
                - 穿搭描述（款式、材质、颜色、贴合度）
                - 肢体语言（坐姿、站姿、手势）

                ▶ **场景维度（必须全部覆盖）**:
                - 空间布局（家具位置、墙面状态）
                - 光线条件（光源类型、色温、阴影）
                - 色调氛围（冷暖调、饱和度）
                - 环境细节（生活痕迹、装饰物）
                - 道具陈设（位置、状态、使用痕迹）

                ▶ **镜头维度（必须全部覆盖）**:
                - 构图方式（正面/侧面/俯拍/仰拍）
                - 景别（特写/近景/中景/全景）
                - 机位高度（平视/俯视/仰视）
                - 运镜方式（推/拉/摇/移/固定）
                - 镜头距离（亲密距离/社交距离）

                ▶ **动作维度（videoPrompt必须覆盖）**:
                - 肢体动作（坐姿、站姿、手势）
                - 表情变化（眼神移动、微笑弧度）
                - 动作节奏（快/慢/停顿）
                - 口播内容（语气、停顿、情感）

                ▶ **产品维度（必须全部覆盖）**:
                - 核心卖点（功能、效果、差异化）
                - 痛点定位（目标用户痛点）
                - 价格策略（定价、促销）
                - 使用场景（适用场景、不适用场景）

                ▶ **数据维度（必须全部覆盖）**:
                - 播放量、点赞数、评论数、分享数、收藏数
                - 转化率(CVR)、GMV、订单数
                - 互动率、完播率
                - 时间趋势、平台对比

                ═══════════════════════════════════════════════════════════════
                【输出要求】
                ═══════════════════════════════════════════════════════════════

                - 每个学习报告必须简洁精准，重点突出核心洞察
                - 拒绝简短、模糊、模板化的内容
                - 每个细节都要具体、可描述、可执行
                - 保留真实的不完美感（皮肤瑕疵、环境痕迹）
                - 追求原生写实主义（raw realism）为核心

                你可以帮用户:
                - "帮我调整选品策略，重点看家居类目" → 自动配置scorer_6d
                - "视频转化率太低了" → 分析原因并优化video_generator
                - "看看最近的系统表现如何" → 运行性能分析
                - "帮我记录这次投放数据" → 录入投放数据到记忆库
                - "启动一次进化" → 手动触发完整进化流程
                
                回答时展示具体的Skill配置变更和数据支撑，确保内容极度精细化。
                """;

        String response = gptChatService.chat(systemPrompt, request.getMessage());

        return AgentResponse.success(response);
    }

    private String extractAction(AgentRequest request) {
        if (request.getParameters() != null && request.getParameters().containsKey("action")) {
            return (String) request.getParameters().get("action");
        }
        String msg = request.getMessage().toLowerCase();
        if (msg.contains("配置") || msg.contains("configure") || msg.contains("设置") || msg.contains("调整"))
            return "configure_skill";
        if (msg.contains("性能") || msg.contains("表现") || msg.contains("分析") || msg.contains("stats"))
            return "analyze_performance";
        if (msg.contains("进化") || msg.contains("evolve") || msg.contains("升级") || msg.contains("优化"))
            return "evolve_now";
        if (msg.contains("记录") || msg.contains("record") || msg.contains("结果") || msg.contains("沉淀"))
            return "record_result";
        if (msg.contains("投放") || msg.contains("delivery") || msg.contains("回流") || msg.contains("数据"))
            return "ingest_delivery";
        if (msg.contains("记忆") || msg.contains("memory") || msg.contains("案例") || msg.contains("case"))
            return "query_memory";
        return "general";
    }

    private WorkflowDefinition findWorkflowByCode(String code) {
        return workflowDefinitionMapper.selectOne(
                new QueryWrapper<WorkflowDefinition>()
                        .eq("workflow_code", code)
                        .eq("status", 1)
                        .eq("deleted", 0)
                        .orderByDesc("version")
                        .last("LIMIT 1")
        );
    }

    private String inferCategory(String skillCode) {
        return switch (skillCode) {
            case "scorer_6d" -> "selection";
            case "script_generator" -> "content_generation";
            case "video_generator" -> "video_production";
            case "compliance_checker" -> "compliance";
            case "causal_analyzer" -> "attribution";
            default -> "general";
        };
    }

    private String generateInfoResponse(Map<String, Object> config, Map<String, Object> params) {
        String skillName = (String) config.getOrDefault("skill_name", "Skill");
        return String.format("""
                关于 **%s** 的信息:
                
                **当前最佳配置:**
                %s
                
                💡 如果您想调整配置，请直接告诉我您的需求，例如:
                - "提高选品门槛"
                - "脚本写短一点"
                - "用更好的视频模型"
                - "合规检查严格一点"
                
                我会自动帮您配置最优参数 ✅
                """, skillName, formatParams(params));
    }

    private String formatParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "(默认)";
        return params.entrySet().stream()
                .map(e -> {
                    String val = String.valueOf(e.getValue());
                    if (val.length() > 50) val = val.substring(0, 50) + "...";
                    return String.format("  - `%s`: `%s`", e.getKey(), val);
                })
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }
}
