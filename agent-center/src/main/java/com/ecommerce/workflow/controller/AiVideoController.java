package com.ecommerce.workflow.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ecommerce.workflow.entity.AiVideoConfig;
import com.ecommerce.workflow.entity.ChatMessage;
import com.ecommerce.workflow.entity.ChatSession;
import com.ecommerce.workflow.entity.Knowledge;
import com.ecommerce.workflow.entity.VideoTask;
import com.ecommerce.workflow.service.ai.AiProviderService;
import com.ecommerce.workflow.service.ai.AiVideoService;
import com.ecommerce.workflow.service.ai.ModelDisplayNameService;
import com.ecommerce.workflow.service.ai.PromptBuilderService;
import com.ecommerce.workflow.service.knowledge.KnowledgeService;
import com.ecommerce.workflow.service.learning.PromptImportService;
import com.ecommerce.workflow.service.prompt.PromptTemplateEngine;
import com.ecommerce.workflow.service.session.SessionService;
import com.ecommerce.workflow.service.video.VideoExpertAnalysisService;
import com.ecommerce.workflow.service.video.VideoPromptFrameworkService;
import com.ecommerce.workflow.util.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/ai-video")
public class AiVideoController {
    private static final Logger log = LoggerFactory.getLogger(AiVideoController.class);

    @Value("${app.upload.path:./uploads}")
    private String uploadPath;

    @Autowired
    private AiVideoService aiVideoService;

    @Autowired
    private AiProviderService aiProviderService;

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private PromptImportService promptImportService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private VideoExpertAnalysisService videoExpertAnalysisService;

    @Autowired
    private PromptBuilderService promptBuilderService;

    @Autowired
    private VideoPromptFrameworkService videoPromptFrameworkService;

    @Autowired
    private ModelDisplayNameService modelDisplayNameService;

    @Autowired
    private PromptTemplateEngine promptTemplateEngine;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/config")
    public ApiResponse<AiVideoConfig> saveConfig(@RequestBody AiVideoConfig config) {
        log.info("保存AI视频配置: {}", config);
        config.setCreatorId(UserContext.getCurrentUserId());
        AiVideoConfig saved = aiVideoService.saveConfig(config);
        return ApiResponse.success(saved);
    }

    @GetMapping("/config/{id}")
    public ApiResponse<AiVideoConfig> getConfig(@PathVariable Long id) {
        AiVideoConfig config = aiVideoService.getConfig(id);
        if (config == null) {
            return ApiResponse.error("配置不存在");
        }
        return ApiResponse.success(config);
    }

    @GetMapping("/configs")
    public ApiResponse<List<AiVideoConfig>> listConfigs() {
        Long userId = UserContext.getCurrentUserId();
        List<AiVideoConfig> configs = aiVideoService.listConfigs(userId);
        return ApiResponse.success(configs);
    }

    @PostMapping("/generate")
    public ApiResponse<Map<String, Object>> generateVideo(
            @RequestBody Map<String, Object> request) {
        log.info("生成AI视频任务: {}", request);

        try {
            Long userId = UserContext.getCurrentUserId();
            AiVideoConfig config = new AiVideoConfig();
            config.setConfigName(
                    extractString(request.getOrDefault("configName", "视频配置-" + System.currentTimeMillis())));
            config.setLanguage(extractString(request.get("language")));
            config.setRace(extractString(request.get("race")));
            config.setRole(extractString(request.get("role")));
            config.setTopic(extractString(request.get("topic")));
            config.setSceneType(extractString(request.get("sceneType")));
            config.setScene(extractString(request.get("scene")));
            config.setFrameType(extractString(request.get("frameType")));
            config.setVideoName(extractString(request.getOrDefault("videoName", "AI生成视频")));
            config.setAspectRatio(extractString(request.getOrDefault("aspectRatio", "9:16")));
            config.setResolution(extractString(request.getOrDefault("resolution", "1080p")));
            Object frameRateObj = request.getOrDefault("frameRate", 30);
            config.setFrameRate(frameRateObj instanceof Number ? ((Number) frameRateObj).intValue() : 30);
            Object durationObj = request.getOrDefault("duration", 60);
            config.setDuration(durationObj instanceof Number ? ((Number) durationObj).intValue() : 60);
            Object styleIntensityObj = request.getOrDefault("styleIntensity", 70);
            config.setStyleIntensity(
                    styleIntensityObj instanceof Number ? ((Number) styleIntensityObj).intValue() : 70);
            Object creativityObj = request.getOrDefault("creativity", 50);
            config.setCreativity(creativityObj instanceof Number ? ((Number) creativityObj).intValue() : 50);
            
            Object autoMixObj = request.get("autoMix");
            log.info("接收到的autoMix参数: raw={}, type={}", autoMixObj, autoMixObj != null ? autoMixObj.getClass().getSimpleName() : "null");
            if (autoMixObj != null) {
                boolean autoMixValue = Boolean.TRUE.equals(autoMixObj) || "true".equals(autoMixObj.toString());
                config.setAutoMix(autoMixValue);
                log.info("设置autoMix为: {}", autoMixValue);
            } else {
                config.setAutoMix(false);
                log.info("autoMix参数为空，设置为默认值: false");
            }
            
            // 保存扩展参数（认知点、兴趣点、利益点、情绪点、基础元素、人物、产品、框架）
            Map<String, Object> extendedParams = new HashMap<>();
            extendedParams.put("cognition", request.get("cognition"));
            extendedParams.put("interest", request.get("interest"));
            extendedParams.put("benefit", request.get("benefit"));
            extendedParams.put("emotion", request.get("emotion"));
            extendedParams.put("basic", request.get("basic"));
            extendedParams.put("character", request.get("character"));
            extendedParams.put("product", request.get("product"));
            extendedParams.put("framework", request.get("framework"));
            config.setExtendedParams(serializeJson(extendedParams));
            
            // 设置 AI 生成的提示词（如果前端传入了）
            Object imagePromptsObj = request.get("imagePrompts");
            if (imagePromptsObj != null) {
                if (imagePromptsObj instanceof String) {
                    config.setImagePrompts((String) imagePromptsObj);
                } else {
                    config.setImagePrompts(serializeJson(imagePromptsObj));
                }
                log.info("接收到 AI 生成的场景图提示词: {}", config.getImagePrompts() != null ? 
                        config.getImagePrompts().substring(0, Math.min(100, config.getImagePrompts().length())) + "..." : "null");
            }
            
            Object videoPromptsObj = request.get("videoPrompts");
            if (videoPromptsObj != null) {
                if (videoPromptsObj instanceof String) {
                    config.setVideoPrompts((String) videoPromptsObj);
                } else {
                    config.setVideoPrompts(serializeJson(videoPromptsObj));
                }
                log.info("接收到 AI 生成的视频提示词: {}", config.getVideoPrompts() != null ? 
                        config.getVideoPrompts().substring(0, Math.min(100, config.getVideoPrompts().length())) + "..." : "null");
            }
            
            config.setCreatorId(userId);

            AiVideoConfig savedConfig = aiVideoService.saveConfig(config);

            List<String> imageUrls = extractStringList(request.get("imageUrls"));
            String[] imageUrlsArray = imageUrls != null ? imageUrls.toArray(new String[0]) : new String[0];

            // 获取用户指定的模型（可选）
            String imageModel = extractString(request.get("imageModel"));
            String videoModel = extractString(request.get("videoModel"));

            VideoTask task = aiVideoService.createTask(savedConfig, imageUrlsArray, userId, imageModel, videoModel);

            Map<String, Object> result = new HashMap<>();
            result.put("taskId", task.getTaskId());
            result.put("status", task.getStatus());
            result.put("progress", task.getProgress());

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("生成AI视频任务失败", e);
            return ApiResponse.error("生成AI视频任务失败: " + getErrorMessage(e));
        }
    }

