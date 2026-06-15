package com.ecommerce.workflow.service.ai;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.workflow.entity.ChatMessage;
import com.ecommerce.workflow.entity.ChatSession;
import com.ecommerce.workflow.entity.Knowledge;
import com.ecommerce.workflow.mapper.ChatMessageMapper;
import com.ecommerce.workflow.mapper.ChatSessionMapper;
import com.ecommerce.workflow.service.config.SysConfigService;
import com.ecommerce.workflow.service.evolution.EvolutionCoreService;
import com.ecommerce.workflow.service.knowledge.KnowledgeService;
import com.ecommerce.workflow.service.memory.UnifiedMemoryService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class UnifiedChatService {
    private static final Logger log = LoggerFactory.getLogger(UnifiedChatService.class);

    private final AiProviderService aiProviderService;
    private final GptChatService gptChatService;
    private final KnowledgeService knowledgeService;
    private final ChatMessageMapper messageMapper;
    private final ChatSessionMapper sessionMapper;
    private final SysConfigService sysConfigService;
    private final UnifiedMemoryService unifiedMemoryService;
    private final EvolutionCoreService evolutionCoreService;
    private final ObjectMapper objectMapper;

    private static final int MAX_CONTEXT_MESSAGES = 20;

    public UnifiedChatService(AiProviderService aiProviderService,
                              GptChatService gptChatService,
                              KnowledgeService knowledgeService,
                              ChatMessageMapper messageMapper,
                              ChatSessionMapper sessionMapper,
                              SysConfigService sysConfigService,
                              UnifiedMemoryService unifiedMemoryService,
                              EvolutionCoreService evolutionCoreService) {
        this.aiProviderService = aiProviderService;
        this.gptChatService = gptChatService;
        this.knowledgeService = knowledgeService;
        this.messageMapper = messageMapper;
        this.sessionMapper = sessionMapper;
        this.sysConfigService = sysConfigService;
        this.unifiedMemoryService = unifiedMemoryService;
        this.evolutionCoreService = evolutionCoreService;
        this.objectMapper = new ObjectMapper();
    }

    public String simpleChat(String userMessage) {
        return simpleChat(null, userMessage);
    }

    public String simpleChat(String systemPrompt, String userMessage) {
        long startTime = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        String response = null;

        try {
            response = gptChatService.chat(systemPrompt, userMessage);
            success = response != null && !response.isEmpty();

            recordChatMetrics("simple_chat", systemPrompt, userMessage, response,
                            startTime, success, errorMessage);

            return response;
        } catch (Exception e) {
            errorMessage = e.getMessage();
            recordChatMetrics("simple_chat", systemPrompt, userMessage, null,
                            startTime, false, errorMessage);
            throw e;
        }
    }

    public String chatWithThinking(String systemPrompt, String userMessage) {
        long startTime = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        String response = null;

        try {
            response = gptChatService.chat(systemPrompt, userMessage);
            success = response != null && !response.isEmpty();

            recordChatMetrics("chat_thinking", systemPrompt, userMessage, response,
                            startTime, success, errorMessage);

            return response;
        } catch (Exception e) {
            errorMessage = e.getMessage();
            recordChatMetrics("chat_thinking", systemPrompt, userMessage, null,
                            startTime, false, errorMessage);
            throw e;
        }
    }

    public void streamChat(String userMessage, StreamCallback callback) {
        streamChat(null, userMessage, callback);
    }

    public void streamChat(String systemPrompt, String userMessage, StreamCallback callback) {
        long startTime = System.currentTimeMillis();
        final boolean[] success = {false};
        final String[] errorMessage = {null};

        try {
            gptChatService.chatStream(systemPrompt, userMessage, new GptChatService.StreamCallback() {
                @Override
                public void onContent(String content) {
                    callback.onContent(content);
                }

                @Override
                public void onComplete() {
                    success[0] = true;
                    callback.onComplete();
                }

                @Override
                public void onError(Throwable error) {
                    errorMessage[0] = error.getMessage();
                    callback.onError(error);
                }
            });

            recordChatMetrics("stream_chat", systemPrompt, userMessage, null,
                            startTime, success[0], errorMessage[0]);

        } catch (Exception e) {
            errorMessage[0] = e.getMessage();
            recordChatMetrics("stream_chat", systemPrompt, userMessage, null,
                            startTime, false, errorMessage[0]);
            throw e;
        }
    }

    public String analyzeImage(String imageUrl, String prompt) {
        return analyzeImage(imageUrl, prompt, false);
    }

    public String analyzeImage(String imageUrl, String prompt, boolean isBase64) {
        long startTime = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        String response = null;

        try {
            response = gptChatService.analyzeImage(imageUrl, prompt, isBase64);
            success = response != null && !response.isEmpty();

            Map<String, Object> inputParams = new HashMap<>();
            inputParams.put("hasImage", true);
            inputParams.put("isBase64", isBase64);
            inputParams.put("promptLength", prompt != null ? prompt.length() : 0);

            Map<String, Object> outputResult = new HashMap<>();
            if (response != null) {
                outputResult.put("responseLength", response.length());
            }
            outputResult.put("latencyMs", System.currentTimeMillis() - startTime);

            unifiedMemoryService.recordBusinessAction(
                    "image_analysis", "analyze_image",
                    inputParams, outputResult, success, errorMessage);

            return response;
        } catch (Exception e) {
            errorMessage = e.getMessage();
            
            Map<String, Object> inputParams = new HashMap<>();
            inputParams.put("hasImage", true);
            inputParams.put("isBase64", isBase64);
            
            unifiedMemoryService.recordBusinessAction(
                    "image_analysis", "analyze_image",
                    inputParams, new HashMap<>(), false, errorMessage);
            
            throw e;
        }
    }

    public String intelligentChat(String sessionId, String userMessage, String context) {
        log.info("智能对话开始: sessionId={}, message={}", 
                sessionId, userMessage.substring(0, Math.min(50, userMessage.length())));

        List<Knowledge> relevantKnowledge = knowledgeService.searchSimilar(userMessage, 5);
        String enhancedPrompt = buildEnhancedPrompt(userMessage, relevantKnowledge, context);

        String response = gptChatService.chat(enhancedPrompt, userMessage);

        learnFromConversation(sessionId, userMessage, response, relevantKnowledge);

        return response;
    }

    public void intelligentStreamChat(String sessionId, String userMessage, String context,
                                       StreamCallback callback) {
        log.info("智能流式对话开始: sessionId={}", sessionId);

        List<Knowledge> relevantKnowledge = knowledgeService.searchSimilar(userMessage, 5);
        String enhancedPrompt = buildEnhancedPrompt(userMessage, relevantKnowledge, context);

        StringBuilder fullResponse = new StringBuilder();

        gptChatService.chatStream(enhancedPrompt, userMessage, new GptChatService.StreamCallback() {
            @Override
            public void onContent(String content) {
                fullResponse.append(content);
                callback.onContent(content);
            }

            @Override
            public void onComplete() {
                learnFromConversation(sessionId, userMessage, fullResponse.toString(), relevantKnowledge);
                callback.onComplete();
            }

            @Override
            public void onError(Throwable error) {
                callback.onError(error);
            }
        });
    }

    @Transactional
    public ChatMessage sessionChat(String sessionId, String userMessage, String[] imageUrls, Long userId) {
        return sessionChat(sessionId, userMessage, imageUrls, userId, null);
    }

    @Transactional
    public ChatMessage sessionChat(String sessionId, String userMessage, String[] imageUrls, 
                                   Long userId, String preferredModel) {
        ensureSessionExists(sessionId, userId);
        saveMessage(sessionId, "user", userMessage, imageUrls, userId);

        List<ChatMessage> history = getSessionMessages(sessionId);

        String assistantReply = generateReply(sessionId, userMessage, imageUrls, history, userId, preferredModel);

        return saveMessage(sessionId, "assistant", assistantReply, null, userId);
    }

    private String generateReply(String sessionId, String userMessage, String[] imageUrls,
                                  List<ChatMessage> history, Long userId, String preferredModel) {
        try {
            String intent = detectIntent(userMessage);
            log.info("检测到用户意图: {}", intent);

            Long usageId = evolutionCoreService.recordSkillUsage(
                    "AI_CHAT", "AI聊天助手", intent,
                    sessionId, userId, true, 0L,
                    Map.of("intent", intent, "messageLength", userMessage.length()),
                    null, null);

            switch (intent) {
                case "LEARN_PROMPT":
                    return handleLearnPrompt(userMessage, userId);
                case "LEARN_KNOWLEDGE":
                    return handleLearnKnowledge(userMessage, userId);
                case "CONFIG_SKILL":
                    return handleConfigSkill(userMessage, userId);
                case "QUERY_SKILL":
                    return handleQuerySkill(userMessage);
                case "QUERY_KNOWLEDGE":
                    return handleQueryKnowledge(userMessage);
                case "VIDEO_GENERATION":
                    return handleVideoGeneration(userMessage, imageUrls, history, preferredModel);
                default:
                    return handleGeneralChat(sessionId, userMessage, imageUrls, history, userId, preferredModel);
            }
        } catch (Exception e) {
            log.error("AI回复生成失败: {}", e.getMessage(), e);
            return "抱歉，处理您的请求时出现了错误。请稍后重试或联系管理员。";
        }
    }

    private String detectIntent(String message) {
        String lowerMsg = message.toLowerCase();

        if (lowerMsg.contains("学习提示词") || lowerMsg.contains("记录提示词") || 
            lowerMsg.contains("保存这个提示词") || lowerMsg.contains("添加提示词") ||
            lowerMsg.contains("提示词入库") || lowerMsg.contains("学习这个prompt")) {
            return "LEARN_PROMPT";
        }

        if (lowerMsg.contains("知识") && (lowerMsg.contains("学习") || lowerMsg.contains("记录"))) {
            return "LEARN_KNOWLEDGE";
        }

        if (lowerMsg.contains("配置") && lowerMsg.contains("skill")) {
            return "CONFIG_SKILL";
        }

        if (lowerMsg.contains("查询") && lowerMsg.contains("skill")) {
            return "QUERY_SKILL";
        }

        if (lowerMsg.contains("查询知识") || lowerMsg.contains("搜索知识")) {
            return "QUERY_KNOWLEDGE";
        }

        if (lowerMsg.contains("生成视频") || lowerMsg.contains("创建视频") || 
            lowerMsg.contains("制作视频") || lowerMsg.contains("视频生成")) {
            return "VIDEO_GENERATION";
        }

        return "GENERAL_CHAT";
    }

    private String handleLearnPrompt(String userMessage, Long userId) {
        try {
            String title = extractField(userMessage, "标题", "title");
            String content = extractField(userMessage, "内容", "content");
            String category = extractField(userMessage, "分类", "category");

            if (content == null || content.isEmpty()) {
                content = extractPromptContent(userMessage);
            }

            if (content == null || content.isEmpty()) {
                return "请提供要学习的提示词内容。格式示例：\n标题：xxx\n内容：xxx\n分类：xxx";
            }

            if (title == null || title.isEmpty()) {
                title = "用户提示词" + System.currentTimeMillis();
            }

            if (category == null || category.isEmpty()) {
                category = "PROMPT";
            }

            Knowledge knowledge = new Knowledge();
            knowledge.setTitle(title);
            knowledge.setContent(content);
            knowledge.setType(category.toUpperCase());
            knowledge.setSource("USER_INPUT");
            knowledge.setConfidence(0.9);
            if (userId != null) {
                knowledge.setCreatedBy(userId);
            }
            knowledge.setCreatedAt(LocalDateTime.now());

            List<String> tags = new ArrayList<>();
            tags.add("用户提示词");
            tags.add(category);
            if (content.contains("视频")) tags.add("视频生成");
            if (content.contains("产品")) tags.add("产品演示");
            knowledge.setTags(objectMapper.writeValueAsString(tags));

            knowledgeService.storeKnowledge(knowledge);

            log.info("成功学习提示词: title={}, userId={}", title, userId);

            return "已成功学习并保存提示词！\n\n" +
                   "标题：" + title + "\n" +
                   "分类：" + category + "\n" +
                   "内容预览：" + (content.length() > 100 ? content.substring(0, 100) + "..." : content) + "\n\n" +
                   "您可以在知识库中查看和管理这个提示词。";

        } catch (Exception e) {
            log.error("学习提示词失败: ", e);
            return "学习提示词时出现错误: " + e.getMessage();
        }
    }

    private String handleLearnKnowledge(String userMessage, Long userId) {
        try {
            String title = extractField(userMessage, "标题", "title");
            String content = extractField(userMessage, "内容", "content");
            String type = extractField(userMessage, "类型", "type");

            if (content == null || content.isEmpty()) {
                content = userMessage.replaceAll("(?i)(学习|记录|保存|添加|知识|标题|内容|类型)[：:]", "").trim();
            }

            if (content == null || content.isEmpty()) {
                return "请提供要学习的知识内容。";
            }

            String extractedKnowledge = extractKnowledgeEssence(content);

            if (title == null || title.isEmpty()) {
                title = extractKnowledgeTitle(content, extractedKnowledge);
            }

            if (type == null || type.isEmpty()) {
                type = detectKnowledgeType(content);
            }

            Knowledge knowledge = new Knowledge();
            knowledge.setTitle(title);
            knowledge.setContent(content);
            knowledge.setType(type);
            knowledge.setSource("USER_INPUT");
            knowledge.setConfidence(0.8);
            if (userId != null) {
                knowledge.setCreatedBy(userId);
            }
            knowledge.setCreatedAt(LocalDateTime.now());

            try {
                List<String> tags = extractKnowledgeTags(content, extractedKnowledge);
                knowledge.setTags(objectMapper.writeValueAsString(tags));
            } catch (Exception e) {
                log.warn("提取知识标签失败", e);
            }

            knowledgeService.storeKnowledge(knowledge);

            StringBuilder response = new StringBuilder();
            response.append("已成功学习并保存知识！\n\n");
            response.append("═══════════════════════════════════════\n");
            response.append("标题：").append(title).append("\n");
            response.append("类型：").append(type).append("\n");
            response.append("已自动分析并存储该知识。您可以在知识库中查看完整内容。\n");
            response.append("═══════════════════════════════════════\n");

            return response.toString();

        } catch (Exception e) {
            log.error("学习知识失败: ", e);
            return "学习知识时出现错误: " + e.getMessage();
        }
    }

    private String handleConfigSkill(String userMessage, Long userId) {
        return "技能配置功能正在开发中，敬请期待。您可以暂时通过系统配置界面进行Skill配置。";
    }

    private String handleQuerySkill(String userMessage) {
        try {
            List<Map<String, Object>> skills = queryAllActiveSkills();
            
            if (skills.isEmpty()) {
                return "当前系统中没有可用的技能配置。";
            }

            StringBuilder response = new StringBuilder();
            response.append("当前系统可用技能列表：\n\n");
            
            for (Map<String, Object> skill : skills) {
                response.append("■ ").append(skill.get("skillCode"))
                       .append(" - ").append(skill.get("skillName")).append("\n");
                response.append("  分类：").append(skill.get("category")).append("\n");
                response.append("  状态：").append(skill.get("isActive") != null ? "活跃" : "停用").append("\n\n");
            }

            response.append("共找到 ").append(skills.size()).append(" 个可用技能。\n");
            response.append("如需了解某个技能的详细参数，请输入\"查询技能 [技能名称]\"。");

            return response.toString();

        } catch (Exception e) {
            log.error("查询技能失败: ", e);
            return "查询技能信息时出现错误: " + e.getMessage();
        }
    }

    private String handleQueryKnowledge(String userMessage) {
        try {
            String query = userMessage.replaceAll("(?i)(查询|搜索|知识)[：:]", "").trim();
            
            if (query.isEmpty()) {
                query = userMessage;
            }

            List<Knowledge> results = knowledgeService.searchSimilar(query, 10);

            if (results.isEmpty()) {
                return "未找到与\"" + query + "\"相关的知识。\n\n建议：\n1. 尝试使用不同的关键词\n2. 确认拼写是否正确\n3. 可以通过\"学习知识\"命令添加新知识";
            }

            StringBuilder response = new StringBuilder();
            response.append("找到 ").append(results.size()).append(" 条相关知识：\n\n");

            for (int i = 0; i < Math.min(results.size(), 5); i++) {
                Knowledge k = results.get(i);
                response.append((i + 1)).append(". 【").append(k.getTitle()).append("】\n");
                
                String content = k.getContent();
                if (content != null && content.length() > 150) {
                    content = content.substring(0, 150) + "...";
                }
                response.append("   ").append(content).append("\n");
                response.append("   类型：").append(k.getType()).append(" | ");
                response.append("置信度：").append(String.format("%.1f%%", k.getConfidence() * 100)).append("\n\n");
            }

            if (results.size() > 5) {
                response.append("... 还有 ").append(results.size() - 5).append(" 条相关结果\n");
            }

            response.append("如需查看某条知识的完整内容，请输入序号或标题关键词。");

            return response.toString();

        } catch (Exception e) {
            log.error("查询知识失败: ", e);
            return "查询知识时出现错误: " + e.getMessage();
        }
    }

    private String handleVideoGeneration(String userMessage, String[] imageUrls, 
                                         List<ChatMessage> history, String preferredModel) {
        return "视频生成功能已集成到智能对话中。您可以直接描述需要生成的视频内容，\n" +
               "例如：\"帮我生成一个30秒的产品展示视频，产品是智能手表\"\n\n" +
               "或者使用专门的视频生成接口以获得更好的体验。";
    }

    private String handleGeneralChat(String sessionId, String userMessage, String[] imageUrls,
                                      List<ChatMessage> history, Long userId, String preferredModel) {
        try {
            List<Knowledge> relevantKnowledge = knowledgeService.searchSimilar(userMessage, 3);
            
            String systemPrompt = buildSystemPrompt(history, relevantKnowledge);
            
            String contextMessages = buildContextMessages(history, MAX_CONTEXT_MESSAGES / 2);

            String fullUserMessage = contextMessages + "\n用户: " + userMessage;

            if (imageUrls != null && imageUrls.length > 0) {
                fullUserMessage += "\n[用户上传了" + imageUrls.length + "张图片]";
            }

            String response = gptChatService.chat(systemPrompt, fullUserMessage);

            if (relevantKnowledge != null && !relevantKnowledge.isEmpty()) {
                for (Knowledge k : relevantKnowledge) {
                    knowledgeService.recordApplication(k.getKnowledgeId(), true);
                }
            }

            return response;

        } catch (Exception e) {
            log.error("通用对话处理失败: ", e);
            return "抱歉，我暂时无法回答这个问题。请稍后重试或联系管理员。";
        }
    }

    private String buildEnhancedPrompt(String userMessage, List<Knowledge> knowledge, String context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个智能视频生产助手，具备以下能力:\n");
        prompt.append("1. 视频脚本创作和优化\n");
        prompt.append("2. 产品卖点分析和提炼\n");
        prompt.append("3. 用户画像和目标受众分析\n");
        prompt.append("4. 视频效果预测和改进建议\n");
        prompt.append("5. 避坑规则和爆款规律应用\n\n");

        if (knowledge != null && !knowledge.isEmpty()) {
            prompt.append("【相关知识库内容】\n");
            for (Knowledge k : knowledge) {
                prompt.append("- ").append(k.getTitle()).append("\n");
                String content = k.getContent();
                if (content != null && content.length() > 200) {
                    content = content.substring(0, 200) + "...";
                }
                prompt.append("  ").append(content).append("\n\n");
            }
        }

        if (context != null && !context.isEmpty()) {
            prompt.append("【上下文信息】\n");
            prompt.append(context).append("\n\n");
        }

        prompt.append("请基于以上知识和上下文，回答用户的问题。如果知识库中有相关内容，请优先参考并应用。\n");

        return prompt.toString();
    }

    private String buildSystemPrompt(List<ChatMessage> history, List<Knowledge> knowledge) {
        StringBuilder systemPrompt = new StringBuilder();
        
        systemPrompt.append("你是一个专业的AI视频生产助手。你的职责包括：\n");
        systemPrompt.append("1. 视频脚本创作和优化建议\n");
        systemPrompt.append("2. 产品卖点分析和提炼\n");
        systemPrompt.append("3. 用户画像和目标受众分析\n");
        systemPrompt.append("4. 视频效果预测和改进建议\n");
        systemPrompt.append("5. 回答用户关于视频生产的相关问题\n\n");

        if (knowledge != null && !knowledge.isEmpty()) {
            systemPrompt.append("【参考知识】\n");
            for (Knowledge k : knowledge.subList(0, Math.min(3, knowledge.size()))) {
                systemPrompt.append("- ").append(k.getTitle()).append(": ")
                          .append(k.getContent() != null && k.getContent().length() > 100 ? 
                                  k.getContent().substring(0, 100) + "..." : k.getContent())
                          .append("\n");
            }
            systemPrompt.append("\n");
        }

        systemPrompt.append("请基于以上信息，专业、友好地回答用户的问题。如果涉及视频生产，请提供具体可行的建议。\n");

        return systemPrompt.toString();
    }

    private String buildContextMessages(List<ChatMessage> history, int limit) {
        if (history == null || history.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        int startIdx = Math.max(0, history.size() - limit);

        for (int i = startIdx; i < history.size() - 1; i++) {
            ChatMessage msg = history.get(i);
            String role = "assistant".equals(msg.getRole()) ? "助手" : "用户";
            context.append(role).append(": ").append(msg.getContent()).append("\n");
        }

        return context.toString();
    }

    private void learnFromConversation(String sessionId, String userMessage, String response,
                                        List<Knowledge> usedKnowledge) {
        try {
            if (shouldCreateKnowledge(userMessage, response)) {
                Knowledge newKnowledge = extractKnowledgeFromChat(userMessage, response);
                if (newKnowledge != null) {
                    knowledgeService.storeKnowledge(newKnowledge);
                    log.info("从对话中学习并创建知识: sessionId={}, knowledgeId={}",
                            sessionId, newKnowledge.getKnowledgeId());
                }
            }

            if (usedKnowledge != null) {
                for (Knowledge k : usedKnowledge) {
                    knowledgeService.recordApplication(k.getKnowledgeId(), true);
                }
            }

        } catch (Exception e) {
            log.warn("从对话学习失败: {}", e.getMessage());
        }
    }

    private boolean shouldCreateKnowledge(String userMessage, String response) {
        if (response == null || response.length() < 100) {
            return false;
        }

        String[] knowledgeIndicators = {
            "建议", "方法", "技巧", "规律", "规则", "策略",
            "优化", "改进", "提升", "避免", "注意", "关键"
        };

        for (String indicator : knowledgeIndicators) {
            if (response.contains(indicator)) {
                return true;
            }
        }

        return false;
    }

    private Knowledge extractKnowledgeFromChat(String userMessage, String response) {
        try {
            Knowledge knowledge = new Knowledge();
            knowledge.setKnowledgeId("KNW_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            knowledge.setTitle(extractTitleFromResponse(response));
            knowledge.setContent(response);
            knowledge.setType("CONVERSATION_LEARNING");
            knowledge.setSource("CHAT_LEARNING");
            knowledge.setConfidence(0.7);
            knowledge.setCreatedAt(LocalDateTime.now());

            List<String> tags = new ArrayList<>();
            tags.add("对话学习");
            if (userMessage.contains("视频")) tags.add("视频生产");
            if (userMessage.contains("产品")) tags.add("产品分析");
            knowledge.setTags(objectMapper.writeValueAsString(tags));

            return knowledge;

        } catch (Exception e) {
            log.warn("从对话提取知识失败", e);
            return null;
        }
    }

    private ChatMessage saveMessage(String sessionId, String role, String content, 
                                    String[] imageUrls, Long userId) {
        ChatMessage message = new ChatMessage();
        message.setMessageId("MSG_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setUserId(userId);
        message.setCreatedAt(LocalDateTime.now());

        if (imageUrls != null && imageUrls.length > 0) {
            try {
                message.setImageUrls(objectMapper.writeValueAsString(imageUrls));
            } catch (Exception e) {
                log.warn("序列化图片URL失败", e);
            }
        }

        messageMapper.insert(message);
        return message;
    }

    public List<ChatMessage> getSessionMessages(String sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId)
               .orderByAsc(ChatMessage::getCreatedAt);
        return messageMapper.selectList(wrapper);
    }

    public void clearSession(String sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId);
        messageMapper.delete(wrapper);

        sessionMapper.deleteById(sessionId);
        log.info("会话已清除: sessionId={}", sessionId);
    }

    private void ensureSessionExists(String sessionId, Long userId) {
        ChatSession existingSession = sessionMapper.selectById(sessionId);
        if (existingSession == null) {
            ChatSession session = new ChatSession();
            session.setSessionId(sessionId);
            session.setUserId(userId);
            session.setTitle("新对话");
            session.setStatus("ACTIVE");
            session.setCreatedAt(LocalDateTime.now());
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.insert(session);
            log.info("创建新聊天会话: sessionId={}, userId={}", sessionId, userId);
        }
    }

    private void recordChatMetrics(String chatType, String systemPrompt, String userMessage,
                                   String response, long startTime, boolean success, String errorMessage) {
        try {
            Map<String, Object> inputParams = new HashMap<>();
            inputParams.put("systemPrompt", systemPrompt != null ? systemPrompt.substring(0, Math.min(systemPrompt.length(), 100)) : "");
            inputParams.put("userMessage", userMessage.substring(0, Math.min(userMessage.length(), 200)));
            inputParams.put("platform", chatType);

            Map<String, Object> outputResult = new HashMap<>();
            if (response != null) {
                outputResult.put("responseLength", response.length());
                outputResult.put("responsePreview", response.substring(0, Math.min(response.length(), 100)));
            }
            outputResult.put("latencyMs", System.currentTimeMillis() - startTime);

            unifiedMemoryService.recordBusinessAction(
                    chatType, "chat_completion",
                    inputParams, outputResult, success, errorMessage);
        } catch (Exception e) {
            log.debug("记录对话指标失败: type={}", chatType, e);
        }
    }

    private String extractField(String text, String fieldName, String fallbackName) {
        Pattern pattern = Pattern.compile(fieldName + "[\\s:：](.+?)(?:\n|$)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        pattern = Pattern.compile(fallbackName + "[\\s:：](.+?)(?:\n|$)", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return null;
    }

    private String extractPromptContent(String message) {
        String cleaned = message.replaceAll("(?i)(学习|记录|保存|添加|提示词|prompt|标题|内容|分类)[\\s:：]", "");
        cleaned = cleaned.replaceAll("[\\n\\r]+", " ").trim();
        return cleaned.length() > 10 ? cleaned : null;
    }

    private String extractKnowledgeEssence(String content) {
        try {
            String prompt = "请作为知识提炼专家，分析以下内容，提取核心要点（不超过200字）：\n\n" + content;
            String essence = gptChatService.chat("你是知识提炼专家", prompt);
            return essence != null && !essence.isEmpty() ? essence : extractSimpleEssence(content);
        } catch (Exception e) {
            return extractSimpleEssence(content);
        }
    }

    private String extractSimpleEssence(String content) {
        String[] sentences = content.split("[。！？]");
        StringBuilder essence = new StringBuilder();
        int count = 0;
        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.length() > 20 && count < 3) {
                essence.append(sentence).append("。");
                count++;
            }
        }
        return essence.length() > 0 ? essence.toString() : content.substring(0, Math.min(200, content.length()));
    }

    private String extractKnowledgeTitle(String content, String essence) {
        if (essence != null && essence.length() > 20) {
            return essence.substring(0, Math.min(50, essence.indexOf("\n") > 0 ? essence.indexOf("\n") : 50)).trim();
        }
        String[] parts = content.split("[\\n\\r]");
        return parts.length > 0 ? parts[0].trim() : "用户知识";
    }

    private String detectKnowledgeType(String content) {
        String lowerContent = content.toLowerCase();
        if (lowerContent.contains("视频") || lowerContent.contains("拍摄")) return "VIDEO_PRODUCTION";
        if (lowerContent.contains("产品") || lowerContent.contains("商品")) return "PRODUCT_ANALYSIS";
        if (lowerContent.contains("营销") || lowerContent.contains("推广")) return "MARKETING";
        if (lowerContent.contains("技术") || lowerContent.contains("代码")) return "TECHNICAL";
        return "GENERAL";
    }

    private List<String> extractKnowledgeTags(String content, String essence) {
        List<String> tags = new ArrayList<>();
        tags.add("用户知识");
        if (content.contains("视频")) tags.add("视频");
        if (content.contains("产品")) tags.add("产品");
        if (content.contains("营销")) tags.add("营销");
        if (content.contains("技巧")) tags.add("技巧");
        return tags;
    }

    private String extractTitleFromResponse(String response) {
        if (response.contains("\n")) {
            return response.substring(0, response.indexOf("\n")).trim();
        }
        return response.substring(0, Math.min(50, response.length())).trim();
    }

    private List<Map<String, Object>> queryAllActiveSkills() {
        return new ArrayList<>();
    }

    @FunctionalInterface
    public interface StreamCallback {
        void onContent(String content);
        default void onComplete() {}
        default void onError(Throwable error) {}
    }
}
