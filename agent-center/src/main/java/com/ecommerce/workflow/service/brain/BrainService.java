package com.ecommerce.workflow.service.brain;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.ecommerce.workflow.entity.ExpertRoleConfig;
import com.ecommerce.workflow.service.ai.GptChatService;
import com.ecommerce.workflow.service.config.SysConfigService;
import com.ecommerce.workflow.service.learning.ExpertRoleService;
import com.ecommerce.workflow.service.memory.UnifiedMemoryService;
import com.ecommerce.workflow.service.orchestrator.IntelligentOrchestrator;
import com.ecommerce.workflow.service.rag.RagService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

@Service
public class BrainService {
    private static final Logger log = LoggerFactory.getLogger(BrainService.class);

    private final GptChatService gptChatService;
    private final UnifiedMemoryService unifiedMemoryService;
    private final IntelligentOrchestrator orchestrator;
    private final RagService ragService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private ExpertRoleService expertRoleService;

    private final Map<String, IntentDefinition> intentDefinitions = new LinkedHashMap<>();

    private double intentConfidenceThreshold = 0.6;
    private boolean llmIntentEnabled = true;
    private boolean keywordFallbackEnabled = true;

    public BrainService(GptChatService gptChatService, UnifiedMemoryService unifiedMemoryService,
                        IntelligentOrchestrator orchestrator, RagService ragService,
                        JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.gptChatService = gptChatService;
        this.unifiedMemoryService = unifiedMemoryService;
        this.orchestrator = orchestrator;
        this.ragService = ragService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        initTables();
        loadBrainConfig();
        loadIntentDefinitions();
        registerDefaultIntents();
        log.info("决策层Brain初始化完成: 意图定义数={}", intentDefinitions.size());
    }

    private void loadBrainConfig() {
        intentConfidenceThreshold = sysConfigService.getDoubleConfig("intent_confidence_threshold", 0.6);
        llmIntentEnabled = sysConfigService.getBooleanConfig("llm_intent_enabled", true);
        keywordFallbackEnabled = sysConfigService.getBooleanConfig("keyword_fallback_enabled", true);
    }