    private String serializeJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("序列化JSON失败: {}", e.getMessage());
            return null;
        }
    }

    private String extractString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Object v = map.get("value");
            return v != null ? v.toString() : null;
        }
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String) {
                    result.add((String) item);
                } else if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return null;
    }

    private String extractMapValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Object v = map.get("value");
            return v != null ? v.toString() : null;
        }
        return null;
    }

    @PostMapping("/chat-generate")
    public ApiResponse<Map<String, Object>> chatGenerate(@RequestBody Map<String, Object> request) {
        log.info("AI对话生成视频请求: {}", request);

        try {
            String message = (String) request.get("message");
            String sessionId = (String) request.get("sessionId");
            @SuppressWarnings("unchecked")
            List<String> imageUrls = request.get("imageUrls") instanceof List ? (List<String>) request.get("imageUrls")
                    : List.of();

            // 获取或创建会话
            Long userId = UserContext.getCurrentUserId();
            ChatSession session = getOrCreateVideoSession(sessionId, userId);
            sessionId = session.getSessionId();

            // 保存用户消息
            if (message != null && !message.isEmpty()) {
                sessionService.saveMessage(sessionId, "user", message);
            }

            Map<String, Object> result = new HashMap<>();

            // Analyze reference images if provided
            if (imageUrls != null && !imageUrls.isEmpty()) {
                try {
                    String imageUrl = imageUrls.get(0);
                    boolean isBase64 = imageUrl != null && imageUrl.length() > 200;
                    String analysisResult = aiProviderService.analyzeImageWithFallback(
                            imageUrl, "分析图像内容，提取视觉元素和风格特征", isBase64);
                    if (analysisResult != null) {
                        result.put("imageAnalysis", analysisResult);
                    }
                } catch (Exception e) {
                    log.warn("图像分析失败，继续处理: {}", e.getMessage());
                }
            }

            String aiResponse = "正在分析您的需求，请稍候...";

            Map<String, String> params = new HashMap<>();

            if (message != null) {
                if (message.contains("热点") || message.contains("趋势")) {
                    params.put("cognition", "hotspot_follow");
                    params.put("topic", "热点追踪视频");
                }
                if (message.contains("兴趣") || message.contains("爱好")) {
                    params.put("interest", "personal_hobby");
                }
                if (message.contains("好处") || message.contains("优势")) {
                    params.put("benefit", "solve_problem");
                }
                if (message.contains("情感") || message.contains("共鸣")) {
                    params.put("emotion", "resonance");
                }

                if (!params.isEmpty()) {
                    aiResponse = "根据您的描述，我为您推荐以下视频参数:\n";
                    for (Map.Entry<String, String> entry : params.entrySet()) {
                        aiResponse += "- " + entry.getKey() + ": " + entry.getValue() + "\n";
                    }
                    aiResponse += "\n正在为您生成视频，请稍候...";
                } else {
                    aiResponse = "您好！我可以帮您生成各种类型的视频。\n\n请告诉我您想要什么样的视频，比如热点追踪、产品介绍、情感故事等，我会为您推荐合适的参数。";
                }
            }

            // 保存AI回复
            sessionService.saveMessage(sessionId, "assistant", aiResponse);

            result.put("message", aiResponse);
            result.put("params", params);
            result.put("sessionId", sessionId);

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("AI对话生成视频失败", e);
            return ApiResponse.error("AI对话生成视频失败: " + getErrorMessage(e));
        }
    }

    @GetMapping("/task/{taskId}")
    public ApiResponse<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        try {
            VideoTask task = aiVideoService.getTask(taskId);
            if (task == null) {
                log.warn("查询任务不存在: taskId={}", taskId);
                return ApiResponse.error("任务不存在");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("taskId", task.getTaskId());
            result.put("status", task.getStatus());
            result.put("progress", task.getProgress());
            result.put("videoUrl", task.getVideoUrl());
            result.put("errorMessage", task.getErrorMessage());
            result.put("sceneCount", task.getSceneCount());
            result.put("mixingMode", task.getMixingMode());
            result.put("imageUrls", task.getImageUrls());
            result.put("configId", task.getConfigId());
            
            try {
                String imageUrlsStr = task.getImageUrls();
                if (imageUrlsStr != null && imageUrlsStr.startsWith("[")) {
                    List<String> sceneVideoUrls = objectMapper.readValue(
                            imageUrlsStr,
                            new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                    result.put("sceneVideos", sceneVideoUrls);
                }
            } catch (Exception e) {
                log.debug("解析场景视频URL列表失败(imageUrls): {}", e.getMessage());
            }
            
            try {
                String sceneImageIdsStr = task.getSceneImageIds();
                if (sceneImageIdsStr != null && sceneImageIdsStr.startsWith("[")) {
                    List<Map<String, Object>> sceneInfoList = objectMapper.readValue(
                            sceneImageIdsStr,
                            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
                    List<String> sceneVideoUrls = new java.util.ArrayList<>();
                    for (Map<String, Object> sceneInfo : sceneInfoList) {
                        Object vUrl = sceneInfo.get("videoUrl");
                        if (vUrl != null && !vUrl.toString().isEmpty()) {
                            sceneVideoUrls.add(vUrl.toString());
                        }
                    }
                    if (!sceneVideoUrls.isEmpty()) {
                        result.put("sceneVideos", sceneVideoUrls);
                    }
                }
            } catch (Exception e) {
                log.debug("解析场景视频URL列表失败(sceneImageIds): {}", e.getMessage());
            }
            result.put("createdAt", task.getCreatedAt());
            result.put("updatedAt", task.getUpdatedAt());
            
            // 解析场景图信息（独立try-catch，失败不影响主结果）
            try {
                String sceneImageIdsStr = task.getSceneImageIds();
                if (sceneImageIdsStr != null && !sceneImageIdsStr.isEmpty()) {
                    // 尝试解析为JSON数组（新格式）
                    if (sceneImageIdsStr.startsWith("[")) {
                        List<Map<String, Object>> sceneImages = objectMapper.readValue(
                                sceneImageIdsStr, 
                                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
                        
                        // 将场景图文件路径转换为base64格式返回给前端
                        for (Map<String, Object> sceneImage : sceneImages) {
                            String imageUrl = (String) sceneImage.get("imageUrl");
                            if (imageUrl != null && !imageUrl.startsWith("data:image")) {
                                // 是文件路径，需要转换为base64
                                String base64Url = convertImagePathToBase64(imageUrl);
                                if (base64Url != null) {
                                    sceneImage.put("imageUrl", base64Url);
                                }
                            }
                        }
                        
                        result.put("sceneImages", sceneImages);
                    } else {
                        // 旧格式：逗号分隔的ID列表
                        result.put("sceneImageIds", sceneImageIdsStr);
                    }
                }
            } catch (Exception e) {
                log.warn("解析场景图信息失败，但不影响任务状态查询: taskId={}, error={}", taskId, e.getMessage());
                // 解析失败，返回原始字符串
                result.put("sceneImageIds", task.getSceneImageIds());
            }

            return ApiResponse.success(result);
        } catch (Exception e) {
            String errorMsg = getErrorMessage(e);
            log.error("查询任务状态异常: taskId={}, error={}", taskId, errorMsg, e);
            return ApiResponse.error("查询任务状态失败: " + errorMsg);
        }
    }

    @GetMapping("/tasks")
    public ApiResponse<List<VideoTask>> listTasks() {
        Long userId = UserContext.getCurrentUserId();
        List<VideoTask> tasks = aiVideoService.listTasks(userId);
        return ApiResponse.success(tasks);
    }

    @GetMapping("/history")
    public ApiResponse<Map<String, Object>> getHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Long userId = UserContext.getCurrentUserId();
        log.info("查询历史视频: userId={}, page={}, size={}, keyword={}", userId, page, size, keyword);

        try {
            Map<String, Object> result = aiVideoService.getHistory(userId, page, size, keyword);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询历史视频失败: userId={}, error={}", userId, e.getMessage(), e);
            return ApiResponse.error("查询历史视频失败: " + getErrorMessage(e));
        }
    }

    @DeleteMapping("/tasks/{taskId}")
    public ApiResponse<Map<String, Object>> deleteTask(@PathVariable String taskId) {
        Long userId = UserContext.getCurrentUserId();
        aiVideoService.deleteTask(taskId, userId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "任务删除成功");
        return ApiResponse.success(result);
    }

    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> getStatistics() {
        Long userId = UserContext.getCurrentUserId();
        Map<String, Object> stats = aiVideoService.getStatistics(userId);
        return ApiResponse.success(stats);
    }

    @GetMapping("/available-models")
    public ApiResponse<Map<String, Object>> getAvailableModels() {
        log.info("获取可用模型列表");

        Map<String, Object> result = new HashMap<>();

        // 获取图片生成模型列表
        List<Map<String, Object>> imageModels = new ArrayList<>();
        try {
            List<String> models = aiVideoService.getAvailableImageModels();
            for (String model : models) {
                Map<String, Object> modelInfo = new HashMap<>();
                modelInfo.put("name", model);
                modelInfo.put("type", "IMAGE");
                modelInfo.put("label", getModelDisplayName(model));
                imageModels.add(modelInfo);
            }
        } catch (Exception e) {
            log.warn("获取图片模型列表失败: {}", e.getMessage());
        }

        // 获取视频生成模型列表
        List<Map<String, Object>> videoModels = new ArrayList<>();
        try {
            List<String> models = aiVideoService.getAvailableVideoModels();
            for (String model : models) {
                Map<String, Object> modelInfo = new HashMap<>();
                modelInfo.put("name", model);
                modelInfo.put("type", "VIDEO");
                modelInfo.put("label", getModelDisplayName(model));
                videoModels.add(modelInfo);
            }
        } catch (Exception e) {
            log.warn("获取视频模型列表失败: {}", e.getMessage());
        }

        result.put("imageModels", imageModels);
        result.put("videoModels", videoModels);

        return ApiResponse.success(result);
    }

    private String getModelDisplayName(String model) {
        // 使用服务从数据库或缓存获取显示名称
        return modelDisplayNameService.getDisplayName(model);
    }

    @PostMapping("/import-prompts")
    public ApiResponse<Map<String, Object>> importPrompts() {
        log.info("开始导入提示词模板文件...");
        int importedCount = promptImportService.importPromptsFromFile();

        Map<String, Object> result = new HashMap<>();
        result.put("importedCount", importedCount);
        result.put("message", "成功导入" + importedCount + " 个提示词模板");

        return ApiResponse.success(result);
    }

    @PostMapping("/import-prompts-from-path")
    public ApiResponse<Map<String, Object>> importPromptsFromPath(@RequestBody Map<String, String> request) {
        String filePath = request.get("filePath");
        if (filePath == null || filePath.isEmpty()) {
            return ApiResponse.error("文件路径不能为空");
        }

        log.info("从指定路径导入提示词模板文件: {}", filePath);
        int importedCount = promptImportService.importPromptsFromCustomPath(filePath);

        Map<String, Object> result = new HashMap<>();
        result.put("importedCount", importedCount);
        result.put("filePath", filePath);
        result.put("message", "成功导入" + importedCount + " 个提示词模板");

        return ApiResponse.success(result);
    }

    @GetMapping("/prompt-import-status")
    public ApiResponse<Map<String, Object>> getPromptImportStatus() {
        Map<String, Object> status = promptImportService.getImportStatus();
        return ApiResponse.success(status);
    }

    @PostMapping("/upload-image")
    public ApiResponse<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResponse.error("请选择要上传的文件");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ApiResponse.error("只支持上传图片文件");
        }

        long maxSize = 10 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            return ApiResponse.error("文件大小不能超过10MB");
        }

        try {
            Path uploadDir = Paths.get(uploadPath, "images");
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = UUID.randomUUID().toString() + extension;

            Path filepath = uploadDir.resolve(filename);
            Files.write(filepath, file.getBytes());

            String fileUrl = "/uploads/images/" + filename;

            Map<String, String> result = new HashMap<>();
            result.put("url", fileUrl);
            result.put("filename", filename);

            return ApiResponse.success(result);

        } catch (IOException e) {
            log.error("上传图片失败", e);
            return ApiResponse.error("上传图片失败: " + getErrorMessage(e));
        }
    }

    @PostMapping("/analyze-image")
    public ApiResponse<Map<String, Object>> analyzeImage(@RequestBody Map<String, Object> request) {
        log.info("分析图像生成视频推荐请求: {}", request);

        try {
            String imageUrl = (String) request.get("imageUrl");
            String base64 = (String) request.get("base64");

            if (imageUrl == null && base64 == null) {
                return ApiResponse.error("请提供图片URL或base64编码");
            }

            String imageSource = (base64 != null && !base64.isEmpty()) ? base64 : imageUrl;
            boolean isBase64 = (base64 != null && !base64.isEmpty());

            Map<String, Object> expertAnalysis = videoExpertAnalysisService.analyzeImageWithExperts(imageSource, isBase64);

            String basicAnalysis = (String) expertAnalysis.get("basicAnalysis");
            String combinedExpertAdvice = (String) expertAnalysis.get("combinedExpertAdvice");

            log.info("AI原始响应长度: {}", basicAnalysis != null ? basicAnalysis.length() : 0);

            String analysisPrompt = promptBuilderService.buildImageAnalysisPrompt();
            
            // 准备图片数据：如果是本地URL，转换为base64
            String imageForAnalysis = isBase64 ? base64 : convertLocalUrlToBase64(imageUrl);
            boolean isBase64ForAnalysis = isBase64 || (imageForAnalysis != null && imageForAnalysis.startsWith("data:image"));
            
            String jsonResponse = aiProviderService.analyzeImageWithFallback(
                    imageForAnalysis != null ? imageForAnalysis : (isBase64 ? base64 : imageUrl), 
                    analysisPrompt, 
                    isBase64ForAnalysis);

            log.info("AI JSON响应长度: {}", jsonResponse != null ? jsonResponse.length() : 0);

            String jsonStr = extractJsonFromResponse(jsonResponse);
            
            if (jsonStr == null) {
                log.warn("AI未返回JSON格式，尝试重新生成");
                String retryPrompt = promptBuilderService.buildImageAnalysisRetryPrompt(basicAnalysis);
                
                String retryResponse = aiProviderService.analyzeImageWithFallback(
                        imageForAnalysis != null ? imageForAnalysis : (isBase64 ? base64 : imageUrl),
                        retryPrompt,
                        isBase64ForAnalysis);
                jsonStr = extractJsonFromResponse(retryResponse);
            }

            Map<String, Object> recommendations = parseRecommendations(jsonStr != null ? jsonStr : basicAnalysis);

            log.info("🔍 解析后的recommendations: {}", recommendations);
            log.info("🔍 recommendations.language: {}", recommendations.get("language"));
            log.info("🔍 recommendations.race: {}", recommendations.get("race"));
            log.info("🔍 recommendations.cognition: {}", recommendations.get("cognition"));
            log.info("🔍 recommendations.interest: {}", recommendations.get("interest"));

            Map<String, Object> result = new HashMap<>();
            result.put("analysis", basicAnalysis);
            result.put("expertAnalysis", expertAnalysis);
            result.put("combinedExpertAdvice", combinedExpertAdvice);
            result.put("recommendations", recommendations);
            result.put("message", "图像分析完成，3位专家联合分析已生成视频参数推荐");

            log.info("🔍 返回给前端的完整result: {}", result);

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("生成视频推荐失败", e);
            return ApiResponse.error("生成视频推荐失败: " + getErrorMessage(e));
        }
    }

    private String extractJsonFromResponse(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }
        
        // 尝试提取markdown代码块中的JSON
        int codeBlockStart = response.indexOf("```json");
        if (codeBlockStart >= 0) {
            int jsonStart = response.indexOf('{', codeBlockStart);
            int codeBlockEnd = response.indexOf("```", codeBlockStart + 7);
            if (jsonStart >= 0 && codeBlockEnd > jsonStart) {
                String jsonStr = response.substring(jsonStart, codeBlockEnd).trim();
                try {
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonStr);
                    return jsonStr;
                } catch (Exception e) {
                    log.warn("提取markdown JSON失败: {}", e.getMessage());
                }
            }
        }
        
        // 尝试提取普通代码块中的JSON
        codeBlockStart = response.indexOf("```");
        if (codeBlockStart >= 0) {
            int jsonStart = response.indexOf('{', codeBlockStart);
            int codeBlockEnd = response.indexOf("```", codeBlockStart + 3);
            if (jsonStart >= 0 && codeBlockEnd > jsonStart) {
                String jsonStr = response.substring(jsonStart, codeBlockEnd).trim();
                try {
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonStr);
                    return jsonStr;
                } catch (Exception e) {
                    log.warn("提取代码块 JSON失败: {}", e.getMessage());
                }
            }
        }
        
        // 使用括号匹配来提取完整JSON
        int jsonStart = response.indexOf('{');
        if (jsonStart >= 0) {
            int braceCount = 0;
            int jsonEnd = -1;
            boolean inString = false;
            boolean escaped = false;
            
            for (int i = jsonStart; i < response.length(); i++) {
                char c = response.charAt(i);
                
                if (escaped) {
                    escaped = false;
                    continue;
                }
                
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                
                if (c == '"') {
                    inString = !inString;
                    continue;
                }
                
                if (!inString) {
                    if (c == '{') {
                        braceCount++;
                    } else if (c == '}') {
                        braceCount--;
                        if (braceCount == 0) {
                            jsonEnd = i;
                            break;
                        }
                    }
                }
            }
            
            if (jsonEnd > jsonStart) {
                String jsonStr = response.substring(jsonStart, jsonEnd + 1);
                try {
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonStr);
                    return jsonStr;
                } catch (Exception e) {
                    log.warn("提取的JSON无效: {}", e.getMessage());
                }
            }
        }
        return null;
    }

    private Map<String, Object> parseRecommendations(String aiResponse) {
        Map<String, Object> recommendations = new HashMap<>();

        try {
            int jsonStart = aiResponse.indexOf('{');
            int jsonEnd = aiResponse.lastIndexOf('}');

            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String jsonStr = aiResponse.substring(jsonStart, jsonEnd + 1);
                
                log.info("🔍 提取的JSON字符串长度: {}", jsonStr.length());
                log.debug("🔍 JSON内容: {}", jsonStr);

                jsonStr = jsonStr.replaceAll(",\\s*}", "}")
                        .replaceAll(",\\s*]", "]")
                        .replaceAll("\"\\s*:\\s*\"", "\":\"")
                        .replaceAll("\"\\s*:\\s*\\[", "\":[")
                        .replaceAll("\"\\s*:\\s*\\{", "\":{");

                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                recommendations = mapper.readValue(jsonStr, Map.class);
                
                log.info("✅ JSON解析成功，字段数: {}", recommendations.size());
            } else {
                log.warn("❌ 未找到有效的JSON内容");
            }
        } catch (Exception e) {
            log.warn("解析AI响应失败，使用默认推荐值: {}", e.getMessage());
            recommendations.put("language", "zh-cn");
            recommendations.put("race", "east-asian");
            recommendations.put("cognition", Arrays.asList("hotspot"));
            recommendations.put("interest", Arrays.asList("hobby"));
            recommendations.put("benefit", Arrays.asList("solve"));
            recommendations.put("emotion", Arrays.asList("resonance"));
            recommendations.put("basic", Arrays.asList("copywriting"));
            recommendations.put("scene", Map.of("type", "scene-function", "value", "immersive"));
            recommendations.put("character", Map.of("type", "content-role", "value", "experiencer"));
            recommendations.put("product", Map.of("type", "product-basic", "value", "selling-point"));
            recommendations.put("framework", "hand-shake");
            recommendations.put("description", "根据图像分析生成的视频推荐参数");
        }

        return recommendations;
    }

    @PostMapping("/generate-prompt")
    public ApiResponse<Map<String, Object>> generatePrompt(@RequestBody Map<String, Object> request) {
        log.info("生成视频提示词请求: {}", request);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = request.get("params") instanceof Map
                    ? (Map<String, Object>) request.get("params")
                    : new HashMap<>();
            @SuppressWarnings("unchecked")
            List<String> imageUrls = request.get("imageUrls") instanceof List ? (List<String>) request.get("imageUrls")
                    : List.of();
            String currentPrompt = (String) request.get("currentPrompt");
            String modificationRequest = (String) request.get("modificationRequest");
            String sessionId = (String) request.get("sessionId");

            // 获取或创建AI视频会话
            Long userId = UserContext.getCurrentUserId();
            ChatSession session = getOrCreateVideoSession(sessionId, userId);
            sessionId = session.getSessionId();

            // 构建用户消息描述（保存到会话历史）
            String userMessageDesc;
            if (modificationRequest != null && !modificationRequest.isEmpty()) {
                userMessageDesc = "修改要求: " + modificationRequest;
            } else {
                userMessageDesc = "生成视频提示词，参数: " + describeParams(params);
            }
            sessionService.saveMessage(sessionId, "user", userMessageDesc);

            // 获取对话历史用于LLM上下文
            List<Map<String, String>> historyMessages = getHistoryForLLM(sessionId, 10);

            String prompt = buildVideoPromptWithAI(params, imageUrls, currentPrompt, modificationRequest, historyMessages);
            
            // 现在提示词是纯文本格式，直接使用
            String finalPrompt = prompt.trim();
            
            log.info("提示词生成成功，长度: {} 字符", finalPrompt.length());

            // 保存AI回复到会话历史
            sessionService.saveMessage(sessionId, "assistant", finalPrompt);

            // 尝试解析 JSON 格式的提示词，提取 imagePrompts 和 videoPrompts
            Map<String, Object> extractedPrompts = extractImageAndVideoPrompts(finalPrompt);

            Map<String, Object> result = new HashMap<>();
            result.put("prompt", finalPrompt);
            result.put("message", "提示词生成成功");
            result.put("sessionId", sessionId);
            result.put("length", finalPrompt.length());
            
            // 如果成功提取了结构化提示词，一并返回
            if (extractedPrompts != null) {
                result.putAll(extractedPrompts);
                log.info("成功提取结构化提示词: imagePrompts={}, videoPrompts={}", 
                        extractedPrompts.get("imagePrompts") != null ? "有" : "无",
                        extractedPrompts.get("videoPrompts") != null ? "有" : "无");
            }

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("生成视频提示词失败", e);
            @SuppressWarnings("unchecked")
            Map<String, Object> params = request.get("params") instanceof Map
                    ? (Map<String, Object>) request.get("params")
                    : new HashMap<>();
            @SuppressWarnings("unchecked")
            List<String> imageUrls = request.get("imageUrls") instanceof List ? (List<String>) request.get("imageUrls")
                    : List.of();
            String fallbackPrompt = buildVideoPrompt(params, imageUrls,
                    (String) request.get("currentPrompt"),
                    (String) request.get("modificationRequest"));

            Map<String, Object> result = new HashMap<>();
            result.put("prompt", fallbackPrompt);
            result.put("message", "AI服务异常，已使用备用方案生成提示词");
            result.put("sessionId", request.get("sessionId"));

            return ApiResponse.success(result);
        }
    }

    /**
     * 修改参数接口 - 先尝试简单匹配，再使用AI解析用户自然语言修改意图
     * 例如 "改成白人" → 将 race 改为 caucasian，其余参数保持不变
     */
    @PostMapping("/modify-params")
    public ApiResponse<Map<String, Object>> modifyParams(@RequestBody Map<String, Object> request) {
        log.info("AI修改视频参数请求: {}", request);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> currentParams = request.get("currentParams") instanceof Map
                    ? (Map<String, Object>) request.get("currentParams")
                    : new HashMap<>();
            String modificationRequest = (String) request.get("message");
            String sessionId = (String) request.get("sessionId");

            if (modificationRequest == null || modificationRequest.isEmpty()) {
                return ApiResponse.error("修改描述不能为空");
            }

            // 第一步：先尝试简单关键词匹配
            log.info("修改请求原文: [{}], 长度: {}", modificationRequest, modificationRequest.length());
            Object[] simpleResultArr = applySimpleParamModification(currentParams, modificationRequest);
            @SuppressWarnings("unchecked")
            Map<String, Object> simpleResult = (Map<String, Object>) simpleResultArr[0];
            boolean simpleMatched = (Boolean) simpleResultArr[1];
            log.info("简单匹配结果: matched={}, resultRace={}", simpleMatched, simpleResult.get("race"));

            Map<String, Object> modifiedParams;
            if (simpleMatched) {
                log.info("简单匹配成功，使用简单匹配结果");
                modifiedParams = simpleResult;
            } else {
                // 第二步：简单匹配失败，使用AI解析
                log.info("简单匹配失败，使用AI解析修改意图");
                modifiedParams = aiModifyParams(currentParams, modificationRequest, sessionId);
            }

            // 保存到会话历史
            if (sessionId != null && !sessionId.isEmpty()) {
                sessionService.saveMessage(sessionId, "user", "修改参数: " + modificationRequest);
                sessionService.saveMessage(sessionId, "assistant", "参数已修改");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("recommendations", modifiedParams);
            result.put("message", "参数修改成功");
            result.put("sessionId", sessionId);

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("修改视频参数失败", e);
            return ApiResponse.error("修改视频参数失败: " + getErrorMessage(e));
        }
    }

    /**
     * 使用AI解析修改意图（当简单匹配无法处理时）
     */
    private Map<String, Object> aiModifyParams(Map<String, Object> currentParams, String modificationRequest, String sessionId) {
            String systemPrompt = "你是一个视频参数修改助手。用户会告诉你他们想修改什么，你需要返回修改后的完整参数JSON。" +
                    "必须严格按照JSON格式输出，不要添加任何其他文字。" +
                    "如果用户只说了修改某一项，那么其他参数保持原值不变。" +
                    "特别注意：用户说'白人'对应race字段值'caucasian'，'黑人'对应'african'，'东亚人'对应'east-asian'。";

            StringBuilder userMsg = new StringBuilder();
            userMsg.append("当前参数:\n");
            userMsg.append(paramsToJsonString(currentParams)).append("\n\n");
            userMsg.append("用户的修改要求: ").append(modificationRequest).append("\n\n");
            userMsg.append("请返回修改后的完整参数JSON，格式如下:\n");
            userMsg.append("{\n");
            userMsg.append("  \"language\": \"语言代码\",\n");
            userMsg.append("  \"race\": \"人种代码\",\n");
            userMsg.append("  \"cognition\": [\"认知点\"],\n");
            userMsg.append("  \"interest\": [\"兴趣点\"],\n");
            userMsg.append("  \"benefit\": [\"利益点\"],\n");
            userMsg.append("  \"emotion\": [\"情绪点\"],\n");
            userMsg.append("  \"basic\": [\"基础元素\"],\n");
            userMsg.append("  \"scene\": {\"type\": \"场景父类\", \"value\": \"场景子类\"},\n");
            userMsg.append("  \"character\": {\"type\": \"人物父类\", \"value\": \"人物子类\"},\n");
            userMsg.append("  \"product\": {\"type\": \"产品父类\", \"value\": \"产品子类\"},\n");
            userMsg.append("  \"framework\": \"框架代码\"\n");
            userMsg.append("}\n\n");
            userMsg.append("参数可选值:\n");
            userMsg.append("- language: zh-cn, en, ja, ko, fr, de, es, pt, it, ru, ar 等\n");
            userMsg.append("- race: east-asian, southeast-asian, south-asian, central-asian, caucasian, mediterranean, nordic, slavic, african, african-american, latino, hispanic, middle-eastern, arab, persian, turkish, native-american, pacific-islander, aboriginal, mixed\n");
            userMsg.append("- cognition: hotspot, fresh, refresh, choice\n");
            userMsg.append("- interest: hobby, aesthetic, sensory, novelty, fun\n");
            userMsg.append("- benefit: solve, efficiency, cost, risk\n");
            userMsg.append("- emotion: resonance, identity, relationship, longing\n");
            userMsg.append("- basic: copywriting, editing, color, sound, perspective, action\n");
            userMsg.append("- scene.type: scene-function, scene-mother, scene-state, scene-anchor\n");
            userMsg.append("- scene.value: immersive, aspirational, painpoint, result, identity-scene, proof, contrast, social, daily, work, commute, leisure, nature, circle, professional, future, in-use, problem, task, effect, emotion-scene, comparison, interaction, extreme, space-anchor, identity-anchor, task-anchor, time-anchor, atmosphere-anchor, circle-anchor, tech-anchor\n");
            userMsg.append("- character.type: relation-identity, content-role, job-label, persona, appearance, emotion-state\n");
            userMsg.append("- character.value: buyer, seller, expert, professional, friend, couple, family, experiencer, recommender, explainer, complainer, comparer, prover, questioner, gifter, teacher, nurse, programmer, designer, photographer, coach, student, office, authentic, refined, professional, funny, relaxed, capable, cool, contrast, front-face, half-face, back, hands, pov, multi, voice-only, surprise, speechless, healing, excited, relaxed, angry, moved, satisfied\n");
            userMsg.append("- product.type: product-basic, product-diff\n");
            userMsg.append("- product.value: selling-point, buying-point, trust-point, category, model, size, material, color, competitor-diff, version-diff, cognition-diff, price-diff, craft, texture, review, sales, certification\n");
            userMsg.append("- framework: hand-shake, mirror, desk, immersive-display, novelty-display, tech-unbox, painpoint-unbox, compare-unbox, bestie, family, dorm, violent-test, effect-compare, avoid-pit, painpoint-product, efficiency-tool, micro-drama, joke-selling\n");
            userMsg.append("\n重要: 只返回修改后的完整JSON，不要有任何其他文字！");

            try {
                // 获取对话历史
                List<Map<String, String>> historyMessages = null;
                if (sessionId != null && !sessionId.isEmpty()) {
                    historyMessages = getHistoryForLLM(sessionId, 6);
                }

                String aiResponse = aiProviderService.chatWithHistory(
                        systemPrompt, userMsg.toString(), historyMessages, 0.3, 1500);

                log.info("AI修改参数响应: {}", aiResponse);

                String jsonStr = extractJsonFromResponse(aiResponse);
                if (jsonStr != null) {
                    return parseRecommendations(jsonStr);
                }
            } catch (Exception e) {
                log.warn("AI修改参数失败，返回原始参数: {}", e.getMessage());
            }
            // AI失败时返回原始参数
            return new HashMap<>(currentParams);
    }

    /**
     * 简单的参数修改匹配（优先于AI解析使用）
     * @return Object[] {Map<String,Object> 修改后的参数, Boolean 是否匹配成功}
     */
    @SuppressWarnings("unchecked")
    private Object[] applySimpleParamModification(Map<String, Object> currentParams, String modification) {
        Map<String, Object> newParams = new HashMap<>(currentParams);
        String lower = modification.toLowerCase();
        boolean matched = false;

        // 人种修改
        if (lower.contains("白人") || lower.contains("高加索") || lower.contains("caucasian")) {
            newParams.put("race", "caucasian");
            matched = true;
        } else if (lower.contains("黑人") || lower.contains("非洲") || lower.contains("african")) {
            newParams.put("race", "african");
            matched = true;
        } else if (lower.contains("东亚") || lower.contains("亚洲人") || lower.contains("east-asian")) {
            newParams.put("race", "east-asian");
            matched = true;
        } else if (lower.contains("东南亚") || lower.contains("southeast-asian")) {
            newParams.put("race", "southeast-asian");
            matched = true;
        } else if (lower.contains("南亚") || lower.contains("印度") || lower.contains("south-asian")) {
            newParams.put("race", "south-asian");
            matched = true;
        } else if (lower.contains("拉丁") || lower.contains("latino")) {
            newParams.put("race", "latino");
            matched = true;
        } else if (lower.contains("中东") || lower.contains("middle-eastern")) {
            newParams.put("race", "middle-eastern");
            matched = true;
        } else if (lower.contains("混血") || lower.contains("mixed")) {
            newParams.put("race", "mixed");
            matched = true;
        } else if (lower.contains("阿拉伯") || lower.contains("arab")) {
            newParams.put("race", "arab");
            matched = true;
        } else if (lower.contains("北欧") || lower.contains("nordic")) {
            newParams.put("race", "nordic");
            matched = true;
        } else if (lower.contains("斯拉夫") || lower.contains("slavic")) {
            newParams.put("race", "slavic");
            matched = true;
        } else if (lower.contains("地中海") || lower.contains("mediterranean")) {
            newParams.put("race", "mediterranean");
            matched = true;
        }

        // 语言修改
        if (lower.contains("英文") || lower.contains("英语") || lower.contains("en") || lower.contains("english")) {
            newParams.put("language", "en");
            matched = true;
        } else if (lower.contains("中文") || lower.contains("汉语") || lower.contains("zh") || lower.contains("chinese")) {
            newParams.put("language", "zh-cn");
            matched = true;
        } else if (lower.contains("日文") || lower.contains("日语") || lower.contains("ja") || lower.contains("japanese")) {
            newParams.put("language", "ja");
            matched = true;
        } else if (lower.contains("韩文") || lower.contains("韩语") || lower.contains("ko") || lower.contains("korean")) {
            newParams.put("language", "ko");
            matched = true;
        } else if (lower.contains("法文") || lower.contains("法语") || lower.contains("fr") || lower.contains("french")) {
            newParams.put("language", "fr");
            matched = true;
        } else if (lower.contains("德文") || lower.contains("德语") || lower.contains("de") || lower.contains("german")) {
            newParams.put("language", "de");
            matched = true;
        } else if (lower.contains("西班牙") || lower.contains("es") || lower.contains("spanish")) {
            newParams.put("language", "es");
            matched = true;
        }

        // 框架修改
        if (lower.contains("产品展示") || lower.contains("手摇") || lower.contains("hand-shake")) {
            newParams.put("framework", "hand-shake");
            matched = true;
        } else if (lower.contains("对镜") || lower.contains("mirror")) {
            newParams.put("framework", "mirror");
            matched = true;
        } else if (lower.contains("氛围") || lower.contains("桌拍") || lower.contains("desk")) {
            newParams.put("framework", "desk");
            matched = true;
        } else if (lower.contains("开箱") || lower.contains("tech-unbox")) {
            newParams.put("framework", "tech-unbox");
            matched = true;
        } else if (lower.contains("闺蜜") || lower.contains("分享") || lower.contains("bestie")) {
            newParams.put("framework", "bestie");
            matched = true;
        } else if (lower.contains("对比") || lower.contains("effect-compare")) {
            newParams.put("framework", "effect-compare");
            matched = true;
        } else if (lower.contains("痛点") || lower.contains("避坑") || lower.contains("avoid-pit")) {
            newParams.put("framework", "avoid-pit");
            matched = true;
        } else if (lower.contains("测试") || lower.contains("暴力") || lower.contains("violent-test")) {
            newParams.put("framework", "violent-test");
            matched = true;
        }

        // 场景修改
        if (lower.contains("户外") || lower.contains("户外场景") || lower.contains("自然") || lower.contains("nature")) {
            newParams.put("scene", Map.of("type", "scene-mother", "value", "nature"));
            matched = true;
        } else if (lower.contains("代入") || lower.contains("immersive")) {
            newParams.put("scene", Map.of("type", "scene-function", "value", "immersive"));
            matched = true;
        } else if (lower.contains("日常") || lower.contains("daily")) {
            newParams.put("scene", Map.of("type", "scene-mother", "value", "daily"));
            matched = true;
        } else if (lower.contains("工作") || lower.contains("办公") || lower.contains("work")) {
            newParams.put("scene", Map.of("type", "scene-mother", "value", "work"));
            matched = true;
        } else if (lower.contains("向往") || lower.contains("aspirational")) {
            newParams.put("scene", Map.of("type", "scene-function", "value", "aspirational"));
            matched = true;
        } else if (lower.contains("痛点场景") || lower.contains("painpoint")) {
            newParams.put("scene", Map.of("type", "scene-function", "value", "painpoint"));
            matched = true;
        }

        // 人物修改
        if (lower.contains("体验者") || lower.contains("experiencer")) {
            newParams.put("character", Map.of("type", "content-role", "value", "experiencer"));
            matched = true;
        } else if (lower.contains("推荐") || lower.contains("recommender")) {
            newParams.put("character", Map.of("type", "content-role", "value", "recommender"));
            matched = true;
        } else if (lower.contains("讲解") || lower.contains("explainer")) {
            newParams.put("character", Map.of("type", "content-role", "value", "explainer"));
            matched = true;
        } else if (lower.contains("吐槽") || lower.contains("complainer")) {
            newParams.put("character", Map.of("type", "content-role", "value", "complainer"));
            matched = true;
        } else if (lower.contains("对比者") || lower.contains("comparer")) {
            newParams.put("character", Map.of("type", "content-role", "value", "comparer"));
            matched = true;
        }

        // 认知点修改
        if (lower.contains("热点") || lower.contains("hotspot")) {
            newParams.put("cognition", List.of("hotspot"));
            matched = true;
        } else if (lower.contains("新鲜") || lower.contains("fresh")) {
            newParams.put("cognition", List.of("fresh"));
            matched = true;
        }

        // 兴趣点修改
        if (lower.contains("爱好") || lower.contains("hobby")) {
            newParams.put("interest", List.of("hobby"));
            matched = true;
        } else if (lower.contains("审美") || lower.contains("aesthetic")) {
            newParams.put("interest", List.of("aesthetic"));
            matched = true;
        }

        // 利益点修改
        if (lower.contains("解决问题") || lower.contains("solve")) {
            newParams.put("benefit", List.of("solve"));
            matched = true;
        } else if (lower.contains("效率") || lower.contains("efficiency")) {
            newParams.put("benefit", List.of("efficiency"));
            matched = true;
        }

        // 情绪点修改
        if (lower.contains("共鸣") || lower.contains("resonance")) {
            newParams.put("emotion", List.of("resonance"));
            matched = true;
        } else if (lower.contains("向往") || lower.contains("longing")) {
            newParams.put("emotion", List.of("longing"));
            matched = true;
        }

        return new Object[]{newParams, matched};
    }

    /**
     * 将参数Map转为易读的JSON字符串（用于AI prompt）
     */
    private String paramsToJsonString(Map<String, Object> params) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writerWithDefaultPrettyPrinter().writeValueAsString(params);
        } catch (Exception e) {
            return params.toString();
        }
    }

    /**
     * 获取或创建AI视频会话
     */
    private ChatSession getOrCreateVideoSession(String sessionId, Long userId) {
        if (sessionId != null && !sessionId.isEmpty()) {
            ChatSession existing = sessionService.getSession(sessionId);
            if (existing != null) {
                return existing;
            }
        }
        return sessionService.createSession(userId, "AI视频对话");
    }

    /**
     * 从会话历史中提取LLM可用的对话记录
     */
    private List<Map<String, String>> getHistoryForLLM(String sessionId, int maxMessages) {
        List<ChatMessage> messages = sessionService.getRecentMessagesAsc(sessionId, maxMessages);
        List<Map<String, String>> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            Map<String, String> m = new HashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            result.add(m);
        }
        return result;
    }
    
    /**
     * 从 AI 生成的提示词中提取 imagePrompts 和 videoPrompts
     * 支持 JSON 数组格式: [{"imagePrompt": "...", "videoPrompt": "..."}, ...]
     */
    private Map<String, Object> extractImageAndVideoPrompts(String prompt) {
        if (prompt == null || prompt.isEmpty()) {
            return null;
        }
        
        try {
            // 尝试解析为 JSON 数组
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> scenes;
            
            try {
                scenes = mapper.readValue(prompt, 
                        mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            } catch (Exception e) {
                // 不是 JSON 数组格式，返回 null
                return null;
            }
            
            if (scenes == null || scenes.isEmpty()) {
                return null;
            }
            
            List<String> imagePrompts = new ArrayList<>();
            List<String> videoPrompts = new ArrayList<>();
            
            for (Map<String, Object> scene : scenes) {
                if (scene.containsKey("imagePrompt")) {
                    imagePrompts.add((String) scene.get("imagePrompt"));
                }
                if (scene.containsKey("videoPrompt")) {
                    videoPrompts.add((String) scene.get("videoPrompt"));
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            if (!imagePrompts.isEmpty()) {
                result.put("imagePrompts", imagePrompts);
            }
            if (!videoPrompts.isEmpty()) {
                result.put("videoPrompts", videoPrompts);
            }
            
            return result.isEmpty() ? null : result;
            
        } catch (Exception e) {
            log.warn("提取 imagePrompts 和 videoPrompts 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 简要描述参数（用于会话历史记录）
     */
    private String describeParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "默认参数";
        StringBuilder sb = new StringBuilder();
        String language = extractString(params.get("language"));
        String race = extractString(params.get("race"));
        String framework = extractString(params.get("framework"));
        String sceneValue = extractMapValue(params.get("scene"));
        if (language != null) sb.append("语言=").append(getLanguageDisplayName(language)).append(" ");
        if (race != null) sb.append("人种=").append(getRaceDisplayName(race)).append(" ");
        if (framework != null) sb.append("框架=").append(framework).append(" ");
        if (sceneValue != null) sb.append("场景=").append(sceneValue);
        return sb.length() > 0 ? sb.toString() : "默认参数";
    }

    private String buildVideoPromptWithAI(Map<String, Object> params, List<String> imageUrls,
            String currentPrompt, String modificationRequest, List<Map<String, String>> historyMessages) {
        try {
            // 检查是否开启混剪模式
            boolean isAutoMix = false;
            if (params != null) {
                Object autoMixObj = params.get("autoMix");
                if (autoMixObj instanceof Boolean) {
                    isAutoMix = (Boolean) autoMixObj;
                } else if (autoMixObj instanceof String) {
                    isAutoMix = Boolean.parseBoolean((String) autoMixObj);
                }
            }
            
            // 检查是否包含人物角色 - 使用智能判断逻辑
            boolean hasCharacter = shouldIncludeCharacterBasedOnParams(params);
            
            // 判断是"创建"还是"修改"模式
            boolean isModification = currentPrompt != null && !currentPrompt.isEmpty() 
                    && modificationRequest != null && !modificationRequest.isEmpty();
            
            log.info("生成提示词，混剪模式: {}, 包含人物: {}, 修改模式: {}", isAutoMix, hasCharacter, isModification);

            // 使用模板引擎渲染 system prompt
            String scene = isModification ? "modify" : (isAutoMix ? "mix" : "single");
            Map<String, Object> templateVars = buildTemplateVariables(params, hasCharacter, currentPrompt, modificationRequest);
            String systemPrompt = promptTemplateEngine.render("video_prompt", scene, templateVars);
            
            if (systemPrompt == null) {
                log.error("渲染模板失败，使用备用方案");
                return buildVideoPrompt(params, imageUrls, currentPrompt, modificationRequest);
            }

            // 构建上下文（用户参数 + 知识库 + 图片分析）
            StringBuilder contextBuilder = new StringBuilder();
            
            // 添加知识库上下文
            String knowledgeContext = searchKnowledgeTemplates(params);
            if (knowledgeContext != null && !knowledgeContext.isEmpty()) {
                contextBuilder.append("参考知识库中的成功案例模板:\n");
                contextBuilder.append("以下是与您需求匹配的成功案例，请参考其结构和风格来生成提示词:\n\n");
                contextBuilder.append(knowledgeContext).append("\n\n");
            }

            // 添加用户参数上下文（使用模板引擎渲染）
            String userContext = promptTemplateEngine.render("video_prompt", scene + ".context", templateVars);
            if (userContext != null) {
                contextBuilder.append(userContext);
            }

            // 添加框架服务提供的额外上下文
            String promptContext = videoPromptFrameworkService.buildControllerPrompt(params, imageUrls, currentPrompt, modificationRequest);
            contextBuilder.append(promptContext);

            // 分析产品图片
            String imageAnalysisResult = analyzeProductImage(imageUrls);
            if (imageAnalysisResult != null && !imageAnalysisResult.isEmpty()) {
                contextBuilder.append("\n\n═══ 产品图片分析结果 ═══\n");
                contextBuilder.append(imageAnalysisResult).append("\n");
                contextBuilder.append("═══════════════════════\n\n");
                contextBuilder.append("【重要】请严格根据以上图片分析结果生成提示词，确保描述与产品实际外观完全一致。\n\n");
            }

            // 使用带对话历史的方法调用LLM
            String aiResponse = aiProviderService.chatWithHistory(
                    systemPrompt, contextBuilder.toString(), historyMessages, 0.7, 1500);

            if (aiResponse != null && !aiResponse.isEmpty()) {
                log.info("AI生成提示词成功(含历史, isModification={}), 长度: {}", isModification, aiResponse.length());
                return aiResponse;
            }

            log.warn("AI响应为空，使用备用方案生成提示词");
            return buildVideoPrompt(params, imageUrls, currentPrompt, modificationRequest);

        } catch (Exception e) {
            log.error("AI生成提示词失败，使用备用方案: {}", e.getMessage(), e);
            return buildVideoPrompt(params, imageUrls, currentPrompt, modificationRequest);
        }
    }

    private String searchKnowledgeTemplates(Map<String, Object> params) {
        try {
            StringBuilder searchQuery = new StringBuilder();

            if (params != null) {
                String framework = extractString(params.get("framework"));
                if (framework != null) {
                    searchQuery.append(framework).append(" ");
                }

                String sceneValue = extractMapValue(params.get("scene"));
                if (sceneValue != null) {
                    searchQuery.append(sceneValue).append(" ");
                }

                String productValue = extractMapValue(params.get("product"));
                if (productValue != null) {
                    searchQuery.append(productValue).append(" ");
                }

                List<String> emotion = extractStringList(params.get("emotion"));
                if (emotion != null && !emotion.isEmpty()) {
                    searchQuery.append(String.join(" ", emotion)).append(" ");
                }
            }

            if (searchQuery.length() == 0) {
                searchQuery.append("视频生成 提示词 成功案例");
            }

            log.info("搜索知识库模板，查询: {}", searchQuery.toString());

            List<Knowledge> templates = knowledgeService.searchSimilar(searchQuery.toString(), 3);

            if (templates != null && !templates.isEmpty()) {
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < templates.size(); i++) {
                    Knowledge k = templates.get(i);
                    result.append("案例").append(i + 1).append(":\n");
                    result.append("标题: ").append(k.getTitle() != null ? k.getTitle() : "无标题").append("\n");
                    result.append("内容: ").append(k.getContent() != null ? k.getContent() : "无内容").append("\n");
                    if (k.getTags() != null && !k.getTags().isEmpty()) {
                        result.append("标签: ").append(k.getTags()).append("\n");
                    }
                    result.append("置信度: ").append(k.getConfidence() != null ? k.getConfidence() : 0).append("\n");
                    result.append("---\n\n");
                }
                return result.toString();
            }

        } catch (Exception e) {
            log.warn("搜索知识库模板失败: {}", e.getMessage());
        }
        return null;
    }

    private String buildVideoPrompt(Map<String, Object> params, List<String> imageUrls,
            String currentPrompt, String modificationRequest) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> jsonPrompt = new HashMap<>();
            
            jsonPrompt.put("title", "AI生成视频");
            jsonPrompt.put("duration", 60);
            jsonPrompt.put("aspectRatio", "9:16");
            
            Map<String, Object> style = new HashMap<>();
            style.put("photographyStyle", "原生写实主义/raw realism");
            style.put("visualTone", "纪录感生活影像");
            style.put("colorPalette", "真实色彩还原");
            style.put("lightingQuality", "自然光/轻微阴影");
            style.put("atmosphereKeywords", Arrays.asList("真实", "自然", "生活感"));
            style.put("aestheticPrinciples", Arrays.asList("原生写实主义", "不完美之美"));
            jsonPrompt.put("style", style);
            
            if (params != null) {
                String language = extractString(params.get("language"));
                String race = extractString(params.get("race"));
                List<String> cognition = extractStringList(params.get("cognition"));
                List<String> interest = extractStringList(params.get("interest"));
                List<String> benefit = extractStringList(params.get("benefit"));
                List<String> emotion = extractStringList(params.get("emotion"));
                String sceneValue = extractMapValue(params.get("scene"));
                String characterValue = extractMapValue(params.get("character"));
                String productValue = extractMapValue(params.get("product"));
                String framework = extractString(params.get("framework"));
                
                Map<String, Object> character = new HashMap<>();
                Map<String, Object> demographics = new HashMap<>();
                demographics.put("age", "根据产品定位确定");
                demographics.put("ethnicity", race != null ? race : "根据目标人群确定");
                demographics.put("gender", "根据产品定位确定");
                character.put("demographics", demographics);
                
                Map<String, Object> facialFeatures = new HashMap<>();
                facialFeatures.put("faceShape", "自然脸型");
                facialFeatures.put("facialProportions", "贴合真实人体结构");
                facialFeatures.put("skinTexture", "真实皮肤质感，毛孔清晰可见，不磨皮、不美颜、无滤镜");
                facialFeatures.put("referenceImageFidelity", "高度还原参考图特征");
                character.put("facialFeatures", facialFeatures);
                
                Map<String, Object> hair = new HashMap<>();
                hair.put("style", "自然发型状态");
                hair.put("texture", "真实发丝质感");
                hair.put("arrangement", "自然不做作");
                character.put("hair", hair);
                
                Map<String, Object> expression = new HashMap<>();
                expression.put("mood", emotion != null && !emotion.isEmpty() ? String.join(", ", emotion) : "自然放松");
                expression.put("eyeContact", "自然眼神交流");
                expression.put("naturalness", "放松无刻意管理");
                character.put("expression", expression);
                
                Map<String, Object> clothing = new HashMap<>();
                clothing.put("type", "根据场景确定");
                clothing.put("material", "真实材质");
                clothing.put("fit", "自然贴合");
                clothing.put("style", "与场景匹配");
                character.put("clothing", clothing);
                
                jsonPrompt.put("character", character);
                
                Map<String, Object> scene = new HashMap<>();
                Map<String, Object> space = new HashMap<>();
                space.put("location", sceneValue != null ? sceneValue : "根据产品确定");
                space.put("layout", "自然空间布局");
                space.put("props", Arrays.asList("产品", "相关道具"));
                scene.put("space", space);
                
                Map<String, Object> background = new HashMap<>();
                background.put("wallDetails", "真实生活痕迹");
                background.put("decorations", "自然装饰");
                background.put("lifeTraces", "日常使用痕迹");
                scene.put("background", background);
                
                Map<String, Object> lighting = new HashMap<>();
                lighting.put("source", "自然光源");
                lighting.put("quality", "真实光线质感");
                lighting.put("distribution", "自然分布，允许轻微不均匀");
                lighting.put("shadows", "自然阴影，不追求完美补光");
                scene.put("lighting", lighting);
                
                scene.put("timeOfDay", "根据场景确定");
                scene.put("environment", "真实环境氛围");
                jsonPrompt.put("scene", scene);
                
                Map<String, Object> camera = new HashMap<>();
                Map<String, Object> composition = new HashMap<>();
                composition.put("type", "正面构图/frontal shot");
                composition.put("framing", "近景/特写");
                composition.put("subjectRatio", "主体突出");
                camera.put("composition", composition);
                
                Map<String, Object> position = new HashMap<>();
                position.put("height", "与眼部平齐");
                position.put("angle", "正面角度");
                position.put("distance", "根据景别确定");
                camera.put("position", position);
                
                Map<String, Object> movement = new HashMap<>();
                movement.put("type", framework != null ? getCameraMovementForFramework(framework) : "自然运动");
                movement.put("speed", "适中");
                movement.put("stability", "稳定");
                camera.put("movement", movement);
                
                Map<String, Object> focus = new HashMap<>();
                focus.put("subject", "人物和产品");
                focus.put("backgroundBlur", "轻微虚化");
                focus.put("depthOfField", "突出主体");
                camera.put("focus", focus);
                
                jsonPrompt.put("camera", camera);
                
                List<Map<String, Object>> shots = new ArrayList<>();
                Map<String, Object> shot1 = new HashMap<>();
                shot1.put("sequenceNumber", 1);
                shot1.put("name", "开场镜头");
                shot1.put("timeRange", "00:00-00:15");
                shot1.put("continuity", "建立基础设定");
                shot1.put("openingState", "展示产品或场景，吸引注意力");
                Map<String, Object> action1 = new HashMap<>();
                action1.put("mainAction", "展示产品外观");
                action1.put("gesture", "自然手势");
                action1.put("eyeMovement", "自然眼神");
                action1.put("facialExpression", "放松表情");
                shot1.put("actionSequence", action1);
                shot1.put("cameraChange", "推镜头，从远景到近景");
                shot1.put("moodShift", "引起好奇");
                shots.add(shot1);
                
                Map<String, Object> shot2 = new HashMap<>();
                shot2.put("sequenceNumber", 2);
                shot2.put("name", "主要内容展示");
                shot2.put("timeRange", "00:15-00:45");
                shot2.put("continuity", "延续分镜1设定，连续镜头");
                shot2.put("openingState", sceneValue != null ? sceneValue : "产品功能展示");
                Map<String, Object> action2 = new HashMap<>();
                action2.put("mainAction", benefit != null && !benefit.isEmpty() ? String.join(", ", benefit) : "展示核心功能");
                action2.put("gesture", "产品互动");
                action2.put("eyeMovement", "专注眼神");
                action2.put("facialExpression", "自然表情");
                shot2.put("actionSequence", action2);
                shot2.put("cameraChange", "多角度切换，展示细节");
                shot2.put("moodShift", "建立信任");
                shots.add(shot2);
                
                Map<String, Object> shot3 = new HashMap<>();
                shot3.put("sequenceNumber", 3);
                shot3.put("name", "结尾镜头");
                shot3.put("timeRange", "00:45-01:00");
                shot3.put("continuity", "延续前两分镜，连续镜头");
                shot3.put("openingState", "产品特写，强化卖点");
                Map<String, Object> action3 = new HashMap<>();
                action3.put("mainAction", "总结展示");
                action3.put("gesture", "自然收尾");
                action3.put("eyeMovement", "自信眼神");
                action3.put("facialExpression", "满意表情");
                shot3.put("actionSequence", action3);
                shot3.put("cameraChange", "拉镜头，展示全貌");
                shot3.put("moodShift", "激发购买欲望");
                shots.add(shot3);
                
                jsonPrompt.put("shots", shots);
                
                List<Map<String, Object>> products = new ArrayList<>();
                if (productValue != null) {
                    Map<String, Object> product = new HashMap<>();
                    product.put("name", productValue);
                    product.put("displayMethod", "特写展示");
                    product.put("interaction", "人物与产品自然互动");
                    product.put("focusDetails", benefit != null && !benefit.isEmpty() ? String.join(", ", benefit) : "核心功能");
                    products.add(product);
                }
                jsonPrompt.put("products", products);
                
                Map<String, Object> audio = new HashMap<>();
                Map<String, Object> bgMusic = new HashMap<>();
                bgMusic.put("style", "轻快科技感");
                bgMusic.put("mood", emotion != null && !emotion.isEmpty() ? String.join(", ", emotion) : "积极");
                bgMusic.put("volume", "适中，不抢主体");
                audio.put("backgroundMusic", bgMusic);
                audio.put("ambientSound", "真实环境音");
                audio.put("voiceOver", cognition != null && !cognition.isEmpty() ? String.join(", ", cognition) : "产品解说");
                jsonPrompt.put("audio", audio);
                
                Map<String, Object> constraints = new HashMap<>();
                constraints.put("noText", true);
                constraints.put("noWatermark", true);
                constraints.put("noCommercialFeel", true);
                constraints.put("authenticity", "追求原生写实主义，不磨皮、不美颜、无滤镜");
                constraints.put("prohibitions", Arrays.asList("禁止商业广告感", "禁止摆拍感", "禁止棚拍痕迹"));
                jsonPrompt.put("constraints", constraints);
            }
            
            if (modificationRequest != null && !modificationRequest.isEmpty()) {
                jsonPrompt.put("modificationRequest", modificationRequest);
            }
            
            return mapper.writeValueAsString(jsonPrompt);
        } catch (Exception e) {
            log.error("生成JSON提示词失败", e);
            return "{\"title\":\"AI生成视频\",\"duration\":60,\"aspectRatio\":\"9:16\"}";
        }
    }

    private String getCameraMovementForFramework(String framework) {
        switch (framework) {
            case "hand-shake":
                return "手持摇晃";
            case "mirror":
                return "对镜自拍";
            case "desk":
                return "桌面固定机位";
            case "tech-unbox":
                return "开箱展示运镜";
            case "bestie":
                return "闺蜜分享视角";
            case "effect-compare":
                return "对比切换";
            case "avoid-pit":
                return "避坑指南视角";
            case "violent-test":
                return "暴力测试运镜";
            default:
                return "自然运动镜头";
        }
    }

    private String determineVideoTheme(String scene, String product, String character) {
        if (product != null && product.contains("swimwear")) {
            return "泳装产品展示与功能演示视频";
        }
        if (scene != null && scene.contains("pool")) {
            return "泳池场景下的产品体验与展示视频";
        }
        if (character != null && character.contains("model")) {
            return "模特展示与产品推荐视频";
        }
        return "产品展示与推广视频";
    }

    private String getVideoType(String framework) {
        if (framework == null)
            return "产品展示类";
        switch (framework) {
            case "hand-shake":
                return "握手开场类";
            case "aesthetic-display":
                return "美学展示类";
            case "immersive-display":
                return "沉浸式展示类";
            case "problem-solution":
                return "问题解决类";
            case "story-telling":
                return "故事讲述类";
            default:
                return "产品展示类";
        }
    }

    private String translateCognition(List<String> cognition) {
        Map<String, String> map = new HashMap<>();
        map.put("hotspot", "热点追踪型内容，紧跟时事热点");
        map.put("refresh", "刷新认知型内容，提供新视角");
        map.put("learn", "知识学习型内容，传授实用技能");
        map.put("curiosity", "好奇心驱动型内容，激发探索欲");
        map.put("trend", "趋势洞察型内容，把握行业动向");
        map.put("novelty", "新奇独特型内容，展现创意视角");

        StringBuilder sb = new StringBuilder();
        for (String c : cognition) {
            if (sb.length() > 0)
                sb.append("、");
            sb.append(map.getOrDefault(c, c));
        }
        return sb.toString();
    }

    private String translateInterest(List<String> interest) {
        Map<String, String> map = new HashMap<>();
        map.put("aesthetic", "美学欣赏型内容，注重视觉美感");
        map.put("sensory", "感官体验型内容，强调视听感受");
        map.put("fun", "娱乐搞笑型内容，带来轻松愉悦");
        map.put("hobby", "兴趣爱好型内容，满足特定爱好");
        map.put("social", "社交互动型内容，促进用户参与");
        map.put("novelty", "新奇探索型内容，满足好奇心");

        StringBuilder sb = new StringBuilder();
        for (String i : interest) {
            if (sb.length() > 0)
                sb.append("、");
            sb.append(map.getOrDefault(i, i));
        }
        return sb.toString();
    }

    private String translateBenefit(List<String> benefit) {
        Map<String, String> map = new HashMap<>();
        map.put("efficiency", "提升效率，节省时间成本");
        map.put("solve", "解决问题，提供实用方案");
        map.put("save", "节省资源，降低使用成本");
        map.put("quality", "提升品质，改善使用体验");
        map.put("convenience", "提供便利，简化操作流程");
        map.put("value", "创造价值，带来实际收益");

        StringBuilder sb = new StringBuilder();
        for (String b : benefit) {
            if (sb.length() > 0)
                sb.append("、");
            sb.append(map.getOrDefault(b, b));
        }
        return sb.toString();
    }

    private String translateEmotion(List<String> emotion) {
        Map<String, String> map = new HashMap<>();
        map.put("resonance", "情感共鸣，引发观众情感共振");
        map.put("identity", "身份认同，建立与观众的情感连接");
        map.put("desire", "激发欲望，唤起观众的向往");
        map.put("trust", "建立信任，增强品牌可信度");
        map.put("surprise", "制造惊喜，带来意外体验");
        map.put("relax", "轻松愉悦，营造舒适氛围");

        StringBuilder sb = new StringBuilder();
        for (String e : emotion) {
            if (sb.length() > 0)
                sb.append("、");
            sb.append(map.getOrDefault(e, e));
        }
        return sb.toString();
    }

    private String translateBasic(List<String> basic) {
        Map<String, String> map = new HashMap<>();
        map.put("editing", "专业剪辑，流畅转场");
        map.put("color", "色彩调色，视觉美感");
        map.put("perspective", "镜头视角，创意构图");
        map.put("copywriting", "文案策划，精准表达");
        map.put("music", "音乐配乐，氛围营造");
        map.put("effects", "特效制作，视觉冲击");

        StringBuilder sb = new StringBuilder();
        for (String b : basic) {
            if (sb.length() > 0)
                sb.append("、");
            sb.append(map.getOrDefault(b, b));
        }
        return sb.toString();
    }

    private String buildContentStructure(String framework, String scene, String character, String product) {
        StringBuilder sb = new StringBuilder();

        sb.append("开场(0-3秒):\n");
        sb.append("使用动态镜头语言，通过快速剪辑吸引注意力，展示产品的核心卖点\n");
        sb.append("文案简洁有力，直击用户痛点，引发观众的好奇心和兴趣\n");
        sb.append("音乐节奏明快，配合画面切换，营造紧张感和期待感\n\n");

        sb.append("主体内容(3-25秒):\n");
        if (product != null && product.contains("swimwear")) {
            sb.append("镜头1:展示泳装的整体设计和面料质感，通过模特展示突出产品特点\n");
            sb.append("镜头2:特写泳装的细节设计，如剪裁、装饰等，展现品质感\n");
            sb.append("镜头3:展示泳装在实际使用中的效果，如舒适度、贴合度等\n");
            sb.append("镜头4:展示产品的使用场景，增强用户的代入感和购买欲望\n");
        } else {
            sb.append("镜头1:展示产品的整体外观，突出设计亮点和品质感\n");
            sb.append("镜头2:展示产品的核心功能和使用方法，让用户了解产品价值\n");
            sb.append("镜头3:展示产品的细节特点，如材质、工艺等，增强品质信任\n");
            sb.append("镜头4:展示产品的使用场景和效果，激发用户的购买欲望\n");
        }
        sb.append("\n");

        sb.append("结尾(25-30秒):\n");
        sb.append("使用强有力的结尾镜头，强化产品卖点和品牌形象\n");
        sb.append("文案简洁有力，突出核心卖点和行动号召，引导用户采取下一步行动\n");
        sb.append("音乐达到高潮，配合画面，营造积极向上的氛围\n\n");

        return sb.toString();
    }

    private String buildExecutionTips(String framework, String scene, String product) {
        StringBuilder sb = new StringBuilder();

        sb.append("拍摄技巧:\n");
        sb.append("- 使用稳定器或三脚架确保画面稳定，避免抖动影响观看体验\n");
        sb.append("- 注意光线条件，尽量在自然光充足的环境下拍摄，或使用补光灯\n");
        sb.append("- 多角度拍摄，为后期剪辑提供丰富的素材选择\n\n");

        sb.append("剪辑技巧:\n");
        sb.append("- 保持快节奏剪辑，每2-3秒切换镜头\n");
        sb.append("- 使用转场效果增强视觉流畅性，但不要过度使用\n");
        sb.append("- 视频中禁止出现任何文字、字幕、标题或标签\n\n");

        sb.append("音频处理:\n");
        sb.append("- 选择合适的背景音乐，增强视频氛围\n");
        sb.append("- 确保音频清晰，避免杂音干扰\n");
        sb.append("- 音画同步，确保音乐节奏与画面切换协调\n");

        return sb.toString();
    }

    private String getLanguageDisplayName(String code) {
        Map<String, String> names = new HashMap<>();
        names.put("zh-cn", "简体中文");
        names.put("zh-tw", "繁体中文");
        names.put("en", "英语");
        names.put("ja", "日语");
        names.put("ko", "韩语");
        names.put("fr", "法语");
        names.put("de", "德语");
        names.put("es", "西班牙语");
        names.put("pt", "葡萄牙语");
        names.put("it", "意大利语");
        names.put("ru", "俄语");
        names.put("ar", "阿拉伯语");
        names.put("hi", "印地语");
        names.put("th", "泰语");
        names.put("vi", "越南语");
        return names.getOrDefault(code, code);
    }

    private String getRaceDisplayName(String code) {
        Map<String, String> names = new HashMap<>();
        names.put("east-asian", "东亚人");
        names.put("southeast-asian", "东南亚人");
        names.put("south-asian", "南亚人");
        names.put("central-asian", "中亚人");
        names.put("caucasian", "高加索人种(白种人)");
        names.put("mediterranean", "地中海人种");
        names.put("nordic", "北欧人");
        names.put("slavic", "斯拉夫人");
        names.put("african", "非洲人(撒哈拉以南)");
        names.put("african-american", "非裔美国人");
        names.put("latino", "拉丁裔");
        names.put("hispanic", "西班牙裔");
        names.put("middle-eastern", "中东人");
        names.put("arab", "阿拉伯人");
        names.put("persian", "波斯人");
        names.put("turkish", "土耳其人");
        names.put("native-american", "美洲原住民");
        names.put("pacific-islander", "太平洋岛民");
        names.put("aboriginal", "澳洲原住民");
        names.put("mixed", "混血人种");
        return names.getOrDefault(code, code);
    }
    
    /**
     * 将图片路径转换为base64格式
     */
    private String convertImagePathToBase64(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }
        
        try {
            byte[] imageBytes = null;
            String mimeType = "image/png";
            
            if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                // 从HTTP URL下载
                log.debug("从HTTP URL下载图片: {}", imageUrl);
                java.net.URL url = new java.net.URL(imageUrl);
                try (java.io.InputStream is = url.openStream()) {
                    imageBytes = is.readAllBytes();
                }
                int lastSlash = imageUrl.lastIndexOf('/');
                String fileName = (lastSlash >= 0) ? imageUrl.substring(lastSlash + 1) : "image.jpg";
                mimeType = getMimeTypeFromFileName(fileName);
            } else if (imageUrl.startsWith("/uploads/")) {
                // 本地文件路径
                String localPath = uploadPath + imageUrl.substring("/uploads".length());
                log.debug("读取本地图片文件: {}", localPath);
                java.io.File file = new java.io.File(localPath);
                if (file.exists()) {
                    imageBytes = Files.readAllBytes(file.toPath());
                    mimeType = getMimeTypeFromFileName(file.getName());
                } else {
                    log.warn("图片文件不存在: {}", localPath);
                    return null;
                }
            } else {
                log.warn("不支持的图片路径格式: {}", imageUrl);
                return null;
            }
            
            if (imageBytes != null && imageBytes.length > 0) {
                String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
                log.debug("图片转换为base64成功: {} -> {} bytes", imageUrl, imageBytes.length);
                return "data:" + mimeType + ";base64," + base64;
            }
        } catch (Exception e) {
            log.warn("转换图片为base64失败: {}, 错误: {}", imageUrl, e.getMessage());
        }
        return null;
    }
    
    private String getMimeTypeFromFileName(String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerName.endsWith(".png")) {
            return "image/png";
        } else if (lowerName.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerName.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }
    
    /**
     * 获取异常的错误消息，如果message为null则返回异常类名
     */
    private String getErrorMessage(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    /**
     * 构建模板变量映射
     */
    private Map<String, Object> buildTemplateVariables(Map<String, Object> params, boolean hasCharacter,
            String currentPrompt, String modificationRequest) {
        Map<String, Object> vars = new HashMap<>();
        
        vars.put("hasCharacter", hasCharacter);
        
        if (params != null) {
            String language = extractString(params.get("language"));
            String race = extractString(params.get("race"));
            List<String> cognition = extractStringList(params.get("cognition"));
            List<String> interest = extractStringList(params.get("interest"));
            List<String> benefit = extractStringList(params.get("benefit"));
            List<String> emotion = extractStringList(params.get("emotion"));
            List<String> basic = extractStringList(params.get("basic"));
            String sceneValue = extractMapValue(params.get("scene"));
            String characterValue = extractMapValue(params.get("character"));
            String productValue = extractMapValue(params.get("product"));
            String framework = extractString(params.get("framework"));

            if (language != null) vars.put("language", getLanguageDisplayName(language));
            if (race != null) vars.put("race", getRaceDisplayName(race));
            if (cognition != null && !cognition.isEmpty()) vars.put("cognition", String.join(", ", cognition));
            if (interest != null && !interest.isEmpty()) vars.put("interest", String.join(", ", interest));
            if (benefit != null && !benefit.isEmpty()) vars.put("benefit", String.join(", ", benefit));
            if (emotion != null && !emotion.isEmpty()) vars.put("emotion", String.join(", ", emotion));
            if (basic != null && !basic.isEmpty()) vars.put("basic", String.join(", ", basic));
            if (sceneValue != null) vars.put("scene", sceneValue);
            if (characterValue != null) vars.put("character", characterValue);
            if (productValue != null) vars.put("product", productValue);
            if (framework != null) vars.put("framework", framework);
        }
        
        if (currentPrompt != null) vars.put("currentPrompt", currentPrompt);
        if (modificationRequest != null) vars.put("modificationRequest", modificationRequest);
        
        return vars;
    }

    /**
     * 根据前端参数智能判断是否需要包含人物
     * 与 AiVideoService.shouldIncludeCharacterBasedOnConfig 保持一致
     */
    private boolean shouldIncludeCharacterBasedOnParams(Map<String, Object> params) {
        if (params == null) {
            return false;
        }
        
        String character = extractMapValue(params.get("character"));
        String sceneType = extractMapValue(params.get("sceneType"));
        String scene = extractMapValue(params.get("scene"));
        
        // 1. 检查 character 字段是否包含人物关键词
        if (character != null && !character.isEmpty()) {
            String[] characterKeywords = {
                "模特", "演员", "人物", "主角", "代言人",
                "model", "actor", "actress", "person", "people",
                "女生", "男生", "女性", "男性", "女士", "男士",
                "手模", "展示者"
            };
            
            String lowerChar = character.toLowerCase();
            for (String keyword : characterKeywords) {
                if (lowerChar.contains(keyword.toLowerCase())) {
                    log.info("检测到角色关键词 '{}'，判定需要人物", keyword);
                    return true;
                }
            }
            
            // 检查否定关键词
            String[] negativeKeywords = {"无", "不要", "不需", "没有", "none", "no", "静物", "产品"};
            for (String negKey : negativeKeywords) {
                if (lowerChar.contains(negKey.toLowerCase())) {
                    log.info("检测到否定关键词 '{}'，判定不需要人物", negKey);
                    return false;
                }
            }
        }
        
        // 2. 检查 sceneType 是否为静物/产品展示
        if (sceneType != null && !sceneType.isEmpty()) {
            String[] noCharacterTypes = {
                "静物", "产品展示", "纯产品", "still life", "product only",
                "纯展示", "细节展示"
            };
            String lowerSceneType = sceneType.toLowerCase();
            for (String type : noCharacterTypes) {
                if (lowerSceneType.contains(type.toLowerCase())) {
                    log.info("场景类型 '{}' 明确不需要人物", sceneType);
                    return false;
                }
            }
        }
        
        // 3. 检查 scene 是否包含动作暗示可能需要人物
        if (scene != null && !scene.isEmpty()) {
            String[] actionVerbs = {
                "使用", "操作", "手持", "手拿", "握着", "展示用法", "演示",
                "开箱", "拆箱", "unbox", "holding", "using", "demonstrat"
            };
            String lowerScene = scene.toLowerCase();
            for (String verb : actionVerbs) {
                if (lowerScene.contains(verb.toLowerCase())) {
                    log.info("检测到动作动词 '{}'，可能需要人物操作", verb);
                    return true;
                }
            }
        }
        
        // 4. 默认：如果 character 有值但不是明确否定，保守处理为不需要人物
        if (character != null && !character.isEmpty()) {
            log.info("角色字段有值但无明确人物指示，默认不需要人物: {}", character);
        } else {
            log.info("未检测到人物需求指示，默认不需要人物");
        }
        return false;
    }

    /**
     * 分析产品图片并返回分析结果
     */
    private String analyzeProductImage(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return null;
        }
        
        try {
            log.info("开始分析产品图片以生成提示词，共 {} 张", imageUrls.size());
            String firstImageUrl = imageUrls.get(0);
            boolean isBase64 = firstImageUrl != null && firstImageUrl.length() > 200 && firstImageUrl.startsWith("data:image");
            
            String analysisPrompt = promptTemplateEngine.render("product_analysis", null, null);
            if (analysisPrompt == null) {
                analysisPrompt = "请详细分析这张产品图片，提取以下信息：\n" +
                        "1. 产品类型和名称\n" +
                        "2. 外观特征：颜色、材质、形状、尺寸\n" +
                        "3. 设计细节：图案、纹理、装饰元素\n" +
                        "4. 风格特点：简约/复杂、现代/复古等\n" +
                        "5. 适合的使用场景和人群\n\n" +
                        "请以结构化的方式输出，方便后续用于生成视频提示词。";
            }
            
            // 如果是本地URL，转换为base64
            String imageForAnalysis = isBase64 ? firstImageUrl : convertLocalUrlToBase64(firstImageUrl);
            boolean isBase64ForAnalysis = isBase64 || (imageForAnalysis != null && imageForAnalysis.startsWith("data:image"));
            
            String imageAnalysisResult = aiProviderService.analyzeImageWithFallback(
                    imageForAnalysis != null ? imageForAnalysis : firstImageUrl, 
                    analysisPrompt, 
                    isBase64ForAnalysis);
            
            if (imageAnalysisResult != null && !imageAnalysisResult.isEmpty()) {
                log.info("产品图片分析成功，长度: {}", imageAnalysisResult.length());
                return imageAnalysisResult;
            }
        } catch (Exception e) {
            log.warn("产品图片分析失败: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * 将本地图片URL转换为base64格式
     * 如果URL是外部http/https开头（非localhost）或转换失败，返回null
     */
    private String convertLocalUrlToBase64(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }
        
        // 如果已经是data:image开头，不需要转换
        if (imageUrl.startsWith("data:image")) {
            return null;
        }
        
        // 如果是外部http/https（非localhost/127.0.0.1），不需要转换
        if ((imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) 
                && !imageUrl.contains("localhost") && !imageUrl.contains("127.0.0.1")) {
            return null;
        }
        
        try {
            // 解析本地路径
            String localPath = imageUrl;
            if (imageUrl.startsWith("/uploads/")) {
                localPath = uploadPath + imageUrl.substring(8);
            } else if (imageUrl.startsWith("/api/files/")) {
                localPath = uploadPath + "/" + imageUrl.substring(11);
            } else if (!java.io.File.separator.equals("/") && !imageUrl.startsWith("/")) {
                // Windows系统路径处理
                localPath = uploadPath + "/" + imageUrl;
            } else {
                localPath = uploadPath + "/" + imageUrl;
            }
            
            java.io.File imageFile = new java.io.File(localPath);
            if (!imageFile.exists()) {
                log.warn("本地图片文件不存在: {}", localPath);
                return null;
            }
            
            byte[] imageBytes = java.nio.file.Files.readAllBytes(imageFile.toPath());
            String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
            String mimeType = getMimeType(imageFile.getName());
            
            log.info("本地图片转换为base64成功: {} -> {} bytes", imageUrl, imageBytes.length);
            return "data:" + mimeType + ";base64," + base64;
            
        } catch (Exception e) {
            log.error("本地图片转换为base64失败: {}, error: {}", imageUrl, e.getMessage());
            return null;
        }
    }

    /**
     * 根据文件名获取MIME类型
     */
    private String getMimeType(String fileName) {
        String ext = fileName.toLowerCase();
        if (ext.endsWith(".png")) return "image/png";
        if (ext.endsWith(".gif")) return "image/gif";
        if (ext.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
