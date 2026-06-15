package com.ecommerce.workflow.agent.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ecommerce.workflow.agent.AgentRequest;
import com.ecommerce.workflow.agent.AgentResponse;
import com.ecommerce.workflow.entity.ChatSession;
import com.ecommerce.workflow.mcp.entity.McpServer;
import com.ecommerce.workflow.mcp.entity.McpTool;
import com.ecommerce.workflow.mcp.service.McpService;
import com.ecommerce.workflow.service.ai.GptChatService;
import com.ecommerce.workflow.service.memory.UnifiedMemoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class McpConfigHandler extends AbstractIntentHandler {

    private static final Logger log = LoggerFactory.getLogger(McpConfigHandler.class);

    @Autowired
    private McpService mcpService;

    @Autowired
    private GptChatService chatService;

    @Autowired
    private UnifiedMemoryService unifiedMemoryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String getIntentCode() {
        return "mcp_config";
    }

    @Override
    public AgentResponse handle(AgentRequest request, ChatSession session, IntentResult intent) throws Exception {
        log.info("MCP配置意图处理: message={}", request.getMessage());

        String action = resolveAction(request.getMessage(), intent);

        switch (action) {
            case "register" -> {
                return handleRegister(request, session, intent);
            }
            case "list" -> {
                return handleList(request, session);
            }
            case "call_tool" -> {
                return handleCallTool(request, session, intent);
            }
            case "unregister" -> {
                return handleUnregister(request, session, intent);
            }
            case "discover" -> {
                return handleDiscover(request, session, intent);
            }
            default -> {
                return handleAutoAction(request, session, intent);
            }
        }
    }

    private String resolveAction(String message, IntentResult intent) {
        String lower = message.toLowerCase();
        if (lower.contains("注册") || lower.contains("添加") || lower.contains("连接") || lower.contains("接入")) {
            return "register";
        }
        if (lower.contains("列表") || lower.contains("查看") || lower.contains("有哪些") || lower.contains("显示")) {
            return "list";
        }
        if (lower.contains("调用") || lower.contains("执行") || lower.contains("运行") || lower.contains("使用")) {
            return "call_tool";
        }
        if (lower.contains("删除") || lower.contains("移除") || lower.contains("注销") || lower.contains("断开")) {
            return "unregister";
        }
        if (lower.contains("发现") || lower.contains("探索") || lower.contains("扫描")) {
            return "discover";
        }
        Map<String, Object> entities = intent.getEntities();
        if (entities != null && entities.containsKey("action")) {
            return entities.get("action").toString();
        }
        return "auto";
    }

    private AgentResponse handleRegister(AgentRequest request, ChatSession session, IntentResult intent) {
        try {
            Map<String, Object> params = extractMcpParams(request.getMessage(), intent);

            String serverName = (String) params.getOrDefault("serverName", "mcp_server_" + System.currentTimeMillis());
            String serverUrl = (String) params.get("serverUrl");
            String transport = (String) params.getOrDefault("transport", "http");
            String description = (String) params.getOrDefault("description", "通过AI对话自动注册");

            if (serverUrl == null || serverUrl.isEmpty()) {
                String reply = "请提供MCP服务器的URL地址，例如: '帮我注册MCP服务器 http://localhost:8080/mcp'";
                sessionService.saveMessage(session.getSessionId(), "assistant", reply);
                return AgentResponse.success(reply);
            }

            McpServer server = mcpService.registerServer(serverName, serverUrl, transport, description);

            if (server != null) {
                List<McpTool> tools = mcpService.listTools(server.getServerId());

                StringBuilder reply = new StringBuilder();
                reply.append("MCP服务器注册成功!\n");
                reply.append("- 服务器名称: ").append(serverName).append("\n");
                reply.append("- 服务器ID: ").append(server.getServerId()).append("\n");
                reply.append("- 传输协议: ").append(transport).append("\n");
                reply.append("- 发现工具数: ").append(tools.size()).append("\n");

                if (!tools.isEmpty()) {
                    reply.append("\n可用工具列表:\n");
                    for (McpTool tool : tools) {
                        reply.append("  - ").append(tool.getToolName()).append(": ").append(tool.getDescription()).append("\n");
                    }
                }

                unifiedMemoryService.recordBusinessAction("mcp_config", "register",
                        Map.of("serverName", serverName, "serverUrl", serverUrl),
                        Map.of("serverId", server.getServerId(), "toolCount", tools.size()),
                        true, null);

                String replyStr = reply.toString();
                sessionService.saveMessage(session.getSessionId(), "assistant", replyStr);
                return AgentResponse.success(replyStr);
            } else {
                String reply = "MCP服务器注册失败，请检查URL是否正确以及服务器是否在线";
                sessionService.saveMessage(session.getSessionId(), "assistant", reply);
                return AgentResponse.success(reply);
            }
        } catch (Exception e) {
            log.error("MCP注册失败", e);
            String reply = "MCP服务器注册失败: " + e.getMessage();
            sessionService.saveMessage(session.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        }
    }

    private AgentResponse handleList(AgentRequest request, ChatSession session) {
        try {
            List<McpServer> servers = mcpService.listServers();

            StringBuilder reply = new StringBuilder();
            reply.append("当前已注册的MCP服务器:\n\n");

            if (servers.isEmpty()) {
                reply.append("暂无已注册的MCP服务器\n");
                reply.append("您可以通过对话注册新的MCP服务器，例如: '帮我注册MCP服务器 http://localhost:8080/mcp'");
            } else {
                for (McpServer server : servers) {
                    reply.append("- **").append(server.getServerName()).append("**\n");
                    reply.append("  ID: ").append(server.getServerId()).append("\n");
                    reply.append("  URL: ").append(server.getServerUrl()).append("\n");
                    reply.append("  状态: ").append(server.getStatus()).append("\n");

                    List<McpTool> tools = mcpService.listTools(server.getServerId());
                    if (!tools.isEmpty()) {
                        reply.append("  工具: ");
                        for (int i = 0; i < tools.size(); i++) {
                            reply.append(tools.get(i).getToolName());
                            if (i < tools.size() - 1) reply.append(", ");
                        }
                        reply.append("\n");
                    }
                    reply.append("\n");
                }
            }

            String replyStr = reply.toString();
            sessionService.saveMessage(session.getSessionId(), "assistant", replyStr);
            return AgentResponse.success(replyStr);
        } catch (Exception e) {
            log.error("获取MCP服务器列表失败", e);
            String reply = "获取MCP服务器列表失败: " + e.getMessage();
            sessionService.saveMessage(session.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        }
    }

    private AgentResponse handleCallTool(AgentRequest request, ChatSession session, IntentResult intent) {
        try {
            Map<String, Object> params = extractMcpParams(request.getMessage(), intent);
            String serverId = (String) params.get("serverId");
            String toolName = (String) params.get("toolName");

            if (serverId == null || toolName == null) {
                List<McpServer> servers = mcpService.listServers();
                if (servers.size() == 1) {
                    serverId = servers.get(0).getServerId();
                } else {
                    String reply = "请指定要调用的MCP服务器ID和工具名称";
                    sessionService.saveMessage(session.getSessionId(), "assistant", reply);
                    return AgentResponse.success(reply);
                }
            }

            Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", new HashMap<>());
            Map<String, Object> result = mcpService.callTool(serverId, toolName, arguments);

            String reply = "MCP工具调用结果:\n" + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            sessionService.saveMessage(session.getSessionId(), "assistant", reply);

            unifiedMemoryService.recordBusinessAction("mcp_config", "call_tool",
                    Map.of("serverId", serverId, "toolName", toolName),
                    result, true, null);

            return AgentResponse.success(reply);
        } catch (Exception e) {
            log.error("MCP工具调用失败", e);
            String reply = "MCP工具调用失败: " + e.getMessage();
            sessionService.saveMessage(session.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        }
    }

    private AgentResponse handleUnregister(AgentRequest request, ChatSession session, IntentResult intent) {
        try {
            Map<String, Object> params = extractMcpParams(request.getMessage(), intent);
            String serverId = (String) params.get("serverId");

            if (serverId == null) {
                String reply = "请指定要注销的MCP服务器ID";
                sessionService.saveMessage(session.getSessionId(), "assistant", reply);
                return AgentResponse.success(reply);
            }

            mcpService.unregisterServer(serverId);
            String reply = "MCP服务器已注销: " + serverId;
            sessionService.saveMessage(session.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        } catch (Exception e) {
            log.error("MCP注销失败", e);
            String reply = "MCP服务器注销失败: " + e.getMessage();
            sessionService.saveMessage(session.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        }
    }

    private AgentResponse handleDiscover(AgentRequest request, ChatSession session, IntentResult intent) {
        try {
            mcpService.healthCheckAll();
            List<McpServer> servers = mcpService.listServers();

            StringBuilder reply = new StringBuilder("MCP服务器健康检查完成:\n\n");
            for (McpServer server : servers) {
                reply.append("- ").append(server.getServerName()).append(": ").append(server.getStatus()).append("\n");
            }

            String replyStr = reply.toString();
            sessionService.saveMessage(session.getSessionId(), "assistant", replyStr);
            return AgentResponse.success(replyStr);
        } catch (Exception e) {
            log.error("MCP发现失败", e);
            String reply = "MCP服务器发现失败: " + e.getMessage();
            sessionService.saveMessage(session.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        }
    }

    private AgentResponse handleAutoAction(AgentRequest request, ChatSession session, IntentResult intent) {
        String systemPrompt = """
                你是MCP服务器配置助手。用户想要配置或管理MCP服务器。
                请分析用户的消息，提取以下信息并返回JSON格式:
                {
                  "action": "register|list|call_tool|unregister|discover",
                  "serverName": "服务器名称",
                  "serverUrl": "服务器URL",
                  "transport": "http|sse|stdio",
                  "description": "描述",
                  "serverId": "服务器ID(如果已知)",
                  "toolName": "工具名称(如果调用)",
                  "arguments": {"key": "value"}
                }
                
                如果信息不完整，请用自然语言引导用户补充。
                """;

        String response = chatService.chat(systemPrompt, request.getMessage());

        try {
            Map<String, Object> parsed = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
            String action = (String) parsed.getOrDefault("action", "list");

            IntentResult enhancedIntent = new IntentResult(
                    intent.getIntent(), intent.getConfidence(),
                    parsed, intent.getMissingParams(), intent.getSuggestedWorkflow());

            return switch (action) {
                case "register" -> handleRegister(request, session, enhancedIntent);
                case "call_tool" -> handleCallTool(request, session, enhancedIntent);
                case "unregister" -> handleUnregister(request, session, enhancedIntent);
                case "discover" -> handleDiscover(request, session, enhancedIntent);
                default -> handleList(request, session);
            };
        } catch (Exception e) {
            log.warn("自动解析MCP配置失败，显示列表", e);
            return handleList(request, session);
        }
    }

    private Map<String, Object> extractMcpParams(String message, IntentResult intent) {
        Map<String, Object> params = new HashMap<>();
        if (intent.getEntities() != null) {
            params.putAll(intent.getEntities());
        }

        String urlPattern = "https?://[^\\s]+";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(urlPattern);
        java.util.regex.Matcher matcher = pattern.matcher(message);
        if (matcher.find() && !params.containsKey("serverUrl")) {
            params.put("serverUrl", matcher.group());
        }

        return params;
    }
}
