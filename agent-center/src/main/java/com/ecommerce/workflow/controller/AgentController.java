package com.ecommerce.workflow.controller;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ecommerce.workflow.agent.AgentRequest;
import com.ecommerce.workflow.agent.AgentResponse;
import com.ecommerce.workflow.agent.impl.ComplianceControlAgent;
import com.ecommerce.workflow.agent.impl.TotalConversationAgent;
import com.ecommerce.workflow.util.UserContext;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private final TotalConversationAgent totalConversationAgent;
    private final ComplianceControlAgent complianceControlAgent;
    private final Executor sseExecutor;

    public AgentController(TotalConversationAgent totalConversationAgent,
                          ComplianceControlAgent complianceControlAgent,
                          @Qualifier("sseExecutor") Executor sseExecutor) {
        this.totalConversationAgent = totalConversationAgent;
        this.complianceControlAgent = complianceControlAgent;
        this.sseExecutor = sseExecutor;
    }

    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> listAgents() {
        List<Map<String, Object>> agents = new java.util.ArrayList<>();
        
        Map<String, Object> agent1 = new java.util.HashMap<>();
        agent1.put("id", "total-conversation-agent");
        agent1.put("name", "全对话Agent");
        agent1.put("description", "支持多轮对话和上下文理解的智能对话助手");
        agent1.put("status", "active");
        agent1.put("type", "CONVERSATION");
        agents.add(agent1);
        
        Map<String, Object> agent2 = new java.util.HashMap<>();
        agent2.put("id", "compliance-control-agent");
        agent2.put("name", "合规控制Agent");
        agent2.put("description", "负责内容合规性检查和控制，确保生成的内容符合平台规范和政策要求");
        agent2.put("status", "active");
        agent2.put("type", "COMPLIANCE");
        agents.add(agent2);
        
        Map<String, Object> agent3 = new java.util.HashMap<>();
        agent3.put("id", "video-generation-agent");
        agent3.put("name", "视频生成Agent");
        agent3.put("description", "专门负责AI视频生成任务的智能体");
        agent3.put("status", "active");
        agent3.put("type", "GENERATION");
        agents.add(agent3);
        
        Map<String, Object> agent4 = new java.util.HashMap<>();
        agent4.put("id", "knowledge-learning-agent");
        agent4.put("name", "知识学习Agent");
        agent4.put("description", "负责自动学习和处理知识，包含数据获取、知识提取、知识存储等功能");
        agent4.put("status", "active");
        agent4.put("type", "LEARNING");
        agents.add(agent4);
        
        return ApiResponse.success(agents);
    }

    @PostMapping("/chat")
    public ApiResponse<AgentResponse> chat(@RequestBody ChatRequest request) {
        try {
            Long userId = request.getUserId() != null && request.getUserId() > 0 ? 
                request.getUserId() : UserContext.getCurrentUserId();
            log.info("开始处理Agent请求: userId={}, message={}", userId, request.getMessage());

            AgentRequest agentRequest = AgentRequest.builder()
                    .sessionId(request.getSessionId())
                    .userId(userId)
                    .message(request.getMessage())
                    .context(request.getContext())
                    .parameters(request.getParameters())
                    .build();

            AgentResponse response = totalConversationAgent.process(agentRequest);
            Map<String, Object> responseData = response.getData() != null
                    ? new HashMap<>(response.getData())
                    : new HashMap<>();
            responseData.put("sessionId", agentRequest.getSessionId());
            response.setData(responseData);

            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("Agent处理失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(180000L);

        try {
            Long userId = request.getUserId() != null && request.getUserId() > 0 ? 
                request.getUserId() : UserContext.getCurrentUserId();

            AgentRequest agentRequest = AgentRequest.builder()
                    .sessionId(request.getSessionId())
                    .userId(userId)
                    .message(request.getMessage())
                    .context(request.getContext())
                    .build();

            sseExecutor.execute(() -> {
                try {
                    emitter.send(SseEmitter.event().name("status").data("processing"));

                    AgentResponse response = totalConversationAgent.process(agentRequest);

                    emitter.send(SseEmitter.event().name("response").data(response));
                    emitter.send(SseEmitter.event().name("status").data("completed"));
                    emitter.complete();
                } catch (Exception e) {
                    log.error("流式对话处理失败", e);
                    try {
                        emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        emitter.completeWithError(ex);
                    }
                }
            });

        } catch (Exception e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    @PostMapping("/compliance/check")
    public ApiResponse<AgentResponse> checkCompliance(@RequestBody ComplianceCheckRequest request) {
        try {
            AgentRequest agentRequest = AgentRequest.builder()
                    .userId(UserContext.getCurrentUserId())
                    .message(request.getContent())
                    .parameter("action", "check_compliance")
                    .parameter("platform", request.getPlatform())
                    .context(Map.of("content", request.getContent()))
                    .build();

            AgentResponse response = complianceControlAgent.process(agentRequest);

            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("合规检查失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    public static class ChatRequest {
        private String sessionId;
        private Long userId;
        private String message;
        private Map<String, Object> context;
        private Map<String, Object> parameters;

        public String getSessionId() { return sessionId; }
        public Long getUserId() { return userId; }
        public String getMessage() { return message; }
        public Map<String, Object> getContext() { return context; }
        public Map<String, Object> getParameters() { return parameters; }

        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public void setMessage(String message) { this.message = message; }
        public void setContext(Map<String, Object> context) { this.context = context; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    }

    public static class ComplianceCheckRequest {
        private String content;
        private String platform = "douyin";

        public String getContent() { return content; }
        public String getPlatform() { return platform; }

        public void setContent(String content) { this.content = content; }
        public void setPlatform(String platform) { this.platform = platform; }
    }
}
