package com.ecommerce.workflow.controller;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.workflow.entity.ChatMessage;
import com.ecommerce.workflow.entity.ChatSession;
import com.ecommerce.workflow.service.session.SessionService;

@RestController
@RequestMapping("/api/session")
public class SessionController {
    private static final Logger log = LoggerFactory.getLogger(SessionController.class);
    private final SessionService sessionService;
    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }
    @PostMapping("/create")
    public ApiResponse<ChatSession> createSession(@RequestBody CreateSessionRequest request) {
        log.info("创建会话: userId={}", request.getUserId());
        ChatSession session = sessionService.createSession(request.getUserId(), request.getTitle());
        return ApiResponse.success(session);
    }
    
    @GetMapping("/list")
    public ApiResponse<List<ChatSession>> listSessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long userId) {
        log.info("查询会话列表: page={}, size={}, userId={}", page, size, userId);
        try {
            if (userId != null) {
                List<ChatSession> sessions = sessionService.getUserSessions(userId, page, size);
                return ApiResponse.success(sessions);
            }
            List<ChatSession> sessions = sessionService.getUserSessions(1L, page, size);
            return ApiResponse.success(sessions);
        } catch (Exception e) {
            log.error("查询会话列表失败", e);
            return ApiResponse.error("查询会话列表失败: " + e.getMessage());
        }
    }
    @GetMapping("/{sessionId}")
    public ApiResponse<ChatSession> getSession(@PathVariable String sessionId) {
        ChatSession session = sessionService.getSession(sessionId);
        if (session == null) {
            return ApiResponse.error("会话不存在");
        }
        return ApiResponse.success(session);
    }
    @GetMapping("/user/{userId}")
    public ApiResponse<List<ChatSession>> getUserSessions(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<ChatSession> sessions = sessionService.getUserSessions(userId, page, size);
        return ApiResponse.success(sessions);
    }
    @GetMapping("/search/{userId}")
    public ApiResponse<List<ChatSession>> searchSessions(
            @PathVariable Long userId,
            @RequestParam String keyword) {
        List<ChatSession> sessions = sessionService.searchSessions(userId, keyword);
        return ApiResponse.success(sessions);
    }
    @GetMapping("/{sessionId}/messages")
    public ApiResponse<List<ChatMessage>> getSessionMessages(@PathVariable String sessionId) {
        List<ChatMessage> messages = sessionService.getSessionMessages(sessionId);
        return ApiResponse.success(messages);
    }
    @PostMapping("/{sessionId}/message")
    public ApiResponse<Void> saveMessage(
            @PathVariable String sessionId,
            @RequestBody SaveMessageRequest request) {
        sessionService.saveMessage(sessionId, request.getRole(), request.getContent());
        return ApiResponse.success(null);
    }
    @PostMapping("/{sessionId}/message/metadata")
    public ApiResponse<Void> saveMessageWithMetadata(
            @PathVariable String sessionId,
            @RequestBody SaveMessageMetadataRequest request) {
        sessionService.saveMessageWithMetadata(
                sessionId,
                request.getRole(),
                request.getContent(),
                request.getReferencedKnowledge(),
                request.getTriggeredSkills()
        );
        return ApiResponse.success(null);
    }
    @PutMapping("/{sessionId}/summary")
    public ApiResponse<Void> updateSummary(
            @PathVariable String sessionId,
            @RequestBody UpdateSummaryRequest request) {
        sessionService.updateSessionSummary(sessionId, request.getSummary());
        return ApiResponse.success(null);
    }
    @PostMapping("/{sessionId}/archive")
    public ApiResponse<Void> archiveSession(@PathVariable String sessionId) {
        sessionService.archiveSession(sessionId);
        return ApiResponse.success(null);
    }
    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable String sessionId) {
        sessionService.deleteSession(sessionId);
        return ApiResponse.success(null);
    }
    @GetMapping("/{sessionId}/context")
    public ApiResponse<List<Map<String, Object>>> getSessionContext(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "10") int lastN) {
        List<Map<String, Object>> context = sessionService.getSessionContext(sessionId, lastN);
        return ApiResponse.success(context);
    }
    @GetMapping("/count/{userId}")
    public ApiResponse<Map<String, Object>> getSessionCount(@PathVariable Long userId) {
        int count = sessionService.getSessionCount(userId);
        return ApiResponse.success(Map.of("count", count));
    }
    public static class CreateSessionRequest {
        private Long userId;
        private String title;
        public Long getUserId() {
            return userId;
        }
        public String getTitle() {
            return title;
        }
        public void setUserId(Long userId) {
            this.userId = userId;
        }
        public void setTitle(String title) {
            this.title = title;
        }
    }
    public static class SaveMessageRequest {
        private String role;
        private String content;
        public String getRole() {
            return role;
        }
        public String getContent() {
            return content;
        }
        public void setRole(String role) {
            this.role = role;
        }
        public void setContent(String content) {
            this.content = content;
        }
    }
    public static class SaveMessageMetadataRequest {
        private String role;
        private String content;
        private List<String> referencedKnowledge;
        private List<String> triggeredSkills;
        public String getRole() {
            return role;
        }
        public String getContent() {
            return content;
        }
        public List<String> getReferencedKnowledge() {
            return referencedKnowledge;
        }
        public List<String> getTriggeredSkills() {
            return triggeredSkills;
        }
        public void setRole(String role) {
            this.role = role;
        }
        public void setContent(String content) {
            this.content = content;
        }
        public void setReferencedKnowledge(List<String> referencedKnowledge) {
            this.referencedKnowledge = referencedKnowledge;
        }
        public void setTriggeredSkills(List<String> triggeredSkills) {
            this.triggeredSkills = triggeredSkills;
        }
    }
    public static class UpdateSummaryRequest {
        private String summary;
        public String getSummary() {
            return summary;
        }
        public void setSummary(String summary) {
            this.summary = summary;
        }
    }
}
