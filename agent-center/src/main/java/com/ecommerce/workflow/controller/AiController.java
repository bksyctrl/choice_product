package com.ecommerce.workflow.controller;

import com.ecommerce.workflow.service.ai.GptChatService;
import com.ecommerce.workflow.service.ai.UnifiedChatService;
import com.ecommerce.workflow.service.ai.VeoVideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);
    private final GptChatService gptChatService;
    private final UnifiedChatService unifiedChatService;
    private final VeoVideoService veoVideoService;
    private final Executor sseExecutor;

    public AiController(GptChatService gptChatService,
                       UnifiedChatService unifiedChatService,
                       VeoVideoService veoVideoService,
                       @Qualifier("sseExecutor") Executor sseExecutor) {
        this.gptChatService = gptChatService;
        this.unifiedChatService = unifiedChatService;
        this.veoVideoService = veoVideoService;
        this.sseExecutor = sseExecutor;
    }

    @PostMapping("/chat")
    public ApiResponse<String> chat(@Valid @RequestBody ChatRequest request) {
        try {
            String response = unifiedChatService.intelligentChat(
                    request.getSessionId(),
                    request.getUserMessage(),
                    request.getSystemPrompt()
            );
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("AI对话失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(180000L);

        sseExecutor.execute(() -> {
            try {
                unifiedChatService.intelligentStreamChat(
                        request.getSessionId(),
                        request.getUserMessage(),
                        request.getSystemPrompt(),
                        new UnifiedChatService.StreamCallback() {
                            @Override
                            public void onContent(String content) {
                                try {
                                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                                            .name("content").data(content));
                                } catch (IOException e) {
                                    log.error("发送AI响应内容失败", e);
                                }
                            }

                            @Override
                            public void onComplete() {
                                try {
                                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                                            .name("done").data("[DONE]"));
                                    emitter.complete();
                                } catch (IOException e) {
                                    log.error("完成AI响应发送失败", e);
                                    emitter.completeWithError(e);
                                }
                            }

                            @Override
                            public void onError(Throwable error) {
                                try {
                                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                                            .name("error").data(error.getMessage()));
                                    emitter.completeWithError(error);
                                } catch (IOException e) {
                                    log.error("发送AI响应错误失败", e);
                                    emitter.completeWithError(e);
                                }
                            }
                        }
                );
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @GetMapping("/learning/stats")
    public ApiResponse<Map<String, Object>> getLearningStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("status", "active");
            stats.put("service", "UnifiedChatService");
            stats.put("timestamp", java.time.LocalDateTime.now());
            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("获取学习统计失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/vision/analyze")
    public ApiResponse<String> analyzeImage(@Valid @RequestBody ImageAnalysisRequest request) {
        try {
            String result = gptChatService.analyzeImage(
                    request.getImageUrl(),
                    request.getPrompt(),
                    request.isBase64()
            );
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("AI图像分析失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/vision/analyze/upload")
    public ApiResponse<String> analyzeUploadedImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "prompt", defaultValue = "请分析这张图片的内容") String prompt) {
        try {
            byte[] imageBytes = file.getBytes();
            String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
            String dataUrl = "data:" + file.getContentType() + ";base64," + base64Image;

            String result = gptChatService.analyzeImage(dataUrl, prompt, true);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("上传图片分析失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/veo/create")
    public ApiResponse<VeoVideoService.VeoCreateResponse> createVideo(@Valid @RequestBody VeoCreateRequest request) {
        try {
            log.info("创建Veo视频: model={}", request.getModel());

            VeoVideoService.VeoCreateRequest veoRequest = new VeoVideoService.VeoCreateRequest();
            veoRequest.setModel(request.getModel() != null ? request.getModel() : "veo3.1-fast");
            veoRequest.setPrompt(request.getPrompt());
            veoRequest.setEnhancePrompt(request.getEnhancePrompt() != null ? request.getEnhancePrompt() : true);
            veoRequest.setEnableUpsample(request.getEnableUpsample() != null ? request.getEnableUpsample() : true);
            veoRequest.setImages(request.getImages());
            veoRequest.setAspectRatio(request.getAspectRatio());

            VeoVideoService.VeoCreateResponse response = veoVideoService.createVideo(veoRequest);

            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("创建Veo视频失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/veo/status/{taskId}")
    public ApiResponse<VeoVideoService.VeoTaskStatus> getVeoStatus(@PathVariable String taskId) {
        try {
            VeoVideoService.VeoTaskStatus status = veoVideoService.checkStatus(taskId);
            return ApiResponse.success(status);
        } catch (Exception e) {
            log.error("查询Veo视频生成状态失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/veo/tasks")
    public ApiResponse<List<Object>> getVeoTasks() {
        return ApiResponse.success(java.util.Collections.emptyList());
    }

    @PostMapping("/veo/poll/{taskId}")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter pollVeoStatus(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "5000") long intervalMs,
            @RequestParam(defaultValue = "120") int maxAttempts) {

        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(600000L);

        sseExecutor.execute(() -> {
            try {
                veoVideoService.pollUntilComplete(taskId, new VeoVideoService.VeoStatusCallback() {
                    @Override
                    public void onStatusUpdate(VeoVideoService.VeoTaskStatus status) {
                        try {
                            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                                    .name("status").data(status));
                        } catch (IOException e) {
                            log.error("发送状态更新到SSE流失败", e);
                        }
                    }

                    @Override
                    public void onSuccess(VeoVideoService.VeoTaskStatus status) {
                        try {
                            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                                    .name("complete").data(status));
                            emitter.complete();
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onFailure(Exception error) {
                        try {
                            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                                    .name("error").data(error.getMessage()));
                            emitter.completeWithError(error);
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }
                }, intervalMs, maxAttempts);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    public static class ChatRequest {
        private String systemPrompt;
        private String userMessage;
        private String sessionId;
        private Double temperature = 0.7;
        private Integer maxTokens = 2000;

        // Getters
        public String getSystemPrompt() {
            return systemPrompt;
        }

        public String getUserMessage() {
            return userMessage;
        }

        public String getSessionId() {
            return sessionId;
        }

        public Double getTemperature() {
            return temperature;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        // Setters
        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public void setUserMessage(String userMessage) {
            this.userMessage = userMessage;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }
    }

    public static class ImageAnalysisRequest {
        private String imageUrl;
        private String prompt;
        private boolean base64 = false;

        // Getters
        public String getImageUrl() {
            return imageUrl;
        }

        public String getPrompt() {
            return prompt;
        }

        public boolean isBase64() {
            return base64;
        }

        // Setters
        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }

        public void setBase64(boolean base64) {
            this.base64 = base64;
        }
    }

    public static class VeoCreateRequest {
        private String model;
        private String prompt;
        private Boolean enhancePrompt = true;
        private Boolean enableUpsample = true;
        private List<String> images;
        private String aspectRatio;

        // Getters
        public String getModel() {
            return model;
        }

        public String getPrompt() {
            return prompt;
        }

        public Boolean getEnhancePrompt() {
            return enhancePrompt;
        }

        public Boolean getEnableUpsample() {
            return enableUpsample;
        }

        public List<String> getImages() {
            return images;
        }

        public String getAspectRatio() {
            return aspectRatio;
        }

        // Setters
        public void setModel(String model) {
            this.model = model;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }

        public void setEnhancePrompt(Boolean enhancePrompt) {
            this.enhancePrompt = enhancePrompt;
        }

        public void setEnableUpsample(Boolean enableUpsample) {
            this.enableUpsample = enableUpsample;
        }

        public void setImages(List<String> images) {
            this.images = images;
        }

        public void setAspectRatio(String aspectRatio) {
            this.aspectRatio = aspectRatio;
        }
    }
}
