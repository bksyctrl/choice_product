package com.ecommerce.workflow.mcp.client;

import com.ecommerce.workflow.mcp.entity.McpResource;
import com.ecommerce.workflow.mcp.entity.McpServer;
import com.ecommerce.workflow.mcp.entity.McpTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class McpClient {
    
    private static final Logger log = LoggerFactory.getLogger(McpClient.class);
    
    private static final String MCP_VERSION = "2024-11-05";
    private static final String JSONRPC = "2.0";
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private final Map<String, McpServer> activeServers = new ConcurrentHashMap<>();
    private final Map<String, List<McpTool>> serverTools = new ConcurrentHashMap<>();
    private final Map<String, List<McpResource>> serverResources = new ConcurrentHashMap<>();
    private int requestId = 0;
    
    public McpServer connect(String serverUrl, String serverName, String transport) {
        try {
            String serverId = "MCP_" + System.currentTimeMillis() + "_" + 
                UUID.randomUUID().toString().substring(0, 8);
            
            McpServer server = new McpServer();
            server.setServerId(serverId);
            server.setServerName(serverName);
            server.setServerUrl(serverUrl);
            server.setTransport(transport);
            server.setStatus("connecting");
            server.setCreatedAt(java.time.LocalDateTime.now());
            server.setUpdatedAt(java.time.LocalDateTime.now());
            
            boolean initialized = initializeMcpConnection(serverUrl, server);
            
            if (initialized) {
                server.setStatus("active");
                server.setLastHeartbeat(java.time.LocalDateTime.now());
                activeServers.put(serverId, server);
                log.info("MCP服务器连接成功: serverId={}, url={}", serverId, serverUrl);
            } else {
                server.setStatus("failed");
                log.warn("MCP服务器连接失败: url={}", serverUrl);
            }
            
            return server;
        } catch (Exception e) {
            log.error("MCP服务器连接异常: url={}", serverUrl, e);
            return null;
        }
    }
    
    private boolean initializeMcpConnection(String serverUrl, McpServer server) {
        try {
            ObjectNode initRequest = createJsonRpcRequest("initialize");
            ObjectNode params = objectMapper.createObjectNode();
            params.put("protocolVersion", MCP_VERSION);
            
            ObjectNode clientInfo = objectMapper.createObjectNode();
            clientInfo.put("name", "workflow-engine");
            clientInfo.put("version", "1.0.0");
            params.set("clientInfo", clientInfo);
            
            ObjectNode capabilities = objectMapper.createObjectNode();
            capabilities.set("roots", objectMapper.createObjectNode().put("listChanged", true));
            capabilities.set("sampling", objectMapper.createObjectNode());
            params.set("capabilities", capabilities);
            
            initRequest.set("params", params);
            
            JsonNode response = sendJsonRpcRequest(serverUrl, initRequest);
            
            if (response != null && response.has("result")) {
                JsonNode result = response.get("result");
                
                if (result.has("serverInfo")) {
                    JsonNode serverInfo = result.get("serverInfo");
                    server.setServerName(serverInfo.has("name") ? 
                        serverInfo.get("name").asText() : server.getServerName());
                }
                
                ObjectNode initializedNotification = createJsonRpcNotification("notifications/initialized");
                sendJsonRpcNotification(serverUrl, initializedNotification);
                
                discoverTools(server.getServerId(), serverUrl);
                discoverResources(server.getServerId(), serverUrl);
                
                return true;
            }
            
            return false;
        } catch (Exception e) {
            log.error("MCP初始化连接失败: url={}", serverUrl, e);
            return false;
        }
    }
    
    public List<McpTool> discoverTools(String serverId, String serverUrl) {
        try {
            ObjectNode request = createJsonRpcRequest("tools/list");
            
            JsonNode response = sendJsonRpcRequest(serverUrl, request);
            
            List<McpTool> tools = new ArrayList<>();
            
            if (response != null && response.has("result")) {
                JsonNode result = response.get("result");
                JsonNode toolsNode = result.has("tools") ? result.get("tools") : result;
                
                if (toolsNode.isArray()) {
                    for (JsonNode toolNode : toolsNode) {
                        McpTool tool = new McpTool();
                        tool.setServerId(serverId);
                        tool.setToolName(toolNode.has("name") ? toolNode.get("name").asText() : "");
                        tool.setDescription(toolNode.has("description") ? 
                            toolNode.get("description").asText() : "");
                        
                        if (toolNode.has("inputSchema")) {
                            tool.setInputSchema(toolNode.get("inputSchema").toString());
                        }
                        
                        tool.setCreatedAt(java.time.LocalDateTime.now());
                        tools.add(tool);
                    }
                }
            }
            
            serverTools.put(serverId, tools);
            log.info("发现MCP工具: serverId={}, count={}", serverId, tools.size());
            return tools;
        } catch (Exception e) {
            log.error("发现MCP工具失败: serverId={}", serverId, e);
            return new ArrayList<>();
        }
    }
    
    public List<McpResource> discoverResources(String serverId, String serverUrl) {
        try {
            ObjectNode request = createJsonRpcRequest("resources/list");
            
            JsonNode response = sendJsonRpcRequest(serverUrl, request);
            
            List<McpResource> resources = new ArrayList<>();
            
            if (response != null && response.has("result")) {
                JsonNode result = response.get("result");
                JsonNode resourcesNode = result.has("resources") ? result.get("resources") : result;
                
                if (resourcesNode.isArray()) {
                    for (JsonNode resourceNode : resourcesNode) {
                        McpResource resource = new McpResource();
                        resource.setServerId(serverId);
                        resource.setResourceUri(resourceNode.has("uri") ? 
                            resourceNode.get("uri").asText() : "");
                        resource.setResourceName(resourceNode.has("name") ? 
                            resourceNode.get("name").asText() : "");
                        resource.setDescription(resourceNode.has("description") ? 
                            resourceNode.get("description").asText() : "");
                        resource.setMimeType(resourceNode.has("mimeType") ? 
                            resourceNode.get("mimeType").asText() : "text/plain");
                        resource.setCreatedAt(java.time.LocalDateTime.now());
                        
                        resources.add(resource);
                    }
                }
            }
            
            serverResources.put(serverId, resources);
            log.info("发现MCP资源: serverId={}, count={}", serverId, resources.size());
            return resources;
        } catch (Exception e) {
            log.error("发现MCP资源失败: serverId={}", serverId, e);
            return new ArrayList<>();
        }
    }
    
    public Map<String, Object> callTool(String serverUrl, String toolName, Map<String, Object> arguments) {
        try {
            ObjectNode request = createJsonRpcRequest("tools/call");
            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", toolName);
            
            if (arguments != null && !arguments.isEmpty()) {
                ObjectNode argsNode = objectMapper.valueToTree(arguments);
                params.set("arguments", argsNode);
            }
            
            request.set("params", params);
            
            JsonNode response = sendJsonRpcRequest(serverUrl, request);
            
            if (response != null) {
                Map<String, Object> result = new HashMap<>();
                
                if (response.has("result")) {
                    JsonNode resultNode = response.get("result");
                    
                    if (resultNode.has("content")) {
                        JsonNode content = resultNode.get("content");
                        if (content.isArray()) {
                            StringBuilder textContent = new StringBuilder();
                            for (JsonNode item : content) {
                                if (item.has("type") && "text".equals(item.get("type").asText())) {
                                    textContent.append(item.get("text").asText());
                                }
                            }
                            result.put("content", textContent.toString());
                        }
                    }
                    
                    if (resultNode.has("isError")) {
                        result.put("isError", resultNode.get("isError").asBoolean());
                    }
                    
                    result.put("rawResponse", objectMapper.writeValueAsString(resultNode));
                } else if (response.has("error")) {
                    JsonNode error = response.get("error");
                    result.put("error", error.has("message") ? 
                        error.get("message").asText() : "Unknown error");
                    result.put("code", error.has("code") ? error.get("code").asInt() : -1);
                }
                
                log.info("调用MCP工具完成: toolName={}", toolName);
                return result;
            }
            
            return Map.of("error", "No response from server");
        } catch (Exception e) {
            log.error("调用MCP工具失败: toolName={}", toolName, e);
            return Map.of("error", e.getMessage());
        }
    }
    
    public String readResource(String serverUrl, String resourceUri) {
        try {
            ObjectNode request = createJsonRpcRequest("resources/read");
            ObjectNode params = objectMapper.createObjectNode();
            params.put("uri", resourceUri);
            request.set("params", params);
            
            JsonNode response = sendJsonRpcRequest(serverUrl, request);
            
            if (response != null && response.has("result")) {
                JsonNode result = response.get("result");
                
                if (result.has("contents")) {
                    JsonNode contents = result.get("contents");
                    if (contents.isArray() && contents.size() > 0) {
                        JsonNode firstContent = contents.get(0);
                        if (firstContent.has("text")) {
                            return firstContent.get("text").asText();
                        }
                    }
                }
                
                log.info("读取MCP资源完成: resourceUri={}", resourceUri);
                return result.toString();
            }
            
            return null;
        } catch (Exception e) {
            log.error("读取MCP资源失败: resourceUri={}", resourceUri, e);
            return null;
        }
    }
    
    public List<Map<String, Object>> listPrompts(String serverUrl) {
        try {
            ObjectNode request = createJsonRpcRequest("prompts/list");
            JsonNode response = sendJsonRpcRequest(serverUrl, request);
            
            List<Map<String, Object>> prompts = new ArrayList<>();
            
            if (response != null && response.has("result")) {
                JsonNode result = response.get("result");
                JsonNode promptsNode = result.has("prompts") ? result.get("prompts") : result;
                
                if (promptsNode.isArray()) {
                    for (JsonNode promptNode : promptsNode) {
                        Map<String, Object> prompt = new HashMap<>();
                        prompt.put("name", promptNode.has("name") ? promptNode.get("name").asText() : "");
                        prompt.put("description", promptNode.has("description") ? 
                            promptNode.get("description").asText() : "");
                        prompts.add(prompt);
                    }
                }
            }
            
            return prompts;
        } catch (Exception e) {
            log.error("获取MCP提示列表失败: url={}", serverUrl, e);
            return new ArrayList<>();
        }
    }
    
    public Map<String, Object> getPrompt(String serverUrl, String promptName, Map<String, Object> args) {
        try {
            ObjectNode request = createJsonRpcRequest("prompts/get");
            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", promptName);
            
            if (args != null && !args.isEmpty()) {
                ObjectNode argsNode = objectMapper.valueToTree(args);
                params.set("arguments", argsNode);
            }
            
            request.set("params", params);
            
            JsonNode response = sendJsonRpcRequest(serverUrl, request);
            
            if (response != null && response.has("result")) {
                return objectMapper.convertValue(response.get("result"), Map.class);
            }
            
            return new HashMap<>();
        } catch (Exception e) {
            log.error("获取MCP提示失败: promptName={}", promptName, e);
            return new HashMap<>();
        }
    }
    
    public boolean healthCheck(String serverUrl) {
        try {
            ObjectNode request = createJsonRpcRequest("ping");
            JsonNode response = sendJsonRpcRequest(serverUrl, request);
            return response != null;
        } catch (Exception e) {
            log.debug("MCP服务器健康检查失败: url={}", serverUrl);
            return false;
        }
    }
    
    public void disconnect(String serverId) {
        McpServer server = activeServers.remove(serverId);
        if (server != null) {
            serverTools.remove(serverId);
            serverResources.remove(serverId);
            log.info("MCP服务器断开连接: serverId={}", serverId);
        }
    }
    
    private ObjectNode createJsonRpcRequest(String method) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", JSONRPC);
        request.put("id", ++requestId);
        request.put("method", method);
        return request;
    }
    
    private ObjectNode createJsonRpcNotification(String method) {
        ObjectNode notification = objectMapper.createObjectNode();
        notification.put("jsonrpc", JSONRPC);
        notification.put("method", method);
        return notification;
    }
    
    private JsonNode sendJsonRpcRequest(String serverUrl, ObjectNode request) {
        try {
            String mcpEndpoint = serverUrl;
            if (!serverUrl.endsWith("/mcp") && !serverUrl.endsWith("/jsonrpc")) {
                mcpEndpoint = serverUrl + "/mcp";
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(request), headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(mcpEndpoint, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return objectMapper.readTree(response.getBody());
            }
        } catch (Exception e) {
            log.debug("JSON-RPC请求失败: url={}, method={}, error={}", serverUrl, request.get("method"), e.getMessage());
        }
        return null;
    }
    
    private void sendJsonRpcNotification(String serverUrl, ObjectNode notification) {
        try {
            String mcpEndpoint = serverUrl;
            if (!serverUrl.endsWith("/mcp") && !serverUrl.endsWith("/jsonrpc")) {
                mcpEndpoint = serverUrl + "/mcp";
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(notification), headers);
            
            restTemplate.postForEntity(mcpEndpoint, entity, String.class);
        } catch (Exception e) {
            log.debug("JSON-RPC通知发送失败: url={}", serverUrl, e);
        }
    }
    
    public Map<String, McpServer> getActiveServers() {
        return new HashMap<>(activeServers);
    }
    
    public List<McpTool> getServerTools(String serverId) {
        return serverTools.getOrDefault(serverId, new ArrayList<>());
    }
    
    public List<McpResource> getServerResources(String serverId) {
        return serverResources.getOrDefault(serverId, new ArrayList<>());
    }
}
