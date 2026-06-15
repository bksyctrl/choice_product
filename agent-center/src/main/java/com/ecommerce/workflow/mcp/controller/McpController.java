package com.ecommerce.workflow.mcp.controller;

import com.ecommerce.workflow.controller.ApiResponse;
import com.ecommerce.workflow.mcp.entity.McpResource;
import com.ecommerce.workflow.mcp.entity.McpServer;
import com.ecommerce.workflow.mcp.entity.McpTool;
import com.ecommerce.workflow.mcp.service.McpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
public class McpController {
    
    @Autowired
    private McpService mcpService;
    
    @PostMapping("/servers/register")
    public ApiResponse<McpServer> registerServer(
            @RequestParam String serverName,
            @RequestParam String serverUrl,
            @RequestParam(defaultValue = "http") String transport,
            @RequestParam(required = false) String description) {
        
        McpServer server = mcpService.registerServer(serverName, serverUrl, transport, description);
        
        return ApiResponse.success(server);
    }
    
    @GetMapping("/servers")
    public ApiResponse<List<McpServer>> listServers() {
        List<McpServer> servers = mcpService.listServers();
        
        return ApiResponse.success(servers);
    }
    
    @GetMapping("/servers/{serverId}")
    public ApiResponse<McpServer> getServer(@PathVariable String serverId) {
        McpServer server = mcpService.getServer(serverId);
        
        return ApiResponse.success(server);
    }
    
    @GetMapping("/servers/{serverId}/resources")
    public ApiResponse<List<McpResource>> listResources(@PathVariable String serverId) {
        List<McpResource> resources = mcpService.listResources(serverId);
        
        return ApiResponse.success(resources);
    }
    
    @GetMapping("/servers/{serverId}/tools")
    public ApiResponse<List<McpTool>> listTools(@PathVariable String serverId) {
        List<McpTool> tools = mcpService.listTools(serverId);
        
        return ApiResponse.success(tools);
    }
    
    @PostMapping("/servers/{serverId}/tools/{toolName}/call")
    public ApiResponse<Map<String, Object>> callTool(
            @PathVariable String serverId,
            @PathVariable String toolName,
            @RequestBody Map<String, Object> arguments) {
        
        Map<String, Object> result = mcpService.callTool(serverId, toolName, arguments);
        
        return ApiResponse.success(result);
    }
    
    @GetMapping("/servers/{serverId}/resources/{resourceUri}")
    public ApiResponse<String> readResource(
            @PathVariable String serverId,
            @PathVariable String resourceUri) {
        
        String content = mcpService.readResource(serverId, resourceUri);
        
        return ApiResponse.success(content);
    }
    
    @DeleteMapping("/servers/{serverId}")
    public ApiResponse<Map<String, Object>> unregisterServer(@PathVariable String serverId) {
        mcpService.unregisterServer(serverId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "MCP服务器注销成功");
        
        return ApiResponse.success(response);
    }
    
    @PostMapping("/health-check")
    public ApiResponse<Map<String, Object>> healthCheck() {
        mcpService.healthCheckAll();
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "MCP健康检查完成");
        
        return ApiResponse.success(response);
    }
}