    private void initTables() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_brain_intent (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    intent_code VARCHAR(50) NOT NULL,
                    intent_name VARCHAR(100) NOT NULL,
                    description VARCHAR(500),
                    mode VARCHAR(10) DEFAULT 'REACT',
                    keywords TEXT,
                    required_params TEXT,
                    tool_chain TEXT,
                    priority INT DEFAULT 5,
                    enabled INT DEFAULT 1,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE INDEX uk_intent_code (intent_code)
                )
                """);
        } catch (Exception e) {
            log.debug("sys_brain_intent table may already exist");
        }

        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_brain_decision_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    session_id VARCHAR(64),
                    user_message TEXT,
                    intent VARCHAR(50),
                    mode VARCHAR(10),
                    plan_steps TEXT,
                    react_actions TEXT,
                    final_result TEXT,
                    success INT DEFAULT 0,
                    latency_ms BIGINT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_session (session_id),
                    INDEX idx_intent (intent),
                    INDEX idx_created (created_at)
                )
                """);
        } catch (Exception e) {
            log.debug("sys_brain_decision_log table may already exist");
        }
    }

    private void loadIntentDefinitions() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM sys_brain_intent WHERE enabled = 1 ORDER BY priority DESC");
            for (Map<String, Object> row : rows) {
                IntentDefinition def = new IntentDefinition();
                def.setIntentCode((String) row.get("intent_code"));
                def.setIntentName((String) row.get("intent_name"));
                def.setDescription((String) row.get("description"));
                def.setMode((String) row.get("mode"));
                def.setKeywords((String) row.get("keywords"));
                def.setRequiredParams((String) row.get("required_params"));
                def.setToolChain((String) row.get("tool_chain"));
                def.setPriority(row.get("priority") != null ? ((Number) row.get("priority")).intValue() : 5);
                intentDefinitions.put(def.getIntentCode(), def);
            }
            log.info("从数据库加载意图定义: {} 条", rows.size());
        } catch (Exception e) {
            log.debug("从数据库加载意图定义失败，使用默认值", e);
        }
    }

    private void registerDefaultIntents() {
        registerIntent("video_generation", "视频生成", "生成产品视频，包括图片分析、提示词生成、视频创建",
                "PLAN", "视频,生成,制作,拍,录制", "imageUrls,productName", "image_analyze,compliance_check,video_generate");
        registerIntent("script_writing", "脚本撰写", "撰写产品视频脚本", "REACT", "脚本,文案,写,撰写,创作",
                "productName,category", "knowledge_search,ai_chat,compliance_check");
        registerIntent("product_selection", "选品分析", "分析推荐适合的产品", "REACT", "选品,推荐,分析,产品,商品",
                "category,platform", "data_analysis,knowledge_search,ai_chat");
        registerIntent("data_analysis", "数据分析", "分析业务数据并生成报告", "REACT", "数据,分析,报告,统计,报表",
                "dataType,dateRange", "data_analysis,ai_chat");
        registerIntent("compliance_check", "合规检查", "检查内容合规性", "REACT", "合规,检查,审核,违规",
                "content,platform", "compliance_check");
        registerIntent("knowledge_query", "知识查询", "查询知识库中的信息", "REACT", "知识,查询,搜索,查找",
                "query", "knowledge_search,rag_query,vector_search");
        registerIntent("project_file_scout", "项目文件侦察", "读取当前项目白名单目录内的本地文件并返回内容",
                "REACT", "读取文件,查看文件,打开文件,文件内容,D:\\choice_product,.md,.java,.html,.css",
                "target_file", "project_file_scout");
        registerIntent("database_query", "数据库只读查询", "查询数据库表结构、索引和简单诊断信息",
                "REACT", "数据库,表结构,索引,SHOW CREATE TABLE,EXPLAIN,慢查询",
                "table_name", "database_query");
        registerIntent("skill_config", "技能配置", "配置或管理技能", "REACT", "技能,配置,设置,参数",
                "skillCode,action", "skill_execute");
        registerIntent("mcp_config", "MCP配置", "配置MCP服务器", "REACT", "MCP,服务器,工具,配置",
                "serverUrl,action", "mcp_call");
        registerIntent("agent_delegate", "子任务委托", "创建和管理子任务", "REACT", "子任务,委托,并行,分配",
                "taskType,description", "delegate_task");
        registerIntent("snapshot_manage", "快照管理", "创建或管理快照", "REACT", "快照,备份,恢复,回滚",
                "action", "snapshot_create");
        registerIntent("self_evolution", "自我进化", "触发系统自我进化", "PLAN", "进化,学习,优化,提升",
                "", "knowledge_search,skill_execute,ai_chat");
        registerIntent("general_chat", "通用对话", "通用AI对话", "REACT", "", "", "ai_chat");
    }

    public void registerIntent(String code, String name, String description, String mode,
                               String keywords, String requiredParams, String toolChain) {
        IntentDefinition def = new IntentDefinition();
        def.setIntentCode(code);
        def.setIntentName(name);
        def.setDescription(description);
        def.setMode(mode);
        def.setKeywords(keywords);
        def.setRequiredParams(requiredParams);
        def.setToolChain(toolChain);
        def.setPriority(5);
        intentDefinitions.put(code, def);

        try {
            jdbcTemplate.update("""
                INSERT INTO sys_brain_intent (intent_code, intent_name, description, mode, keywords, required_params, tool_chain, priority, enabled)
                VALUES (?, ?, ?, ?, ?, ?, ?, 5, 1)
                ON DUPLICATE KEY UPDATE intent_name=?, description=?, mode=?, keywords=?, required_params=?, tool_chain=?
                """, code, name, description, mode, keywords, requiredParams, toolChain,
                    name, description, mode, keywords, requiredParams, toolChain);
        } catch (Exception e) {
            log.debug("注册意图到数据库失败: {}", code, e);
        }
    }

    public DecisionResult decide(String userMessage, Map<String, Object> context) {
        long startTime = System.currentTimeMillis();
        loadBrainConfig();

        try {
            int topK = sysConfigService.getIntConfig("relevant_context_top_k", 3);
            Map<String, Object> memoryContext;
            try {
                memoryContext = unifiedMemoryService.getRelevantContext(userMessage, topK);
            } catch (Exception e) {
                log.warn("记忆上下文检索失败，继续执行决策: {}", e.getMessage());
                memoryContext = new HashMap<>();
            }
            try {
                ragService.enhanceQuery(userMessage, context);
            } catch (Exception e) {
                log.warn("RAG增强失败，继续执行决策: {}", e.getMessage());
            }

            IntentRecognitionResult intentResult;
            if (llmIntentEnabled) {
                intentResult = recognizeIntentWithLlm(userMessage, memoryContext, context);
                if (intentResult.getConfidence() < intentConfidenceThreshold && keywordFallbackEnabled) {
                    IntentRecognitionResult keywordResult = recognizeByKeywords(userMessage);
                    if (keywordResult.getConfidence() > intentResult.getConfidence()) {
                        intentResult = keywordResult;
                    }
                }
            } else {
                intentResult = recognizeByKeywords(userMessage);
            }

            IntentDefinition intentDef = intentDefinitions.get(intentResult.getIntent());
            if (intentDef == null) {
                intentDef = intentDefinitions.get("general_chat");
            }

            DecisionResult result;
            if ("PLAN".equals(intentDef.getMode())) {
                result = executePlanMode(userMessage, intentResult, intentDef, memoryContext, context);
            } else {
                result = executeReactMode(userMessage, intentResult, intentDef, memoryContext, context);
            }

            result.setIntent(intentResult.getIntent());
            result.setConfidence(intentResult.getConfidence());
            result.setMode(intentDef.getMode());
            result.setMissingParams(intentResult.getMissingParams());
            result.setLatencyMs(System.currentTimeMillis() - startTime);

            logDecision(userMessage, result, context);

            return result;
        } catch (Exception e) {
            log.error("决策层处理失败", e);
            DecisionResult fallback = new DecisionResult();
            fallback.setIntent("general_chat");
            fallback.setConfidence(0.0);
            fallback.setMode("REACT");
            fallback.setSuccess(false);
            fallback.setErrorMessage(e.getMessage());
            fallback.setLatencyMs(System.currentTimeMillis() - startTime);
            return fallback;
        }
    }

    private IntentRecognitionResult recognizeIntentWithLlm(String userMessage,
                                                            Map<String, Object> memoryContext,
                                                            Map<String, Object> context) {
        StringBuilder intentList = new StringBuilder();
        for (IntentDefinition def : intentDefinitions.values()) {
            intentList.append("- ").append(def.getIntentCode()).append(": ")
                    .append(def.getIntentName()).append(" (").append(def.getDescription()).append(")\n");
        }

        StringBuilder memoryHint = new StringBuilder();
        if (memoryContext.containsKey("relevantKnowledge")) {
            memoryHint.append("相关知识: ").append(memoryContext.get("relevantKnowledge")).append("\n");
        }
        if (memoryContext.containsKey("avoidanceRules")) {
            memoryHint.append("避坑规则: ").append(memoryContext.get("avoidanceRules")).append("\n");
        }
        if (memoryContext.containsKey("recommendedSkill")) {
            memoryHint.append("推荐技能: ").append(memoryContext.get("recommendedSkill")).append("\n");
        }

        StringBuilder expertHint = new StringBuilder();
        try {
            // 通用解决办法：扩大专家视角候选池，不再单纯靠关键词强过滤
            List<ExpertRoleConfig> relevantExperts =
                    expertRoleService.getAllExpertRoles().stream()
                            .filter(r -> "ACTIVE".equals(r.getStatus()))
                            .limit(6) // 覆盖更多可能的专家视角
                            .toList();
            if (!relevantExperts.isEmpty()) {
                expertHint.append("可供参考的专家视角（请根据语义从中筛选最相关的）:\n");
                for (ExpertRoleConfig expert : relevantExperts) {
                    List<String> dimensions = expertRoleService.getExpertCoreDimensions(expert.getRoleCode());
                    expertHint.append("- ").append(expert.getRoleName()).append(": ")
                            .append(dimensions != null ? String.join("、", dimensions) : "")
                            .append("\n");
                }
            }
        } catch (Exception e) {
            log.debug("获取专家视角失败", e);
        }

        String systemPrompt = String.format("""
                你是电商内容自动化生产系统的【首席战略决策大脑】。
                你的核心任务是：从用户杂乱的叙述中，精准蒸馏出其【当前最核心、最真实】的意图，并压制无关背景噪音。

                【防偏见与语义加权准则】：
                1. 全局语义优先：不要因为某个词（如“商品”）出现就立刻判定为业务意图。请权衡全句重心。
                2. 解决近期偏见：用户可能先说了一段废话或背景，重点往往在句末或转折词（但是、所以、我想问的是）之后。
                3. 意图冲突处理：如果用户既提到了“天气”又提到了“看电视”，且“看电视”是动作指令，则忽略“天气”。
                4. 参数非阻塞原则：如果识别出的意图缺少必要参数（如 product_id），严禁报错。请将缺失项填入 missing_params 数组，专家团队会以对话形式引导用户补充。

                支持的意图库:
                %s

                历史背景与知识:
                %s
                %s

                请返回严格的JSON格式:
                {
                  "intent": "意图代码",
                  "confidence": 0.0-1.0之间的置信度,
                  "is_logic_transition": true/false (是否包含需求转折),
                  "core_goal": "你理解的用户核心目标",
                  "entities": {"参数名": "提取到的值"},
                  "missing_params": ["该意图下确实缺少但必须的参数"],
                  "reasoning": "为什么你认为这是核心意图，你是如何排除干扰项的"
                }
                """, intentList.toString(), memoryHint.toString(), expertHint.toString());

        String response = gptChatService.chat(systemPrompt, userMessage);

        try {
            Map<String, Object> parsed = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
            return new IntentRecognitionResult(
                    (String) parsed.getOrDefault("intent", "general_chat"),
                    ((Number) parsed.getOrDefault("confidence", 0.5)).doubleValue(),
                    (Map<String, Object>) parsed.getOrDefault("entities", new HashMap<>()),
                    (List<String>) parsed.getOrDefault("missing_params", new ArrayList<>()),
                    (String) parsed.getOrDefault("reasoning", "")
            );
        } catch (Exception e) {
            log.warn("LLM意图识别解析失败，使用关键词匹配", e);
            return recognizeByKeywords(userMessage);
        }
    }

    private IntentRecognitionResult recognizeByKeywords(String userMessage) {
        String lowerMsg = userMessage.toLowerCase();
        IntentDefinition bestMatch = null;
        int bestScore = 0;

        for (IntentDefinition def : intentDefinitions.values()) {
            if (def.getKeywords() == null || def.getKeywords().isEmpty()) continue;
            String[] keywords = def.getKeywords().split(",");
            int score = 0;
            for (String kw : keywords) {
                if (lowerMsg.contains(kw.trim().toLowerCase())) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestMatch = def;
            }
        }

        if (bestMatch != null && bestScore > 0) {
            return new IntentRecognitionResult(bestMatch.getIntentCode(), bestScore * 0.2,
                    new HashMap<>(), new ArrayList<>(), "关键词匹配");
        }
        return new IntentRecognitionResult("general_chat", 0.3, new HashMap<>(), new ArrayList<>(), "默认匹配");
    }

    private DecisionResult executePlanMode(String userMessage, IntentRecognitionResult intentResult,
                                           IntentDefinition intentDef, Map<String, Object> memoryContext,
                                           Map<String, Object> context) {
        String[] tools = intentDef.getToolChain() != null ? intentDef.getToolChain().split(",") : new String[0];

        String planPrompt = String.format("""
                你是系统的规划器。请为以下任务制定执行计划。

                用户意图: %s
                可用工具: %s
                用户消息: %s
                上下文: %s

                请返回JSON格式的执行计划:
                {
                  "steps": [
                    {"step": 1, "tool": "工具名", "action": "动作描述", "params": {"key": "value"}, "depends_on": null},
                    {"step": 2, "tool": "工具名", "action": "动作描述", "params": {"key": "value"}, "depends_on": 1}
                  ],
                  "estimated_time": "预估时间",
                  "risk_assessment": "风险评估"
                }
                """, intentResult.getIntent(), String.join(",", tools), userMessage, context);

        String planResponse = gptChatService.chat(planPrompt, "请制定执行计划");

        DecisionResult result = new DecisionResult();
        result.setSuccess(true);
        result.setPlanSteps(planResponse);
        result.setData(Map.of("intent", intentResult.getIntent(), "mode", "PLAN",
                "tools", tools, "entities", intentResult.getEntities()));

        for (String tool : tools) {
            try {
                orchestrator.submitTask(tool.trim(), intentResult.getEntities(), intentDef.getPriority());
            } catch (Exception e) {
                log.warn("提交工具任务失败: {}", tool, e);
            }
        }

        return result;
    }

    private DecisionResult executeReactMode(String userMessage, IntentRecognitionResult intentResult,
                                            IntentDefinition intentDef, Map<String, Object> memoryContext,
                                            Map<String, Object> context) {
        String[] tools = intentDef.getToolChain() != null ? intentDef.getToolChain().split(",") : new String[0];

        DecisionResult result = new DecisionResult();
        result.setSuccess(true);

        List<Map<String, Object>> actions = new ArrayList<>();

        for (String tool : tools) {
            try {
                var taskResult = orchestrator.executeDirect(tool.trim(), intentResult.getEntities());
                Map<String, Object> action = new HashMap<>();
                action.put("tool", tool.trim());
                action.put("success", taskResult.isSuccess());
                action.put("data", taskResult.getData());
                if (!taskResult.isSuccess()) {
                    action.put("error", taskResult.getErrorMessage());
                }
                actions.add(action);
            } catch (Exception e) {
                Map<String, Object> action = new HashMap<>();
                action.put("tool", tool.trim());
                action.put("success", false);
                action.put("error", e.getMessage());
                actions.add(action);
            }
        }

        result.setReactActions(actions);
        result.setData(Map.of("intent", intentResult.getIntent(), "mode", "REACT",
                "actions", actions, "entities", intentResult.getEntities()));

        return result;
    }

    private void logDecision(String userMessage, DecisionResult result, Map<String, Object> context) {
        try {
            String sessionId = context != null && context.containsKey("sessionId")
                    ? context.get("sessionId").toString() : "unknown";
            jdbcTemplate.update("""
                INSERT INTO sys_brain_decision_log
                (session_id, user_message, intent, mode, plan_steps, react_actions, final_result, success, latency_ms, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """, sessionId, userMessage.substring(0, Math.min(userMessage.length(), 500)),
                    result.getIntent(), result.getMode(),
                    result.getPlanSteps(),
                    result.getReactActions() != null ? objectMapper.writeValueAsString(result.getReactActions()) : null,
                    result.getErrorMessage(),
                    result.isSuccess() ? 1 : 0, result.getLatencyMs());
        } catch (Exception e) {
            log.debug("记录决策日志失败", e);
        }
    }

    public List<IntentDefinition> getIntentDefinitions() {
        return new ArrayList<>(intentDefinitions.values());
    }

    public Map<String, Object> getBrainStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("intentCount", intentDefinitions.size());
        try {
            Long totalDecisions = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_brain_decision_log", Long.class);
            Long successDecisions = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_brain_decision_log WHERE success = 1", Long.class);
            stats.put("totalDecisions", totalDecisions != null ? totalDecisions : 0);
            stats.put("successDecisions", successDecisions != null ? successDecisions : 0);
        } catch (Exception e) {
            stats.put("totalDecisions", 0);
            stats.put("successDecisions", 0);
        }
        return stats;
    }

    public static class IntentDefinition {
        private String intentCode;
        private String intentName;
        private String description;
        private String mode;
        private String keywords;
        private String requiredParams;
        private String toolChain;
        private int priority;

        public String getIntentCode() { return intentCode; }
        public void setIntentCode(String intentCode) { this.intentCode = intentCode; }
        public String getIntentName() { return intentName; }
        public void setIntentName(String intentName) { this.intentName = intentName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getKeywords() { return keywords; }
        public void setKeywords(String keywords) { this.keywords = keywords; }
        public String getRequiredParams() { return requiredParams; }
        public void setRequiredParams(String requiredParams) { this.requiredParams = requiredParams; }
        public String getToolChain() { return toolChain; }
        public void setToolChain(String toolChain) { this.toolChain = toolChain; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
    }

    public static class IntentRecognitionResult {
        private final String intent;
        private final double confidence;
        private final Map<String, Object> entities;
        private final List<String> missingParams;
        private final String reasoning;

        public IntentRecognitionResult(String intent, double confidence, Map<String, Object> entities,
                                       List<String> missingParams, String reasoning) {
            this.intent = intent;
            this.confidence = confidence;
            this.entities = entities;
            this.missingParams = missingParams;
            this.reasoning = reasoning;
        }

        public String getIntent() { return intent; }
        public double getConfidence() { return confidence; }
        public Map<String, Object> getEntities() { return entities; }
        public List<String> getMissingParams() { return missingParams; }
        public String getReasoning() { return reasoning; }
    }

    public static class DecisionResult {
        private String intent;
        private double confidence;
        private String mode;
        private boolean success;
        private String errorMessage;
        private List<String> missingParams;
        private String planSteps;
        private List<Map<String, Object>> reactActions;
        private Map<String, Object> data;
        private long latencyMs;

        public String getIntent() { return intent; }
        public void setIntent(String intent) { this.intent = intent; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public List<String> getMissingParams() { return missingParams; }
        public void setMissingParams(List<String> missingParams) { this.missingParams = missingParams; }
        public String getPlanSteps() { return planSteps; }
        public void setPlanSteps(String planSteps) { this.planSteps = planSteps; }
        public List<Map<String, Object>> getReactActions() { return reactActions; }
        public void setReactActions(List<Map<String, Object>> reactActions) { this.reactActions = reactActions; }
        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> data) { this.data = data; }
        public long getLatencyMs() { return latencyMs; }
        public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    }
}
