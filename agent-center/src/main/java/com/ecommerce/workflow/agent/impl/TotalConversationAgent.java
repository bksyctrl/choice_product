package com.ecommerce.workflow.agent.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ecommerce.workflow.agent.Agent;
import com.ecommerce.workflow.agent.AgentRequest;
import com.ecommerce.workflow.agent.AgentResponse;
import com.ecommerce.workflow.agent.handler.IntentHandler;
import com.ecommerce.workflow.agent.handler.IntentHandlerFactory;
import com.ecommerce.workflow.agent.handler.IntentResult;
import com.ecommerce.workflow.engine.WorkflowEngine;
import com.ecommerce.workflow.entity.ChatSession;
import com.ecommerce.workflow.entity.ExpertRoleConfig;
import com.ecommerce.workflow.entity.IntentConfig;
import com.ecommerce.workflow.entity.Knowledge;
import com.ecommerce.workflow.entity.SkillConfig;
import com.ecommerce.workflow.mapper.WorkflowDefinitionMapper;
import com.ecommerce.workflow.mapper.WorkflowInstanceMapper;
import com.ecommerce.workflow.service.ai.GptChatService;
import com.ecommerce.workflow.service.brain.BrainService;
import com.ecommerce.workflow.service.checkpoint.AutoSnapshotService;
import com.ecommerce.workflow.service.evolution.SkillConfigService;
import com.ecommerce.workflow.service.knowledge.KnowledgeService;
import com.ecommerce.workflow.service.learning.ExpertRoleService;
import com.ecommerce.workflow.service.memory.UnifiedMemoryService;
import com.ecommerce.workflow.service.rag.RagService;
import com.ecommerce.workflow.service.session.SessionService;
import com.ecommerce.workflow.service.user.UserMindModelService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class TotalConversationAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(TotalConversationAgent.class);
    private final GptChatService gptChatService;
    private final WorkflowEngine workflowEngine;
    private final SessionService sessionService;
    private final WorkflowDefinitionMapper workflowDefinitionMapper;
    private final WorkflowInstanceMapper workflowInstanceMapper;
    private final SkillConfigService skillConfigService;
    private final SelfEvolutionAgent selfEvolutionAgent;
    private final ObjectMapper objectMapper;
    private final KnowledgeService knowledgeService;
    private final RagService ragService;
    private final UserMindModelService userMindModelService;
    private final IntentHandlerFactory intentHandlerFactory;
    private final BrainService brainService;
    private final ComplianceControlAgent complianceControlAgent;
    private final AutoSnapshotService autoSnapshotService;
    private final UnifiedMemoryService unifiedMemoryService;
    private final ExpertRoleService expertRoleService;

    public TotalConversationAgent(GptChatService gptChatService, WorkflowEngine workflowEngine,
                                SessionService sessionService, WorkflowDefinitionMapper workflowDefinitionMapper,
                                WorkflowInstanceMapper workflowInstanceMapper, SkillConfigService skillConfigService,
                                SelfEvolutionAgent selfEvolutionAgent, ObjectMapper objectMapper,
                                KnowledgeService knowledgeService, RagService ragService,
                                UserMindModelService userMindModelService,
                                IntentHandlerFactory intentHandlerFactory,
                                BrainService brainService,
                                ComplianceControlAgent complianceControlAgent,
                                AutoSnapshotService autoSnapshotService,
                                UnifiedMemoryService unifiedMemoryService,
                                ExpertRoleService expertRoleService) {
        this.gptChatService = gptChatService;
        this.workflowEngine = workflowEngine;
        this.sessionService = sessionService;
        this.workflowDefinitionMapper = workflowDefinitionMapper;
        this.workflowInstanceMapper = workflowInstanceMapper;
        this.skillConfigService = skillConfigService;
        this.selfEvolutionAgent = selfEvolutionAgent;
        this.objectMapper = objectMapper;
        this.knowledgeService = knowledgeService;
        this.ragService = ragService;
        this.userMindModelService = userMindModelService;
        this.intentHandlerFactory = intentHandlerFactory;
        this.brainService = brainService;
        this.complianceControlAgent = complianceControlAgent;
        this.autoSnapshotService = autoSnapshotService;
        this.unifiedMemoryService = unifiedMemoryService;
        this.expertRoleService = expertRoleService;
    }

    @Override
    public String getAgentType() {
        return "total_conversation";
    }

    @Override
    public String getName() {
        return "业务对话总Agent";
    }

    @Override
    public AgentResponse process(AgentRequest request) throws Exception {
        log.info("业务对话总Agent处理请求: userId={}, message={}", request.getUserId(), request.getMessage());

        ChatSession session = getOrCreateSession(request);
        sessionService.saveMessage(session.getSessionId(), "user", request.getMessage());

        try {
            ComplianceControlAgent.ComplianceCheckResult complianceResult =
                    complianceControlAgent.checkCompliance(request.getMessage(), "ALL");
            if (!complianceResult.isPassed()) {
                log.warn("安全层拦截: violations={}", complianceResult.getViolations());
                sessionService.saveMessage(session.getSessionId(), "assistant",
                        "您的内容未通过合规检查: " + complianceResult.getSummary());
                return AgentResponse.failure(new RuntimeException("内容未通过合规检查: " + complianceResult.getSummary()));
            }

            AgentResponse deterministicResponse = tryHandleDeterministicRequest(request, session);
            if (deterministicResponse != null) {
                return deterministicResponse;
            }

            List<Knowledge> relevantKnowledge = searchKnowledgeFailOpen(request.getMessage(), 3);
            List<SkillConfig> matchedSkills = matchSkills(request.getMessage(), request.getContext());
            String enhancedQuery = enhanceQueryFailOpen(request.getMessage(), request.getContext());

            // 通用解决办法：弃用硬编码关键词匹配，改为初步语义初选（或全量透传给大脑决策）
            List<ExpertRoleConfig> relevantExperts =
                    expertRoleService.getAllExpertRoles().stream()
                            .filter(r -> "ACTIVE".equals(r.getStatus()))
                            .sorted((a, b) -> {
                                // 简单的启发式排序：如果message包含角色名，优先级提高
                                boolean aMatch = request.getMessage().contains(a.getRoleName());
                                boolean bMatch = request.getMessage().contains(b.getRoleName());
                                if (aMatch && !bMatch) return -1;
                                if (!aMatch && bMatch) return 1;
                                return 0;
                            })
                            .limit(5) // 扩大候选池，交给 BrainService 去精准蒸馏
                            .toList();

            Map<String, Object> enhancedContext = new HashMap<>();
            if (request.getContext() != null) {
                enhancedContext.putAll(request.getContext());
            }

            // 视觉神经：如果上下文携带图片，标记给决策大脑
            if (enhancedContext.containsKey("images")) {
                List<Object> imgs = (List<Object>) enhancedContext.get("images");
                if (imgs != null && !imgs.isEmpty()) {
                    enhancedContext.put("hasImages", true);
                    enhancedContext.put("imageCount", imgs.size());
                }
            }

            enhancedContext.put("sessionId", session.getSessionId());
            if (!relevantKnowledge.isEmpty()) {
                StringBuilder knowledgeContext = new StringBuilder();
                for (Knowledge k : relevantKnowledge) {
                    knowledgeContext.append("- ").append(k.getTitle()).append(": ").append(k.getContent()).append("\n");
                }
                enhancedContext.put("relevantKnowledge", knowledgeContext.toString());
            }
            if (enhancedQuery != null && !enhancedQuery.isEmpty()) {
                enhancedContext.put("enhancedQuery", enhancedQuery);
            }
            if (!relevantExperts.isEmpty()) {
                StringBuilder expertContext = new StringBuilder();
                for (ExpertRoleConfig expert : relevantExperts) {
                    List<String> dimensions = expertRoleService.getExpertCoreDimensions(expert.getRoleCode());
                    expertContext.append(String.format("【%s】%s\n",
                            expert.getRoleName(),
                            dimensions != null ? String.join("、", dimensions) : ""));
                }
                enhancedContext.put("expertPerspectives", expertContext.toString());
            }

            request.setContext(enhancedContext);

            BrainService.DecisionResult brainResult = brainService.decide(request.getMessage(), enhancedContext);
            String intentType = brainResult.getIntent();

            // 通用解决办法：参数协商机制
            // 如果决策大脑识别出意图但缺少必要参数，不报错，而是引导用户补全
            if (brainResult.getMissingParams() != null && !brainResult.getMissingParams().isEmpty()) {
                String missingDesc = String.join("、", brainResult.getMissingParams());
                String reply = String.format("我理解您想进行【%s】，但目前还缺少必要的信息：%s。您能补充一下吗？或者您是想了解相关的通用逻辑？", 
                                             intentType, missingDesc);
                
                sessionService.saveMessage(session.getSessionId(), "assistant", reply);
                AgentResponse response = new AgentResponse();
                response.setSuccess(true);
                response.setMessage(reply);
                response.setData(Map.of("missingParams", brainResult.getMissingParams(), "intent", intentType));
                return response;
            }

            IntentResult intent;
            if (brainResult.getConfidence() > 0.6 && !"general_chat".equals(intentType)) {
                intent = new IntentResult(intentType, brainResult.getConfidence(),
                        brainResult.getData() != null ? (Map<String, Object>) brainResult.getData().get("entities") : new HashMap<>(),
                        List.of(), null);
                log.info("Brain决策层识别意图: intent={}, confidence={}, mode={}",
                        intentType, brainResult.getConfidence(), brainResult.getMode());
            } else {
                intent = recognizeIntent(request);
            }

            if (!matchedSkills.isEmpty()) {
                SkillConfig topSkill = matchedSkills.get(0);
                log.info("匹配到Skill: {}", topSkill.getSkillName());
                userMindModelService.learnFromAction(request.getUserId(), "SKILL_USED",
                        Map.of("skillId", topSkill.getSkillCode()));
            }

            IntentHandler handler = intentHandlerFactory.getHandler(intent.getIntent());
            AgentResponse response;
            if (handler != null) {
                response = handler.handle(request, session, intent);
            } else {
                response = handleWithBrainDecision(request, session, brainResult);
            }

            autoSnapshotService.autoSnapshotBeforeAction(intentType, request.getMessage(),
                    Map.of("userId", request.getUserId(), "intent", intentType));

            String skillCode = matchedSkills.isEmpty() ? intentType : matchedSkills.get(0).getSkillCode();
            unifiedMemoryService.recordBusinessAction(skillCode, intentType,
                    Map.of("message", request.getMessage()),
                    Map.of("success", response.isSuccess(), "intent", intentType),
                    response.isSuccess(),
                    response.getError() != null ? response.getError().toString() : null);

            sessionService.saveMessage(session.getSessionId(), "assistant",
                    response.getMessage() != null ? response.getMessage() : "处理完成");

            extractConversationKnowledge(request.getMessage(), response, intentType, relevantExperts);

            return response;
        } catch (Exception e) {
            log.error("Agent处理失败", e);
            sessionService.saveMessage(session.getSessionId(), "assistant", "抱歉,处理您的请求时出现错误: " + e.getMessage());
            return AgentResponse.failure(e);
        }
    }

    private AgentResponse handleWithBrainDecision(AgentRequest request, ChatSession session,
                                                   BrainService.DecisionResult brainResult) {
        AgentResponse response = new AgentResponse();
        response.setSuccess(brainResult.isSuccess());

        if (brainResult.getData() != null) {
            response.setData(brainResult.getData());
        }

        if ("PLAN".equals(brainResult.getMode()) && brainResult.getPlanSteps() != null) {
            response.setMessage("已为您制定执行计划:\n" + brainResult.getPlanSteps());
        } else if ("REACT".equals(brainResult.getMode()) && brainResult.getReactActions() != null) {
            StringBuilder msg = new StringBuilder("已执行以下操作:\n");
            for (Map<String, Object> action : brainResult.getReactActions()) {
                msg.append("- ").append(action.get("tool")).append(": ")
                   .append(Boolean.TRUE.equals(action.get("success")) ? "成功" : "失败").append("\n");
            }
            response.setMessage(msg.toString());
        } else {
            response.setMessage("处理完成");
        }

        return response;
    }

    private List<SkillConfig> matchSkills(String query, Map<String, Object> context) {
        List<SkillConfig> allSkills = skillConfigService.getAllActiveSkills();
        return allSkills.stream()
                .filter(skill -> matchesTrigger(skill, query, context))
                .sorted((a, b) -> Double.compare(
                    b.getConfidence() != null ? b.getConfidence() : 0.0,
                    a.getConfidence() != null ? a.getConfidence() : 0.0))
                .toList();
    }

    private boolean matchesTrigger(SkillConfig skill, String query, Map<String, Object> context) {
        String triggerPattern = skill.getTriggerPattern();
        if (triggerPattern == null || triggerPattern.isEmpty()) {
            return false;
        }

        try {
            Map<String, Object> pattern = objectMapper.readValue(triggerPattern, new TypeReference<Map<String, Object>>() {});
            String type = (String) pattern.getOrDefault("type", "KEYWORD");

            switch (type) {
                case "KEYWORD":
                    List<String> keywords = (List<String>) pattern.getOrDefault("keywords", List.of());
                    String lowerQuery = query.toLowerCase();
                    return keywords.stream().anyMatch(k -> lowerQuery.contains(k.toLowerCase()));
                case "INTENT":
                    String intent = (String) pattern.getOrDefault("intent", "");
                    return query.toLowerCase().contains(intent.toLowerCase());
                case "CONTEXT":
                    String contextKey = (String) pattern.getOrDefault("contextKey", "");
                    String contextValue = (String) pattern.getOrDefault("contextValue", "");
                    return context != null && contextValue.equals(context.get(contextKey));
                default:
                    return false;
            }
        } catch (Exception e) {
            log.warn("匹配触发条件失败: {}", skill.getSkillCode(), e);
            return false;
        }
    }

    private IntentResult recognizeIntent(AgentRequest request) {
        Map<String, IntentConfig> intentConfigs = intentHandlerFactory.getAllIntentConfigs();

        StringBuilder intentListBuilder = new StringBuilder();
        int index = 1;
        for (Map.Entry<String, IntentConfig> entry : intentConfigs.entrySet()) {
            IntentConfig config = entry.getValue();
            intentListBuilder.append(index++).append(". ")
                    .append(config.getIntentCode()).append(" - ")
                    .append(config.getIntentName())
                    .append("(").append(config.getDescription()).append(")\n");
        }

        String systemPrompt = String.format("""
                你是电商内容自动化生产系统的智能助手。请分析用户输入的意图,并返回JSON格式结果。

                支持的意图类型:
                %s

                请返回JSON格式:
                {
                  "intent": "意图类型",
                  "confidence": 0.95,
                  "entities": {
                    "platform": "平台(douyin/kuaishou等)",
                    "category": "品类",
                    "product_id": "商品ID",
                    "count": "数量",
                    "keywords": ["关键词列表"]
                  },
                  "missing_params": ["缺失的必要参数列表"],
                  "suggested_workflow": "建议的工作流编码"
                }
                """, intentListBuilder.toString());

        String response = gptChatService.chat(systemPrompt, request.getMessage());

        try {
            Map<String, Object> result = objectMapper.readValue(response, Map.class);
            return new IntentResult(
                    (String) result.get("intent"),
                    ((Number) result.getOrDefault("confidence", 0)).doubleValue(),
                    (Map<String, Object>) result.get("entities"),
                    (List<String>) result.get("missing_params"),
                    (String) result.get("suggested_workflow")
            );
        } catch (Exception e) {
            log.warn("意图识别解析失败,使用默认值", e);
            return new IntentResult("general_chat", 0.5, new HashMap<>(), List.of(), null);
        }
    }

    private ChatSession getOrCreateSession(AgentRequest request) {
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = java.util.UUID.randomUUID().toString();
        }

        ChatSession session = sessionService.getSession(sessionId);
        if (session == null) {
            session = sessionService.createSession(request.getUserId(), "AI对话");
        }
        request.setSessionId(session.getSessionId());
        return session;
    }

    private AgentResponse tryHandleDeterministicRequest(AgentRequest request, ChatSession session) throws Exception {
        String message = request.getMessage() != null ? request.getMessage() : "";
        IntentResult shortcutIntent = null;

        String targetFile = extractChoiceProductPath(message);
        if (targetFile != null && looksLikeFileReadRequest(message)) {
            Map<String, Object> entities = new HashMap<>();
            entities.put("target_file", targetFile);
            shortcutIntent = new IntentResult("project_file_scout", 0.99, entities, List.of(), null);
        } else if (looksLikeDatabaseReadRequest(message)) {
            String tableName = extractTableName(message);
            Map<String, Object> entities = new HashMap<>();
            if (tableName != null) {
                entities.put("table_name", tableName);
            }
            shortcutIntent = new IntentResult("database_query", 0.95, entities, List.of(), null);
        }

        if (shortcutIntent == null) {
            return null;
        }

        IntentHandler handler = intentHandlerFactory.getHandler(shortcutIntent.getIntent());
        if (handler == null) {
            return null;
        }

        log.info("确定性任务短路执行: intent={}, message={}", shortcutIntent.getIntent(), message);
        AgentResponse response = handler.handle(request, session, shortcutIntent);
        String reply = response.getMessage() != null ? response.getMessage() : response.getReply();
        if (reply == null && response.getError() != null) {
            reply = String.valueOf(response.getError());
        }
        sessionService.saveMessage(session.getSessionId(), "assistant", reply != null ? reply : "处理完成");
        return response;
    }

    private List<Knowledge> searchKnowledgeFailOpen(String message, int topK) {
        try {
            return knowledgeService.searchSimilar(message, topK);
        } catch (Exception e) {
            log.warn("知识检索失败，跳过RAG继续执行: {}", e.getMessage());
            return List.of();
        }
    }

    private String enhanceQueryFailOpen(String message, Map<String, Object> context) {
        try {
            return ragService.enhanceQuery(message, context);
        } catch (Exception e) {
            log.warn("RAG增强失败，使用原始问题继续执行: {}", e.getMessage());
            return "";
        }
    }

    private boolean looksLikeFileReadRequest(String message) {
        return message.contains("读取") || message.contains("查看") || message.contains("打开")
                || message.toLowerCase().contains("read");
    }

    private String extractChoiceProductPath(String message) {
        int start = message.indexOf("D:\\choice_product");
        if (start < 0) {
            start = message.indexOf("D:/choice_product");
        }
        if (start < 0) {
            return null;
        }
        int end = message.length();
        String delimiters = " \n\r\t，。；;、'\"`";
        for (int i = start; i < message.length(); i++) {
            if (delimiters.indexOf(message.charAt(i)) >= 0) {
                end = i;
                break;
            }
        }
        return message.substring(start, end).trim();
    }

    private boolean looksLikeDatabaseReadRequest(String message) {
        String lower = message.toLowerCase();
        return message.contains("数据库") || message.contains("表结构") || message.contains("索引")
                || lower.contains("show create table") || lower.contains("explain");
    }

    private String extractTableName(String message) {
        String cleaned = message.replace("`", " ");
        String[] parts = cleaned.split("[\\s，。；;()]+");
        for (String part : parts) {
            if (part.matches("[A-Za-z][A-Za-z0-9_]{2,}")) {
                String lower = part.toLowerCase();
                if (!List.of("select", "from", "where", "order", "limit", "offset", "explain", "show", "create", "table").contains(lower)) {
                    return part;
                }
            }
        }
        return null;
    }

    private void extractConversationKnowledge(String userMessage, AgentResponse response,
                                               String intentType,
                                               List<ExpertRoleConfig> relevantExperts) {
        try {
            if (response == null || !response.isSuccess()) return;
            String responseMsg = response.getMessage();
            if (responseMsg == null || responseMsg.length() < 50) return;

            Knowledge knowledge = new Knowledge();
            knowledge.setType("CONVERSATION_INSIGHT");
            knowledge.setTitle(String.format("[对话洞察] %s - %s", intentType,
                    userMessage.substring(0, Math.min(userMessage.length(), 30))));

            StringBuilder content = new StringBuilder();
            content.append("用户意图: ").append(intentType).append("\n");
            content.append("用户消息: ").append(userMessage, 0, Math.min(userMessage.length(), 500)).append("\n");
            content.append("系统回复: ").append(responseMsg, 0, Math.min(responseMsg.length(), 800)).append("\n");

            if (!relevantExperts.isEmpty()) {
                content.append("\n专家深度分析:\n");
                for (ExpertRoleConfig expert : relevantExperts) {
                    List<String> dimensions = expertRoleService.getExpertCoreDimensions(expert.getRoleCode());
                    content.append(String.format("【%s】分析维度: %s\n",
                            expert.getRoleName(),
                            dimensions != null ? String.join("、", dimensions) : ""));
                }

                try {
                    StringBuilder analysisPrompt = new StringBuilder();
                    analysisPrompt.append("请对以下对话内容进行深度知识提取，返回JSON格式:\n");
                    analysisPrompt.append("{\n");
                    analysisPrompt.append("  \"core_insight\": \"核心洞察\",\n");
                    analysisPrompt.append("  \"business_value\": \"业务价值\",\n");
                    analysisPrompt.append("  \"actionable_knowledge\": \"可操作知识\",\n");
                    analysisPrompt.append("  \"expert_perspectives\": {\n");
                    for (ExpertRoleConfig expert : relevantExperts) {
                        analysisPrompt.append("    \"").append(expert.getRoleName()).append("\": \"")
                                .append(expert.getRoleName()).append("视角的分析结论\",\n");
                    }
                    analysisPrompt.append("  },\n");
                    analysisPrompt.append("  \"related_patterns\": [\"关联规律1\", \"关联规律2\"],\n");
                    analysisPrompt.append("  \"confidence\": 0.85\n");
                    analysisPrompt.append("}\n\n");
                    analysisPrompt.append("用户意图: ").append(intentType).append("\n");
                    analysisPrompt.append("用户消息: ").append(userMessage, 0, Math.min(userMessage.length(), 300)).append("\n");
                    analysisPrompt.append("系统回复: ").append(responseMsg, 0, Math.min(responseMsg.length(), 500)).append("\n");

                    String expertAnalysis = gptChatService.chat(
                            "你是知识提取专家，擅长从对话中提取深度业务知识", analysisPrompt.toString());
                    if (expertAnalysis != null && !expertAnalysis.isEmpty()) {
                        content.append("\nAI深度分析结果:\n").append(expertAnalysis, 0, Math.min(expertAnalysis.length(), 1000)).append("\n");
                    }
                } catch (Exception e) {
                    log.debug("AI深度对话知识提取失败", e);
                }
            }

            knowledge.setContent(content.toString());
            knowledge.setSource("CONVERSATION");
            knowledge.setSourceId("conv_" + System.currentTimeMillis());
            knowledge.setConfidence(relevantExperts.isEmpty() ? 0.6 : 0.8);

            List<String> tags = new ArrayList<>();
            tags.add(intentType);
            tags.add("CONVERSATION_INSIGHT");
            for (ExpertRoleConfig expert : relevantExperts) {
                tags.add(expert.getRoleCode());
            }
            knowledge.setTags(objectMapper.writeValueAsString(tags.stream().distinct().limit(8).toList()));

            knowledgeService.storeKnowledge(knowledge);
            log.info("对话知识已深度提取: intent={}, experts={}", intentType, relevantExperts.size());
        } catch (Exception e) {
            log.debug("提取对话知识失败", e);
        }
    }
}
