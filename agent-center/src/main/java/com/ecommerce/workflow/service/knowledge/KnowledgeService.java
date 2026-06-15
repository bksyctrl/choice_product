package com.ecommerce.workflow.service.knowledge;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.workflow.entity.Knowledge;
import com.ecommerce.workflow.mapper.KnowledgeMapper;
import com.ecommerce.workflow.service.rag.EmbeddingService;
import com.ecommerce.workflow.service.vector.VectorStoreService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class KnowledgeService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);
    private final KnowledgeMapper knowledgeMapper;
    private final ObjectMapper objectMapper;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    
    public KnowledgeService(KnowledgeMapper knowledgeMapper, ObjectMapper objectMapper,
                           EmbeddingService embeddingService, VectorStoreService vectorStoreService) {
        this.knowledgeMapper = knowledgeMapper;
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }
    public Knowledge storeKnowledge(Knowledge knowledge) {
        // 检查是否已存在相同内容的知识（基于source和sourceId）
        if (knowledge.getSource() != null && knowledge.getSourceId() != null) {
            Knowledge existing = findBySourceAndSourceId(knowledge.getSource(), knowledge.getSourceId());
            if (existing != null) {
                log.info("知识已存在，跳过插入: source={}, sourceId={}", 
                    knowledge.getSource(), knowledge.getSourceId());
                return existing;
            }
        }
        
        // 检查内容哈希是否重复
        if (knowledge.getContent() != null) {
            String contentHash = calculateContentHash(knowledge.getContent());
            Knowledge existingByHash = findByContentHash(contentHash);
            if (existingByHash != null) {
                log.info("知识内容重复，跳过插入: contentHash={}", contentHash);
                return existingByHash;
            }
            knowledge.setContentHash(contentHash);
        }
        
        if (knowledge.getKnowledgeId() == null) {
            knowledge.setKnowledgeId("KN_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        if (knowledge.getStatus() == null) {
            knowledge.setStatus("ACTIVE");
        }
        if (knowledge.getConfidence() == null) {
            knowledge.setConfidence(0.5);
        }
        if (knowledge.getApplyCount() == null) {
            knowledge.setApplyCount(0);
        }
        if (knowledge.getSuccessCount() == null) {
            knowledge.setSuccessCount(0);
        }
        knowledge.setCreatedAt(LocalDateTime.now());
        knowledge.setUpdatedAt(LocalDateTime.now());
        knowledge.setDeleted(0);
        knowledgeMapper.insert(knowledge);
        log.info("存储知识: knowledgeId={}, type={}", knowledge.getKnowledgeId(), knowledge.getType());
        return knowledge;
    }
    
    private Knowledge findBySourceAndSourceId(String source, String sourceId) {
        return knowledgeMapper.selectOne(
            new QueryWrapper<Knowledge>()
                .eq("source", source)
                .eq("source_id", sourceId)
                .eq("deleted", 0)
        );
    }
    
    private Knowledge findByContentHash(String contentHash) {
        if (contentHash == null) return null;
        return knowledgeMapper.selectOne(
            new QueryWrapper<Knowledge>()
                .eq("content_hash", contentHash)
                .eq("deleted", 0)
        );
    }
    
    private String calculateContentHash(String content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 64);
        } catch (Exception e) {
            log.warn("计算内容哈希失败: {}", e.getMessage());
            return null;
        }
    }
    public Knowledge getKnowledge(String knowledgeId) {
        return knowledgeMapper.selectOne(
                new QueryWrapper<Knowledge>()
                        .eq("knowledge_id", knowledgeId)
                        .eq("deleted", 0)
        );
    }
    public List<Knowledge> getByType(String type) {
        return knowledgeMapper.selectList(
                new QueryWrapper<Knowledge>()
                        .eq("type", type)
                        .eq("status", "ACTIVE")
                        .eq("deleted", 0)
                        .orderByDesc("confidence")
        );
    }
    public List<Knowledge> getBySource(String source) {
        return knowledgeMapper.selectList(
                new QueryWrapper<Knowledge>()
                        .eq("source", source)
                        .eq("status", "ACTIVE")
                        .eq("deleted", 0)
                        .orderByDesc("created_at")
        );
    }
    public List<Knowledge> searchByTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        QueryWrapper<Knowledge> wrapper = new QueryWrapper<Knowledge>()
                .eq("status", "ACTIVE")
                .eq("deleted", 0);
        for (String tag : tags) {
            wrapper.like("tags", tag);
        }
        return knowledgeMapper.selectList(wrapper);
    }
    public List<Knowledge> searchSimilar(String query, int topK) {
        log.debug("向量相似搜索: query={}, topK={}", query, topK);
        
        try {
            float[] queryVector = embeddingService.embed(query);
            if (queryVector != null && queryVector.length > 0) {
                List<Map<String, Object>> vectorResults = vectorStoreService.searchSimilar(queryVector, topK);
                if (!vectorResults.isEmpty()) {
                    List<Knowledge> results = new ArrayList<>();
                    for (Map<String, Object> result : vectorResults) {
                        String knowledgeId = (String) result.get("knowledgeId");
                        Knowledge k = getKnowledge(knowledgeId);
                        if (k != null) {
                            results.add(k);
                        }
                    }
                    if (!results.isEmpty()) {
                        log.debug("向量搜索返回{}条结果", results.size());
                        return results;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("向量搜索失败，回退到关键词搜索: {}", e.getMessage());
        }
        
        return fallbackKeywordSearch(query, topK);
    }
    
    private List<Knowledge> fallbackKeywordSearch(String query, int topK) {
        List<Knowledge> allKnowledge = knowledgeMapper.selectList(
                new QueryWrapper<Knowledge>()
                        .eq("status", "ACTIVE")
                        .eq("deleted", 0)
                        .orderByDesc("confidence")
                        .last("LIMIT " + (topK * 10))
        );
        return allKnowledge.stream()
                .filter(k -> calculateSimilarity(k, query) > 0.2)
                .sorted((a, b) -> Double.compare(
                        calculateSimilarity(b, query),
                        calculateSimilarity(a, query)))
                .limit(topK)
                .collect(Collectors.toList());
    }
    
    private double calculateSimilarity(Knowledge knowledge, String query) {
        String content = (knowledge.getTitle() + " " + knowledge.getContent()).toLowerCase();
        String lowerQuery = query.toLowerCase();
        String[] queryWords = lowerQuery.split("\\s+");
        int matchCount = 0;
        for (String word : queryWords) {
            if (word.length() > 1 && content.contains(word)) {
                matchCount++;
            }
        }
        double textSimilarity = (double) matchCount / queryWords.length;
        double confidenceBoost = knowledge.getConfidence() != null ? knowledge.getConfidence() * 0.1 : 0;
        return textSimilarity + confidenceBoost;
    }
    public List<Knowledge> getTopKnowledge(String type, int limit) {
        return knowledgeMapper.selectList(
                new QueryWrapper<Knowledge>()
                        .eq("type", type)
                        .eq("status", "ACTIVE")
                        .eq("deleted", 0)
                        .orderByDesc("confidence")
                        .last("LIMIT " + limit)
        );
    }
    public void recordApplication(String knowledgeId, boolean success) {
        Knowledge knowledge = getKnowledge(knowledgeId);
        if (knowledge == null) return;
        knowledge.setApplyCount(knowledge.getApplyCount() + 1);
        if (success) {
            knowledge.setSuccessCount(knowledge.getSuccessCount() + 1);
        }
        if (knowledge.getApplyCount() > 0) {
            double rate = (double) knowledge.getSuccessCount() / knowledge.getApplyCount();
            knowledge.setConfidence(rate);
        }
        knowledge.setUpdatedAt(LocalDateTime.now());
        knowledgeMapper.updateById(knowledge);
    }
    public Knowledge extractFromCase(Map<String, Object> caseData, String caseType) {
        String title = generateKnowledgeTitle(caseData);
        String content = generateKnowledgeContent(caseData);
        List<String> tags = extractTags(caseData);
        Knowledge knowledge = new Knowledge();
        knowledge.setType(caseType);
        knowledge.setTitle(title);
        knowledge.setContent(content);
        try {
            knowledge.setTags(objectMapper.writeValueAsString(tags));
        } catch (Exception e) {
            knowledge.setTags("[]");
        }
        knowledge.setSource("CASE_EXTRACTION");
        knowledge.setSourceId((String) caseData.getOrDefault("caseNo", "unknown"));
        knowledge.setConfidence(0.7);
        return storeKnowledge(knowledge);
    }
    private String generateKnowledgeTitle(Map<String, Object> caseData) {
        String caseType = (String) caseData.getOrDefault("caseType", "unknown");
        String productName = (String) caseData.getOrDefault("productName", "");
        // 过滤 "null" 字符串和空值
        if (productName == null || productName.isEmpty() || "null".equals(productName)) {
            productName = "未知产品";
        }
        if ("success".equalsIgnoreCase(caseType)) {
            return "成功案例: " + productName + " 爆款视频分析";
        } else {
            return "失败案例: " + productName + " 避坑经验总结";
        }
    }
    private String generateKnowledgeContent(Map<String, Object> caseData) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("═══════════════════════════════════════\n");
        sb.append("【案例基本信息】\n");
        sb.append("═══════════════════════════════════════\n");
        
        sb.append("───────────────────────────────────────\n");
        sb.append("案例编号: ").append(caseData.getOrDefault("caseNo", "未知")).append("\n");
        sb.append("───────────────────────────────────────\n");
        sb.append("▪ 案例编号: ").append(caseData.getOrDefault("caseNo", "未知")).append("\n");
        sb.append("▪ 产品名称: ").append(caseData.getOrDefault("productName", "未知")).append("\n");
        sb.append("▪ 产品ID: ").append(caseData.getOrDefault("productId", "未知")).append("\n");
        sb.append("▪ 案例类型: ").append(caseData.getOrDefault("caseType", "未知")).append("\n");
        sb.append("▪ 质量标签: ").append(caseData.getOrDefault("qualityTag", "未知")).append("\n");
        sb.append("▪ 所属品类: ").append(caseData.getOrDefault("category", "未知")).append("\n");
        sb.append("▪ 发布平台: ").append(caseData.getOrDefault("platform", "默认")).append("\n");
        sb.append("▪ 创建时间: ").append(caseData.getOrDefault("createdAt", "")).append("\n\n");
        
        sb.append("───────────────────────────────────────\n");
        sb.append("【数据指标】\n");
        sb.append("───────────────────────────────────────\n");
        Object playCount = caseData.get("playCount");
        if (playCount != null) {
            sb.append("▪ 播放量: ").append(playCount).append("\n");
        }
        Object likeCount = caseData.get("likeCount");
        if (likeCount != null) {
            sb.append("▪ 点赞数: ").append(likeCount).append("\n");
        }
        Object commentCount = caseData.get("commentCount");
        if (commentCount != null) {
            sb.append("▪ 评论数: ").append(commentCount).append("\n");
        }
        Object shareCount = caseData.get("shareCount");
        if (shareCount != null) {
            sb.append("▪ 分享数: ").append(shareCount).append("\n");
        }
        Object collectCount = caseData.get("collectCount");
        if (collectCount != null) {
            sb.append("▪ 收藏数: ").append(collectCount).append("\n");
        }
        Object cvr = caseData.get("cvr");
        if (cvr != null) {
            sb.append("▪ 转化率(CVR): ").append(cvr).append("\n");
        }
        Object gmv = caseData.get("gmv");
        if (gmv != null) {
            sb.append("▪ GMV: ").append(gmv).append("\n");
        }
        Object conversionCount = caseData.get("conversionCount");
        if (conversionCount != null) {
            sb.append("▪ 转化订单数: ").append(conversionCount).append("\n");
        }
        sb.append("\n");
        
        sb.append("───────────────────────────────────────\n");
        sb.append("【输出结果分析】\n");
        sb.append("───────────────────────────────────────\n");
        Object outputResult = caseData.get("outputResult");
        if (outputResult != null) {
            sb.append("▪ 输出内容: ").append(outputResult.toString()).append("\n");
        }
        sb.append("▪ 视频结构: [待分析 - 需要解析视频开头、中间、结尾结构]\n");
        sb.append("▪ 情感框架: [待分析 - 需要识别情感驱动类型和情绪曲线]\n");
        sb.append("▪ 用户痛点: [待分析 - 需要提取目标用户痛点和解决方案]\n");
        sb.append("▪ 卖点展示: [待分析 - 需要分析BGM、画面、文案的卖点呈现方式]\n");
        sb.append("▪ 转化路径: [待分析 - 需要分析从观看到购买的转化路径]\n\n");
        
        sb.append("───────────────────────────────────────\n");
        sb.append("【输入参数分析】\n");
        sb.append("───────────────────────────────────────\n");
        Object inputParams = caseData.get("inputParams");
        if (inputParams != null) {
            sb.append("▪ 输入参数: ").append(inputParams.toString()).append("\n");
        }
        sb.append("▪ 脚本模板: [待分析 - 需要解析使用的脚本模板和结构]\n");
        sb.append("▪ 提示词策略: [待分析 - 需要提取提示词的关键策略]\n");
        sb.append("▪ 产品定位: [待分析 - 需要分析产品定位和差异化]\n");
        sb.append("▪ 目标受众: [待分析 - 需要分析目标受众画像和需求]\n\n");
        
        sb.append("───────────────────────────────────────\n");
        sb.append("【关键洞察】\n");
        sb.append("───────────────────────────────────────\n");
        sb.append("▪ 评论情感倾向: [待分析 - 需要分析用户评论的情感倾向和关键词]\n");
        sb.append("▪ 优化空间: [待分析 - 需要识别可优化的环节和改进方向]\n");
        sb.append("▪ 用户行为模式: [待分析 - 需要分析用户观看和互动行为模式]\n");
        sb.append("▪ 转化漏斗瓶颈: [待分析 - 需要分析转化漏斗中的瓶颈环节]\n\n");
        
        sb.append("───────────────────────────────────────\n");
        sb.append("【经验总结】\n");
        sb.append("───────────────────────────────────────\n");
        String lessonLearned = (String) caseData.getOrDefault("lessonLearned", "");
        if (lessonLearned != null && !lessonLearned.isEmpty()) {
            String[] lessons = lessonLearned.split("[\n]");
            for (int i = 0; i < lessons.length; i++) {
                String lesson = lessons[i].trim();
                if (!lesson.isEmpty()) {
                    sb.append("▪ 经验").append(i + 1).append(": ").append(lesson).append("\n");
                }
            }
        } else {
            sb.append("▪ [待补充 - 需要人工或AI分析补充成功经验总结]\n");
        }
        sb.append("\n");
        
        String failureReason = (String) caseData.getOrDefault("failureReason", "");
        if (failureReason != null && !failureReason.isEmpty()) {
            sb.append("───────────────────────────────────────\n");
            sb.append("【失败原因分析】\n");
            sb.append("───────────────────────────────────────\n");
            String[] reasons = failureReason.split("[\n]");
            for (int i = 0; i < reasons.length; i++) {
                String reason = reasons[i].trim();
                if (!reason.isEmpty()) {
                    sb.append("▪ 原因").append(i + 1).append(": ").append(reason).append("\n");
                }
            }
            sb.append("\n");
        }
        
        sb.append("───────────────────────────────────────\n");
        sb.append("【改进建议】\n");
        sb.append("───────────────────────────────────────\n");
        sb.append("▪ 脚本优化建议: [待分析 - AI将基于案例数据生成脚本优化建议]\n");
        sb.append("▪ 提示词改进: [待分析 - 需要分析提示词的改进方向和优化策略]\n");
        sb.append("▪ 策略调整建议: [待分析 - 需要基于数据给出策略调整的具体建议]\n");
        sb.append("▪ 风险预警: [待分析 - 需要识别潜在风险和预防措施]\n\n");
        
        sb.append("═══════════════════════════════════════\n");
        sb.append("生成时间: ").append(LocalDateTime.now()).append("\n");
        sb.append("═══════════════════════════════════════\n");
        
        return sb.toString();
    }
    private List<String> extractTags(Map<String, Object> caseData) {
        List<String> tags = new ArrayList<>();
        String category = (String) caseData.getOrDefault("category", "");
        if (category != null && !category.isEmpty()) {
            tags.add(category);
        }
        String platform = (String) caseData.getOrDefault("platform", "");
        if (platform != null && !platform.isEmpty()) {
            tags.add(platform);
        }
        String caseType = (String) caseData.getOrDefault("caseType", "");
        if ("success".equalsIgnoreCase(caseType)) {
            tags.add("成功案例");
            tags.add("爆款规律");
            tags.add("优秀");
        } else {
            tags.add("失败案例");
            tags.add("避坑规则");
            tags.add("待改进");
        }
        String productName = (String) caseData.getOrDefault("productName", "");
        if (productName != null && !productName.isEmpty() && !"null".equals(productName)) {
            tags.add(productName);
        }
        String qualityTag = (String) caseData.getOrDefault("qualityTag", "");
        if (qualityTag != null && !qualityTag.isEmpty()) {
            tags.add(qualityTag);
        }
        return tags;
    }
    public void mergeKnowledge(String knowledgeId1, String knowledgeId2) {
        Knowledge k1 = getKnowledge(knowledgeId1);
        Knowledge k2 = getKnowledge(knowledgeId2);
        if (k1 == null || k2 == null) return;
        String mergedContent = k1.getContent() + "\n\n---\n\n" + k2.getContent();
        k1.setContent(mergedContent);
        k1.setApplyCount(k1.getApplyCount() + k2.getApplyCount());
        k1.setSuccessCount(k1.getSuccessCount() + k2.getSuccessCount());
        if (k1.getApplyCount() > 0) {
            k1.setConfidence((double) k1.getSuccessCount() / k1.getApplyCount());
        }
        k1.setUpdatedAt(LocalDateTime.now());
        knowledgeMapper.updateById(k1);
        k2.setStatus("MERGED");
        k2.setDeleted(1);
        knowledgeMapper.updateById(k2);
        log.info("合并知识: {} <- {}", knowledgeId1, knowledgeId2);
    }
    public List<Map<String, Object>> getKnowledgeContext(List<String> knowledgeIds) {
        if (knowledgeIds == null || knowledgeIds.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (String id : knowledgeIds) {
            Knowledge k = getKnowledge(id);
            if (k != null) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", k.getKnowledgeId());
                map.put("title", k.getTitle());
                map.put("type", k.getType());
                map.put("confidence", k.getConfidence() != null ? k.getConfidence() : 0.0);
                result.add(map);
            }
        }
        return result;
    }
}
