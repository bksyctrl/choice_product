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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class McpAutoTriggerService {

    private static final Logger log = LoggerFactory.getLogger(McpAutoTriggerService.class);

    private final McpService mcpService;
    private final GptChatService gptChatService;
    private final UnifiedMemoryService memoryService;
    private final ObjectMapper objectMapper;

    private volatile Map<String, List<Map<String, Object>>> toolTriggerRules = new HashMap<>();
    private volatile long lastTriggerTime = 0;

    public McpAutoTriggerService(McpService mcpService,
                                 GptChatService gptChatService,
                                 UnifiedMemoryService memoryService,
                                 ObjectMapper objectMapper) {
        this.mcpService = mcpService;
        this.gptChatService = gptChatService;
        this.memoryService = memoryService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedRateString = "${mcp.auto-trigger.interval-ms:3600000}")
    public void analyzeAndTriggerTools() {
        log.info("=== 开始MCP工具自动触发分析 ===");
        
        analyzeBusinessContext();
        triggerRelevantTools();
        updateTriggerRules();
        
        log.info("=== MCP工具自动触发分析完成 ===");
    }

    private void analyzeBusinessContext() {
        try {
            List<McpServer> activeServers = mcpService.listServers().stream()
                .filter(s -> "active".equals(s.getStatus()))
                .collect(Collectors.toList());
            
            if (activeServers.isEmpty()) {
                log.info("没有活跃的MCP服务器，跳过工具触发");
                return;
            }

            Map<String, Object> context = new HashMap<>();
            context.put("activeServers", activeServers.size());
            context.put("servers", activeServers.stream().map(McpServer::getServerName).collect(Collectors.toList()));
            
            String systemPrompt = """
                你是MCP工具触发策略专家。根据当前的业务上下文和可用的MCP服务器，
                分析在什么业务场景下应该自动触发哪些MCP工具。
                
                请返回JSON格式:
                {
                  "triggerRules": [
                    {
                      "ruleName": "规则名称",
                      "triggerCondition": "触发条件描述",
                      "serverName": "服务器名称",
                      "toolName": "工具名称",
                      "arguments": {"参数": "值或模板"},
                      "priority": "high|medium|low",
                      "businessValue": "业务价值说明"
                    }
                  ]
                }
                """;

            String userPrompt = buildContextPrompt(activeServers);
            String response = gptChatService.chat(systemPrompt, userPrompt);

            try {
                Map<String, Object> analysis = objectMapper.readValue(response, 
                    new TypeReference<Map<String, Object>>() {});
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rules = (List<Map<String, Object>>) 
                    analysis.getOrDefault("triggerRules", new ArrayList<>());
                
                Map<String, List<Map<String, Object>>> rulesByServer = new HashMap<>();
                for (Map<String, Object> rule : rules) {
                    String serverName = (String) rule.get("serverName");
                    rulesByServer.computeIfAbsent(serverName, k -> new ArrayList<>()).add(rule);
                }
                
                toolTriggerRules = rulesByServer;
                lastTriggerTime = System.currentTimeMillis();
                
                log.info("MCP工具触发规则分析完成，共{}条规则", rules.size());
                
            } catch (Exception e) {
                log.error("解析MCP工具触发规则失败", e);
            }
            
        } catch (Exception e) {
            log.error("MCP工具触发业务上下文分析失败", e);
        }
    }

    private String buildContextPrompt(List<McpServer> activeServers) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("当前业务上下文:\n\n");
        prompt.append("活跃的MCP服务器:\n");
        for (McpServer server : activeServers) {
            List<McpTool> tools = mcpService.listTools(server.getServerId());
            prompt.append("- ").append(server.getServerName())
                  .append(" (").append(server.getServerUrl()).append(")\n");
            prompt.append("  可用工具:\n");
            for (McpTool tool : tools) {
                prompt.append("    * ").append(tool.getToolName())
                      .append(": ").append(tool.getDescription()).append("\n");
            }
        }
        
        prompt.append("\n请分析在视频生成、产品分析、脚本创作等业务场景下，");
        prompt.append("应该自动触发哪些MCP工具来增强业务能力。\n");
        prompt.append("重点关注: 数据增强、质量检查、优化建议、自动化流程等场景。");
        
        return prompt.toString();
    }

    private void triggerRelevantTools() {
        try {
            List<McpServer> activeServers = mcpService.listServers().stream()
                .filter(s -> "active".equals(s.getStatus()))
                .collect(Collectors.toList());
            
            for (McpServer server : activeServers) {
                List<Map<String, Object>> rules = toolTriggerRules.getOrDefault(
                    server.getServerName(), new ArrayList<>());
                
                for (Map<String, Object> rule : rules) {
                    String priority = (String) rule.getOrDefault("priority", "medium");
                    
                    if ("high".equals(priority)) {
                        log.info("自动触发高优先级MCP工具: server={}, tool={}", 
                            server.getServerName(), rule.get("toolName"));
                        autoTriggerTool(server, rule);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("MCP工具自动触发失败", e);
        }
    }

    private void autoTriggerTool(McpServer server, Map<String, Object> rule) {
        try {
            String toolName = (String) rule.get("toolName");
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) rule.getOrDefault("arguments", new HashMap<>());
            
            Map<String, Object> result = mcpService.callTool(server.getServerId(), toolName, arguments);
            
            log.info("MCP工具自动触发成功: server={}, tool={}, result={}", 
                server.getServerName(), toolName, result);
            
            memoryService.recordBusinessAction("mcp_auto_trigger", "auto_execute",
                Map.of("serverName", server.getServerName(), "toolName", toolName, "rule", rule),
                result, true, null);
            
        } catch (Exception e) {
            log.error("MCP工具自动触发异常: server={}, tool={}", 
                server.getServerName(), rule.get("toolName"), e);
            
            memoryService.recordBusinessAction("mcp_auto_trigger", "auto_execute",
                Map.of("serverName", server.getServerName(), "toolName", rule.get("toolName")),
                null, false, e.getMessage());
        }
    }

    private void updateTriggerRules() {
        try {
            List<McpServer> allServers = mcpService.listServers();
            
            for (McpServer server : allServers) {
                List<McpTool> tools = mcpService.listTools(server.getServerId());
                
                for (McpTool tool : tools) {
                    if (shouldUpdateToolRule(server, tool)) {
                        updateToolTriggerRule(server, tool);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("更新MCP工具触发规则失败", e);
        }
    }

    private boolean shouldUpdateToolRule(McpServer server, McpTool tool) {
        List<Map<String, Object>> rules = toolTriggerRules.getOrDefault(
            server.getServerName(), new ArrayList<>());
        
        boolean exists = rules.stream()
            .anyMatch(r -> tool.getToolName().equals(r.get("toolName")));
        
        return !exists;
    }

    private void updateToolTriggerRule(McpServer server, McpTool tool) {
        try {
            String systemPrompt = """
                你是MCP工具规则配置专家。根据工具的描述，分析:
                1. 什么业务场景下应该触发这个工具
                2. 需要什么参数
                3. 触发优先级
                
                返回JSON格式:
                {
                  "triggerCondition": "触发条件",
                  "arguments": {"参数": "说明"},
                  "priority": "high|medium|low",
                  "businessValue": "业务价值"
                }
                """;

            String userPrompt = String.format(
                "工具名称: %s\n描述: %s\n服务器: %s\n\n请分析触发规则。",
                tool.getToolName(), tool.getDescription(), server.getServerName()
            );
            
            String response = gptChatService.chat(systemPrompt, userPrompt);
            
            try {
                Map<String, Object> rule = objectMapper.readValue(response, 
                    new TypeReference<Map<String, Object>>() {});
                
                rule.put("serverName", server.getServerName());
                rule.put("toolName", tool.getToolName());
                rule.put("ruleName", tool.getToolName() + "_auto_trigger");
                
                List<Map<String, Object>> rules = toolTriggerRules.getOrDefault(
                    server.getServerName(), new ArrayList<>());
                rules.add(rule);
                toolTriggerRules.put(server.getServerName(), rules);
                
                log.info("MCP工具触发规则已更新: server={}, tool={}", 
                    server.getServerName(), tool.getToolName());
                
            } catch (Exception e) {
                log.error("解析MCP工具触发规则失败: tool={}", tool.getToolName(), e);
            }
            
        } catch (Exception e) {
            log.error("更新MCP工具触发规则异常: tool={}", tool.getToolName(), e);
        }
    }

    public Map<String, Object> triggerToolByName(String serverName, String toolName, Map<String, Object> arguments) {
        McpServer server = mcpService.listServers().stream()
            .filter(s -> s.getServerName().equals(serverName) && "active".equals(s.getStatus()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("MCP服务器不存在或不活跃: " + serverName));
        
        Map<String, Object> rule = new HashMap<>();
        rule.put("toolName", toolName);
        rule.put("arguments", arguments != null ? arguments : new HashMap<>());
        rule.put("priority", "high");
        
        autoTriggerTool(server, rule);
        
        return Map.of("status", "triggered", "server", serverName, "tool", toolName);
    }

    public Map<String, List<Map<String, Object>>> getTriggerRules() {
        return new HashMap<>(toolTriggerRules);
    }

    public long getLastTriggerTime() {
        return lastTriggerTime;
    }

    public void triggerManualAnalysis() {
        log.info("手动触发MCP工具分析");
        toolTriggerRules = new HashMap<>();
        lastTriggerTime = 0;
        analyzeAndTriggerTools();
    }
}
