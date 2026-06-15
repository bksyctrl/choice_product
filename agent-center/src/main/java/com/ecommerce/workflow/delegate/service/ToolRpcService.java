package com.ecommerce.workflow.delegate.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ToolRpcService {
    
    private static final Logger log = LoggerFactory.getLogger(ToolRpcService.class);
    
    private final Map<String, ToolHandler> toolHandlers = new HashMap<>();
    
    public ToolRpcService() {
        registerDefaultTools();
    }
    
    private void registerDefaultTools() {
        registerTool("video_generator", this::handleVideoGeneration);
        registerTool("content_analyzer", this::handleContentAnalysis);
        registerTool("data_processor", this::handleDataProcessing);
        registerTool("report_generator", this::handleReportGeneration);
    }
    
    public void registerTool(String toolName, ToolHandler handler) {
        toolHandlers.put(toolName, handler);
        log.info("注册工具: {}", toolName);
    }
    
    public Map<String, Object> callTool(String toolName, Map<String, Object> parameters) {
        String callId = "RPC_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        
        log.info("调用工具RPC: callId={}, tool={}", callId, toolName);
        
        ToolHandler handler = toolHandlers.get(toolName);
        
        if (handler == null) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", "未找到工具: " + toolName);
            return errorResult;
        }
        
        try {
            Map<String, Object> result = handler.handle(parameters);
            result.put("callId", callId);
            result.put("toolName", toolName);
            result.put("success", true);
            
            log.info("工具调用成功: callId={}, tool={}", callId, toolName);
            
            return result;
        } catch (Exception e) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            errorResult.put("callId", callId);
            errorResult.put("toolName", toolName);
            
            log.error("工具调用失败: callId={}, tool={}", callId, toolName, e);
            
            return errorResult;
        }
    }
    
    private Map<String, Object> handleVideoGeneration(Map<String, Object> parameters) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "视频生成完成");
        result.put("videoUrl", "https://example.com/video.mp4");
        result.put("duration", parameters.getOrDefault("duration", 60));
        return result;
    }
    
    private Map<String, Object> handleContentAnalysis(Map<String, Object> parameters) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "内容分析完成");
        result.put("sentiment", "positive");
        result.put("keywords", new String[]{"产品", "视频", "营销"});
        return result;
    }
    
    private Map<String, Object> handleDataProcessing(Map<String, Object> parameters) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "数据处理完成");
        result.put("processedRecords", 100);
        result.put("successRate", 0.95);
        return result;
    }
    
    private Map<String, Object> handleReportGeneration(Map<String, Object> parameters) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "报告生成完成");
        result.put("reportUrl", "https://example.com/report.pdf");
        result.put("pages", 10);
        return result;
    }
    
    @FunctionalInterface
    public interface ToolHandler {
        Map<String, Object> handle(Map<String, Object> parameters);
    }
}
