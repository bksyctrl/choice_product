package com.ecommerce.workflow.mcp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.workflow.mcp.client.McpClient;
import com.ecommerce.workflow.mcp.entity.McpResource;
import com.ecommerce.workflow.mcp.entity.McpServer;
import com.ecommerce.workflow.mcp.entity.McpTool;
import com.ecommerce.workflow.mcp.mapper.McpResourceMapper;
import com.ecommerce.workflow.mcp.mapper.McpServerMapper;
import com.ecommerce.workflow.mcp.mapper.McpToolMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class McpService {
    
    private static final Logger log = LoggerFactory.getLogger(McpService.class);
    
    @Autowired
    private McpServerMapper serverMapper;
    
    @Autowired
    private McpResourceMapper resourceMapper;
    
    @Autowired
    private McpToolMapper toolMapper;
    
    @Autowired
    private McpClient mcpClient;
    
    @Transactional
    public McpServer registerServer(String serverName, String serverUrl, String transport, String description) {
        McpServer server = mcpClient.connect(serverUrl, serverName, transport);
        
        if (server != null) {
            server.setDescription(description);
            serverMapper.insert(server);
            
            discoverAndSaveResources(server.getServerId(), serverUrl);
            discoverAndSaveTools(server.getServerId(), serverUrl);
            
            log.info("注册MCP服务器成功: serverId={}, name={}", server.getServerId(), serverName);
        }
        
        return server;
    }
    
    private void discoverAndSaveResources(String serverId, String serverUrl) {
        List<McpResource> resources = mcpClient.discoverResources(serverId, serverUrl);
        
        for (McpResource resource : resources) {
            resourceMapper.insert(resource);
        }
        
        log.info("发现MCP资源: serverId={}, count={}", serverId, resources.size());
    }
    
    private void discoverAndSaveTools(String serverId, String serverUrl) {
        List<McpTool> tools = mcpClient.discoverTools(serverId, serverUrl);
        
        for (McpTool tool : tools) {
            toolMapper.insert(tool);
        }
        
        log.info("发现MCP工具: serverId={}, count={}", serverId, tools.size());
    }
    
    public List<McpServer> listServers() {
        LambdaQueryWrapper<McpServer> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(McpServer::getCreatedAt);
        
        return serverMapper.selectList(wrapper);
    }
    
    public McpServer getServer(String serverId) {
        LambdaQueryWrapper<McpServer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(McpServer::getServerId, serverId);
        
        return serverMapper.selectOne(wrapper);
    }
    
    public List<McpResource> listResources(String serverId) {
        LambdaQueryWrapper<McpResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(McpResource::getServerId, serverId);
        
        return resourceMapper.selectList(wrapper);
    }
    
    public List<McpTool> listTools(String serverId) {
        LambdaQueryWrapper<McpTool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(McpTool::getServerId, serverId);
        
        return toolMapper.selectList(wrapper);
    }
    
    public Map<String, Object> callTool(String serverId, String toolName, Map<String, Object> arguments) {
        McpServer server = getServer(serverId);
        
        if (server == null) {
            throw new IllegalArgumentException("MCP服务器不存在: " + serverId);
        }
        
        return mcpClient.callTool(server.getServerUrl(), toolName, arguments);
    }
    
    public String readResource(String serverId, String resourceUri) {
        McpServer server = getServer(serverId);
        
        if (server == null) {
            throw new IllegalArgumentException("MCP服务器不存在: " + serverId);
        }
        
        return mcpClient.readResource(server.getServerUrl(), resourceUri);
    }
    
    @Transactional
    public void unregisterServer(String serverId) {
        McpServer server = getServer(serverId);
        
        if (server != null) {
            server.setStatus("inactive");
            server.setUpdatedAt(LocalDateTime.now());
            serverMapper.updateById(server);
            
            log.info("注销MCP服务器: serverId={}", serverId);
        }
    }
    
    @Transactional
    public void deleteServer(String serverId) {
        // 先删除关联的工具和资源
        LambdaQueryWrapper<McpTool> toolWrapper = new LambdaQueryWrapper<>();
        toolWrapper.eq(McpTool::getServerId, serverId);
        toolMapper.delete(toolWrapper);
        
        LambdaQueryWrapper<McpResource> resourceWrapper = new LambdaQueryWrapper<>();
        resourceWrapper.eq(McpResource::getServerId, serverId);
        resourceMapper.delete(resourceWrapper);
        
        // 删除服务器记录
        LambdaQueryWrapper<McpServer> serverWrapper = new LambdaQueryWrapper<>();
        serverWrapper.eq(McpServer::getServerId, serverId);
        serverMapper.delete(serverWrapper);
        
        log.info("删除MCP服务器及其关联数据: serverId={}", serverId);
    }
    
    public void healthCheckAll() {
        List<McpServer> servers = listServers();
        
        for (McpServer server : servers) {
            boolean healthy = mcpClient.healthCheck(server.getServerUrl());
            
            server.setStatus(healthy ? "active" : "inactive");
            server.setLastHeartbeat(LocalDateTime.now());
            server.setUpdatedAt(LocalDateTime.now());
            serverMapper.updateById(server);
        }
        
        log.info("MCP服务器健康检查完成: count={}", servers.size());
    }
}
