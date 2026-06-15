package com.ecommerce.workflow.service.mcp;

import com.ecommerce.workflow.mcp.entity.McpServer;
import com.ecommerce.workflow.mcp.entity.McpTool;
import com.ecommerce.workflow.mcp.service.McpService;
import com.ecommerce.workflow.service.ai.GptChatService;
import com.ecommerce.workflow.service.memory.UnifiedMemoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AutoMcpConfigService {

    private static final Logger log = LoggerFactory.getLogger(AutoMcpConfigService.class);

    private final McpService mcpService;
    private final GptChatService gptChatService;
    private final UnifiedMemoryService memoryService;
    private final ObjectMapper objectMapper;

    @Value("${mcp.local-services.video-generation:http://localhost:8080/api/ai-video}")
    private String videoGenerationUrl;

    @Value("${mcp.local-services.image-analysis:http://localhost:8080/api/ai-video/analyze-image}")
    private String imageAnalysisUrl;

    @Value("${mcp.local-services.prompt-generation:http://localhost:8080/api/ai-video/generate-prompt}")
    private String promptGenerationUrl;

    @Value("${mcp.local-services.workflow:http://localhost:8080/api/workflow}")
    private String workflowUrl;

    @Value("${mcp.local-services.chat:http://localhost:8080/api/chat}")
    private String chatUrl;

    private volatile Map<String, Object> mcpRecommendations = new HashMap<>();
    private volatile long lastRecommendTime = 0;

    public AutoMcpConfigService(McpService mcpService,
                                GptChatService gptChatService,
                                UnifiedMemoryService memoryService,
                                ObjectMapper objectMapper) {
        this.mcpService = mcpService;
        this.gptChatService = gptChatService;
        this.memoryService = memoryService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedRateString = "${mcp.auto-config.interval-ms:86400000}")
    public void autoDiscoverAndConfigure() {
        log.info("=== 开始MCP自动发现与配置 ===");
        
        analyzeBusinessNeeds();
        discoverRecommendedServers();
        healthCheckAndOptimize();
        
        log.info("=== MCP自动发现与配置完成 ===");
    }

    private void analyzeBusinessNeeds() {
        try {
            List<McpServer> existingServers = mcpService.listServers();
            List<Map<String, Object>> recentTasks = getRecentBusinessTasks();
            
            if (recentTasks.isEmpty()) {
                log.info("暂无业务任务，跳过MCP需求分析");
                return;
            }

            String systemPrompt = String.format("""
                你是MCP服务器配置分析专家。根据当前的业务需求和已有的MCP服务器，
                分析需要哪些额外的MCP服务器来增强业务能力。
                
                重要规则：
                1. 如果系统已有视频生成能力（如 /api/ai-video 接口），必须推荐本地MCP服务器
                2. 本地服务URL格式: %%s
                3. 不要生成 example.com 等虚假地址
                4. 可用的本地服务包括：
                   - 视频生成: %%s
                   - 图像分析: %%s
                   - 提示词生成: %%s
                   - 工作流: %%s
                   - AI对话: %%s
                
                请返回JSON格式:
                {
                  "neededServers": [
                    {
                      "serverName": "服务器名称",
                      "serverUrl": "服务器URL（优先使用本地真实地址）",
                      "transport": "http|sse|stdio",
                      "description": "为什么需要这个服务器",
                      "businessValue": "对业务的价值",
                      "priority": "high|medium|low"
                    }
                  ],
                  "optimizationSuggestions": [
                    {
                      "serverId": "已有服务器ID",
                      "suggestion": "优化建议"
                    }
                  ]
                }
                """, videoGenerationUrl, videoGenerationUrl, imageAnalysisUrl, 
                    promptGenerationUrl, workflowUrl, chatUrl);

            String userPrompt = buildBusinessNeedsPrompt(existingServers, recentTasks);
            String response = gptChatService.chat(systemPrompt, userPrompt);

            try {
                // 清理 JSON 响应（去除 markdown 代码块等）
                String cleanedResponse = extractJsonFromResponse(response);
                Map<String, Object> analysis = objectMapper.readValue(cleanedResponse, new TypeReference<Map<String, Object>>() {});
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> neededServers = (List<Map<String, Object>>) analysis.getOrDefault("neededServers", new ArrayList<>());
                
                mcpRecommendations = new HashMap<>();
                mcpRecommendations.put("neededServers", neededServers);
                mcpRecommendations.put("analysisTime", System.currentTimeMillis());
                lastRecommendTime = System.currentTimeMillis();
                
                log.info("MCP需求分析完成，发现{}个推荐服务器", neededServers.size());
                
            } catch (Exception e) {
                log.error("解析MCP需求分析结果失败，原始响应: {}", response.substring(0, Math.min(200, response.length())), e);
            }
            
        } catch (Exception e) {
            log.error("MCP业务需求分析失败", e);
        }
    }

    private String buildBusinessNeedsPrompt(List<McpServer> existingServers, List<Map<String, Object>> recentTasks) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("当前业务情况:\n\n");
        prompt.append("已有MCP服务器:\n");
        if (existingServers.isEmpty()) {
            prompt.append("  暂无已注册的MCP服务器\n");
        } else {
            for (McpServer server : existingServers) {
                prompt.append("  - ").append(server.getServerName())
                      .append(" (").append(server.getServerUrl()).append(")")
                      .append(" [状态: ").append(server.getStatus()).append("]\n");
            }
        }
        
        prompt.append("\n近期业务任务:\n");
        for (Map<String, Object> task : recentTasks) {
            prompt.append("  - ").append(task.getOrDefault("taskType", "unknown"))
                  .append(": ").append(task.getOrDefault("description", ""))
                  .append(" [状态: ").append(task.getOrDefault("status", "unknown")).append("]\n");
        }
        
        // 添加本地可用服务信息
        prompt.append("\n本地可用服务（优先使用这些真实地址）:\n");
        prompt.append("  - 视频生成服务: ").append(videoGenerationUrl).append("\n");
        prompt.append("    功能: AI视频生成、任务管理、提示词生成\n");
        prompt.append("    端点: POST /generate, GET /task/{id}, POST /analyze-image\n");
        prompt.append("  - 图像分析服务: ").append(imageAnalysisUrl).append("\n");
        prompt.append("    功能: 上传图片分析、智能参数推荐\n");
        prompt.append("    端点: POST /analyze-image\n");
        prompt.append("  - 提示词生成服务: ").append(promptGenerationUrl).append("\n");
        prompt.append("    功能: 基于参数生成AI视频提示词\n");
        prompt.append("    端点: POST /generate-prompt\n");
        prompt.append("  - 工作流服务: ").append(workflowUrl).append("\n");
        prompt.append("    功能: 工作流执行、节点管理\n");
        prompt.append("  - AI对话服务: ").append(chatUrl).append("\n");
        prompt.append("    功能: 多轮对话、意图识别\n");
        
        prompt.append("\n请分析需要哪些MCP服务器来增强这些业务能力。");
        prompt.append("如果业务涉及视频生成，请推荐本地视频生成MCP服务器 (").append(videoGenerationUrl).append(")。");
        prompt.append("不要使用 example.com 等虚假域名。");
        
        return prompt.toString();
    }

    private List<Map<String, Object>> getRecentBusinessTasks() {
        List<Map<String, Object>> tasks = new ArrayList<>();
        
        try {
            List<McpServer> servers = mcpService.listServers();
            for (McpServer server : servers) {
                List<McpTool> tools = mcpService.listTools(server.getServerId());
                for (McpTool tool : tools) {
                    Map<String, Object> task = new HashMap<>();
                    task.put("taskType", "mcp_tool_usage");
                    task.put("description", "使用MCP工具: " + tool.getToolName());
                    task.put("status", "active");
                    tasks.add(task);
                }
            }
            
            Map<String, Object> videoTask = new HashMap<>();
            videoTask.put("taskType", "video_generation");
            videoTask.put("description", "视频生成任务");
            videoTask.put("status", "active");
            tasks.add(videoTask);
            
        } catch (Exception e) {
            log.error("获取业务任务失败", e);
        }
        
        return tasks;
    }

    private void discoverRecommendedServers() {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> recommendedServers = (List<Map<String, Object>>) 
                mcpRecommendations.getOrDefault("neededServers", new ArrayList<>());
            
            List<McpServer> existingServers = mcpService.listServers();
            Set<String> existingUrls = existingServers.stream()
                .map(McpServer::getServerUrl)
                .collect(Collectors.toSet());
            
            int autoRegisteredCount = 0;
            int pendingApprovalCount = 0;
            
            for (Map<String, Object> recommendation : recommendedServers) {
                String serverUrl = (String) recommendation.get("serverUrl");
                String priority = (String) recommendation.getOrDefault("priority", "medium");
                
                if (serverUrl != null && !existingUrls.contains(serverUrl)) {
                    if ("high".equals(priority) || "medium".equals(priority)) {
                        log.info("自动注册{}优先级MCP服务器: {}", priority, recommendation.get("serverName"));
                        autoRegisterServer(recommendation);
                        autoRegisteredCount++;
                        existingUrls.add(serverUrl);
                    } else {
                        log.info("推荐低优先级MCP服务器(优先级: {}): {} - {}", 
                            priority, recommendation.get("serverName"), recommendation.get("description"));
                        pendingApprovalCount++;
                    }
                }
            }
            
            log.info("MCP服务器注册完成: 自动注册={}, 待审批={}", autoRegisteredCount, pendingApprovalCount);
            
        } catch (Exception e) {
            log.error("MCP推荐服务器注册失败", e);
        }
    }

    private void autoRegisterServer(Map<String, Object> recommendation) {
        try {
            String serverName = (String) recommendation.getOrDefault("serverName", 
                "auto_mcp_" + System.currentTimeMillis());
            String serverUrl = (String) recommendation.get("serverUrl");
            String transport = (String) recommendation.getOrDefault("transport", "http");
            String description = (String) recommendation.getOrDefault("description", "自动发现并注册");
            
            if (serverUrl == null || serverUrl.isEmpty()) {
                log.warn("MCP服务器URL为空，跳过注册");
                return;
            }
            
            McpServer server = mcpService.registerServer(serverName, serverUrl, transport, description);
            
            if (server != null) {
                log.info("MCP服务器自动注册成功: name={}, url={}", serverName, serverUrl);
                
                memoryService.recordBusinessAction("mcp_auto_config", "auto_register",
                    recommendation,
                    Map.of("serverId", server.getServerId(), "status", "success"),
                    true, null);
            } else {
                log.warn("MCP服务器自动注册失败: url={}", serverUrl);
                
                memoryService.recordBusinessAction("mcp_auto_config", "auto_register",
                    recommendation,
                    Map.of("status", "failed", "reason", "connection_failed"),
                    false, "连接失败");
            }
            
        } catch (Exception e) {
            log.error("MCP服务器自动注册异常: {}", recommendation.get("serverName"), e);
        }
    }

    private void healthCheckAndOptimize() {
        try {
            mcpService.healthCheckAll();
            
            List<McpServer> servers = mcpService.listServers();
            int activeCount = 0;
            int inactiveCount = 0;
            int cleanedCount = 0;
            
            for (McpServer server : servers) {
                String serverUrl = server.getServerUrl();
                
                // 清理虚假地址（example.com 等）
                if (serverUrl != null && (serverUrl.contains("example.com") || 
                    serverUrl.contains("localhost:3000") ||
                    serverUrl.contains("fake") ||
                    serverUrl.contains("placeholder"))) {
                    log.warn("发现虚假MCP服务器，正在清理: name={}, url={}", 
                        server.getServerName(), serverUrl);
                    mcpService.deleteServer(server.getServerId());
                    cleanedCount++;
                    continue;
                }
                
                if ("active".equals(server.getStatus())) {
                    activeCount++;
                } else {
                    inactiveCount++;
                    log.warn("MCP服务器不活跃: name={}, url={}", 
                        server.getServerName(), serverUrl);
                }
            }
            
            log.info("MCP健康检查完成: 总数={}, 活跃={}, 不活跃={}, 清理虚假={}", 
                servers.size() - cleanedCount, activeCount, inactiveCount, cleanedCount);
            
            if (inactiveCount > 0) {
                optimizeInactiveServers(mcpService.listServers());
            }
            
        } catch (Exception e) {
            log.error("MCP健康检查和优化失败", e);
        }
    }

    private void optimizeInactiveServers(List<McpServer> servers) {
        try {
            String systemPrompt = """
                你是MCP服务器优化专家。根据MCP服务器的健康检查结果，
                提供优化建议。返回JSON格式:
                {
                  "optimizations": [
                    {
                      "serverId": "服务器ID",
                      "action": "retry|reconnect|remove|update_url",
                      "reason": "优化原因",
                      "newUrl": "新URL(如果需要)"
                    }
                  ]
                }
                """;

            StringBuilder userPrompt = new StringBuilder("MCP服务器健康检查结果:\n\n");
            for (McpServer server : servers) {
                userPrompt.append("- ").append(server.getServerName())
                          .append(": ").append(server.getStatus())
                          .append(" (").append(server.getServerUrl()).append(")\n");
            }
            
            String response = gptChatService.chat(systemPrompt, userPrompt.toString());

            try {
                // 清理 JSON 响应（去除 markdown 代码块等）
                String cleanedResponse = extractJsonFromResponse(response);
                Map<String, Object> optimizations = objectMapper.readValue(cleanedResponse,
                    new TypeReference<Map<String, Object>>() {});

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> optimizationList = (List<Map<String, Object>>)
                    optimizations.getOrDefault("optimizations", new ArrayList<>());

                for (Map<String, Object> opt : optimizationList) {
                    String serverId = (String) opt.get("serverId");
                    String action = (String) opt.get("action");

                    log.info("执行MCP优化: serverId={}, action={}", serverId, action);

                    switch (action) {
                        case "retry" -> retryServerConnection(serverId);
                        case "reconnect" -> reconnectServer(serverId);
                        case "remove" -> removeInactiveServer(serverId);
                        case "update_url" -> updateServerUrl(serverId, (String) opt.get("newUrl"));
                    }
                }

            } catch (Exception e) {
                log.error("解析MCP优化建议失败，原始响应: {}", response.substring(0, Math.min(200, response.length())), e);
            }
            
        } catch (Exception e) {
            log.error("MCP服务器优化失败", e);
        }
    }

    private void retryServerConnection(String serverId) {
        try {
            McpServer server = mcpService.getServer(serverId);
            if (server != null) {
                mcpService.healthCheckAll();
                
                McpServer updated = mcpService.getServer(serverId);
                if (updated != null && "active".equals(updated.getStatus())) {
                    log.info("MCP服务器重连成功: serverId={}", serverId);
                } else {
                    log.warn("MCP服务器重连失败: serverId={}", serverId);
                }
            }
        } catch (Exception e) {
            log.error("重试MCP服务器连接失败: serverId={}", serverId, e);
        }
    }

    private void reconnectServer(String serverId) {
        try {
            McpServer server = mcpService.getServer(serverId);
            if (server != null) {
                log.info("重新连接MCP服务器: serverId={}", serverId);
                mcpService.unregisterServer(serverId);
                
                McpServer newServer = mcpService.registerServer(
                    server.getServerName(),
                    server.getServerUrl(),
                    server.getTransport(),
                    server.getDescription()
                );
                
                if (newServer != null) {
                    log.info("MCP服务器重新连接成功: serverId={}", serverId);
                }
            }
        } catch (Exception e) {
            log.error("重新连接MCP服务器失败: serverId={}", serverId, e);
        }
    }

    private void removeInactiveServer(String serverId) {
        try {
            McpServer server = mcpService.getServer(serverId);
            if (server != null && "inactive".equals(server.getStatus())) {
                mcpService.unregisterServer(serverId);
                log.info("已移除不活跃的MCP服务器: serverId={}", serverId);
            }
        } catch (Exception e) {
            log.error("移除不活跃MCP服务器失败: serverId={}", serverId, e);
        }
    }

    private void updateServerUrl(String serverId, String newUrl) {
        try {
            if (newUrl == null || newUrl.isEmpty()) {
                log.warn("新URL为空，跳过更新: serverId={}", serverId);
                return;
            }
            
            McpServer server = mcpService.getServer(serverId);
            if (server != null) {
                log.info("更新MCP服务器URL: serverId={}, oldUrl={}, newUrl={}", 
                    serverId, server.getServerUrl(), newUrl);
                
                mcpService.unregisterServer(serverId);
                
                McpServer newServer = mcpService.registerServer(
                    server.getServerName(),
                    newUrl,
                    server.getTransport(),
                    server.getDescription() + " [URL已更新]"
                );
                
                if (newServer != null) {
                    log.info("MCP服务器URL更新成功: serverId={}", serverId);
                }
            }
        } catch (Exception e) {
            log.error("更新MCP服务器URL失败: serverId={}", serverId, e);
        }
    }

    public Map<String, Object> getMcpRecommendations() {
        return new HashMap<>(mcpRecommendations);
    }

    public void triggerManualAnalysis() {
        log.info("手动触发MCP需求分析");
        mcpRecommendations = new HashMap<>();
        lastRecommendTime = 0;
        autoDiscoverAndConfigure();
    }

    public long getLastRecommendTime() {
        return lastRecommendTime;
    }

    /**
     * 从 AI 响应中提取 JSON 内容
     * 处理 markdown 代码块、前缀文本等情况
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.isEmpty()) {
            return "{}";
        }

        String cleaned = response.trim();

        // 去除 markdown 代码块标记
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        cleaned = cleaned.trim();

        // 查找 JSON 对象的开始和结束
        int start = cleaned.indexOf("{");
        int end = cleaned.lastIndexOf("}");

        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }

        return cleaned;
    }
}
