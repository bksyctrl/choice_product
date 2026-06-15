package com.ecommerce.workflow.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class VeoVideoService {

    private static final Logger log = LoggerFactory.getLogger(VeoVideoService.class);
    private final AiProviderService aiProviderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VeoVideoService(AiProviderService aiProviderService) {
        this.aiProviderService = aiProviderService;
    }

    public VeoCreateResponse createVideo(VeoCreateRequest request) {
        try {
            log.info("发起VEO视频创建请求: model={}, prompt={}", request.getModel(), request.getPrompt());

            Map<String, Object> result = aiProviderService.createVideoWithFallback(
                    request.getPrompt(),
                    request.getModel(),
                    request.isEnhancePrompt(),
                    request.isEnableUpsample(),
                    request.getImages(),
                    request.getAspectRatio()
            );

            VeoCreateResponse response = new VeoCreateResponse();
            response.setId((String) result.get("id"));
            response.setStatus((String) result.get("status"));
            response.setStatusUpdateTime(result.containsKey("status_update_time") ? ((Number) result.get("status_update_time")).longValue() : 0);
            response.setEnhancedPrompt((String) result.getOrDefault("enhanced_prompt", ""));
            response.setProviderName((String) result.get("providerName")); // 保存提供商名称

            log.info("VEO视频创建请求发起成功: id={}, status={}, provider={}", response.getId(), response.getStatus(), response.getProviderName());
            return response;

        } catch (Exception e) {
            log.error("VEO视频创建失败", e);
            throw new RuntimeException("VEO视频创建失败: " + e.getMessage(), e);
        }
    }

    public VeoTaskStatus checkStatus(String taskId) {
        return checkStatus(taskId, null);
    }

    public VeoTaskStatus checkStatus(String taskId, String providerName) {
        try {
            Map<String, Object> result = aiProviderService.checkVideoStatusWithFallback(taskId, providerName);

            VeoTaskStatus status = new VeoTaskStatus();
            status.setId((String) result.get("id"));
            status.setStatus((String) result.get("status"));
            status.setProgress(result.containsKey("progress") ? ((Number) result.get("progress")).doubleValue() : 0.0);

            if (result.containsKey("video_url")) {
                status.setVideoUrl((String) result.get("video_url"));
            }
            if (result.containsKey("error")) {
                status.setError((String) result.get("error"));
            }

            return status;

        } catch (Exception e) {
            log.error("查询VEO视频状态失败: taskId={}, provider={}", taskId, providerName, e);
            throw new RuntimeException("查询VEO视频状态失败: " + e.getMessage(), e);
        }
    }

    public void pollUntilComplete(String taskId, VeoStatusCallback callback, long intervalMs, int maxAttempts) {
        pollUntilComplete(taskId, null, callback, intervalMs, maxAttempts);
    }

    public void pollUntilComplete(String taskId, String providerName, VeoStatusCallback callback, long intervalMs, int maxAttempts) {
        int attempts = 0;

        while (attempts < maxAttempts) {
            attempts++;
            log.info("轮询VEO视频状态: taskId={}, provider={}, attempt={}/{}", taskId, providerName, attempts, maxAttempts);

            try {
                VeoTaskStatus status = checkStatus(taskId, providerName);
                callback.onStatusUpdate(status);

                if (isTerminalStatus(status.getStatus())) {
                    if ("completed".equals(status.getStatus())) {
                        callback.onSuccess(status);
                    } else {
                        callback.onFailure(new Exception("视频生成失败: " + status.getError()));
                    }
                    return;
                }

                Thread.sleep(intervalMs);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                callback.onFailure(e);
                return;
            } catch (Exception e) {
                log.error("轮询状态异常", e);
                if (attempts >= maxAttempts) {
                    callback.onFailure(e);
                    return;
                }
                try {
                    Thread.sleep(intervalMs * 2);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    callback.onFailure(ie);
                    return;
                }
            }
        }

        callback.onFailure(new Exception("超过最大轮询次数，视频生成超时"));
    }

    private boolean isTerminalStatus(String status) {
        return switch (status) {
            case "completed", "failed", "error" -> true;
            default -> false;
        };
    }

    public static class VeoCreateRequest {
        private String model;
        private String prompt;
        private boolean enhancePrompt = true;
        private boolean enableUpsample = true;
        private List<String> images;
        private String aspectRatio;

        public String getModel() {
            return model;
        }

        public String getPrompt() {
            return prompt;
        }

        public boolean isEnhancePrompt() {
            return enhancePrompt;
        }

        public boolean isEnableUpsample() {
            return enableUpsample;
        }

        public List<String> getImages() {
            return images;
        }

        public String getAspectRatio() {
            return aspectRatio;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }

        public void setEnhancePrompt(boolean enhancePrompt) {
            this.enhancePrompt = enhancePrompt;
        }

        public void setEnableUpsample(boolean enableUpsample) {
            this.enableUpsample = enableUpsample;
        }

        public void setImages(List<String> images) {
            this.images = images;
        }

        public void setAspectRatio(String aspectRatio) {
            this.aspectRatio = aspectRatio;
        }
    }

    public static class VeoCreateResponse {
        private String id;
        private String status;
        private long statusUpdateTime;
        private String enhancedPrompt;
        private String providerName; // 记录创建视频的提供商名称

        public String getId() {
            return id;
        }

        public String getStatus() {
            return status;
        }

        public long getStatusUpdateTime() {
            return statusUpdateTime;
        }

        public String getEnhancedPrompt() {
            return enhancedPrompt;
        }

        public String getProviderName() {
            return providerName;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public void setStatusUpdateTime(long statusUpdateTime) {
            this.statusUpdateTime = statusUpdateTime;
        }

        public void setEnhancedPrompt(String enhancedPrompt) {
            this.enhancedPrompt = enhancedPrompt;
        }

        public void setProviderName(String providerName) {
            this.providerName = providerName;
        }
    }

    public static class VeoTaskStatus {
        private String id;
        private String status;
        private double progress;
        private String videoUrl;
        private String error;

        public String getId() {
            return id;
        }

        public String getStatus() {
            return status;
        }

        public double getProgress() {
            return progress;
        }

        public String getVideoUrl() {
            return videoUrl;
        }

        public String getError() {
            return error;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public void setProgress(double progress) {
            this.progress = progress;
        }

        public void setVideoUrl(String videoUrl) {
            this.videoUrl = videoUrl;
        }

        public void setError(String error) {
            this.error = error;
        }
    }

    @FunctionalInterface
    public interface VeoStatusCallback {
        void onStatusUpdate(VeoTaskStatus status);
        default void onSuccess(VeoTaskStatus status) {}
        default void onFailure(Exception error) {}
    }
}
