package com.ecommerce.workflow.service.learning;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ecommerce.workflow.entity.Knowledge;
import com.ecommerce.workflow.service.knowledge.KnowledgeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PromptImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PromptImportService.class);
    
    private final KnowledgeService knowledgeService;
    private final ObjectMapper objectMapper;
    
    @Value("${app.prompt.file-path:api-docs/提示词}")
    private String promptFilePath;
    
    public PromptImportService(KnowledgeService knowledgeService, ObjectMapper objectMapper) {
        this.knowledgeService = knowledgeService;
        this.objectMapper = objectMapper;
    }
    
    public int importPromptsFromFile() {
        log.info("开始导入提示词文件...");
        
        try {
            File promptFile = new File(promptFilePath);
            if (!promptFile.exists()) {
                log.warn("提示词文件不存在: {}", promptFilePath);
                return 0;
            }
            
            String content = new String(Files.readAllBytes(promptFile.toPath()), "UTF-8");
            List<Map<String, Object>> prompts = objectMapper.readValue(content, 
                new TypeReference<List<Map<String, Object>>>() {});
            
            int importedCount = 0;
            for (Map<String, Object> prompt : prompts) {
                try {
                    importPromptAsKnowledge(prompt);
                    importedCount++;
                } catch (Exception e) {
                    log.error("导入提示词失败: {}", prompt.get("name"), e);
                }
            }
            
            log.info("提示词导入完成，共导入 {} 个", importedCount);
            return importedCount;
            
        } catch (IOException e) {
            log.error("读取提示词文件失败", e);
            return 0;
        }
    }
    
    private void importPromptAsKnowledge(Map<String, Object> prompt) {
        String name = (String) prompt.get("name");
        String imagePrompt = (String) prompt.get("imagePrompt");
        String videoPrompt = (String) prompt.get("videoPrompt");
        Integer sequenceNumber = (Integer) prompt.get("sequenceNumber");
        
        if (name == null || name.isEmpty()) {
            log.warn("提示词名称不能为空，跳过导入");
            return;
        }
        
        Knowledge knowledge = new Knowledge();
        knowledge.setKnowledgeId("PROMPT_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        knowledge.setType("PROMPT_TEMPLATE");
        knowledge.setTitle(name);
        
        StringBuilder fullContent = new StringBuilder();
        fullContent.append("# 视频生成提示词模板\n\n");
        fullContent.append("名称: ").append(name).append("\n");
        fullContent.append("序号: ").append(sequenceNumber != null ? sequenceNumber : "N/A").append("\n\n");
        
        if (imagePrompt != null && !imagePrompt.isEmpty()) {
            fullContent.append("## 图片生成提示词\n");
            fullContent.append("---\n");
            fullContent.append(imagePrompt).append("\n\n");
        }
        
        if (videoPrompt != null && !videoPrompt.isEmpty()) {
            fullContent.append("## 视频生成提示词\n");
            fullContent.append("---\n");
            fullContent.append(videoPrompt).append("\n\n");
        }
        
        fullContent.append("## 使用说明\n");
        fullContent.append("---\n");
        fullContent.append("此提示词模板用于AI视频生成场景，可根据产品特点调整关键词和参数。\n");
        fullContent.append("建议结合实际需求优化提示词内容，以获得更好的生成效果。\n");
        
        knowledge.setContent(fullContent.toString());
        
        List<String> tags = extractTags(imagePrompt, videoPrompt, name);
        try {
            knowledge.setTags(objectMapper.writeValueAsString(tags));
        } catch (Exception e) {
            knowledge.setTags("[]");
        }
        
        knowledge.setSource("PROMPT_FILE");
        knowledge.setSourceId(sequenceNumber != null ? sequenceNumber.toString() : name);
        knowledge.setConfidence(95.0);
        knowledge.setApplyCount(0);
        knowledge.setSuccessCount(0);
        knowledge.setStatus("ACTIVE");
        knowledge.setCreatedBy(1L);
        knowledge.setCreatedAt(LocalDateTime.now());
        knowledge.setUpdatedAt(LocalDateTime.now());
        
        knowledgeService.storeKnowledge(knowledge);
        
        log.info("成功导入提示词模板: {} (ID: {})", name, knowledge.getKnowledgeId());
    }
    
    private List<String> extractTags(String imagePrompt, String videoPrompt, String name) {
        List<String> tags = new ArrayList<>();
        
        tags.add("提示词模板");
        tags.add("视频生成");
        
        if (name != null) {
            if (name.contains("图片") || name.contains("图像")) {
                tags.add("图片生成");
            }
            if (name.contains("视频") || name.contains("影片")) {
                tags.add("视频生成");
            }
            if (name.contains("产品") || name.contains("商品")) {
                tags.add("产品展示");
            }
        }
        
        String combinedText = (imagePrompt != null ? imagePrompt : "") + " " + (videoPrompt != null ? videoPrompt : "");
        
        if (combinedText.contains("美妆") || combinedText.contains("护肤")) {
            tags.add("美妆护肤");
        }
        if (combinedText.contains("场景") || combinedText.contains("环境")) {
            tags.add("场景环境");
        }
        if (combinedText.contains("功能") || combinedText.contains("特性")) {
            tags.add("功能特性");
        }
        if (combinedText.contains("效果") || combinedText.contains("展示")) {
            tags.add("效果展示");
        }
        if (combinedText.contains("情感") || combinedText.contains("情绪")) {
            tags.add("情感表达");
        }
        if (combinedText.contains("故事") || combinedText.contains("叙述")) {
            tags.add("故事叙述");
        }
        if (combinedText.contains("产品") || combinedText.contains("卖点")) {
            tags.add("产品卖点");
        }
        if (combinedText.contains("对比") || combinedText.contains("比较")) {
            tags.add("对比展示");
        }
        
        return tags;
    }
    
    public int importPromptsFromCustomPath(String filePath) {
        log.info("从自定义路径导入提示词文件: {}", filePath);
        
        try {
            File promptFile = new File(filePath);
            if (!promptFile.exists()) {
                log.warn("提示词文件不存在: {}", filePath);
                return 0;
            }
            
            String content = new String(Files.readAllBytes(promptFile.toPath()), "UTF-8");
            List<Map<String, Object>> prompts = objectMapper.readValue(content, 
                new TypeReference<List<Map<String, Object>>>() {});
            
            int importedCount = 0;
            for (Map<String, Object> prompt : prompts) {
                try {
                    importPromptAsKnowledge(prompt);
                    importedCount++;
                } catch (Exception e) {
                    log.error("导入提示词失败: {}", prompt.get("name"), e);
                }
            }
            
            log.info("提示词导入完成，共导入 {} 个", importedCount);
            return importedCount;
            
        } catch (Exception e) {
            log.error("从自定义路径导入提示词失败", e);
            return 0;
        }
    }
    
    public Map<String, Object> getImportStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("promptFilePath", promptFilePath);
        status.put("fileExists", new File(promptFilePath).exists());
        return status;
    }
}
