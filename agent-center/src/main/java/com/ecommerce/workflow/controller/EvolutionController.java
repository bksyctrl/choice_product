package com.ecommerce.workflow.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.workflow.entity.CaseMemory;
import com.ecommerce.workflow.entity.DeliveryData;
import com.ecommerce.workflow.entity.Knowledge;
import com.ecommerce.workflow.mapper.CaseMemoryMapper;
import com.ecommerce.workflow.mapper.DeliveryDataMapper;
import com.ecommerce.workflow.mapper.KnowledgeMapper;
import com.ecommerce.workflow.service.evolution.EvolutionCoreService;
import com.ecommerce.workflow.service.learning.LearningCoreService;

@RestController
@RequestMapping("/api/evolution")
public class EvolutionController {
    private static final Logger log = LoggerFactory.getLogger(EvolutionController.class);
    
    private final EvolutionCoreService evolutionCoreService;
    private final CaseMemoryMapper caseMemoryMapper;
    private final DeliveryDataMapper deliveryDataMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final LearningCoreService learningCoreService;

    public EvolutionController(EvolutionCoreService evolutionCoreService,
                                CaseMemoryMapper caseMemoryMapper,
                                DeliveryDataMapper deliveryDataMapper,
                                KnowledgeMapper knowledgeMapper,
                                LearningCoreService learningCoreService) {
        this.evolutionCoreService = evolutionCoreService;
        this.caseMemoryMapper = caseMemoryMapper;
        this.deliveryDataMapper = deliveryDataMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.learningCoreService = learningCoreService;
    }
    
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getStatus() {
        long totalCases = caseMemoryMapper.selectCount(
            new QueryWrapper<CaseMemory>().eq("deleted", 0)
        );
        
        long successCases = caseMemoryMapper.selectCount(
            new QueryWrapper<CaseMemory>()
                .eq("deleted", 0)
                .eq("quality_tag", "SUCCESS")
        );
        
        double successRate = totalCases > 0 ? (double) successCases / totalCases * 100 : 0;
        
        int learningProgress = Math.min((int) (totalCases / 10), 100);
        
        List<Map<String, Object>> recentEvolutions = new java.util.ArrayList<>();
        List<CaseMemory> recentCases = caseMemoryMapper.selectList(
            new QueryWrapper<CaseMemory>()
                .eq("deleted", 0)
                .orderByDesc("created_at")
                .last("LIMIT 5")
        );
        
        for (CaseMemory caseMemory : recentCases) {
            Map<String, Object> evolution = new HashMap<>();
            evolution.put("success", "SUCCESS".equals(caseMemory.getQualityTag()));
            
            String description = caseMemory.getProductName();
            if (description == null || description.isEmpty()) {
                description = caseMemory.getLessonLearned() != null ? 
                    caseMemory.getLessonLearned() : 
                    caseMemory.getCaseNo();
            }
            evolution.put("description", description);
            evolution.put("time", caseMemory.getCreatedAt());
            recentEvolutions.add(evolution);
        }
        
        return ApiResponse.success(Map.of(
                "status", "active",
                "statusText", "运行中",
                "learningProgress", learningProgress,
                "memoryCount", (int) totalCases,
                "successRate", Math.round(successRate * 10) / 10.0,
                "recentEvolutions", recentEvolutions
        ));
    }
    
    @PostMapping("/feedback")
    public ApiResponse<Void> submitUserFeedback(
            @RequestParam String skillCode,
            @RequestParam String evolutionLogId,
            @RequestParam boolean positive,
            @RequestParam(required = false) String comment) {
        log.info("记录用户反馈: skillCode={}, positive={}", skillCode, positive);
        return ApiResponse.success(null);
    }

    @GetMapping("/strategies")
    public ApiResponse<Map<String, Object>> getEffectiveStrategies() {
        Map<String, Object> result = new HashMap<>();
        evolutionCoreService.getLearningStates().forEach((k, v) -> result.put(k, v));
        return ApiResponse.success(result);
    }

