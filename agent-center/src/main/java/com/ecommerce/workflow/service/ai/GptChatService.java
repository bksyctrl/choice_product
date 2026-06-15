package com.ecommerce.workflow.service.ai;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ecommerce.workflow.service.config.SysConfigService;
import com.ecommerce.workflow.service.memory.UnifiedMemoryService;

@Service
public class GptChatService {

    private static final Logger log = LoggerFactory.getLogger(GptChatService.class);
    private final AiProviderService aiProviderService;
    private final UnifiedMemoryService unifiedMemoryService;
    private final SysConfigService sysConfigService;

    public GptChatService(AiProviderService aiProviderService, UnifiedMemoryService unifiedMemoryService,
                          SysConfigService sysConfigService) {
        this.aiProviderService = aiProviderService;
        this.unifiedMemoryService = unifiedMemoryService;
        this.sysConfigService = sysConfigService;
    }

    public String chat(String userMessage) {
        return chat(null, userMessage);
    }

    public String chat(String systemPrompt, String userMessage) {
        long startTime = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        String response = null;

        try {
            int maxTokens = sysConfigService.getIntConfig("chat_max_tokens", 8000);
            response = aiProviderService.chatWithFallback(systemPrompt, userMessage, 0.7, maxTokens);
            success = response != null && !response.isEmpty();
            return response;
        } catch (Exception e) {
            errorMessage = e.getMessage();
            throw e;
        } finally {
            try {
                Map<String, Object> inputParams = new HashMap<>();
                inputParams.put("systemPrompt", systemPrompt != null ? systemPrompt.substring(0, Math.min(systemPrompt.length(), 100)) : "");
                inputParams.put("userMessage", userMessage.substring(0, Math.min(userMessage.length(), 200)));
                inputParams.put("platform", "ai_chat");

                Map<String, Object> outputResult = new HashMap<>();
                if (response != null) {
                    outputResult.put("responseLength", response.length());
                    outputResult.put("responsePreview", response.substring(0, Math.min(response.length(), 100)));
                }
                outputResult.put("latencyMs", System.currentTimeMillis() - startTime);

                unifiedMemoryService.recordBusinessAction(
                        "ai_chat", "chat_completion",
                        inputParams, outputResult, success, errorMessage);
            } catch (Exception e) {
                log.debug("记录AI对话记忆失败", e);
            }
        }
    }

    public String chatWithThinking(String systemPrompt, String userMessage) {
        long startTime = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        String response = null;

        try {
            int thinkingMaxTokens = sysConfigService.getIntConfig("chat_thinking_max_tokens", 16000);
            response = aiProviderService.chatWithFallback(systemPrompt, userMessage, 0.7, thinkingMaxTokens);
            success = response != null && !response.isEmpty();
            return response;
        } catch (Exception e) {
            errorMessage = e.getMessage();
            throw e;
        } finally {
            try {
                Map<String, Object> inputParams = new HashMap<>();
                inputParams.put("systemPrompt", systemPrompt != null ? systemPrompt.substring(0, Math.min(systemPrompt.length(), 100)) : "");
                inputParams.put("userMessage", userMessage.substring(0, Math.min(userMessage.length(), 200)));
                inputParams.put("platform", "ai_chat_thinking");

                Map<String, Object> outputResult = new HashMap<>();
                if (response != null) {
                    outputResult.put("responseLength", response.length());
                }
                outputResult.put("latencyMs", System.currentTimeMillis() - startTime);

                unifiedMemoryService.recordBusinessAction(
                        "ai_chat", "chat_with_thinking",
                        inputParams, outputResult, success, errorMessage);
            } catch (Exception e) {
                log.debug("记录AI深度对话记忆失败", e);
            }
        }
    }

    public void chatStream(String userMessage, StreamCallback callback) {
        chatStream(null, userMessage, callback);
    }

    public void chatStream(String systemPrompt, String userMessage, StreamCallback callback) {
        aiProviderService.chatStreamWithFallback(systemPrompt, userMessage, callback);
    }

    public String analyzeImage(String imageUrl, String prompt) {
        return analyzeImage(imageUrl, prompt, false);
    }

    public String analyzeImage(String imageUrl, String prompt, boolean isBase64) {
        long startTime = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        String response = null;

        try {
            response = aiProviderService.analyzeImageWithFallback(imageUrl, prompt, isBase64);
            success = response != null && !response.isEmpty();
            return response;
        } catch (Exception e) {
            errorMessage = e.getMessage();
            throw e;
        } finally {
            try {
                Map<String, Object> inputParams = new HashMap<>();
                inputParams.put("hasImage", true);
                inputParams.put("isBase64", isBase64);
                inputParams.put("promptLength", prompt != null ? prompt.length() : 0);
                inputParams.put("platform", "image_analysis");

                Map<String, Object> outputResult = new HashMap<>();
                if (response != null) {
                    outputResult.put("responseLength", response.length());
                }
                outputResult.put("latencyMs", System.currentTimeMillis() - startTime);

                unifiedMemoryService.recordBusinessAction(
                        "image_analysis", "analyze_image",
                        inputParams, outputResult, success, errorMessage);
            } catch (Exception e) {
                log.debug("记录图片分析记忆失败", e);
            }
        }
    }

    public String callGptsWithSystem(String gptsModelId, String systemPrompt, String userMessage) {
        return aiProviderService.chatWithFallback(systemPrompt, userMessage, 0.7, 2000);
    }

    @FunctionalInterface
    public interface StreamCallback {
        void onContent(String content);
        default void onComplete() {}
        default void onError(Throwable error) {}
    }
}
