package com.ecommerce.workflow.controller;

import com.ecommerce.workflow.entity.ChatMessage;
import com.ecommerce.workflow.service.ai.UnifiedChatService;
import com.ecommerce.workflow.service.ai.AiProviderService;
import com.ecommerce.workflow.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai-chat")
public class AiChatController {
    private static final Logger log = LoggerFactory.getLogger(AiChatController.class);

    @Autowired
    private UnifiedChatService unifiedChatService;

    @Autowired
    private AiProviderService aiProviderService;

    @PostMapping("/session")
    public ApiResponse<Map<String, String>> createSession() {
        String sessionId = "SESSION_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        Map<String, String> result = new HashMap<>();
        result.put("sessionId", sessionId);

        return ApiResponse.success(result);
    }

    @PostMapping("/message")
    public ApiResponse<ChatMessage> sendMessage(@RequestBody Map<String, Object> request) {
        String sessionId = (String) request.get("sessionId");
        String message = (String) request.get("message");
        @SuppressWarnings("unchecked")
        List<String> imageUrls = request.get("imageUrls") instanceof List ? (List<String>) request.get("imageUrls")
                : List.of();
        String preferredModel = (String) request.get("model");

        Long userId = UserContext.getCurrentUserId();

        log.info("收到聊天消息: sessionId={}, message={}, images={}, model={}, userId={}",
                sessionId, message, imageUrls, preferredModel, userId);

        String[] imageUrlsArray = imageUrls != null ? imageUrls.toArray(new String[0]) : null;

        ChatMessage reply = unifiedChatService.sessionChat(sessionId, message, imageUrlsArray, userId, preferredModel);

        return ApiResponse.success(reply);
    }

    @GetMapping("/messages/{sessionId}")
    public ApiResponse<List<ChatMessage>> getSessionMessages(@PathVariable String sessionId) {
        List<ChatMessage> messages = unifiedChatService.getSessionMessages(sessionId);
        return ApiResponse.success(messages);
    }

    @DeleteMapping("/session/{sessionId}")
    public ApiResponse<String> clearSession(@PathVariable String sessionId) {
        unifiedChatService.clearSession(sessionId);
        return ApiResponse.success("会话已清除");
    }

    @GetMapping("/models")
    public ApiResponse<List<Map<String, Object>>> getAvailableModels() {
        List<Map<String, Object>> models = aiProviderService.getAvailableModels();
        return ApiResponse.success(models);
    }
}