    @GetMapping("/memories/{skillCode}")
    public ApiResponse<List<Object>> getEvolutionMemories(@PathVariable String skillCode) {
        List<Object> memories = new java.util.ArrayList<>();
        memories.addAll(evolutionCoreService.getRecentEpisodes(20));
        return ApiResponse.success(memories);
    }
    
    @GetMapping("/memory/cases")
    public ApiResponse<List<CaseMemory>> listCases(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        QueryWrapper<CaseMemory> wrapper = new QueryWrapper<CaseMemory>()
                .eq("deleted", 0)
                .orderByDesc("created_at")
                .last("LIMIT " + safeLimit);

        if (type != null && !type.isEmpty()) {
            wrapper.eq("quality_tag", type);
        }

        return ApiResponse.success(caseMemoryMapper.selectList(wrapper));
    }
    
    /**
     * 触发深度学习和专家视角分析，将成功案例转化为可复用的专家规则
     */
    @PostMapping("/deep-learn/trigger")
    public ApiResponse<Map<String, Object>> triggerDeepLearning() {
        try {
            log.info("触发深度学习和专家视角分析");
            
            List<CaseMemory> unlearnedCases = caseMemoryMapper.selectList(
                new QueryWrapper<CaseMemory>()
                    .eq("learned", 0)
                    .eq("deleted", 0)
                    .orderByDesc("created_at")
                    .last("LIMIT 10")
            );
            
            int processedCount = 0;
            for (CaseMemory caseMemory : unlearnedCases) {
                try {
                    learningCoreService.learnFromSingleCase(caseMemory);
                    processedCount++;
                } catch (Exception e) {
                    log.warn("知识学习失败 {}: {}", caseMemory.getCaseNo(), e.getMessage());
                }
            }

            return ApiResponse.success(Map.of(
                "success", true,
                "message", "深度学习和专家视角分析完成",
                "processedCases", processedCount,
                "totalCases", unlearnedCases.size(),
                "timestamp", java.time.LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("深度学习知识学习失败", e);
            return ApiResponse.error("深度学习知识学习失败: " + e.getMessage());
        }
    }
    
    /**
     * 使用模板触发深度学习，支持自定义提示词模板
     */
    @PostMapping("/deep-learn/with-template")
    public ApiResponse<Map<String, Object>> triggerDeepLearningWithTemplate(
            @RequestParam(required = false) String caseNo,
            @RequestBody(required = false) Map<String, String> templateData) {
        
        try {
            log.info("使用模板触发深度学习: caseNo={}", caseNo);
            
            CaseMemory targetCase = null;
            if (caseNo != null && !caseNo.isEmpty()) {
                targetCase = caseMemoryMapper.selectOne(
                    new QueryWrapper<CaseMemory>()
                        .eq("case_no", caseNo)
                        .eq("deleted", 0)
                );
            }
            
            if (targetCase == null) {
                targetCase = caseMemoryMapper.selectOne(
                    new QueryWrapper<CaseMemory>()
                        .eq("learned", 0)
                        .eq("deleted", 0)
                        .orderByDesc("created_at")
                        .last("LIMIT 1")
                );
            }
            
            if (targetCase == null) {
                return ApiResponse.error("未找到可学习的案例记录");
            }
            
            String promptTemplateContent = null;
            if (templateData != null && templateData.containsKey("templateContent")) {
                promptTemplateContent = templateData.get("templateContent");
            }
            
            if (promptTemplateContent == null || promptTemplateContent.isEmpty()) {
                return ApiResponse.error("提示词模板不能为空，请提供有效的模板内容");
            }

            learningCoreService.learnFromSingleCase(targetCase);

            return ApiResponse.success(Map.of(
                "success", true,
                "message", "使用模板触发深度学习成功完成",
                "caseNo", targetCase.getCaseNo(),
                "productName", targetCase.getProductName(),
                "timestamp", java.time.LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("模板深度学习处理失败", e);
            return ApiResponse.error("模板深度学习处理失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取深度学习系统状态
     */
    @GetMapping("/deep-learn/status")
    public ApiResponse<Map<String, Object>> getDeepLearningStatus() {
        try {
            Map<String, Object> systemStatus = new HashMap<>();

            long totalKnowledge = knowledgeMapper.selectCount(
                new QueryWrapper<Knowledge>().eq("deleted", 0)
            );

            long expertKnowledge = knowledgeMapper.selectCount(
                new QueryWrapper<Knowledge>()
                    .eq("deleted", 0)
                    .like("type", "EXPERT")
            );

            systemStatus.put("totalKnowledgeCount", totalKnowledge);
            systemStatus.put("expertKnowledgeCount", expertKnowledge);
            systemStatus.put("learningEfficiency", calculateLearningEfficiency());
            systemStatus.put("timestamp", java.time.LocalDateTime.now());

            return ApiResponse.success(systemStatus);
        } catch (Exception e) {
            log.error("获取深度学习状态失败", e);
            return ApiResponse.error("获取深度学习状态失败: " + e.getMessage());
        }
    }
    
    private double calculateLearningEfficiency() {
        long totalCases = caseMemoryMapper.selectCount(
            new QueryWrapper<CaseMemory>().eq("deleted", 0)
        );
        
        long learnedCases = caseMemoryMapper.selectCount(
            new QueryWrapper<CaseMemory>()
                .eq("deleted", 0)
                .eq("learned", 1)
        );
        
        if (totalCases == 0) return 0.0;
        return (double) learnedCases / totalCases;
    }
    public ApiResponse<CaseMemory> recordCase(@Valid @RequestBody CaseMemory caseData) {
        try {
            if (caseData.getCaseNo() == null || caseData.getCaseNo().isEmpty()) {
                caseData.setCaseNo("CASE_" + System.currentTimeMillis());
            }
            caseData.setCreatedAt(java.time.LocalDateTime.now());
            caseMemoryMapper.insert(caseData);
            log.info("记录案例成功: {}", caseData.getCaseNo());
            return ApiResponse.success(caseData);
        } catch (Exception e) {
            log.error("记录案例失败", e);
            return ApiResponse.error("记录案例失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/delivery/ingest")
    public ApiResponse<Void> ingestDeliveryData(@RequestBody DeliveryData deliveryData) {
        try {
            deliveryData.setCreatedAt(java.time.LocalDateTime.now());
            deliveryDataMapper.insert(deliveryData);
            log.info("投放数据已记录: {}", deliveryData.getId());
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("记录投放数据失败", e);
            return ApiResponse.error("记录投放数据失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/delivery/list")
    public ApiResponse<List<DeliveryData>> listDeliveries(
            @RequestParam(defaultValue = "30") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        List<DeliveryData> deliveries = deliveryDataMapper.selectList(
                new QueryWrapper<DeliveryData>()
                        .orderByDesc("created_at")
                        .last("LIMIT " + safeLimit)
        );
        return ApiResponse.success(deliveries);
    }
    
    @PostMapping("/trigger-learning")
    public ApiResponse<Map<String, Object>> triggerLearning() {
        try {
            log.info("手动触发知识学习迭代");
            evolutionCoreService.runLearningIteration();
            
            return ApiResponse.success(Map.of(
                "success", true,
                "message", "知识学习迭代已成功触发",
                "timestamp", java.time.LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("触发知识学习失败", e);
            return ApiResponse.error("触发知识学习失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/failed-patterns")
    public ApiResponse<Map<String, Object>> getFailedPatterns(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
        
        List<Map<String, Object>> allPatterns = new java.util.ArrayList<>();
        
        List<CaseMemory> failedCases = caseMemoryMapper.selectList(
            new QueryWrapper<CaseMemory>()
                .eq("deleted", 0)
                .eq("quality_tag", "FAIL")
                .orderByDesc("created_at")
                .last("LIMIT 500")
        );
        
        if (!failedCases.isEmpty()) {
            java.util.Map<String, Integer> patternCounts = new java.util.HashMap<>();
            java.util.Map<String, String> patternDescriptions = new java.util.HashMap<>();
            java.util.Map<String, String> patternSeverity = new java.util.HashMap<>();
            java.util.Map<String, java.time.LocalDateTime> patternLastSeen = new java.util.HashMap<>();
            java.util.Map<String, java.util.List<String>> patternCases = new java.util.HashMap<>();
            
            for (CaseMemory caseMemory : failedCases) {
                String lessonLearned = caseMemory.getLessonLearned();
                if (lessonLearned == null || lessonLearned.isEmpty()) {
                    lessonLearned = caseMemory.getProductName() != null ? 
                        "产品: " + caseMemory.getProductName() + " 执行失败 : " + (caseMemory.getFailureReason() != null ? caseMemory.getFailureReason() : "未知错误") : "未知失败案例";
                }
                
                String patternKey = extractPatternKey(lessonLearned);
                
                patternCounts.merge(patternKey, 1, Integer::sum);
                patternDescriptions.putIfAbsent(patternKey, lessonLearned);
                patternLastSeen.put(patternKey, caseMemory.getCreatedAt());
                
                String severity = determineSeverity(caseMemory);
                patternSeverity.put(patternKey, severity);
                
                patternCases.computeIfAbsent(patternKey, k -> new java.util.ArrayList<>())
                    .add(caseMemory.getCaseNo());
            }
            
            int patternId = 1;
            for (java.util.Map.Entry<String, Integer> entry : patternCounts.entrySet()) {
                String patternKey = entry.getKey();
                int occurrences = entry.getValue();
                
                Map<String, Object> pattern = new java.util.HashMap<>();
                pattern.put("id", "FP_" + String.format("%03d", patternId++));
                pattern.put("title", patternKey);
                pattern.put("description", patternDescriptions.get(patternKey));
                pattern.put("severity", patternSeverity.get(patternKey));
                pattern.put("occurrences", occurrences);
                pattern.put("lastSeen", patternLastSeen.get(patternKey) != null ? 
                    patternLastSeen.get(patternKey).toLocalDate().toString() : "");
                pattern.put("caseReferences", patternCases.get(patternKey));
                
                allPatterns.add(pattern);
            }
            
            allPatterns.sort((a, b) -> {
                int severityOrder = getSeverityOrder((String) b.get("severity")) - 
                                   getSeverityOrder((String) a.get("severity"));
                if (severityOrder != 0) return severityOrder;
                return (Integer) b.get("occurrences") - (Integer) a.get("occurrences");
            });
        }
        
        int total = allPatterns.size();
        int start = (safePage - 1) * safeSize;
        int end = Math.min(start + safeSize, total);
        List<Map<String, Object>> paginatedPatterns = start < total ? allPatterns.subList(start, end) : new java.util.ArrayList<>();
        
        return ApiResponse.success(Map.of(
                "list", paginatedPatterns,
                "total", total
        ));
    }
    
    private String extractPatternKey(String lessonLearned) {
        if (lessonLearned.contains("视频") || lessonLearned.contains("画面") || lessonLearned.contains("质量")) {
            return "视频质量问题";
        } else if (lessonLearned.contains("音频") || lessonLearned.contains("声音") || lessonLearned.contains("音效")) {
            return "音频处理问题";
        } else if (lessonLearned.contains("脚本") || lessonLearned.contains("文案") || lessonLearned.contains("内容")) {
            return "脚本内容问题";
        } else if (lessonLearned.contains("时长") || lessonLearned.contains("长度") || lessonLearned.contains("时间")) {
            return "时长控制问题";
        } else if (lessonLearned.contains("节奏") || lessonLearned.contains("速度") || lessonLearned.contains("快慢")) {
            return "节奏把控问题";
        } else if (lessonLearned.contains("引导") || lessonLearned.contains("CTA") || lessonLearned.contains("行动号召")) {
            return "引导转化问题";
        } else if (lessonLearned.contains("产品") || lessonLearned.contains("展示") || lessonLearned.contains("卖点")) {
            return "产品展示问题";
        } else if (lessonLearned.contains("开头") || lessonLearned.contains("前3秒") || lessonLearned.contains("吸引")) {
            return "开头吸引力问题";
        } else if (lessonLearned.contains("合规") || lessonLearned.contains("审核") || lessonLearned.contains("违规")) {
            return "合规审核问题";
        } else {
            return "其他问题";
        }
    }
    
    private String determineSeverity(CaseMemory caseMemory) {
        if (caseMemory.getCvr() != null && caseMemory.getCvr() < 0.01) {
            return "high";
        } else if (caseMemory.getCvr() != null && caseMemory.getCvr() < 0.03) {
            return "medium";
        }
        return "low";
    }
    
    private int getSeverityOrder(String severity) {
        if ("high".equals(severity)) return 3;
        if ("medium".equals(severity)) return 2;
        if ("low".equals(severity)) return 1;
        return 0;
    }
    
    @GetMapping("/knowledge/{id}")
    public ApiResponse<Knowledge> getKnowledgeDetail(@PathVariable String id) {
        QueryWrapper<Knowledge> wrapper = new QueryWrapper<Knowledge>()
                .eq("deleted", 0);
        
        // 尝试将ID解析为数字主键
        try {
            Long primaryKey = Long.parseLong(id);
            wrapper.eq("id", primaryKey);
        } catch (NumberFormatException e) {
            // 如果不是数字，则按knowledge_id查询
            wrapper.eq("knowledge_id", id);
        }
        
        Knowledge knowledge = knowledgeMapper.selectOne(wrapper);
        
        if (knowledge == null) {
            return ApiResponse.error("知识不存在");
        }
        
        return ApiResponse.success(knowledge);
    }
    
    @DeleteMapping("/knowledge/{id}")
    public ApiResponse<Map<String, Object>> deleteKnowledge(@PathVariable String id) {
        try {
            log.info("删除知识请求ID: {}", id);
            
            QueryWrapper<Knowledge> wrapper = new QueryWrapper<Knowledge>()
                    .eq("deleted", 0);
            
            // 尝试将ID解析为数字主键
            try {
                Long primaryKey = Long.parseLong(id);
                wrapper.eq("id", primaryKey);
                log.info("使用数字ID查询: {}", primaryKey);
            } catch (NumberFormatException e) {
                // 如果不是数字，则按knowledge_id查询
                wrapper.eq("knowledge_id", id);
                log.info("使用knowledge_id查询: {}", id);
            }
            
            Knowledge knowledge = knowledgeMapper.selectOne(wrapper);
            log.info("查询结果: {}", knowledge != null ? "找到知识" : "知识不存在");
            
            if (knowledge == null) {
                log.warn("知识不存在，ID: {}", id);
                return ApiResponse.error(400, "知识不存在");
            }
            
            log.info("找到知识: ID={}, knowledgeId={}, title={}, deleted={}", 
                    knowledge.getId(), knowledge.getKnowledgeId(), knowledge.getTitle(), knowledge.getDeleted());
            
            // 使用MyBatis Plus deleteById方法，配合@TableLogic注解实现软删除
            // 会自动设置deleted=1并更新updatedAt时间
            log.info("执行MyBatis Plus deleteById软删除");
            int result = knowledgeMapper.deleteById(knowledge.getId());
            log.info("删除结果: {} 条记录受影响", result);
            
            if (result > 0) {
                // 验证更新后的知识记录状态
                Knowledge updatedKnowledge = knowledgeMapper.selectById(knowledge.getId());
                if (updatedKnowledge != null) {
                    log.info("验证更新后状态: ID={}, deleted={}", updatedKnowledge.getId(), updatedKnowledge.getDeleted());
                } else {
                    log.info("验证更新后状态: 知识不存在，可能是因为deleted=1被过滤");
                    
                    // 尝试使用原始SQL查询验证
                    try {
                        QueryWrapper<Knowledge> rawWrapper = new QueryWrapper<>();
                        rawWrapper.eq("id", knowledge.getId()); // 不过滤deleted
                        Knowledge rawKnowledge = knowledgeMapper.selectOne(rawWrapper);
                        if (rawKnowledge != null) {
                            log.info("原始查询验证: ID={}, deleted={}", rawKnowledge.getId(), rawKnowledge.getDeleted());
                        }
                    } catch (Exception e) {
                        log.warn("原始查询验证失败: {}", e.getMessage());
                    }
                }
                
                log.info("知识软删除成功: {}", knowledge.getKnowledgeId());
                return ApiResponse.success(Map.of(
                    "success", true,
                    "message", "知识软删除成功",
                    "knowledgeId", knowledge.getKnowledgeId(),
                    "affectedRows", result
                ));
            } else {
                log.warn("删除失败，未影响任何记录");
                return ApiResponse.error(400, "删除失败，未影响任何记录");
            }
        } catch (Exception e) {
            log.error("删除知识失败", e);
            return ApiResponse.error(500, "删除知识失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/episodes")
    public ApiResponse<Map<String, Object>> getEpisodes(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
        
        List<?> allEpisodes = evolutionCoreService.getRecentEpisodes(500);
        int total = allEpisodes.size();
        int start = (safePage - 1) * safeSize;
        int end = Math.min(start + safeSize, total);
        List<?> paginatedEpisodes = start < total ? allEpisodes.subList(start, end) : new java.util.ArrayList<>();
        
        return ApiResponse.success(Map.of(
                "list", paginatedEpisodes,
                "total", total
        ));
    }
    
    @GetMapping("/knowledge/list")
    public ApiResponse<Map<String, Object>> getKnowledgeList(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
        int offset = (safePage - 1) * safeSize;
        
        QueryWrapper<Knowledge> wrapper = new QueryWrapper<Knowledge>()
                .eq("deleted", 0)
                .eq("status", "ACTIVE");
        
        if (type != null && !type.isEmpty()) {
            wrapper.eq("type", type);
        }
        
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like("title", keyword).or().like("content", keyword));
        }
        
        wrapper.orderByDesc("confidence")
               .last("LIMIT " + safeSize + " OFFSET " + offset);
        
        List<Knowledge> knowledgeList = knowledgeMapper.selectList(wrapper);
        
        long total = knowledgeMapper.selectCount(
                new QueryWrapper<Knowledge>().eq("deleted", 0).eq("status", "ACTIVE")
        );
        
        return ApiResponse.success(Map.of(
                "list", knowledgeList,
                "total", total
        ));
    }
    

    
    @GetMapping("/expert-knowledge")
    public ApiResponse<List<Knowledge>> getExpertKnowledge(
            @RequestParam(required = false) String expertType,
            @RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        
        QueryWrapper<Knowledge> wrapper = new QueryWrapper<Knowledge>()
                .eq("deleted", 0)
                .eq("status", "ACTIVE")
                .eq("type", "EXPERT_KNOWLEDGE")
                .orderByDesc("confidence")
                .last("LIMIT " + safeLimit);
        
        if (expertType != null && !expertType.isEmpty()) {
            wrapper.like("tags", expertType);
        }
        
        List<Knowledge> knowledgeList = knowledgeMapper.selectList(wrapper);
        return ApiResponse.success(knowledgeList);
    }
}
