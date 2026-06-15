package com.ecommerce.workflow.service.ai;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.workflow.entity.AiProviderConfig;
import com.ecommerce.workflow.entity.AiVideoConfig;
import com.ecommerce.workflow.entity.UnifiedAiConfig;
import com.ecommerce.workflow.entity.VideoTask;
import com.ecommerce.workflow.mapper.UnifiedAiConfigMapper;
import com.ecommerce.workflow.mapper.VideoTaskMapper;
import com.ecommerce.workflow.service.config.SysConfigService;
import com.ecommerce.workflow.service.memory.UnifiedMemoryService;
import com.ecommerce.workflow.service.prompt.PromptTemplateEngine;
import com.ecommerce.workflow.service.video.VideoExpertAnalysisService;
import com.ecommerce.workflow.service.video.VideoKnowledgeEnhancer;
import com.ecommerce.workflow.service.video.VideoAvoidanceChecker;
import com.ecommerce.workflow.service.video.VideoPostReviewService;
import com.ecommerce.workflow.service.video.VideoPromptFrameworkService;
import com.ecommerce.workflow.service.video.VideoPromptTemplateLearningService;
import com.ecommerce.workflow.service.mcp.McpAutoTriggerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AiVideoService {
    private static final Logger log = LoggerFactory.getLogger(AiVideoService.class);

    @Autowired
    private UnifiedAiConfigMapper unifiedConfigMapper;

    @Autowired
    private VideoTaskMapper taskMapper;

    @Autowired
    private AiProviderService aiProviderService;

    @Autowired
    private UnifiedMemoryService unifiedMemoryService;

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VideoExpertAnalysisService videoExpertAnalysisService;

    @Autowired
    private VideoKnowledgeEnhancer videoKnowledgeEnhancer;

    @Autowired
    private VideoAvoidanceChecker videoAvoidanceChecker;

    @Autowired
    private VideoPostReviewService videoPostReviewService;

    @Autowired
    private VideoPromptFrameworkService videoPromptFrameworkService;

    @Autowired
    private VideoPromptTemplateLearningService templateLearningService;

    @Autowired
    private com.ecommerce.workflow.service.learning.LearningCoreService learningCoreService;

    @Autowired
    private com.ecommerce.workflow.service.knowledge.AutoKnowledgeLearningService autoKnowledgeLearningService;

    @Autowired
    private McpAutoTriggerService mcpAutoTriggerService;

    @Autowired
    private com.ecommerce.workflow.service.video.SceneImageGenerationService sceneImageGenerationService;

    @Autowired
    private com.ecommerce.workflow.service.video.McpQualityInspectionService mcpQualityInspectionService;

    @Autowired
    private com.ecommerce.workflow.service.video.VideoMixingService videoMixingService;

    @Autowired
    private com.ecommerce.workflow.service.video.VideoTrackService videoTrackService;

    @Autowired
    private com.ecommerce.workflow.service.media.FfmpegService ffmpegService;

    @Autowired
    private PromptTemplateEngine promptTemplateEngine;

    @Autowired
    @Qualifier("videoGenerationExecutor")
    private Executor videoGenerationExecutor;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private AiVideoService self;

    @Value("${app.upload.path:./uploads}")
    private String uploadPath;

    @Value("${app.server.url:http://localhost:8080}")
    private String serverUrl;

    // 存储图片分析结果，供视频生成时使用
    private final Map<String, Map<String, Object>> imageAnalysisCache = new ConcurrentHashMap<>();

    public AiVideoConfig saveConfig(AiVideoConfig config) {
        UnifiedAiConfig unifiedConfig = convertToUnifiedConfig(config);
        unifiedConfig.setCreatedAt(LocalDateTime.now());
        unifiedConfig.setUpdatedAt(LocalDateTime.now());
        unifiedConfigMapper.insert(unifiedConfig);
        config.setId(unifiedConfig.getId());
        return config;
    }

    public AiVideoConfig getConfig(Long id) {
        UnifiedAiConfig unifiedConfig = unifiedConfigMapper.selectById(id);
        if (unifiedConfig == null) {
            return null;
        }
        return convertToVideoConfig(unifiedConfig);
    }

    public List<AiVideoConfig> listConfigs(Long creatorId) {
        LambdaQueryWrapper<UnifiedAiConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UnifiedAiConfig::getDeleted, 0)
                .eq(UnifiedAiConfig::getProviderType, "VIDEO")
                .orderByDesc(UnifiedAiConfig::getCreatedAt);
        List<UnifiedAiConfig> unifiedConfigs = unifiedConfigMapper.selectList(wrapper);

        List<AiVideoConfig> videoConfigs = new ArrayList<>();
        for (UnifiedAiConfig unified : unifiedConfigs) {
            videoConfigs.add(convertToVideoConfig(unified));
        }
        return videoConfigs;
    }

    private UnifiedAiConfig convertToUnifiedConfig(AiVideoConfig videoConfig) {
        UnifiedAiConfig unified = new UnifiedAiConfig();
        unified.setConfigCode("video-" + System.currentTimeMillis());
        unified.setConfigName(videoConfig.getConfigName());
        unified.setProviderType("VIDEO");
        // 设置必填字段，避免数据库插入失败
        unified.setBaseUrl("https://api.kie.ai");
        unified.setApiKey("placeholder-key"); // 占位符，实际使用时应从配置中获取
        unified.setModels("[\"veo3_lite\"]");
        unified.setDefaultModel("veo3_lite");
        unified.setTaskTypes("[\"VIDEO_GENERATION\"]");
        unified.setRoutingStrategy("balanced");
        unified.setFallbackEnabled(1);
        unified.setMaxFallbackDepth(2);
        unified.setCapabilities("[\"VIDEO_GENERATION\", \"COST_EFFECTIVE\", \"FAST\"]");
        unified.setQualityScore(new java.math.BigDecimal("0.80"));
        unified.setSpeedScore(new java.math.BigDecimal("0.90"));
        unified.setCostRate(new java.math.BigDecimal("0.25"));
        unified.setPriority(1);
        unified.setEnabled(1);
        unified.setDailyQuota(2000);
        unified.setDailyUsed(0);
        unified.setFailCount(0);

        try {
            Map<String, Object> extendedParams = new HashMap<>();
            extendedParams.put("race", videoConfig.getRace());
            extendedParams.put("role", videoConfig.getRole());
            extendedParams.put("topic", videoConfig.getTopic());
            extendedParams.put("sceneType", videoConfig.getSceneType());
            extendedParams.put("scene", videoConfig.getScene());
            extendedParams.put("frameType", videoConfig.getFrameType());
            extendedParams.put("videoName", videoConfig.getVideoName());
            extendedParams.put("aspectRatio", videoConfig.getAspectRatio());
            extendedParams.put("resolution", videoConfig.getResolution());
            extendedParams.put("frameRate", videoConfig.getFrameRate());
            extendedParams.put("duration", videoConfig.getDuration());
            extendedParams.put("styleIntensity", videoConfig.getStyleIntensity());
            extendedParams.put("creativity", videoConfig.getCreativity());
            extendedParams.put("autoMix", videoConfig.getAutoMix());
            extendedParams.put("language", videoConfig.getLanguage());

            if (videoConfig.getExtendedParams() != null && !videoConfig.getExtendedParams().isEmpty()) {
                Map<String, Object> originalExtended = objectMapper.readValue(
                        videoConfig.getExtendedParams(),
                        objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));
                extendedParams.putAll(originalExtended);
            }

            extendedParams.put("imagePrompts", videoConfig.getImagePrompts());
            extendedParams.put("videoPrompts", videoConfig.getVideoPrompts());

            unified.setExtendedParams(objectMapper.writeValueAsString(extendedParams));
        } catch (Exception e) {
            log.warn("转换扩展参数失败: {}", e.getMessage());
            unified.setExtendedParams(videoConfig.getExtendedParams());
        }

        unified.setCreatorId(videoConfig.getCreatorId());
        return unified;
    }

    private AiVideoConfig convertToVideoConfig(UnifiedAiConfig unified) {
        AiVideoConfig videoConfig = new AiVideoConfig();
        videoConfig.setId(unified.getId());
        videoConfig.setConfigName(unified.getConfigName());
        videoConfig.setCreatedAt(unified.getCreatedAt());
        videoConfig.setUpdatedAt(unified.getUpdatedAt());
        videoConfig.setCreatorId(unified.getCreatorId());
        videoConfig.setDeleted(unified.getDeleted());

        try {
            if (unified.getExtendedParams() != null && !unified.getExtendedParams().isEmpty()) {
                Map<String, Object> extendedParams = objectMapper.readValue(
                        unified.getExtendedParams(),
                        objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));

                videoConfig.setRace(extractString(extendedParams.get("race")));
                videoConfig.setRole(extractString(extendedParams.get("role")));
                videoConfig.setTopic(extractString(extendedParams.get("topic")));
                videoConfig.setSceneType(extractString(extendedParams.get("sceneType")));
                videoConfig.setScene(extractString(extendedParams.get("scene")));
                videoConfig.setFrameType(extractString(extendedParams.get("frameType")));
                videoConfig.setVideoName(extractString(extendedParams.get("videoName")));
                videoConfig.setAspectRatio(extractString(extendedParams.getOrDefault("aspectRatio", "9:16")));
                videoConfig.setResolution(extractString(extendedParams.getOrDefault("resolution", "1080p")));

                Object frameRateObj = extendedParams.getOrDefault("frameRate", 30);
                videoConfig.setFrameRate(frameRateObj instanceof Number ? ((Number) frameRateObj).intValue() : 30);

                Object durationObj = extendedParams.getOrDefault("duration", 60);
                videoConfig.setDuration(durationObj instanceof Number ? ((Number) durationObj).intValue() : 60);

                Object styleIntensityObj = extendedParams.getOrDefault("styleIntensity", 70);
                videoConfig.setStyleIntensity(
                        styleIntensityObj instanceof Number ? ((Number) styleIntensityObj).intValue() : 70);

                Object creativityObj = extendedParams.getOrDefault("creativity", 50);
                videoConfig.setCreativity(creativityObj instanceof Number ? ((Number) creativityObj).intValue() : 50);

                Object autoMixObj = extendedParams.get("autoMix");
                videoConfig.setAutoMix(
                        autoMixObj != null ? Boolean.TRUE.equals(autoMixObj) || "true".equals(autoMixObj.toString())
                                : false);

                videoConfig.setLanguage(extractString(extendedParams.get("language")));
                videoConfig.setImagePrompts(extractString(extendedParams.get("imagePrompts")));
                videoConfig.setVideoPrompts(extractString(extendedParams.get("videoPrompts")));

                Map<String, Object> cleanExtended = new HashMap<>(extendedParams);
                cleanExtended.remove("race");
                cleanExtended.remove("role");
                cleanExtended.remove("topic");
                cleanExtended.remove("sceneType");
                cleanExtended.remove("scene");
                cleanExtended.remove("frameType");
                cleanExtended.remove("videoName");
                cleanExtended.remove("aspectRatio");
                cleanExtended.remove("resolution");
                cleanExtended.remove("frameRate");
                cleanExtended.remove("duration");
                cleanExtended.remove("styleIntensity");
                cleanExtended.remove("creativity");
                cleanExtended.remove("autoMix");
                cleanExtended.remove("language");
                cleanExtended.remove("imagePrompts");
                cleanExtended.remove("videoPrompts");

                if (!cleanExtended.isEmpty()) {
                    videoConfig.setExtendedParams(objectMapper.writeValueAsString(cleanExtended));
                }
            }
        } catch (Exception e) {
            log.warn("解析视频配置参数失败: {}", e.getMessage());
        }

        return videoConfig;
    }

    private String extractString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return value.toString();
    }

    /**
     * 获取可用的图片生成模型列表
     */
    public List<String> getAvailableImageModels() {
        return sceneImageGenerationService.getAvailableImageModels();
    }

    /**
     * 获取可用的视频生成模型列表
     */
    public List<String> getAvailableVideoModels() {
        List<AiProviderConfig> providers = aiProviderService.getProvidersByType("VIDEO");
        List<String> models = new ArrayList<>();
        for (AiProviderConfig provider : providers) {
            if (provider.getEnabled() != null && provider.getEnabled() == 1) {
                if (provider.getDefaultModel() != null) {
                    models.add(provider.getDefaultModel());
                }
                if (provider.getModels() != null && !provider.getModels().isEmpty()) {
                    try {
                        List<String> modelList = objectMapper.readValue(provider.getModels(),
                                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                        for (String model : modelList) {
                            if (!models.contains(model)) {
                                models.add(model);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("解析模型列表失败: {}", e.getMessage());
                    }
                }
            }
        }
        return models;
    }

    public VideoTask createTask(AiVideoConfig config, String[] imageUrls, Long creatorId, String imageModel,
            String videoModel) {
        VideoTask task = new VideoTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setConfigId(config.getId().toString());
        task.setStatus("pending");
        task.setProgress(0);
        task.setImageUrls(serializeImageUrls(imageUrls));
        task.setCreatorId(creatorId);
        task.setImageModel(imageModel);
        task.setVideoModel(videoModel);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        taskMapper.insert(task);

        // 异步进行图片专家分析并缓存结果
        if (imageUrls != null && imageUrls.length > 0) {
            self.analyzeAndCacheImagesAsync(task.getTaskId(), imageUrls);
        }

        // 异步处理视频生成任务
        self.processTaskAsyncWithSceneGeneration(task.getTaskId(), config, imageUrls, imageModel, videoModel);

        return task;
    }

    /**
     * 异步分析图片并缓存专家分析结果
     */
    @Async
    public void analyzeAndCacheImagesAsync(String taskId, String[] imageUrls) {
        try {
            log.info("开始分析图片并缓存专家结果: taskId={}, 图片数={}", taskId, imageUrls.length);

            Map<String, Object> combinedAnalysis = new HashMap<>();
            List<String> allImageDescriptions = new ArrayList<>();
            List<String> allExpertAdvice = new ArrayList<>();

            for (int i = 0; i < imageUrls.length; i++) {
                String imageUrl = imageUrls[i];
                if (imageUrl == null || imageUrl.isEmpty()) {
                    continue;
                }

                boolean isBase64 = imageUrl.startsWith("data:image") ||
                        (imageUrl.length() > 200 && !imageUrl.startsWith("http") && !imageUrl.startsWith("/"));

                log.info("分析第{}张图片: isBase64={}", i + 1, isBase64);

                Map<String, Object> analysis = videoExpertAnalysisService.analyzeImageWithExperts(imageUrl, isBase64);

                String basicAnalysis = (String) analysis.get("basicAnalysis");
                String combinedExpertAdvice = (String) analysis.get("combinedExpertAdvice");

                if (basicAnalysis != null && !basicAnalysis.isEmpty()) {
                    allImageDescriptions.add("图片" + (i + 1) + ": " + basicAnalysis);
                }
                if (combinedExpertAdvice != null && !combinedExpertAdvice.isEmpty()) {
                    allExpertAdvice.add("【图片" + (i + 1) + "专家建议】\n" + combinedExpertAdvice);
                }
            }

            combinedAnalysis.put("imageDescriptions", String.join("\n\n", allImageDescriptions));
            combinedAnalysis.put("expertAdvice", String.join("\n\n", allExpertAdvice));
            combinedAnalysis.put("imageCount", imageUrls.length);

            imageAnalysisCache.put(taskId, combinedAnalysis);
            log.info("图片分析结果已缓存: taskId={}, 描述长度={}, 建议长度={}",
                    taskId,
                    combinedAnalysis.get("imageDescriptions").toString().length(),
                    combinedAnalysis.get("expertAdvice").toString().length());

        } catch (Exception e) {
            log.error("图片分析缓存失败: taskId={}", taskId, e);
        }
    }

    private String serializeImageUrls(String[] imageUrls) {
        if (imageUrls == null || imageUrls.length == 0) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(imageUrls);
        } catch (JsonProcessingException e) {
            return String.join(",", imageUrls);
        }
    }

    private String[] deserializeImageUrls(String json) {
        if (json == null || json.isEmpty()) {
            return new String[0];
        }
        try {
            return objectMapper.readValue(json, String[].class);
        } catch (JsonProcessingException e) {
            return json.split(",");
        }
    }

    @Async
    public void processTaskAsyncWithSceneGeneration(String taskId, AiVideoConfig config, String[] imageUrls,
            String imageModel, String videoModel) {
        try {
            VideoTask task = taskMapper.selectOne(
                    new LambdaQueryWrapper<VideoTask>()
                            .eq(VideoTask::getTaskId, taskId));

            if (task == null) {
                log.error("任务不存在: {}", taskId);
                return;
            }

            updateTaskStatus(task, "processing", 5);

            boolean autoMix = config.getAutoMix() != null && config.getAutoMix();
            int sceneCount = autoMix ? 3 : 1;
            task.setSceneCount(sceneCount);
            task.setMixingMode(autoMix ? "auto" : "single");
            taskMapper.updateById(task);

            log.info("【VIDEO-GEN-START】开始视频生成异步任务: taskId={}, 自动混剪={}, 场景数={}", taskId, autoMix, sceneCount);

            Map<String, Object> cachedAnalysis = null;
            boolean hasImages = imageUrls != null && imageUrls.length > 0;

            if (hasImages) {
                int waitAttempts = 0;
                int maxWaitAttempts = 60;

                while (cachedAnalysis == null && waitAttempts < maxWaitAttempts) {
                    cachedAnalysis = imageAnalysisCache.get(taskId);
                    if (cachedAnalysis == null) {
                        if (waitAttempts % 10 == 0) {
                            log.info("等待图片分析完成... taskId={}, 已等待={}秒/{}秒", taskId, waitAttempts, maxWaitAttempts);
                        }
                        Thread.sleep(1000);
                        waitAttempts++;
                    }
                }

                if (cachedAnalysis == null) {
                    log.warn("图片分析超时，使用空分析结果: taskId={}", taskId);
                    cachedAnalysis = new HashMap<>();
                    cachedAnalysis.put("imageDescriptions", "");
                    cachedAnalysis.put("expertAdvice", "");
                    cachedAnalysis.put("imageCount", 0);
                }
            } else {
                cachedAnalysis = new HashMap<>();
                cachedAnalysis.put("imageDescriptions", "");
                cachedAnalysis.put("expertAdvice", "");
                cachedAnalysis.put("imageCount", 0);
            }

            updateTaskStatus(task, "processing", 8);

            // 先处理产品图片URL，供后续使用
            String productImageUrl = null;
            if (imageUrls != null && imageUrls.length > 0) {
                productImageUrl = imageUrls[0];
                if (productImageUrl.startsWith("data:image")) {
                    productImageUrl = saveBase64Image(productImageUrl, taskId);
                } else if (!productImageUrl.startsWith("http")) {
                    productImageUrl = "http://localhost:8080" + productImageUrl;
                }
            }

            // 如果前端没有传入提示词，则根据图片分析自动生成
            String imgPrompts = config.getImagePrompts();
            String vidPrompts = config.getVideoPrompts();
            boolean hasImgPrompts = imgPrompts != null && !imgPrompts.isEmpty() && !"[]".equals(imgPrompts);
            boolean hasVidPrompts = vidPrompts != null && !vidPrompts.isEmpty() && !"[]".equals(vidPrompts);

            log.info("提示词检查: imagePrompts={}, videoPrompts={}, hasImg={}, hasVid={}",
                    imgPrompts, vidPrompts, hasImgPrompts, hasVidPrompts);

            if (!hasImgPrompts && !hasVidPrompts) {
                log.info("前端未传入提示词，开始根据图片分析自动生成场景提示词: taskId={}, 场景数={}, autoMix={}",
                        taskId, sceneCount, config.getAutoMix());
                try {
                    Map<String, String> generatedPrompts = generateScenePromptsWithAi(cachedAnalysis, sceneCount,
                            config, productImageUrl);
                    if (generatedPrompts != null) {
                        String imagePromptsJson = generatedPrompts.get("imagePrompts");
                        String videoPromptsJson = generatedPrompts.get("videoPrompts");
                        if (imagePromptsJson != null && !imagePromptsJson.isEmpty()) {
                            config.setImagePrompts(imagePromptsJson);
                            log.info("自动生成imagePrompts成功: 长度={}", imagePromptsJson.length());
                        }
                        if (videoPromptsJson != null && !videoPromptsJson.isEmpty()) {
                            config.setVideoPrompts(videoPromptsJson);
                            log.info("自动生成videoPrompts成功: 长度={}", videoPromptsJson.length());
                        }
                    }
                } catch (Exception e) {
                    log.error("自动生成场景提示词失败: {}", e.getMessage(), e);
                    // 自动生成失败不阻断流程，后续会使用默认生成逻辑
                }
            } else {
                log.info("使用前端传入的提示词: imagePrompts长度={}, videoPrompts长度={}, autoMix={}",
                        imgPrompts != null ? imgPrompts.length() : 0,
                        vidPrompts != null ? vidPrompts.length() : 0,
                        config.getAutoMix());
                log.info("【注意】由于前端传入了提示词，跳过AI自动生成，无法应用非混剪简化格式！");
            }

            updateTaskStatus(task, "processing", 10);

            List<Map<String, Object>> sceneImages = generateSceneImagesWithQc(
                    taskId, config, cachedAnalysis, sceneCount, productImageUrl, imageModel);

            if (sceneImages == null || sceneImages.isEmpty()) {
                log.warn("场景图生成全部失败，使用产品原图作为降级方案");
                for (int i = 1; i <= sceneCount; i++) {
                    Map<String, Object> fallbackScene = new HashMap<>();
                    fallbackScene.put("imageId", "scene_" + taskId + "_" + i);
                    fallbackScene.put("imageUrl", productImageUrl);
                    fallbackScene.put("imagePrompt", "使用产品原图作为场景" + i);
                    fallbackScene.put("qualityScore", 0.0);
                    fallbackScene.put("model", "fallback");
                    sceneImages.add(fallbackScene);
                }
            } else {
                int failedCount = sceneCount - sceneImages.size();
                if (failedCount > 0) {
                    log.warn("部分场景图生成失败({}个)，使用产品原图降级", failedCount);
                    for (int i = sceneImages.size() + 1; i <= sceneCount; i++) {
                        Map<String, Object> fallbackScene = new HashMap<>();
                        fallbackScene.put("imageId", "scene_" + taskId + "_" + i);
                        fallbackScene.put("imageUrl", productImageUrl);
                        fallbackScene.put("imagePrompt", "使用产品原图作为场景" + i);
                        fallbackScene.put("qualityScore", 0.0);
                        fallbackScene.put("model", "fallback");
                        sceneImages.add(fallbackScene);
                    }
                }
            }

            updateTaskStatus(task, "processing", 40);
            log.info("【VIDEO-GEN-PHASE-1】场景图生成完成: taskId={}, 成功场景数={}", taskId, sceneImages.size());

            List<String> sceneImageUrls = new ArrayList<>();
            List<String> sceneImageIds = new ArrayList<>();
            List<Map<String, Object>> sceneInfoList = new ArrayList<>();

            for (Map<String, Object> scene : sceneImages) {
                String imageUrl = (String) scene.get("imageUrl");
                String imageId = (String) scene.get("imageId");
                String imagePrompt = (String) scene.get("imagePrompt");

                // 如果是base64图片，保存到文件系统并获取URL
                if (imageUrl != null && imageUrl.startsWith("data:image")) {
                    String savedUrl = saveSceneImage(imageUrl, taskId, imageId);
                    if (savedUrl != null) {
                        imageUrl = savedUrl;
                        log.info("场景图base64已保存到文件系统: {}", savedUrl);
                    }
                }

                sceneImageUrls.add(imageUrl);
                sceneImageIds.add(imageId);

                // 保存场景图信息（现在imageUrl是文件路径，不是base64）
                Map<String, Object> sceneInfo = new HashMap<>();
                sceneInfo.put("imageId", imageId);
                sceneInfo.put("imageUrl", imageUrl);
                sceneInfo.put("imagePrompt",
                        imagePrompt != null ? imagePrompt.substring(0, Math.min(100, imagePrompt.length())) : "");
                sceneInfoList.add(sceneInfo);
            }

            // 将场景图信息保存到task中（使用JSON格式）
            try {
                String sceneInfoJson = objectMapper.writeValueAsString(sceneInfoList);
                task.setSceneImageIds(sceneInfoJson);
            } catch (Exception e) {
                log.warn("序列化场景图信息失败: {}", e.getMessage());
                task.setSceneImageIds(serializeImageUrls(sceneImageIds.toArray(new String[0])));
            }

            taskMapper.updateById(task);

            updateTaskStatus(task, "processing", 50);

            log.info("开始并发生成视频: 场景数={}", sceneCount);

            List<CompletableFuture<Map<String, Object>>> videoFutures = new ArrayList<>();
            // 用于跟踪完成的场景数，用于进度更新
            final AtomicInteger completedScenes = new AtomicInteger(0);

            // 使用用户指定的视频模型，如果没有指定则使用null（将使用默认模型）
            final String videoModelToUse = videoModel;

            for (int i = 0; i < sceneImages.size(); i++) {
                final int sceneIndex = i;
                Map<String, Object> scene = sceneImages.get(i);
                String sceneImageUrl = (String) scene.get("imageUrl");
                String sceneImagePrompt = (String) scene.get("imagePrompt");
                // 优先使用 AI 生成的 videoPrompt，如果没有则使用 imagePrompt
                String sceneVideoPrompt = (String) scene.get("videoPrompt");

                // 🔍🔍🔍 关键调试：记录原始提示词信息
                log.info("🎯🎯🎯 场景{}/{} 原始提示词信息:", i + 1, sceneCount);
                log.info("  - sceneImagePrompt: 长度={}, 内容前100字符={}",
                        sceneImagePrompt != null ? sceneImagePrompt.length() : 0,
                        sceneImagePrompt != null && sceneImagePrompt.length() > 100
                                ? sceneImagePrompt.substring(0, 100) + "..."
                                : sceneImagePrompt);
                log.info("  - sceneVideoPrompt: 长度={}, 内容前100字符={}",
                        sceneVideoPrompt != null ? sceneVideoPrompt.length() : 0,
                        sceneVideoPrompt != null && sceneVideoPrompt.length() > 100
                                ? sceneVideoPrompt.substring(0, 100) + "..."
                                : sceneVideoPrompt);
                log.info("  - sceneImageUrl: 长度={}, 是否存在={}",
                        sceneImageUrl != null ? sceneImageUrl.length() : 0,
                        sceneImageUrl != null && !sceneImageUrl.isEmpty());

                String promptToUse = sceneVideoPrompt != null && !sceneVideoPrompt.isEmpty()
                        ? sceneVideoPrompt
                        : sceneImagePrompt;

                log.info("  - 最终选用的promptToUse: 长度={}, 来源={}",
                        promptToUse != null ? promptToUse.length() : 0,
                        sceneVideoPrompt != null && !sceneVideoPrompt.isEmpty() ? "videoPrompt" : "imagePrompt");

                String videoPrompt = buildEnhancedVideoPrompt(config, taskId, promptToUse, i + 1, sceneCount);

                // 🔍 详细日志：记录场景图URL状态（用于排查图片未传递问题）
                boolean hasSceneImage = sceneImageUrl != null && !sceneImageUrl.isEmpty();
                String imagePreview = hasSceneImage
                        ? (sceneImageUrl.length() > 100 ? sceneImageUrl.substring(0, 100) + "..." : sceneImageUrl)
                        : "NULL/空";
                log.info("📸 场景{}/{} 准备生成视频: imagePrompt长度={}, videoPrompt长度={}, 场景图URL={}, 场景图是否存在={}",
                        i + 1, sceneCount,
                        sceneImagePrompt != null ? sceneImagePrompt.length() : 0,
                        sceneVideoPrompt != null ? sceneVideoPrompt.length() : 0,
                        imagePreview,
                        hasSceneImage);
                log.info("构建增强提示词(场景{}/{}): 长度={}, 使用={}", i + 1, sceneCount, videoPrompt.length(),
                        sceneVideoPrompt != null ? "videoPrompt" : "imagePrompt");

                // 🔍🔍🔍 关键调试：记录最终传递给视频生成的参数
                log.info("🎬🎬🎬 场景{}/{} 最终视频生成参数:", i + 1, sceneCount);
                log.info("  - videoPrompt长度: {}", videoPrompt.length());
                log.info("  - videoPrompt前200字符: {}",
                        videoPrompt.length() > 200 ? videoPrompt.substring(0, 200) + "..." : videoPrompt);
                log.info("  - sceneImageUrl: {}",
                        sceneImageUrl != null
                                ? (sceneImageUrl.length() > 50 ? sceneImageUrl.substring(0, 50) + "..." : sceneImageUrl)
                                : "NULL");

                // 必须使用 final 变量才能在 Lambda 中正确捕获
                final String finalVideoPromptForLambda = videoPrompt;
                final String finalSceneImageUrlForLambda = sceneImageUrl;

                CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
                    log.info("开始生成视频(场景{}/{}): 线程={}, 提示词长度={}", sceneIndex + 1, sceneCount,
                            Thread.currentThread().getName(), finalVideoPromptForLambda.length());
                    String videoUrl = generateVideoFromScene(task, config, finalVideoPromptForLambda,
                            finalSceneImageUrlForLambda, sceneIndex + 1, videoModelToUse);
                    Map<String, Object> result = new HashMap<>();
                    result.put("sceneNumber", sceneIndex + 1);
                    result.put("videoUrl", videoUrl);
                    result.put("success", videoUrl != null);

                    // 每完成一个场景，更新进度 (50% ~ 80%)
                    int completed = completedScenes.incrementAndGet();
                    int progress = 50 + (completed * 30 / sceneCount);
                    updateTaskStatus(task, "processing", Math.min(progress, 80));
                    log.info("场景{}/{} 视频生成完成，当前总进度: {}%", sceneIndex + 1, sceneCount, progress);

                    return result;
                }, videoGenerationExecutor);

                videoFutures.add(future);
            }

            CompletableFuture<Void> allVideosFuture = CompletableFuture.allOf(
                    videoFutures.toArray(new CompletableFuture[0]));

            try {
                allVideosFuture.get();
            } catch (Exception e) {
                log.error("等待视频生成完成时发生异常: {}", e.getMessage(), e);
            }

            // 使用Map保存场景编号与视频URL的映射，确保能正确对应
            Map<Integer, String> sceneVideoMap = new HashMap<>();
            List<String> videoUrls = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;
            for (int i = 0; i < videoFutures.size(); i++) {
                try {
                    Map<String, Object> result = videoFutures.get(i).get();
                    int sceneNumber = (Integer) result.get("sceneNumber");
                    String videoUrl = (String) result.get("videoUrl");
                    boolean success = Boolean.TRUE.equals(result.get("success"));

                    if (success && videoUrl != null && !videoUrl.isEmpty()) {
                        sceneVideoMap.put(sceneNumber, videoUrl);
                        videoUrls.add(videoUrl);
                        successCount++;
                        log.info("✅ 场景{}/{} 视频生成成功, URL={}", sceneNumber, sceneCount,
                                videoUrl.substring(0, Math.min(50, videoUrl.length())) + "...");
                    } else {
                        failCount++;
                        log.warn("❌ 场景{}/{} 视频生成失败, success={}, url为空={}",
                                sceneNumber, sceneCount, success, (videoUrl == null || videoUrl.isEmpty()));
                    }
                } catch (Exception e) {
                    failCount++;
                    log.error("❌ 获取场景{}/{} 视频结果失败: {}", i + 1, sceneCount, e.getMessage(), e);
                }
            }
            log.info("📊 视频生成统计: 成功={}, 失败={}, 总计={}", successCount, failCount, videoFutures.size());

            if (videoUrls.isEmpty()) {
                updateTaskStatus(task, "failed", 0);
                task.setErrorMessage("所有场景视频生成失败");
                taskMapper.updateById(task);
                return;
            }

            updateTaskStatus(task, "processing", 85);

            String finalVideoUrl;
            if (autoMix && videoUrls.size() > 1) {
                log.info("开始自动混剪: {} 个视频", videoUrls.size());
                try {
                    // processBatchVideos 内部已处理下载到本地和降级逻辑
                    String mixedVideoPath = videoMixingService.processBatchVideos(videoUrls, true);
                    log.info("自动混剪完成(本地路径): {}", mixedVideoPath);

                    // 将本地路径转换为完整URL，以便前端播放
                    finalVideoUrl = convertLocalPathToUrl(mixedVideoPath);
                    log.info("混剪视频URL转换: {} -> {}", mixedVideoPath, finalVideoUrl);
                    updateTaskStatus(task, "processing", 95);
                } catch (Exception e) {
                    log.error("自动混剪失败: {}", e.getMessage(), e);
                    // 混剪失败，任务标记为失败
                    throw new RuntimeException("视频混剪失败: " + e.getMessage(), e);
                }
            } else {
                // 不混剪，只返回第一个视频
                finalVideoUrl = videoUrls.get(0);
                log.info("不混剪，使用第一个视频: {}", finalVideoUrl);
            }

            task.setVideoUrl(finalVideoUrl);

            try {
                String sceneImageIdsStr = task.getSceneImageIds();
                if (sceneImageIdsStr != null && sceneImageIdsStr.startsWith("[")) {
                    List<Map<String, Object>> existingSceneInfoList = objectMapper.readValue(
                            sceneImageIdsStr,
                            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                            });
                    // 使用sceneVideoMap按场景编号写入视频URL，确保正确对应
                    for (int i = 0; i < existingSceneInfoList.size(); i++) {
                        int sceneNumber = i + 1; // 场景编号从1开始
                        String videoUrl = sceneVideoMap.get(sceneNumber);
                        if (videoUrl != null) {
                            existingSceneInfoList.get(i).put("videoUrl", videoUrl);
                        }
                    }
                    task.setSceneImageIds(objectMapper.writeValueAsString(existingSceneInfoList));
                    log.info("场景视频URL已写入sceneImageIds: {} 个场景视频, 成功视频数: {}",
                            existingSceneInfoList.size(), sceneVideoMap.size());
                }
            } catch (Exception e) {
                log.warn("写入场景视频URL到sceneImageIds失败: {}", e.getMessage());
            }

            updateTaskStatus(task, "completed", 100);
            log.info("视频生成完成: taskId={}, videoUrl={}", task.getTaskId(), finalVideoUrl);

            // 自动进行视频分轨
            try {
                if (finalVideoUrl != null && !finalVideoUrl.isEmpty()) {
                    log.info("开始自动视频分轨: taskId={}, videoUrl={}", taskId, finalVideoUrl);
                    Map<String, Object> trackResult = videoTrackService.separateTracks(finalVideoUrl);
                    if (Boolean.TRUE.equals(trackResult.get("success"))) {
                        log.info("视频分轨成功: taskId={}, tracks={}", taskId, trackResult.get("extractedTracks"));
                    } else {
                        log.warn("视频分轨失败: taskId={}, error={}", taskId, trackResult.get("error"));
                    }
                }
            } catch (Exception e) {
                log.error("视频分轨异常: taskId={}", taskId, e);
            }

            // 异步自动学习：对生成的视频进行打分和入库，不阻塞主流程
            self.autoLearnFromGeneratedVideo(task, config, finalVideoUrl, sceneImages);

            try {
                recordLearning(task, config, true, null);
            } catch (Exception e) {
                log.warn("记录学习结果失败（不影响主流程）: taskId={}, error={}", taskId, e.getMessage());
            }

        } catch (Exception e) {
            log.error("视频生成异常: {}", taskId, e);
            VideoTask task = taskMapper.selectOne(
                    new LambdaQueryWrapper<VideoTask>()
                            .eq(VideoTask::getTaskId, taskId));
            if (task != null) {
                task.setStatus("failed");
                String errorMsg = e.getMessage();
                if (errorMsg == null || errorMsg.isEmpty()) {
                    errorMsg = "视频生成错误: " + e.getClass().getSimpleName();
                }
                task.setErrorMessage(errorMsg);
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateById(task);
            }
        }
    }

    private String saveBase64Image(String base64Data, String taskId) {
        try {
            String base64Image = base64Data.split(",")[1];
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);

            // 使用UUID确保文件名唯一，避免并发冲突
            String fileName = "product_" + taskId + "_" + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
            // 按任务ID分目录存储，实现完全隔离
            Path filePath = Paths.get(uploadPath, "products", taskId, fileName);
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, imageBytes);

            return "/uploads/products/" + taskId + "/" + fileName;
        } catch (Exception e) {
            log.error("保存base64图片失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 保存场景图到文件系统
     * 
     * @param base64Data base64图片数据
     * @param taskId     任务ID
     * @param imageId    图片ID
     * @return 保存后的URL路径
     */
    private String saveSceneImage(String base64Data, String taskId, String imageId) {
        try {
            // 解析base64数据
            String[] parts = base64Data.split(",");
            String mimeType = parts[0].split(":")[1].split(";")[0]; // 例如: image/png
            String base64Image = parts[1];
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);

            // 根据MIME类型确定文件扩展名
            String extension = "png";
            if (mimeType.contains("jpeg") || mimeType.contains("jpg")) {
                extension = "jpg";
            } else if (mimeType.contains("webp")) {
                extension = "webp";
            }

            // 使用UUID确保文件名唯一，避免并发冲突
            String fileName = "scene_" + taskId + "_" + imageId + "_" + UUID.randomUUID().toString().substring(0, 8)
                    + "." + extension;
            // 按任务ID分目录存储，实现完全隔离
            Path filePath = Paths.get(uploadPath, "scenes", taskId, fileName);
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, imageBytes);

            // 返回访问URL
            return "/uploads/scenes/" + taskId + "/" + fileName;
        } catch (Exception e) {
            log.error("保存场景图失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private List<Map<String, Object>> generateSceneImagesWithQc(
            String taskId, AiVideoConfig config, Map<String, Object> imageAnalysis,
            int sceneCount, String productImageUrl, String preferredImageModel) {

        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();

        // 解析 AI 生成的提示词（同时包含 imagePrompt 和 videoPrompt）
        // 注意：imagePrompts 和 videoPrompts 是分开存储的，需要合并解析
        List<ScenePromptPair> aiPromptPairs = parseScenePromptsCombined(
                config.getImagePrompts(), config.getVideoPrompts(), sceneCount);
        boolean useAiPrompts = aiPromptPairs != null && !aiPromptPairs.isEmpty();

        if (useAiPrompts) {
            log.info("使用 AI 生成的场景提示词，共 {} 个（包含 imagePrompt 和 videoPrompt）", aiPromptPairs.size());
        } else {
            log.info("AI 场景提示词为空，使用系统自动生成");
        }

        for (int i = 1; i <= sceneCount; i++) {
            final int sceneNum = i;

            // 优先使用 AI 生成的提示词，否则自动生成
            String imagePrompt;
            String videoPrompt = null;
            if (useAiPrompts && i <= aiPromptPairs.size()) {
                ScenePromptPair pair = aiPromptPairs.get(i - 1);
                String aiImagePrompt = pair.getImagePrompt();
                videoPrompt = pair.getVideoPrompt();
                // 增强 AI 生成的提示词：根据是否有人物追加原生感或场景细节
                imagePrompt = enhanceAiImagePrompt(aiImagePrompt, config, sceneNum, sceneCount, imageAnalysis);
                log.info("场景{} 使用 AI 生成的提示词 - imagePrompt长度: {}, videoPrompt长度: {}",
                        sceneNum,
                        imagePrompt != null ? imagePrompt.length() : 0,
                        videoPrompt != null ? videoPrompt.length() : 0);
            } else {
                imagePrompt = sceneImageGenerationService.buildSceneImagePrompt(
                        config, imageAnalysis, i, sceneCount, new ArrayList<>());
                log.info("场景{} 使用自动生成的 imagePrompt", sceneNum);
            }

            // 保存 videoPrompt 供后续视频生成使用
            final String finalVideoPrompt = videoPrompt;

            final String imageModelToUse = preferredImageModel;
            CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
                Map<String, Object> result = generateSingleSceneWithRetry(taskId, imagePrompt, productImageUrl,
                        sceneNum, 2, imageModelToUse);
                // 在结果中添加 videoPrompt，供视频生成阶段使用
                if (result != null && finalVideoPrompt != null) {
                    result.put("videoPrompt", finalVideoPrompt);
                }
                return result;
            },
                    videoGenerationExecutor);
            futures.add(future);
        }

        List<Map<String, Object>> passedScenes = new ArrayList<>();
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        } catch (Exception e) {
            log.error("等待场景图并行生成完成异常: {}", e.getMessage());
        }

        for (int i = 0; i < futures.size(); i++) {
            try {
                Map<String, Object> result = futures.get(i).get();
                if (result != null) {
                    passedScenes.add(result);
                } else {
                    log.warn("场景{} 生成失败", i + 1);
                }
            } catch (Exception e) {
                log.error("获取场景{} 结果失败: {}", i + 1, e.getMessage());
            }
        }

        log.info("场景图并行生成完成: 成功={}/{}", passedScenes.size(), sceneCount);
        return passedScenes;
    }

    /**
     * 场景提示词对，同时包含 imagePrompt 和 videoPrompt
     */
    public static class ScenePromptPair {
        private final String imagePrompt;
        private final String videoPrompt;

        public ScenePromptPair(String imagePrompt, String videoPrompt) {
            this.imagePrompt = imagePrompt;
            this.videoPrompt = videoPrompt;
        }

        public String getImagePrompt() {
            return imagePrompt;
        }

        public String getVideoPrompt() {
            return videoPrompt;
        }
    }

    /**
     * 解析 AI 生成的提示词 JSON 字符串
     * 支持两种格式：
     * 1. 字符串数组：["prompt1", "prompt2", ...]
     * 2. 对象数组：[{"imagePrompt": "xxx", "videoPrompt": "yyy"}, ...]
     */
    private List<ScenePromptPair> parseScenePrompts(String promptsJson, int expectedCount) {
        if (promptsJson == null || promptsJson.isEmpty()) {
            return null;
        }

        try {
            List<ScenePromptPair> promptPairs = new ArrayList<>();

            // 尝试解析为对象数组（包含 imagePrompt 和 videoPrompt 字段）
            try {
                List<Map<String, Object>> promptObjects = objectMapper.readValue(promptsJson,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

                for (Map<String, Object> obj : promptObjects) {
                    String imagePrompt = null;
                    String videoPrompt = null;

                    if (obj.containsKey("imagePrompt")) {
                        imagePrompt = (String) obj.get("imagePrompt");
                    } else if (obj.containsKey("prompt")) {
                        imagePrompt = (String) obj.get("prompt");
                    }

                    if (obj.containsKey("videoPrompt")) {
                        videoPrompt = (String) obj.get("videoPrompt");
                    }

                    if (imagePrompt != null || videoPrompt != null) {
                        promptPairs.add(new ScenePromptPair(imagePrompt, videoPrompt));
                    }
                }

                if (!promptPairs.isEmpty()) {
                    log.info("解析场景提示词对象数组成功，共 {} 个（包含 imagePrompt 和 videoPrompt）", promptPairs.size());
                    return promptPairs;
                }
            } catch (Exception e) {
                // 不是对象数组格式，尝试字符串数组
            }

            // 尝试解析为字符串数组（兼容旧格式，此时 videoPrompt 为 null）
            try {
                List<String> stringPrompts = objectMapper.readValue(promptsJson,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                for (String prompt : stringPrompts) {
                    promptPairs.add(new ScenePromptPair(prompt, null));
                }
                log.info("解析字符串数组格式成功，共 {} 个提示词（无 videoPrompt）", promptPairs.size());
                return promptPairs;
            } catch (Exception e) {
                // 不是数组格式
            }

            // 如果是单个字符串，包装成列表
            promptPairs.add(new ScenePromptPair(promptsJson, null));
            return promptPairs;

        } catch (Exception e) {
            log.warn("解析场景提示词失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 合并解析 imagePrompts 和 videoPrompts（分开存储的情况）
     * 前端传入的是两个独立的数组：imagePrompts 和 videoPrompts
     */
    private List<ScenePromptPair> parseScenePromptsCombined(String imagePromptsJson, String videoPromptsJson,
            int expectedCount) {
        List<ScenePromptPair> pairs = new ArrayList<>();

        // 解析 imagePrompts
        List<String> imagePrompts = new ArrayList<>();
        if (imagePromptsJson != null && !imagePromptsJson.isEmpty()) {
            try {
                // 尝试解析为数组
                try {
                    List<String> list = objectMapper.readValue(imagePromptsJson,
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                    imagePrompts.addAll(list);
                } catch (Exception e) {
                    // 不是数组，作为单个字符串
                    imagePrompts.add(imagePromptsJson);
                }
            } catch (Exception e) {
                log.warn("解析 imagePrompts 失败: {}", e.getMessage());
            }
        }

        // 解析 videoPrompts
        List<String> videoPrompts = new ArrayList<>();
        if (videoPromptsJson != null && !videoPromptsJson.isEmpty()) {
            try {
                // 尝试解析为数组
                try {
                    List<String> list = objectMapper.readValue(videoPromptsJson,
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                    videoPrompts.addAll(list);
                } catch (Exception e) {
                    // 不是数组，作为单个字符串
                    videoPrompts.add(videoPromptsJson);
                }
            } catch (Exception e) {
                log.warn("解析 videoPrompts 失败: {}", e.getMessage());
            }
        }

        // 合并成 ScenePromptPair
        int maxSize = Math.max(imagePrompts.size(), videoPrompts.size());
        for (int i = 0; i < maxSize; i++) {
            String imagePrompt = i < imagePrompts.size() ? imagePrompts.get(i) : null;
            String videoPrompt = i < videoPrompts.size() ? videoPrompts.get(i) : null;
            if (imagePrompt != null || videoPrompt != null) {
                pairs.add(new ScenePromptPair(imagePrompt, videoPrompt));
            }
        }

        if (!pairs.isEmpty()) {
            log.info("合并解析提示词成功: imagePrompts={}, videoPrompts={}, 合并后={}",
                    imagePrompts.size(), videoPrompts.size(), pairs.size());
        }

        return pairs.isEmpty() ? null : pairs;
    }

    /**
     * @deprecated 使用 parseScenePrompts 替代
     */
    @Deprecated
    private List<String> parseImagePrompts(String imagePromptsJson, int expectedCount) {
        List<ScenePromptPair> pairs = parseScenePrompts(imagePromptsJson, expectedCount);
        if (pairs == null)
            return null;

        List<String> imagePrompts = new ArrayList<>();
        for (ScenePromptPair pair : pairs) {
            if (pair.getImagePrompt() != null) {
                imagePrompts.add(pair.getImagePrompt());
            }
        }
        return imagePrompts;
    }

    /**
     * 增强 AI 生成的 imagePrompt
     * - 在AI提示词基础上，追加完整的原生感场景描述
     * - 包括摄影风格、场景设定、光线色调、构图要求等
     * - 保持AI生成内容的灵活性，同时注入原生感方法论
     * 
     * 直接调用 SceneImageGenerationService 中的方法
     */
    private String enhanceAiImagePrompt(String aiPrompt, AiVideoConfig config, int sceneNumber, int totalScenes,
            Map<String, Object> imageAnalysis) {
        if (aiPrompt == null || aiPrompt.isEmpty()) {
            return aiPrompt;
        }

        StringBuilder enhancedPrompt = new StringBuilder();

        // 第一步：强制添加产品一致性要求（最重要，放在最前面确保不被截断）
        // 从图片分析结果中获取产品描述
        String productDesc = "";
        if (imageAnalysis != null) {
            String imageDescriptions = (String) imageAnalysis.get("imageDescriptions");
            if (imageDescriptions != null && !imageDescriptions.isEmpty()) {
                productDesc = imageDescriptions;
            }
        }
        enhancedPrompt.append(" 【强制还原项 - 必须100%严格匹配原图】");
        enhancedPrompt.append("1. 产品整体外形轮廓、长宽高比例、三维结构、造型弧度，完全与原图一致，禁止任何形变、拉伸、扭曲、透视错位。");
        enhancedPrompt.append("2. 产品外观所有细节：表面纹理、材质质感、颜色色值、图案印花、logo标识、文字内容、按键接口、开孔位置、配件数量、拼接缝隙，必须与原图完全一致。");
        enhancedPrompt.append(" 【绝对禁止项】");
        enhancedPrompt.append("1. 禁止改变产品的外观造型、整体轮廓、尺寸比例。");
        enhancedPrompt.append("2. 禁止增加、删减、移动产品外观的任何细节、配件、结构、图案、标识。");
        enhancedPrompt.append("3. 禁止对产品进行变形、拉伸、扭曲、液化、透视畸变。");
        enhancedPrompt.append("4. 禁止替换产品的材质、颜色、纹理，禁止改变产品的质感表现。");
        enhancedPrompt.append("5. 禁止为产品添加任何原图不存在的装饰、特效、配件、附着物。");
        enhancedPrompt.append("6. 禁止删减、遮挡产品的核心外观结构与关键细节。");
        enhancedPrompt.append(" 【画面质量要求】");
        enhancedPrompt.append("- 产品主体为画面绝对核心，产品外观1:1精准还原为第一优先级。");
        if (!productDesc.isEmpty()) {
            enhancedPrompt.append("- 产品为").append(productDesc).append("，必须严格保持与参考图完全一致。");
        }
        enhancedPrompt.append("- 画面所有其他元素（背景、光影、环境、道具）均不得干扰、改变产品的外观特征。");
        enhancedPrompt.append("- 画面精美，构图专业，光线自然，质感真实。");
        enhancedPrompt.append("- 8K高清，细节丰富。");
        enhancedPrompt.append(
                "- 【物理真实性要求】产品必须放置在稳固的表面上（桌面、柜台、支架等），禁止悬浮、漂浮、悬空或违反重力定律的摆放方式；产品必须自然合理地放置在场景中，有明确可见的支撑物或承托面；禁止出现任何不符合物理规律的画面效果。");

        // 第二步：添加AI生成的核心内容（产品、主题等）
        enhancedPrompt.append(aiPrompt);

        // 第三步：检测是否包含人物
        boolean hasPerson = sceneImageGenerationService.containsPerson(aiPrompt);

        // 第四步：追加摄影风格（写实纪录风）
        enhancedPrompt.append(" ").append(sceneImageGenerationService.buildPhotographyStyle(config));

        // 第五步：追加场景设定（真实生活痕迹、非对称布局）- 这是防止白底图的关键
        enhancedPrompt.append(" ")
                .append(sceneImageGenerationService.buildSceneSetting(config, sceneNumber, totalScenes));

        // 第六步：追加光线色调（自然光、无滤镜）
        enhancedPrompt.append(" ").append(sceneImageGenerationService.buildLightingAndTone(config, sceneNumber));

        // 第七步：追加构图要求（正面构图、亲密距离）
        enhancedPrompt.append(" ")
                .append(sceneImageGenerationService.buildCompositionRequirements(config, sceneNumber));

        // 第八步：根据是否有人物追加不同内容
        if (hasPerson) {
            String nativeStyleDesc = sceneImageGenerationService.buildNativeStyleDescription();
            enhancedPrompt.append(" ").append(nativeStyleDesc);
            String characterNativeStyle = sceneImageGenerationService.buildCharacterNativeStyle(config);
            enhancedPrompt.append(" ").append(characterNativeStyle);
            log.info("场景{} 检测到人物，追加完整原生感描述（含摄影风格、场景、光线、构图、人物细节）", sceneNumber);
        } else {
            enhancedPrompt.append(" 背景带有真实生活痕迹，装饰布局非对称设计，摒弃样板间式的规整感。");
            enhancedPrompt.append("道具状态自然，带有轻微使用痕迹，无刻意整理。");
            log.info("场景{} 未检测到人物，追加场景细节描述", sceneNumber);
        }

        // 第九步：多场景时追加连贯性要求
        if (totalScenes > 1) {
            List<Map<String, Object>> previousScenes = new ArrayList<>();
            String continuity = sceneImageGenerationService.buildContinuityRequirementsCompact(previousScenes,
                    sceneNumber);
            enhancedPrompt.append(" ").append(continuity);
        }

        // 图片提示词不截断，完整保留所有内容
        String fullPrompt = enhancedPrompt.toString();
        log.info("场景{} 提示词构建完成，总长度: {} 字符", sceneNumber, fullPrompt.length());

        return fullPrompt;
    }

    private Map<String, Object> generateSingleSceneWithRetry(
            String taskId, String imagePrompt, String productImageUrl,
            int sceneNumber, int maxRetries, String preferredImageModel) {

        List<String> imageModels = sceneImageGenerationService.getAvailableImageModels();

        if (imageModels.isEmpty()) {
            log.error("没有可用的图片生成模型");
            return null;
        }

        // 如果用户明确指定了模型，优先使用该模型，但失败后会自动fallback到其他模型
        boolean isUserPreferredModel = false;
        if (preferredImageModel != null && !preferredImageModel.isEmpty()) {
            if (imageModels.contains(preferredImageModel)) {
                // 将用户指定的模型移到列表最前面，优先使用
                imageModels.remove(preferredImageModel);
                imageModels.add(0, preferredImageModel);
                isUserPreferredModel = true;
                log.info("用户指定模型: {}, 将优先使用该模型，失败后自动切换", preferredImageModel);
            } else {
                log.warn("用户指定的模型 {} 不在可用列表中，将使用默认模型列表", preferredImageModel);
            }
        }

        log.info("开始生成场景图: 场景{}, 可用模型数量: {}", sceneNumber, imageModels.size());

        for (int modelIndex = 0; modelIndex < imageModels.size(); modelIndex++) {
            String model = imageModels.get(modelIndex);

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    log.info("生成场景图: 场景{}, 模型[{}]: {}, 第{}次尝试",
                            sceneNumber, modelIndex, model, attempt);

                    CompletableFuture<Map<String, Object>> future = sceneImageGenerationService
                            .generateSingleSceneImage(
                                    imagePrompt, imageModels, modelIndex, productImageUrl);
                    Map<String, Object> result = future.get();

                    if (!Boolean.TRUE.equals(result.get("success"))) {
                        log.warn("场景图生成失败: 场景{}, 模型[{}]: {}, 第{}次, 错误={}",
                                sceneNumber, modelIndex, model, attempt, result.get("error"));
                        if (attempt == maxRetries) {
                            if (isUserPreferredModel && modelIndex == 0) {
                                log.warn("用户指定的模型 {} 所有尝试都失败，自动切换到其他模型", model);
                            } else {
                                log.warn("模型[{}]: {} 所有尝试都失败，切换到下一个模型", modelIndex, model);
                            }
                        }
                        continue;
                    }

                    String generatedImageUrl = (String) result.get("imageUrl");
                    log.info("场景图生成成功: 场景{}, 模型[{}]: {}", sceneNumber, modelIndex, model);

                    if (generatedImageUrl != null && !generatedImageUrl.isEmpty() &&
                            !generatedImageUrl.startsWith("data:image")) {
                        try {
                            byte[] imageBytes = null;
                            String mimeType = "image/png";

                            if (generatedImageUrl.startsWith("http://") || generatedImageUrl.startsWith("https://")) {
                                java.net.URL url = new java.net.URL(generatedImageUrl);
                                try (java.io.InputStream is = url.openStream()) {
                                    imageBytes = is.readAllBytes();
                                }
                                mimeType = getMimeType(
                                        generatedImageUrl.substring(generatedImageUrl.lastIndexOf('/') + 1));
                            } else {
                                String localPath = resolveImagePath(generatedImageUrl);
                                File imageFile = new File(localPath);
                                if (imageFile.exists()) {
                                    imageBytes = Files.readAllBytes(imageFile.toPath());
                                    mimeType = getMimeType(imageFile.getName());
                                }
                            }

                            if (imageBytes != null && imageBytes.length > 0) {
                                String base64 = Base64.getEncoder().encodeToString(imageBytes);
                                generatedImageUrl = "data:" + mimeType + ";base64," + base64;
                                log.info("场景图转换为base64成功, 大小: {} bytes", imageBytes.length);
                            }
                        } catch (Exception e) {
                            log.error("场景图转换为base64失败，使用原始URL: {}", e.getMessage());
                        }
                    }

                    Map<String, Object> sceneResult = new HashMap<>();
                    sceneResult.put("imageId", "scene_" + taskId + "_" + sceneNumber);
                    sceneResult.put("imageUrl", generatedImageUrl);
                    sceneResult.put("imagePrompt", imagePrompt);
                    sceneResult.put("qualityScore", 1.0);
                    sceneResult.put("model", model);
                    log.info("场景图生成完成: 场景{}, 使用模型: {}", sceneNumber, model);
                    return sceneResult;

                } catch (Exception e) {
                    log.error("场景图生成异常: 场景{}, 模型[{}]: {}, 第{}次, 错误={}",
                            sceneNumber, modelIndex, model, attempt, e.getMessage());
                    if (attempt == maxRetries) {
                        log.warn("模型[{}]: {} 所有尝试都因异常失败，切换到下一个模型", modelIndex, model);
                    }
                }
            }
        }

        log.error("场景图生成失败，所有模型都已尝试: 场景{}, 模型数量={}", sceneNumber, imageModels.size());

        // 降级策略：如果产品图片URL可用，使用产品原图作为场景图
        if (productImageUrl != null && !productImageUrl.isEmpty()) {
            log.warn("使用产品原图作为场景{}的降级方案", sceneNumber);
            Map<String, Object> fallbackResult = new HashMap<>();
            fallbackResult.put("imageId", "scene_" + taskId + "_" + sceneNumber);
            fallbackResult.put("imageUrl", productImageUrl);
            fallbackResult.put("imagePrompt", "产品原图（AI生成失败降级）");
            fallbackResult.put("qualityScore", 0.5);
            fallbackResult.put("model", "fallback-original-image");
            return fallbackResult;
        }

        return null;
    }

    private String buildVideoPromptWithScene(AiVideoConfig config, String taskId,
            String sceneImagePrompt, int sceneNumber, int totalScenes) {
        try {
            StringBuilder videoPrompt = new StringBuilder();

            if (totalScenes > 1) {
                videoPrompt.append("Scene ").append(sceneNumber).append("/").append(totalScenes).append(": ");
            }

            // 场景图提示词不截断，完整保留
            videoPrompt.append(sceneImagePrompt);

            String frameType = config.getFrameType();
            if (frameType != null && !frameType.isEmpty()) {
                videoPrompt.append(". Camera: ").append(frameType);
            }

            String sceneType = config.getSceneType();
            if (sceneType != null && !sceneType.isEmpty()) {
                videoPrompt.append(". Scene type: ").append(sceneType);
            }

            String fullPrompt = videoPrompt.toString();

            log.info("场景{} 提示词构建完成, 长度={}", sceneNumber, fullPrompt.length());
            return fullPrompt;

        } catch (Exception e) {
            log.error("构建视频提示词失败: {}", e.getMessage(), e);
            return sceneImagePrompt;
        }
    }

    /**
     * 智能判断是否需要在提示词中包含人物
     * 根据前端传入的多维度参数综合判断，而非简单检查 role 是否为空
     * 
     * 判断逻辑：
     * 1. 明确的人物角色：role 包含模特/演员/人物等关键词 → 需要人物
     * 2. 场景暗示：topic/scene 包含使用/操作/开箱等动词 → 可能需要手部
     * 3. 场景类型：sceneType 为静物/产品展示 → 不需要人物
     * 4. 默认规则：无明确指示时，不包含人物（避免AI自行添加）
     */
    private boolean shouldIncludeCharacterBasedOnConfig(AiVideoConfig config) {
        if (config == null) {
            return false;
        }

        String role = config.getRole();
        if (role != null && !role.isEmpty()) {
            String[] characterKeywords = {
                    "模特", "演员", "人物", "主角", "代言人",
                    "model", "actor", "actress", "person", "people",
                    "女生", "男生", "女性", "男性", "女士", "男士",
                    "手模", "展示者"
            };

            String lowerRole = role.toLowerCase();
            for (String keyword : characterKeywords) {
                if (lowerRole.contains(keyword.toLowerCase())) {
                    log.info("检测到角色关键词 '{}' 在 role 字段中，判定需要人物", keyword);
                    return true;
                }
            }

            String[] negativeKeywords = { "无", "不要", "不需", "没有", "none", "no", "静物", "产品" };
            for (String negKey : negativeKeywords) {
                if (lowerRole.contains(negKey.toLowerCase())) {
                    log.info("检测到否定关键词 '{}' 在 role 字段中，判定不需要人物", negKey);
                    return false;
                }
            }
        }

        String topic = config.getTopic();
        String scene = config.getScene();
        String combinedText = (topic != null ? topic : "") + " " + (scene != null ? scene : "");

        if (!combinedText.trim().isEmpty()) {
            String[] actionVerbs = {
                    "使用", "操作", "手持", "手拿", "握着", "展示用法", "演示",
                    "开箱", "拆箱", "unbox", "holding", "using", "demonstrat"
            };

            String lowerCombined = combinedText.toLowerCase();
            for (String verb : actionVerbs) {
                if (lowerCombined.contains(verb.toLowerCase())) {
                    log.info("检测到动作动词 '{}' 在 topic/scene 中，可能需要人物操作", verb);
                    return true;
                }
            }
        }

        String sceneType = config.getSceneType();
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

        log.info("未检测到明确的人物需求指示，默认不包含人物（role={}, topic={}, sceneType={})",
                role, topic, sceneType);
        return false;
    }

    /**
     * 根据图片分析结果自动生成场景提示词(imagePrompts和videoPrompts)
     * 
     * @param imageAnalysis   图片分析结果
     * @param sceneCount      场景数量
     * @param config          视频配置
     * @param productImageUrl 产品图片URL
     * @return 包含imagePrompts和videoPrompts的Map
     */
    private Map<String, String> generateScenePromptsWithAi(Map<String, Object> imageAnalysis,
            int sceneCount,
            AiVideoConfig config,
            String productImageUrl) {
        try {
            log.info("开始调用AI生成场景提示词: 场景数={}, 有产品图片={}", sceneCount, productImageUrl != null);

            // 根据是否混剪决定提示词复杂度
            boolean isAutoMix = config.getAutoMix() != null && config.getAutoMix();
            String scene = isAutoMix ? "mix" : "single";

            // 使用模板引擎渲染提示词
            Map<String, Object> templateVars = new HashMap<>();
            templateVars.put("sceneCount", sceneCount);

            // 智能判断是否需要人物：根据前端传入的多维度参数综合判断
            boolean shouldIncludeCharacter = shouldIncludeCharacterBasedOnConfig(config);
            templateVars.put("hasCharacter", shouldIncludeCharacter);

            log.info("人物判断结果: hasCharacter={}, role={}, topic={}, sceneType={}, scene={}",
                    shouldIncludeCharacter,
                    config.getRole(),
                    config.getTopic(),
                    config.getSceneType(),
                    config.getScene());

            // 添加产品分析信息
            String imageDescriptions = (String) imageAnalysis.get("imageDescriptions");
            String expertAdvice = (String) imageAnalysis.get("expertAdvice");
            if (imageDescriptions != null && !imageDescriptions.isEmpty()) {
                templateVars.put("imageDescriptions", imageDescriptions);
            }
            if (expertAdvice != null && !expertAdvice.isEmpty()) {
                templateVars.put("expertAdvice", expertAdvice);
            }

            // 添加配置信息
            if (config.getTopic() != null && !config.getTopic().isEmpty()) {
                templateVars.put("topic", config.getTopic());
            }
            if (config.getScene() != null && !config.getScene().isEmpty()) {
                templateVars.put("scene", config.getScene());
            }
            if (config.getRole() != null && !config.getRole().isEmpty()) {
                templateVars.put("role", config.getRole());
            }
            if (config.getFrameType() != null && !config.getFrameType().isEmpty()) {
                templateVars.put("frameType", config.getFrameType());
            }
            if (config.getSceneType() != null && !config.getSceneType().isEmpty()) {
                templateVars.put("sceneType", config.getSceneType());
            }

            // 渲染视觉分析提示词
            String visionPrompt = promptTemplateEngine.render("product_analysis", scene, templateVars);
            if (visionPrompt == null) {
                log.error("渲染产品分析模板失败，使用备用方案");
                visionPrompt = buildFallbackVisionPrompt(sceneCount, isAutoMix);
            }

            // 记录实际发送给AI的提示词
            log.info("========== 发送给AI的提示词(模式={}) ==========\n{}\n========== 提示词结束 ==========",
                    isAutoMix ? "混剪" : "非混剪", visionPrompt);

            String aiResponse;

            // 如果有产品图片URL，使用视觉分析API让AI看到图片
            if (productImageUrl != null && !productImageUrl.isEmpty()) {
                log.info("使用视觉分析API生成场景提示词: productImageUrl长度={}", productImageUrl.length());

                boolean isBase64 = productImageUrl.startsWith("data:image");
                String imageUrlForAnalysis = productImageUrl;

                // 如果是本地URL，转换为base64，因为外部AI无法访问localhost
                if (!isBase64 && (productImageUrl.contains("localhost") || productImageUrl.contains("127.0.0.1"))) {
                    log.info("检测到本地图片URL，转换为base64格式以便AI分析");
                    try {
                        String localPath = resolveImagePath(productImageUrl);
                        File imageFile = new File(localPath);
                        if (imageFile.exists()) {
                            byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
                            String base64 = Base64.getEncoder().encodeToString(imageBytes);
                            String mimeType = getMimeType(imageFile.getName());
                            imageUrlForAnalysis = "data:" + mimeType + ";base64," + base64;
                            isBase64 = true;
                            log.info("本地图片转换为base64成功: {} bytes", imageBytes.length);
                        } else {
                            log.warn("本地图片文件不存在: {}", localPath);
                        }
                    } catch (Exception e) {
                        log.error("本地图片转换为base64失败: {}", e.getMessage());
                    }
                }

                aiResponse = aiProviderService.analyzeImageWithFallback(imageUrlForAnalysis, visionPrompt, isBase64);

                log.info("视觉分析API返回: 长度={}", aiResponse != null ? aiResponse.length() : 0);
            } else {
                // 没有图片，使用纯文本方式（降级）
                log.warn("没有产品图片URL，使用纯文本方式生成场景提示词");

                String systemPrompt = "你是一个专业的电商短视频场景设计专家。";
                String userPrompt = visionPrompt + "\n\n请严格按JSON格式输出" + sceneCount + "个场景的设计。";

                aiResponse = aiProviderService.chatWithFallback(systemPrompt, userPrompt, 0.7, 4000);
            }

            if (aiResponse == null || aiResponse.isEmpty()) {
                log.error("AI生成场景提示词返回空");
                return null;
            }

            log.info("AI生成场景提示词成功: 响应长度={}", aiResponse.length());

            // 解析AI响应，传递isAutoMix以便在不混剪模式下检测复杂格式
            return parseAiGeneratedPrompts(aiResponse, sceneCount, isAutoMix);

        } catch (Exception e) {
            log.error("生成场景提示词失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建备用视觉提示词（当模板引擎失败时使用）
     */
    private String buildFallbackVisionPrompt(int sceneCount, boolean isAutoMix) {
        if (isAutoMix) {
            return "请仔细看这张产品图片，理解产品的外观特征、材质、颜色、设计风格等细节。" +
                    "然后设计" + sceneCount + "个不同但连贯的电商短视频场景。\n\n" +
                    "输出格式必须严格按照以下JSON结构：\n" +
                    "{\"scenes\": [{\"sequenceNumber\": 1, \"name\": \"场景名称\", \"imagePrompt\": \"...\", \"videoPrompt\": \"...\"}]}\n\n"
                    +
                    "imagePrompt和videoPrompt必须是纯文本字符串。";
        } else {
            return "你是一个专业的电商短视频提示词生成助手。请仔细看这张产品图片。\n\n" +
                    "设计1个电商短视频场景，只返回最简单的JSON格式：\n" +
                    "{\"scenes\": [{\"sequenceNumber\": 1, \"name\": \"场景名称\", \"imagePrompt\": \"字符串\", \"videoPrompt\": \"字符串\"}]}\n\n"
                    +
                    "禁止返回复杂的video_prompt对象，只返回简单的字符串值。";
        }
    }

    /**
     * 解析AI生成的提示词响应
     * 支持两种格式：
     * 1. scenes数组格式: {"scenes": [{"sequenceNumber": 1, "name": "...",
     * "imagePrompt": "...", "videoPrompt": "..."}]}
     * 2. video_prompt格式: {"video_prompt": {"语言": "...", "场景": "...", ...}}
     */
    private Map<String, String> parseAiGeneratedPrompts(String aiResponse, int expectedCount, boolean isAutoMix) {
        try {
            Map<String, String> result = new HashMap<>();

            // 尝试从响应中提取JSON
            String jsonStr = extractJsonFromResponse(aiResponse);

            // 解析JSON
            Map<String, Object> parsed = objectMapper.readValue(jsonStr,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

            // 首先尝试解析scenes数组格式
            List<Map<String, Object>> scenes = (List<Map<String, Object>>) parsed.get("scenes");
            if (scenes != null && !scenes.isEmpty()) {
                log.info("解析到scenes数组格式: 共{}个场景", scenes.size());
                return parseScenesFormat(scenes);
            }

            // 如果scenes不存在，尝试解析video_prompt格式
            Map<String, Object> videoPromptData = (Map<String, Object>) parsed.get("video_prompt");
            if (videoPromptData != null) {
                if (!isAutoMix) {
                    log.warn("【警告】非混剪模式下AI仍返回了复杂的video_prompt格式！提示词要求未被遵守。");
                    log.warn("原始响应前500字符: {}", aiResponse.substring(0, Math.min(500, aiResponse.length())));
                }
                log.info("检测到video_prompt格式，转换为scenes格式");
                return convertVideoPromptFormat(videoPromptData, expectedCount);
            }

            // 尝试其他可能的格式
            // 有些AI可能直接返回数组
            try {
                List<Map<String, Object>> directScenes = objectMapper.readValue(jsonStr,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                if (directScenes != null && !directScenes.isEmpty()) {
                    return parseScenesFormat(directScenes);
                }
            } catch (Exception ignored) {
            }

            log.error("AI响应中未找到可识别的格式，原始响应前500字符: {}",
                    aiResponse.substring(0, Math.min(500, aiResponse.length())));
            return null;

        } catch (Exception e) {
            log.error("解析AI生成的提示词失败: {}, 原始响应前500字符: {}",
                    e.getMessage(),
                    aiResponse.substring(0, Math.min(500, aiResponse.length())));
            return null;
        }
    }

    /**
     * 从AI响应中提取JSON字符串
     */
    private String extractJsonFromResponse(String aiResponse) {
        String jsonStr = aiResponse;

        // 如果响应包含markdown代码块，提取其中的JSON
        if (aiResponse.contains("```json")) {
            int start = aiResponse.indexOf("```json") + 7;
            int end = aiResponse.indexOf("```", start);
            if (end > start) {
                jsonStr = aiResponse.substring(start, end).trim();
            }
        } else if (aiResponse.contains("```")) {
            int start = aiResponse.indexOf("```") + 3;
            int end = aiResponse.indexOf("```", start);
            if (end > start) {
                jsonStr = aiResponse.substring(start, end).trim();
            }
        }

        return jsonStr;
    }

    /**
     * 解析scenes数组格式
     */
    private Map<String, String> parseScenesFormat(List<Map<String, Object>> scenes) throws Exception {
        Map<String, String> result = new HashMap<>();
        List<String> imagePrompts = new ArrayList<>();
        List<String> videoPrompts = new ArrayList<>();

        for (Map<String, Object> scene : scenes) {
            String imagePrompt = (String) scene.get("imagePrompt");
            String videoPrompt = (String) scene.get("videoPrompt");

            if (imagePrompt != null && !imagePrompt.isEmpty()) {
                imagePrompts.add(imagePrompt);
            }
            if (videoPrompt != null && !videoPrompt.isEmpty()) {
                videoPrompts.add(videoPrompt);
            }
        }

        // 转换为JSON字符串
        String imagePromptsJson = objectMapper.writeValueAsString(imagePrompts);
        String videoPromptsJson = objectMapper.writeValueAsString(videoPrompts);

        result.put("imagePrompts", imagePromptsJson);
        result.put("videoPrompts", videoPromptsJson);

        log.info("解析scenes格式成功: imagePrompts={}, videoPrompts={}",
                imagePrompts.size(), videoPrompts.size());

        return result;
    }

    /**
     * 将video_prompt格式转换为scenes格式
     */
    private Map<String, String> convertVideoPromptFormat(Map<String, Object> videoPromptData, int expectedCount)
            throws Exception {
        Map<String, String> result = new HashMap<>();

        // 从video_prompt中提取信息
        String language = (String) videoPromptData.get("语言");
        String race = (String) videoPromptData.get("种族");
        String scene = (String) videoPromptData.get("场景");
        String frameType = (String) videoPromptData.get("框架");
        String visualStyle = (String) videoPromptData.get("视觉风格");

        Map<String, Object> description = (Map<String, Object>) videoPromptData.get("描述");
        Map<String, Object> opening = description != null ? (Map<String, Object>) description.get("开头") : null;
        Map<String, Object> features = description != null ? (Map<String, Object>) description.get("主要特性") : null;
        Map<String, Object> product = features != null ? (Map<String, Object>) features.get("产品") : null;
        Map<String, Object> environment = features != null ? (Map<String, Object>) features.get("环境") : null;
        Map<String, Object> cameraDesign = (Map<String, Object>) videoPromptData.get("镜头设计");
        Map<String, Object> actionSequence = (Map<String, Object>) videoPromptData.get("动作序列");

        // 构建imagePrompt - 基于场景描述
        StringBuilder imagePromptBuilder = new StringBuilder();
        if (opening != null) {
            String sceneDesc = (String) opening.get("场景");
            String lighting = (String) opening.get("光线");
            String photoStyle = (String) opening.get("摄影风格");

            if (sceneDesc != null)
                imagePromptBuilder.append(sceneDesc).append("，");
            if (lighting != null)
                imagePromptBuilder.append(lighting).append("，");
            if (photoStyle != null)
                imagePromptBuilder.append(photoStyle);
        }

        // 添加产品信息
        if (product != null) {
            String productType = (String) product.get("类型");
            String productDesign = (String) product.get("设计");
            String material = (String) product.get("材质");
            String color = (String) product.get("颜色");
            String details = (String) product.get("细节");

            if (productType != null)
                imagePromptBuilder.append("，产品：").append(productType);
            if (productDesign != null)
                imagePromptBuilder.append("，").append(productDesign);
            if (material != null)
                imagePromptBuilder.append("，材质：").append(material);
            if (color != null)
                imagePromptBuilder.append("，颜色：").append(color);
        }

        // 构建videoPrompt
        StringBuilder videoPromptBuilder = new StringBuilder();
        if (language != null)
            videoPromptBuilder.append("语言：").append(language).append("，");
        if (race != null)
            videoPromptBuilder.append("人物种族：").append(race).append("，");
        if (scene != null)
            videoPromptBuilder.append("场景：").append(scene).append("，");
        if (visualStyle != null)
            videoPromptBuilder.append("风格：").append(visualStyle).append("，");

        // 添加描述信息
        if (opening != null) {
            String sceneDesc = (String) opening.get("场景");
            if (sceneDesc != null)
                videoPromptBuilder.append("场景描述：").append(sceneDesc).append("，");
        }

        // 添加镜头设计
        if (cameraDesign != null) {
            String angle = (String) cameraDesign.get("角度");
            String motion = (String) cameraDesign.get("运动");
            String focus = (String) cameraDesign.get("焦点");

            if (angle != null)
                videoPromptBuilder.append("镜头角度：").append(angle).append("，");
            if (motion != null)
                videoPromptBuilder.append("镜头运动：").append(motion).append("，");
            if (focus != null)
                videoPromptBuilder.append("焦点：").append(focus).append("，");
        }

        // 添加动作序列
        if (actionSequence != null) {
            String introduction = (String) actionSequence.get("介绍");
            String interaction = (String) actionSequence.get("互动");
            String display = (String) actionSequence.get("展示");
            String ending = (String) actionSequence.get("结尾");

            videoPromptBuilder.append("动作序列：");
            if (introduction != null)
                videoPromptBuilder.append("介绍-").append(introduction).append("，");
            if (interaction != null)
                videoPromptBuilder.append("互动-").append(interaction).append("，");
            if (display != null)
                videoPromptBuilder.append("展示-").append(display).append("，");
            if (ending != null)
                videoPromptBuilder.append("结尾-").append(ending);
        }

        String imagePrompt = imagePromptBuilder.toString();
        String videoPrompt = videoPromptBuilder.toString();

        // 生成expectedCount个场景（目前只生成1个，如果有多个需求需要扩展）
        List<String> imagePrompts = new ArrayList<>();
        List<String> videoPrompts = new ArrayList<>();

        for (int i = 0; i < expectedCount; i++) {
            imagePrompts.add(imagePrompt);
            videoPrompts.add(videoPrompt + "（场景" + (i + 1) + "/" + expectedCount + "）");
        }

        // 转换为JSON字符串
        String imagePromptsJson = objectMapper.writeValueAsString(imagePrompts);
        String videoPromptsJson = objectMapper.writeValueAsString(videoPrompts);

        result.put("imagePrompts", imagePromptsJson);
        result.put("videoPrompts", videoPromptsJson);

        log.info("转换video_prompt格式成功: 生成{}个场景", expectedCount);

        return result;
    }

    private String buildEnhancedVideoPrompt(AiVideoConfig config, String taskId,
            String sceneImagePrompt, int sceneNumber, int totalScenes) {
        try {
            StringBuilder videoPrompt = new StringBuilder();

            // ========== 学习闭环：读取学习结果 ==========
            log.info("场景{} 开始学习闭环数据读取...", sceneNumber);

            // 1. 读取爆款规律
            String explosiveRules = "";
            try {
                explosiveRules = videoKnowledgeEnhancer.getExplosiveRulesForConfig(config);
                if (!explosiveRules.isEmpty()) {
                    log.info("场景{} 成功读取爆款规律: {} 字符", sceneNumber, explosiveRules.length());
                }
            } catch (Exception e) {
                log.warn("场景{} 读取爆款规律失败: {}", sceneNumber, e.getMessage());
            }

            // 2. 读取避坑规则
            String avoidanceRules = "";
            try {
                avoidanceRules = videoKnowledgeEnhancer.getAvoidanceRulesForConfig(config);
                if (!avoidanceRules.isEmpty()) {
                    log.info("场景{} 成功读取避坑规则: {} 字符", sceneNumber, avoidanceRules.length());
                }
            } catch (Exception e) {
                log.warn("场景{} 读取避坑规则失败: {}", sceneNumber, e.getMessage());
            }

            // 3. 读取知识库案例
            String knowledgeCases = "";
            try {
                knowledgeCases = videoKnowledgeEnhancer.getKnowledgeCasesForConfig(config);
                if (!knowledgeCases.isEmpty()) {
                    log.info("场景{} 成功读取知识库案例: {} 字符", sceneNumber, knowledgeCases.length());
                }
            } catch (Exception e) {
                log.warn("场景{} 读取知识库案例失败: {}", sceneNumber, e.getMessage());
            }

            // 4. 读取Q-Table优化参数
            Map<String, Object> optimizedParams = new HashMap<>();
            try {
                optimizedParams = videoKnowledgeEnhancer.getOptimizedParamsFromQTable(config);
                if (optimizedParams.containsKey("hasOptimization")
                        && (Boolean) optimizedParams.get("hasOptimization")) {
                    log.info("场景{} 成功读取Q-Table优化参数", sceneNumber);
                }
            } catch (Exception e) {
                log.warn("场景{} 读取Q-Table优化参数失败: {}", sceneNumber, e.getMessage());
            }

            // ========== 构建基础提示词 ==========
            if (totalScenes > 1) {
                videoPrompt.append("Scene ").append(sceneNumber).append("/").append(totalScenes).append(": ");
            }

            String topic = config.getTopic() != null ? config.getTopic() : "product showcase";
            String scene = config.getScene() != null ? config.getScene() : "";
            String sceneType = config.getSceneType() != null ? config.getSceneType() : "";
            String frameType = config.getFrameType() != null ? config.getFrameType() : "";
            String role = config.getRole() != null ? config.getRole() : "";

            String sceneDesc = !scene.isEmpty() ? scene : (!sceneType.isEmpty() ? sceneType : "自然场景");

            videoPrompt.append("一个").append(topic).append("在").append(sceneDesc).append("中。");

            boolean hasPerson = sceneImagePrompt != null
                    && sceneImageGenerationService.containsPerson(sceneImagePrompt);

            if (hasPerson) {
                // 复用 SceneImageGenerationService 的人物描述（中文）
                String characterDesc = sceneImageGenerationService.getCharacterDescriptionForVideo(config, null);
                videoPrompt.append(characterDesc);
            }

            // 从缓存获取产品详细描述
            String productDetailDesc = "";
            Map<String, Object> cachedAnalysis = imageAnalysisCache.get(taskId);
            if (cachedAnalysis != null) {
                String imageDescriptions = (String) cachedAnalysis.get("imageDescriptions");
                if (imageDescriptions != null && !imageDescriptions.isEmpty()) {
                    productDetailDesc = imageDescriptions;
                }
            }

            // 核心约束：物理真实性和产品一致性（不限制具体动作，只限制违反物理的行为）
            videoPrompt.append("【产品详细描述 - 视频生成必须严格遵循 - 像理解人物一样理解产品】");
            if (!productDetailDesc.isEmpty()) {
                videoPrompt.append(productDetailDesc).append("。");
            }
            videoPrompt.append("【产品三维结构理解 - 防止两面一样】");
            videoPrompt.append("1. 产品是一个三维立体物体，有正面、背面、侧面、顶面、底面，不同面有不同的视觉特征。");
            videoPrompt.append("2. 当前画面展示的是产品的哪个面（正面/背面/侧面），必须严格保持这个面的特征。");
            videoPrompt.append("3. 禁止将产品正面和背面生成成一样的外观，产品不同面必须有明显区别。");
            videoPrompt.append("4. 如果首帧图显示的是产品正面，视频必须始终保持展示正面特征，不能变成背面。");
            videoPrompt.append("5. 产品在画面中的朝向、角度、透视关系必须与首帧图完全一致。");
            videoPrompt.append("【强制还原项 - 必须100%严格匹配首帧图】");
            videoPrompt.append("1. 产品整体外形轮廓、长宽高比例、三维结构、造型弧度，完全与首帧图一致，禁止任何形变、拉伸、扭曲、透视错位。");
            videoPrompt.append("2. 产品外观所有细节：表面纹理、材质质感、颜色色值、图案印花、logo标识、文字内容、按键接口、开孔位置、配件数量、拼接缝隙，必须与首帧图完全一致。");
            videoPrompt.append("3. 产品当前展示的面（正面/背面/侧面）必须与首帧图保持一致，禁止切换到其他面。");
            videoPrompt.append(" 【绝对禁止项】");
            videoPrompt.append("1. 禁止改变产品的外观造型、整体轮廓、尺寸比例。");
            videoPrompt.append("2. 禁止增加、删减、移动产品外观的任何细节、配件、结构、图案、标识。");
            videoPrompt.append("3. 禁止对产品进行变形、拉伸、扭曲、液化、透视畸变。");
            videoPrompt.append("4. 禁止替换产品的材质、颜色、纹理，禁止改变产品的质感表现。");
            videoPrompt.append("5. 禁止为产品添加任何首帧图不存在的装饰、特效、配件、附着物。");
            videoPrompt.append("6. 禁止删减、遮挡产品的核心外观结构与关键细节。");
            videoPrompt.append("7. 禁止将产品正面和背面生成成相同外观，产品不同面必须有明显视觉差异。");
            videoPrompt.append("8. 禁止在视频中突然改变产品的展示面（如从正面突然变成背面）。");
            videoPrompt.append(" 【物理真实性要求】");
            videoPrompt.append("1. 所有物体必须遵循重力定律，禁止悬浮、漂浮、悬空或违反物理规律的运动。");
            videoPrompt.append("2. 产品必须有稳固的支撑面，与接触面有真实的物理接触和压力表现。");
            videoPrompt.append("3. 产品必须自然合理地放置在场景中，有明确可见的支撑物或承托面。");
            videoPrompt.append("4. 产品摆放姿态必须合理：必须平放或自然倾斜靠在稳固支撑物上，禁止直立站立或竖直悬浮。");
            videoPrompt.append("5. 产品重心必须稳定，符合重力定律，不存在倾倒风险，必须有可见的支撑面（桌面、支架、靠垫等）承托。");
            videoPrompt.append("6. 人物与产品的互动必须自然真实，符合日常物理规律，禁止超自然或脱离物理的动作。");
            videoPrompt.append("7. 禁止出现任何不符合物理规律的画面效果。");
            videoPrompt.append(" 【画面质量要求】");
            videoPrompt.append("- 产品主体为画面绝对核心，产品外观1:1精准还原为第一优先级。");
            videoPrompt.append("- 画面所有其他元素（背景、光影、环境、道具）均不得干扰、改变产品的外观特征。");
            videoPrompt.append("- 产品在整个视频中必须保持完全一致的外观，禁止帧间变形。");

            if (!frameType.isEmpty()) {
                videoPrompt.append("镜头：").append(frameType).append("，缓慢平滑移动。");
            }

            videoPrompt.append("风格：纪录片写实，原始素材感，无滤镜，无调色，真实日常生活质感。");

            // ========== 学习闭环：将学习结果融入提示词 ==========
            StringBuilder learningSection = new StringBuilder();
            boolean hasLearningContent = false;

            // 1. 融入爆款规律
            if (!explosiveRules.isEmpty()) {
                learningSection.append("\n\n【学习闭环 - 爆款规律参考】\n");
                learningSection.append(explosiveRules);
                hasLearningContent = true;
                log.info("场景{} 已将爆款规律融入提示词", sceneNumber);
            }

            // 2. 融入避坑规则
            if (!avoidanceRules.isEmpty()) {
                learningSection.append("\n\n【学习闭环 - 避坑规则警告】\n");
                learningSection.append(avoidanceRules);
                hasLearningContent = true;
                log.info("场景{} 已将避坑规则融入提示词", sceneNumber);
            }

            // 3. 融入知识库案例
            if (!knowledgeCases.isEmpty()) {
                learningSection.append("\n\n【学习闭环 - 知识库相似案例】\n");
                learningSection.append(knowledgeCases);
                hasLearningContent = true;
                log.info("场景{} 已将知识库案例融入提示词", sceneNumber);
            }

            // 4. 融入Q-Table优化建议
            if (optimizedParams.containsKey("hasOptimization") && (Boolean) optimizedParams.get("hasOptimization")) {
                learningSection.append("\n\n【学习闭环 - Q-Table优化建议】\n");
                if (optimizedParams.containsKey("qTableOptimized")) {
                    Map<String, Double> bestParams = (Map<String, Double>) optimizedParams.get("qTableOptimized");
                    learningSection.append("基于历史学习的最优参数配置：\n");
                    for (Map.Entry<String, Double> entry : bestParams.entrySet()) {
                        learningSection.append("- ").append(entry.getKey()).append(" : 置信度 ")
                                .append(String.format("%.2f", entry.getValue() * 100)).append("%\n");
                    }
                }
                hasLearningContent = true;
                log.info("场景{} 已将Q-Table优化建议融入提示词", sceneNumber);
            }

            // 将学习部分添加到提示词末尾
            if (hasLearningContent) {
                videoPrompt.append(learningSection.toString());
                log.info("场景{} 学习闭环数据融入完成，共融入 {} 字符学习数据",
                        sceneNumber, learningSection.length());
            } else {
                log.info("场景{} 没有可用的学习数据可供融入", sceneNumber);
            }

            String fullPrompt = videoPrompt.toString();
            // 视频提示词不截断，完整保留所有内容
            log.info("场景{} 增强提示词构建完成(含学习闭环), 长度={}, 有人物={}, 有学习数据={}",
                    sceneNumber, fullPrompt.length(), hasPerson, hasLearningContent);
            log.info("========== 场景{} 视频生成完整提示词(含学习闭环) ==========\n{}\n========== 提示词结束 ==========", sceneNumber,
                    fullPrompt);
            return fullPrompt;

        } catch (Exception e) {
            log.error("构建增强提示词失败: {}", e.getMessage(), e);
            return buildVideoPromptWithScene(config, taskId, sceneImagePrompt, sceneNumber, totalScenes);
        }
    }

    private String optimizePromptLength(String prompt, int maxLength) {
        StringBuilder optimized = new StringBuilder();

        // 提取场景描述部分
        int sceneDescStart = prompt.indexOf("场景描述：");
        if (sceneDescStart >= 0) {
            int sceneDescEnd = prompt.indexOf("\n\n", sceneDescStart);
            if (sceneDescEnd < 0) {
                sceneDescEnd = prompt.length();
            }
            String sceneDesc = prompt.substring(sceneDescStart, sceneDescEnd).trim();
            optimized.append(sceneDesc).append("\n\n");
        }

        // 提取视频生成要求部分
        int videoReqStart = prompt.indexOf("视频生成要求：");
        if (videoReqStart >= 0) {
            // 找到下一个大段落标记（如专家建议、知识库案例等）
            int nextSection = prompt.length();
            String[] sectionMarkers = { "═══ 图片专家建议 ═══", "═══ 参考图片内容分析 ═══", "知识库案例", "knowledgeCases" };
            for (String marker : sectionMarkers) {
                int markerPos = prompt.indexOf(marker, videoReqStart + 10);
                if (markerPos > 0 && markerPos < nextSection) {
                    nextSection = markerPos;
                }
            }

            String videoReq = prompt.substring(videoReqStart, nextSection).trim();
            // 清理Markdown符号和编号
            videoReq = videoReq.replaceAll("[#*`]", "").trim();

            if (optimized.length() + videoReq.length() + 10 < maxLength) {
                optimized.append(videoReq).append("\n");
            } else {
                // 如果还是太长，只取前面部分
                int remaining = maxLength - optimized.length() - 10;
                if (remaining > 50) {
                    optimized.append(videoReq.substring(0, Math.min(remaining, videoReq.length()))).append("\n");
                }
            }
        }

        // 如果提取的内容太少，尝试从原始提示词中提取更多有用信息
        if (optimized.length() < 100) {
            // 简单截取前maxLength字符
            if (prompt.length() > maxLength) {
                optimized = new StringBuilder(prompt.substring(0, maxLength));
            } else {
                optimized = new StringBuilder(prompt);
            }
        }

        return optimized.toString().trim();
    }

    private String generateVideoFromScene(VideoTask task, AiVideoConfig config,
            String videoPrompt, String sceneImageUrl, int sceneNumber, String preferredVideoModel) {
        try {
            List<AiProviderConfig> providers = aiProviderService.getProvidersByType("VIDEO");
            if (providers.isEmpty()) {
                throw new RuntimeException("没有可用的视频生成服务提供商");
            }

            // 如果用户指定了优先模型，调整提供商列表顺序
            if (preferredVideoModel != null && !preferredVideoModel.isEmpty()) {
                Optional<AiProviderConfig> preferredProvider = providers.stream()
                        .filter(p -> preferredVideoModel.equals(p.getDefaultModel()) ||
                                (p.getModels() != null && p.getModels().contains(preferredVideoModel)))
                        .findFirst();

                if (preferredProvider.isPresent()) {
                    providers.remove(preferredProvider.get());
                    providers.add(0, preferredProvider.get());
                    log.info("用户指定优先视频模型: {}, 已调整提供商优先级", preferredVideoModel);
                } else {
                    log.warn("用户指定的视频模型 {} 不在可用列表中，将使用默认模型", preferredVideoModel);
                }
            }

            List<String> images = new ArrayList<>();

            // 🔍 详细日志：记录场景图输入参数（用于排查图片未传递问题）
            log.info("🎬🎬🎬 场景{} generateVideoFromScene 接收参数:", sceneNumber);
            log.info("  - sceneImageUrl: {}",
                    sceneImageUrl != null
                            ? (sceneImageUrl.length() > 100 ? sceneImageUrl.substring(0, 100) + "..." : sceneImageUrl)
                            : "NULL");
            log.info("  - sceneImageUrl是否为空: {}", sceneImageUrl == null || sceneImageUrl.isEmpty());
            log.info("  - videoPrompt长度: {}", videoPrompt != null ? videoPrompt.length() : 0);
            log.info("  - videoPrompt前200字符: {}",
                    videoPrompt != null && videoPrompt.length() > 200 ? videoPrompt.substring(0, 200) + "..."
                            : videoPrompt);

            if (sceneImageUrl != null && !sceneImageUrl.isEmpty()) {
                if (sceneImageUrl.startsWith("data:image")) {
                    images.add(sceneImageUrl);
                    log.info("场景{} 使用base64格式场景图, 长度: {}", sceneNumber, sceneImageUrl.length());
                } else {
                    try {
                        byte[] imageBytes = null;
                        String mimeType = "image/png";

                        if (sceneImageUrl.startsWith("http://") || sceneImageUrl.startsWith("https://")) {
                            java.net.URL url = new java.net.URL(sceneImageUrl);
                            try (java.io.InputStream is = url.openStream()) {
                                imageBytes = is.readAllBytes();
                            }
                            String fileName = sceneImageUrl.substring(sceneImageUrl.lastIndexOf('/') + 1);
                            mimeType = getMimeType(fileName);
                            log.info("从HTTP URL下载场景图成功: {}, 大小: {} bytes", sceneImageUrl, imageBytes.length);
                        } else {
                            String localPath = resolveImagePath(sceneImageUrl);
                            File imageFile = new File(localPath);
                            if (imageFile.exists()) {
                                imageBytes = Files.readAllBytes(imageFile.toPath());
                                mimeType = getMimeType(imageFile.getName());
                            }
                        }

                        if (imageBytes != null && imageBytes.length > 0) {
                            String base64 = Base64.getEncoder().encodeToString(imageBytes);
                            images.add("data:" + mimeType + ";base64," + base64);
                            log.info("场景{} 场景图转换为base64成功, 大小: {} bytes", sceneNumber, imageBytes.length);
                        } else {
                            log.warn("场景{} 场景图数据为空: {}", sceneNumber, sceneImageUrl);
                        }
                    } catch (Exception e) {
                        log.error("场景{} 转换场景图为base64失败: {}, 错误: {}", sceneNumber, sceneImageUrl, e.getMessage(), e);
                    }
                }
            } else {
                log.warn("场景{} 场景图为空，将仅使用提示词生成视频", sceneNumber);
            }

            String aspectRatio = convertAspectRatio(config.getAspectRatio());
            log.info("🎬🎬🎬 场景{} 即将调用视频生成:", sceneNumber);
            log.info("  - aspectRatio: {}", aspectRatio);
            log.info("  - images数量: {}", images.size());
            log.info("  - videoPrompt长度: {}", videoPrompt.length());
            if (!images.isEmpty()) {
                String firstImg = images.get(0);
                log.info("  - 第一张图片类型: {}, 长度: {}",
                        firstImg.startsWith("data:image") ? "base64" : "URL",
                        firstImg.length());
            } else {
                log.warn("  - ⚠️⚠️⚠️ 警告: images列表为空！场景图没有传递给视频生成！");
            }

            String finalPrompt = videoPrompt;
            // 提示词不截断，完整保留所有内容

            int maxRetries = 2;
            for (int retry = 0; retry <= maxRetries; retry++) {
                if (retry > 0) {
                    long retryDelay = (long) Math.pow(2, retry) * 5000;
                    log.info("场景{} 第{}次重试视频生成, 等待{}ms", sceneNumber, retry, retryDelay);
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }

                for (int i = 0; i < providers.size(); i++) {
                    AiProviderConfig provider = providers.get(i);
                    if (provider.getEnabled() == null || provider.getEnabled() != 1) {
                        continue;
                    }

                    try {
                        log.info("场景{} 第{}次尝试使用视频生成服务[{}]", sceneNumber, i + 1, provider.getProviderName());

                        Map<String, Object> result = aiProviderService.createVideoWithFallback(
                                finalPrompt,
                                provider.getDefaultModel(),
                                false,
                                true,
                                images,
                                aspectRatio);

                        String veoTaskId = (String) result.get("id");
                        String providerName = (String) result.get("providerName");
                        log.info("场景{} 获取VEO任务标识: {}, 提供商: {}", sceneNumber, veoTaskId, providerName);

                        String videoUrl = pollVeoTaskStatusForScene(task, veoTaskId, providerName, config, sceneNumber);
                        if (videoUrl != null) {
                            return videoUrl;
                        }
                        log.warn("场景{} VEO任务返回null，准备重试", sceneNumber);

                    } catch (Exception e) {
                        log.warn("场景{} 服务[{}] 失败: {}", sceneNumber, provider.getProviderName(), e.getMessage());
                    }
                }
            }

            log.error("❌ 场景{} 所有视频生成服务都已失败（已重试{}次）", sceneNumber, maxRetries);
            return null;

        } catch (Exception e) {
            log.error("❌ 场景{} 视频生成异常: {}", sceneNumber, e.getMessage(), e);
            return null;
        }
    }

    private String pollVeoTaskStatusForScene(VideoTask task, String veoTaskId, String providerName,
            AiVideoConfig config, int sceneNumber) {
        // 检查taskId是否为空
        if (veoTaskId == null || veoTaskId.isEmpty()) {
            log.error("场景{} VEO任务ID为空，无法查询状态", sceneNumber);
            return null;
        }

        int maxAttempts = sysConfigService.getIntConfig("video_poll_max_attempts", 120);
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                long pollInterval;
                if (attempt < 3) {
                    pollInterval = 15000;
                } else if (attempt < 10) {
                    pollInterval = 10000;
                } else {
                    pollInterval = Math.min(30000, 10000L + (attempt - 10) * 2000L);
                }

                Map<String, Object> status = aiProviderService.checkVideoStatusWithFallback(veoTaskId, providerName);
                String statusStr = (String) status.get("status");
                String videoUrl = (String) status.get("video_url");

                if ("completed".equals(statusStr)) {
                    log.info("✅ 场景{} 视频生成完成: {}", sceneNumber,
                            videoUrl != null ? videoUrl.substring(0, Math.min(50, videoUrl.length())) + "..." : "null");
                    return videoUrl;
                } else if ("failed".equals(statusStr) || "error".equals(statusStr)) {
                    String errorMsg = (String) status.get("error_message");
                    log.error("❌ 场景{} 视频生成失败, status={}, error={}", sceneNumber, statusStr, errorMsg);
                    return null;
                }

                if (attempt % 5 == 0) {
                    log.info("场景{} 视频生成中... 尝试: {}/{}, 下次轮询: {}ms",
                            sceneNumber, attempt, maxAttempts, pollInterval);
                }

                Thread.sleep(pollInterval);
                attempt++;

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("场景{} VEO轮询被中断", sceneNumber);
                return null;
            } catch (Exception e) {
                log.warn("检查场景{} VEO任务状态失败: {}", sceneNumber, e.getMessage());
                attempt++;
            }
        }

        log.error("场景{} 视频生成超时（已尝试{}次）", sceneNumber, maxAttempts);
        return null;
    }

    @Async
    public void processTaskAsync(String taskId, AiVideoConfig config, String[] imageUrls) {
        try {
            VideoTask task = taskMapper.selectOne(
                    new LambdaQueryWrapper<VideoTask>()
                            .eq(VideoTask::getTaskId, taskId));

            if (task == null) {
                log.error("任务不存在: {}", taskId);
                return;
            }

            updateTaskStatus(task, "processing", 10);

            String prompt = buildPromptWithImageAnalysis(config, taskId);
            log.info("生成视频提示词: 长度={}", prompt.length());

            updateTaskStatus(task, "processing", 20);

            List<AiProviderConfig> providers = aiProviderService.getProvidersByType("VIDEO");
            if (providers.isEmpty()) {
                throw new RuntimeException("没有可用的视频生成服务提供商");
            }

            List<String> images = convertImagesToBase64(imageUrls);
            String aspectRatio = convertAspectRatio(config.getAspectRatio());

            if (images != null && !images.isEmpty()) {
                log.info("视频生成包含参考图片: {} 张", images.size());
            }

            boolean success = false;
            List<String> errors = new ArrayList<>();

            for (int i = 0; i < providers.size(); i++) {
                AiProviderConfig provider = providers.get(i);
                if (provider.getEnabled() == null || provider.getEnabled() != 1) {
                    continue;
                }

                try {
                    log.info("尝试使用视频生成服务[{}]: {}", i + 1, provider.getProviderName());

                    updateTaskStatus(task, "processing", 20 + i * 5);

                    Map<String, Object> result = aiProviderService.createVideoWithFallback(
                            prompt,
                            provider.getDefaultModel(),
                            null,
                            null,
                            images,
                            aspectRatio);

                    String veoTaskId = (String) result.get("id");
                    String providerName = (String) result.get("providerName");
                    log.info("获取VEO任务标识: provider={}, veoTaskId={}", providerName, veoTaskId);

                    updateTaskStatus(task, "processing", 30);

                    pollVeoTaskStatus(task, veoTaskId, providerName, config);

                    success = true;
                    break;
                } catch (Exception e) {
                    String errorMsg = String.format("服务[%s] 失败: %s",
                            provider.getProviderName(), e.getMessage());
                    log.warn("重试: {}", errorMsg);
                    errors.add(errorMsg);
                }
            }

            if (!success) {
                String allErrors = String.join("; ", errors);
                log.error("视频生成失败，所有服务提供商不可用: {}", allErrors);

                task.setStatus("failed");
                task.setErrorMessage("所有视频生成服务调用失败: " + allErrors);
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateById(task);

                recordLearning(task, config, false, allErrors);
            }

        } catch (Exception e) {
            log.error("视频生成异常: {}", taskId, e);
            VideoTask task = taskMapper.selectOne(
                    new LambdaQueryWrapper<VideoTask>()
                            .eq(VideoTask::getTaskId, taskId));
            if (task != null) {
                task.setStatus("failed");
                String errorMsg = e.getMessage();
                if (errorMsg == null || errorMsg.isEmpty()) {
                    errorMsg = "视频生成错误: " + e.getClass().getSimpleName();
                }
                task.setErrorMessage(errorMsg);
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateById(task);
            }
        }
    }

    private void pollVeoTaskStatus(VideoTask task, String veoTaskId, String providerName, AiVideoConfig config) {
        // 检查taskId是否为空
        if (veoTaskId == null || veoTaskId.isEmpty()) {
            log.error("VEO任务ID为空，无法查询状态");
            task.setStatus("failed");
            task.setErrorMessage("视频生成失败：任务ID为空");
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            return;
        }

        int maxAttempts = sysConfigService.getIntConfig("video_poll_max_attempts", 120);
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                Map<String, Object> status = aiProviderService.checkVideoStatusWithFallback(veoTaskId, providerName);
                String statusStr = (String) status.get("status");
                String videoUrl = (String) status.get("video_url");
                String errorMsg = (String) status.get("error");
                String videoGenError = (String) status.get("video_generation_error");

                int progressPercent;
                switch (statusStr != null ? statusStr : "pending") {
                    case "pending":
                        progressPercent = 10 + attempt;
                        break;
                    case "processing":
                        progressPercent = 30 + attempt * 2;
                        break;
                    default:
                        progressPercent = 30 + attempt;
                        break;
                }
                updateTaskStatus(task, "processing", Math.min(progressPercent, 95));

                if ("completed".equals(statusStr)) {
                    task.setVideoUrl(videoUrl);
                    updateTaskStatus(task, "completed", 100);
                    log.info("视频生成完成: taskId={}, videoUrl={}", task.getTaskId(), videoUrl);

                    recordLearning(task, config, true, null);
                    return;
                } else if ("failed".equals(statusStr) || "error".equals(statusStr)) {
                    String error = errorMsg != null ? errorMsg : (videoGenError != null ? videoGenError : "视频生成失败");
                    task.setStatus("failed");
                    task.setErrorMessage(error);
                    task.setUpdatedAt(LocalDateTime.now());
                    taskMapper.updateById(task);

                    recordLearning(task, config, false, error);
                    return;
                }

                Thread.sleep(5000);
                attempt++;

            } catch (Exception e) {
                log.warn("检查VEO任务状态失败: {}", e.getMessage());
                attempt++;
            }
        }

        task.setStatus("failed");
        task.setErrorMessage("视频生成超时");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        recordLearning(task, config, false, "视频生成超时");
    }

    private void simulateVideoGeneration(VideoTask task, AiVideoConfig config) throws InterruptedException {
        Thread.sleep(2000);
        updateTaskStatus(task, "processing", 50);

        Thread.sleep(2000);
        updateTaskStatus(task, "processing", 70);

        Thread.sleep(2000);
        updateTaskStatus(task, "processing", 90);

        String videoUrl = "/videos/sample_" + task.getTaskId() + ".mp4";
        task.setVideoUrl(videoUrl);
        updateTaskStatus(task, "completed", 100);

        log.info("视频生成完成(模拟): {}", task.getTaskId());

        recordLearning(task, config, true, null);
    }

    private void updateTaskStatus(VideoTask task, String status, Integer progress) {
        task.setStatus(status);
        task.setProgress(progress);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private String buildPrompt(AiVideoConfig config) {
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append(config.getTopic() != null ? config.getTopic() : "product video");
            prompt.append(", ").append(config.getScene() != null ? config.getScene() : "product showcase");
            prompt.append(", ").append(config.getFrameType() != null ? config.getFrameType() : "standard");

            String result = prompt.toString();
            if (result.length() > 500) {
                result = result.substring(0, 500);
            }

            log.info("简化提示词构建完成: 长度={}", result.length());
            return result;

        } catch (Exception e) {
            log.error("构建提示词失败: {}", e.getMessage(), e);
            return config.getTopic() != null ? config.getTopic() : "product video";
        }
    }

    /**
     * 构建包含图片分析结果的增强提示词
     */
    private String buildPromptWithImageAnalysis(AiVideoConfig config, String taskId) {
        try {
            StringBuilder prompt = new StringBuilder();

            Map<String, Object> cachedAnalysis = imageAnalysisCache.get(taskId);
            if (cachedAnalysis != null) {
                String imageDescriptions = (String) cachedAnalysis.get("imageDescriptions");
                if (imageDescriptions != null && !imageDescriptions.isEmpty()) {
                    prompt.append(imageDescriptions).append(". ");
                }
            }

            prompt.append(config.getTopic() != null ? config.getTopic() : "product video");
            prompt.append(", ").append(config.getScene() != null ? config.getScene() : "product showcase");
            prompt.append(", ").append(config.getFrameType() != null ? config.getFrameType() : "standard");

            String result = prompt.toString();
            if (result.length() > 500) {
                result = result.substring(0, 500);
            }

            log.info("简化提示词构建完成: 长度={}", result.length());
            return result;

        } catch (Exception e) {
            log.error("构建提示词失败: {}", e.getMessage(), e);
            return config.getTopic() != null ? config.getTopic() : "product video";
        }
    }

    private String buildFallbackPrompt(AiVideoConfig config) {
        try {
            Map<String, Object> promptJson = new HashMap<>();

            promptJson.put("duration", config.getDuration());
            promptJson.put("race", config.getRace());
            promptJson.put("role", config.getRole());
            promptJson.put("topic", config.getTopic());
            promptJson.put("sceneType", config.getSceneType());
            promptJson.put("scene", config.getScene());
            promptJson.put("frameType", config.getFrameType());
            promptJson.put("aspectRatio", config.getAspectRatio());
            promptJson.put("resolution", config.getResolution());
            promptJson.put("styleIntensity", config.getStyleIntensity());
            promptJson.put("creativity", config.getCreativity());

            Map<String, Object> description = new HashMap<>();
            description.put("duration", config.getDuration() + "秒");
            description.put("character", config.getRace() + " " + config.getRole());
            description.put("topic", config.getTopic());
            description.put("scene", config.getSceneType() + " " + config.getScene());
            description.put("camera", config.getFrameType() + "运动镜头");
            description.put("aspectRatio", config.getAspectRatio());
            description.put("resolution", config.getResolution());
            description.put("styleIntensity", config.getStyleIntensity() + "%");
            description.put("creativity", config.getCreativity() + "%");

            promptJson.put("description", description);
            promptJson.put("promptText", String.format(
                    "时长%d秒的视频。人物：%s，%s。主题：%s。场景：%s，%s。运镜：%s运动镜头。画面比例：%s。分辨率：%s。风格强度：%d%%。创意度：%d%%。",
                    config.getDuration(),
                    config.getRace(),
                    config.getRole(),
                    config.getTopic(),
                    config.getSceneType(),
                    config.getScene(),
                    config.getFrameType(),
                    config.getAspectRatio(),
                    config.getResolution(),
                    config.getStyleIntensity(),
                    config.getCreativity()));

            return objectMapper.writeValueAsString(promptJson);
        } catch (Exception e) {
            log.warn("生成JSON提示词失败，使用文本格式", e);
            return String.format(
                    "时长%d秒的视频。人物：%s，%s。主题：%s。场景：%s，%s。运镜：%s运动镜头。画面比例：%s。分辨率：%s。风格强度：%d%%。创意度：%d%%。",
                    config.getDuration(),
                    config.getRace(),
                    config.getRole(),
                    config.getTopic(),
                    config.getSceneType(),
                    config.getScene(),
                    config.getFrameType(),
                    config.getAspectRatio(),
                    config.getResolution(),
                    config.getStyleIntensity(),
                    config.getCreativity());
        }
    }

    private String convertAspectRatio(String ratio) {
        // 强制使用9:16竖屏比例，无论传入什么参数
        return "9:16";
    }

    /**
     * 将本地文件路径转换为完整的URL，以便前端播放
     */
    private String convertLocalPathToUrl(String localPath) {
        if (localPath == null || localPath.isEmpty()) {
            return localPath;
        }

        // 如果已经是完整URL，直接返回
        if (localPath.startsWith("http://") || localPath.startsWith("https://")) {
            return localPath;
        }
        // 处理相对路径，如 ./uploads/media/processed/mixed_xxx.mp4
        // 或 /uploads/media/processed/mixed_xxx.mp4
        try {
            // 统一使用正斜杠
            String normalizedPath = localPath.replace("\\", "/");

            // 如果以 . 开头，去掉 .
            if (normalizedPath.startsWith("./")) {
                normalizedPath = normalizedPath.substring(1);
            }

            // 确保以 / 开头
            if (!normalizedPath.startsWith("/")) {
                normalizedPath = "/" + normalizedPath;
            }

            // 构建完整URL
            return serverUrl + normalizedPath;
        } catch (Exception e) {
            log.error("转换本地路径为URL失败: {}", localPath, e);
            return localPath;
        }
    }

    private List<String> convertImagesToBase64(String[] imageUrls) {
        if (imageUrls == null || imageUrls.length == 0) {
            return null;
        }

        List<String> base64Images = new ArrayList<>();
        for (String imageUrl : imageUrls) {
            if (imageUrl == null || imageUrl.isEmpty()) {
                continue;
            }

            if (imageUrl.startsWith("data:image")) {
                base64Images.add(imageUrl);
                continue;
            }

            if (imageUrl.length() > 200 && !imageUrl.startsWith("http") && !imageUrl.startsWith("/")) {
                base64Images.add("data:image/jpeg;base64," + imageUrl);
                continue;
            }

            try {
                String localPath = resolveImagePath(imageUrl);
                File imageFile = new File(localPath);
                if (imageFile.exists()) {
                    byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
                    String base64 = Base64.getEncoder().encodeToString(imageBytes);
                    String mimeType = getMimeType(imageFile.getName());
                    base64Images.add("data:" + mimeType + ";base64," + base64);
                    log.info("图片转换为base64成功: {} -> {} bytes", imageUrl, imageBytes.length);
                } else {
                    log.warn("图片文件不存在: {}", localPath);
                }
            } catch (Exception e) {
                log.error("转换图片为base64失败: {}", imageUrl, e);
            }
        }

        return base64Images.isEmpty() ? null : base64Images;
    }

    private String resolveImagePath(String imageUrl) {
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            return imageUrl;
        }

        String path = imageUrl;
        if (path.startsWith("/uploads/")) {
            path = uploadPath + path.substring(8);
        } else if (path.startsWith("/api/files/")) {
            path = uploadPath + "/" + path.substring(11);
        } else if (!new File(path).isAbsolute()) {
            path = uploadPath + "/" + path;
        }

        return path;
    }

    private String getMimeType(String fileName) {
        String ext = fileName.toLowerCase();
        if (ext.endsWith(".png"))
            return "image/png";
        if (ext.endsWith(".gif"))
            return "image/gif";
        if (ext.endsWith(".webp"))
            return "image/webp";
        return "image/jpeg";
    }

    public VideoTask getTask(String taskId) {
        VideoTask task = taskMapper.selectOne(
                new LambdaQueryWrapper<VideoTask>()
                        .eq(VideoTask::getTaskId, taskId));
        return task;
    }

    public List<VideoTask> listTasks(Long creatorId) {
        LambdaQueryWrapper<VideoTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VideoTask::getCreatorId, creatorId)
                .orderByDesc(VideoTask::getCreatedAt);
        return taskMapper.selectList(wrapper);
    }

    /**
     * 分页查询历史视频（已完成状态）
     */
    public Map<String, Object> getHistory(Long creatorId, int page, int size, String keyword) {
        Map<String, Object> result = new HashMap<>();

        // 构建查询条件
        LambdaQueryWrapper<VideoTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VideoTask::getCreatorId, creatorId)
                .eq(VideoTask::getStatus, "completed");

        // 关键词搜索（任务ID）- 暂时只支持taskId搜索
        if (keyword != null && !keyword.trim().isEmpty()) {
            String searchKey = "%" + keyword.trim() + "%";
            wrapper.like(VideoTask::getTaskId, searchKey);
        }

        // 按创建时间倒序
        wrapper.orderByDesc(VideoTask::getCreatedAt);

        // 查询总数
        long total = taskMapper.selectCount(wrapper);

        // 分页查询
        int offset = (page - 1) * size;
        wrapper.last("LIMIT " + size + " OFFSET " + offset);
        List<VideoTask> records = taskMapper.selectList(wrapper);

        // 为每个任务设置显示名称（使用taskId前8位）
        for (VideoTask task : records) {
            if (task.getTaskName() == null) {
                task.setTaskName("视频_" + task.getTaskId().substring(0, 8));
            }
        }

        // 构建返回结果
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("pages", (total + size - 1) / size);

        return result;
    }

    public Map<String, Object> getStatistics(Long creatorId) {
        Map<String, Object> stats = new HashMap<>();

        LambdaQueryWrapper<VideoTask> allWrapper = new LambdaQueryWrapper<>();
        allWrapper.eq(VideoTask::getCreatorId, creatorId);
        stats.put("total", taskMapper.selectCount(allWrapper));

        LambdaQueryWrapper<VideoTask> pendingWrapper = new LambdaQueryWrapper<>();
        pendingWrapper.eq(VideoTask::getCreatorId, creatorId)
                .eq(VideoTask::getStatus, "pending");
        stats.put("pending", taskMapper.selectCount(pendingWrapper));

        LambdaQueryWrapper<VideoTask> processingWrapper = new LambdaQueryWrapper<>();
        processingWrapper.eq(VideoTask::getCreatorId, creatorId)
                .eq(VideoTask::getStatus, "processing");
        stats.put("processing", taskMapper.selectCount(processingWrapper));

        LambdaQueryWrapper<VideoTask> completedWrapper = new LambdaQueryWrapper<>();
        completedWrapper.eq(VideoTask::getCreatorId, creatorId)
                .eq(VideoTask::getStatus, "completed");
        stats.put("completed", taskMapper.selectCount(completedWrapper));

        LambdaQueryWrapper<VideoTask> failedWrapper = new LambdaQueryWrapper<>();
        failedWrapper.eq(VideoTask::getCreatorId, creatorId)
                .eq(VideoTask::getStatus, "failed");
        stats.put("failed", taskMapper.selectCount(failedWrapper));

        return stats;
    }

    public void deleteTask(String taskId, Long creatorId) {
        LambdaQueryWrapper<VideoTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VideoTask::getTaskId, taskId)
                .eq(VideoTask::getCreatorId, creatorId);
        VideoTask task = taskMapper.selectOne(wrapper);
        if (task != null) {
            taskMapper.deleteById(task.getId());
        }
    }

    private void triggerMcpToolsForVideoGeneration(VideoTask task, AiVideoConfig config) {
        try {
            log.info("开始自动触发MCP工具增强视频生成: taskId={}", task.getTaskId());

            Map<String, Object> videoContext = new HashMap<>();
            videoContext.put("taskId", task.getTaskId());
            videoContext.put("topic", config.getTopic());
            videoContext.put("sceneType", config.getSceneType());
            videoContext.put("race", config.getRace());
            videoContext.put("role", config.getRole());
            videoContext.put("scene", config.getScene());
            videoContext.put("frameType", config.getFrameType());

            Map<String, List<Map<String, Object>>> triggerRules = mcpAutoTriggerService.getTriggerRules();

            int triggeredCount = 0;
            for (Map.Entry<String, List<Map<String, Object>>> entry : triggerRules.entrySet()) {
                String serverName = entry.getKey();
                List<Map<String, Object>> rules = entry.getValue();

                for (Map<String, Object> rule : rules) {
                    String condition = (String) rule.getOrDefault("triggerCondition", "");
                    String toolName = (String) rule.get("toolName");
                    String priority = (String) rule.getOrDefault("priority", "medium");

                    if (shouldTriggerTool(condition, videoContext)) {
                        log.info("自动触发MCP工具: server={}, tool={}, priority={}, condition={}",
                                serverName, toolName, priority, condition);

                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> arguments = (Map<String, Object>) rule.getOrDefault("arguments",
                                    new HashMap<>());

                            Map<String, Object> enrichedArguments = enrichArguments(arguments, videoContext);

                            mcpAutoTriggerService.triggerToolByName(serverName, toolName, enrichedArguments);
                            triggeredCount++;

                        } catch (Exception e) {
                            log.warn("MCP工具触发失败: server={}, tool={}", serverName, toolName, e);
                        }
                    }
                }
            }

            log.info("MCP工具自动触发完成: taskId={}, 触发数量={}", task.getTaskId(), triggeredCount);

        } catch (Exception e) {
            log.error("MCP工具自动触发异常: taskId={}", task.getTaskId(), e);
        }
    }

    private boolean shouldTriggerTool(String condition, Map<String, Object> videoContext) {
        if (condition == null || condition.isEmpty()) {
            return false;
        }

        String lowerCondition = condition.toLowerCase();

        if (lowerCondition.contains("视频") || lowerCondition.contains("video")) {
            return true;
        }

        if (lowerCondition.contains("产品") || lowerCondition.contains("product")) {
            return videoContext.get("topic") != null;
        }

        if (lowerCondition.contains("场景") || lowerCondition.contains("scene")) {
            return videoContext.get("sceneType") != null || videoContext.get("scene") != null;
        }

        if (lowerCondition.contains("质量") || lowerCondition.contains("quality")) {
            return true;
        }

        if (lowerCondition.contains("优化") || lowerCondition.contains("optimize")) {
            return true;
        }

        return false;
    }

    private Map<String, Object> enrichArguments(Map<String, Object> originalArgs, Map<String, Object> videoContext) {
        Map<String, Object> enriched = new HashMap<>(originalArgs);

        for (Map.Entry<String, Object> entry : videoContext.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value != null && !enriched.containsKey(key)) {
                enriched.put(key, value);
            }
        }

        return enriched;
    }

    /**
     * 异步自动学习：对生成的视频进行打分和入库
     * 不阻塞视频生成主流程
     */
    @Async
    public void autoLearnFromGeneratedVideo(VideoTask task, AiVideoConfig config,
            String videoUrl, List<Map<String, Object>> sceneImages) {
        try {
            if (videoUrl == null || videoUrl.isEmpty()) {
                log.info("视频URL为空，跳过自动学习: taskId={}", task.getTaskId());
                return;
            }

            log.info("开始自动视频学习: taskId={}, videoUrl={}", task.getTaskId(), videoUrl);

            // 1. 使用AI对生成的视频进行质量打分
            double qualityScore = 0.7; // 默认分数
            try {
                String scoringPrompt = "请对这个AI生成的产品宣传视频进行质量评估：\n" +
                        "1. 画面质量：清晰度、稳定性、色彩\n" +
                        "2. 产品展示：产品是否突出、特征是否清晰\n" +
                        "3. 场景匹配：视频内容是否与场景描述一致\n" +
                        "4. 整体效果：视觉吸引力、专业度\n\n" +
                        "请给出0-100的总分，并说明理由。";

                // 先从视频中提取帧图片
                String framePath = null;
                try {
                    framePath = ffmpegService.extractFrame(videoUrl, 1.0);
                    log.info("视频帧提取成功: taskId={}, framePath={}", task.getTaskId(), framePath);
                } catch (Exception ex) {
                    log.warn("视频帧提取失败，将尝试直接使用视频URL: {}", ex.getMessage());
                }

                // 使用提取的帧图片或原始视频URL进行质量分析
                String imageUrl = framePath != null ? framePath : videoUrl;
                String scoringResult = aiProviderService.analyzeImageWithFallback(
                        imageUrl, scoringPrompt, false);

                if (scoringResult != null && !scoringResult.isEmpty()) {
                    qualityScore = extractScoreFromText(scoringResult);
                    log.info("视频质量打分完成: taskId={}, score={:.2f}", task.getTaskId(), qualityScore);
                }

                // 清理临时帧图片
                if (framePath != null) {
                    try {
                        java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(framePath));
                        log.debug("临时帧图片已清理: {}", framePath);
                    } catch (Exception ex) {
                        log.warn("临时帧图片清理失败: {}", ex.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("视频质量打分失败，使用默认分数: {}", e.getMessage());
            }

            // 2. 将视频作为成功案例存入知识库
            Map<String, Object> caseData = new HashMap<>();
            caseData.put("sceneType", config.getSceneType());
            caseData.put("frameType", config.getFrameType());
            caseData.put("topic", config.getTopic());
            caseData.put("scene", config.getScene());
            caseData.put("videoUrl", videoUrl);
            caseData.put("qualityScore", qualityScore);
            caseData.put("taskId", task.getTaskId());
            caseData.put("sceneCount", sceneImages != null ? sceneImages.size() : 0);

            // 提取场景描述作为模板内容
            if (sceneImages != null && !sceneImages.isEmpty()) {
                StringBuilder sceneDesc = new StringBuilder();
                for (Map<String, Object> scene : sceneImages) {
                    String prompt = (String) scene.get("imagePrompt");
                    if (prompt != null && !prompt.isEmpty()) {
                        sceneDesc.append(prompt).append(" | ");
                    }
                }
                caseData.put("scenePrompts", sceneDesc.toString());
            }

            // 3. 存入知识库（成功模板）
            autoKnowledgeLearningService.learnFromVideoCase(caseData, true);
            log.info("视频学习入库完成: taskId={}, score={:.2f}", task.getTaskId(), qualityScore);

        } catch (Exception e) {
            log.error("自动视频学习异常: taskId={}, error={}", task.getTaskId(), e.getMessage(), e);
        }
    }

    private double extractScoreFromText(String text) {
        try {
            // 尝试匹配 "XX分" 或 "score: XX" 或 "总分：XX" 等格式
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{1,3})\\s*分");
            java.util.regex.Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                double score = Double.parseDouble(matcher.group(1));
                return Math.min(score / 100.0, 1.0);
            }

            // 尝试匹配 0-1 范围的小数
            pattern = java.util.regex.Pattern.compile("(0\\.\\d{1,2})");
            matcher = pattern.matcher(text);
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1));
            }
        } catch (Exception e) {
            log.warn("解析分数失败: {}", e.getMessage());
        }
        return 0.7;
    }

    private void recordLearning(VideoTask task, AiVideoConfig config, boolean success, String errorMessage) {
        try {
            Map<String, Object> inputParams = new HashMap<>();
            inputParams.put("race", config.getRace());
            inputParams.put("role", config.getRole());
            inputParams.put("topic", config.getTopic());
            inputParams.put("scene", config.getScene());
            inputParams.put("frameType", config.getFrameType());
            inputParams.put("sceneType", config.getSceneType());
            inputParams.put("productName", config.getTopic());
            inputParams.put("category", config.getSceneType());
            inputParams.put("platform", "ai_video");

            Map<String, Object> outputResult = new HashMap<>();
            outputResult.put("taskId", task.getTaskId());
            outputResult.put("videoUrl", task.getVideoUrl());
            outputResult.put("status", task.getStatus());

            unifiedMemoryService.recordBusinessAction(
                    "video_generator", "video_generation",
                    inputParams, outputResult, success, errorMessage);

            Long templateId = videoPromptFrameworkService.getLastSelectedTemplateId();
            if (templateId != null) {
                double qualityScore = success ? 0.8 : 0.2;
                templateLearningService.recordTemplateUsage(templateId, success, qualityScore);
                log.info("模板使用记录完成: templateId={}, success={}", templateId, success);
            }

            Map<String, Object> caseData = new HashMap<>();
            caseData.put("sceneType", config.getSceneType());
            caseData.put("frameType", config.getFrameType());
            caseData.put("topic", config.getTopic());
            caseData.put("race", config.getRace());
            caseData.put("role", config.getRole());
            caseData.put("videoUrl", task.getVideoUrl());
            caseData.put("errorMessage", errorMessage);
            caseData.put("taskId", task.getTaskId());

            learningCoreService.learnFromVideoCase(caseData, success);
            log.info("专家学习记录完成: taskId={}, success={}", task.getTaskId(), success);

            autoKnowledgeLearningService.learnFromVideoCase(caseData, success);
            log.info("知识自动提取记录完成: taskId={}, success={}", task.getTaskId(), success);

            if (success) {
                videoPostReviewService.reviewSuccess(task, config);
            } else {
                videoPostReviewService.reviewFailure(task, config, errorMessage);
            }

            log.info("统一记忆记录完成: taskId={}, success={}", task.getTaskId(), success);

        } catch (Exception e) {
            log.error("记录学习案例失败: {}", task.getTaskId(), e);
        }
    }

    /**
     * 如果提示词包含中文，则使用LLM翻译成英文
     */
    private String translateToEnglishIfNeeded(String prompt) {
        if (prompt == null || prompt.isEmpty()) {
            return prompt;
        }

        // 检查是否包含中文字符
        boolean hasChinese = prompt.chars().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF);

        if (!hasChinese) {
            // 已经是英文，直接返回
            return prompt;
        }

        try {
            log.info("检测到中文提示词，开始翻译成英文...");

            String translationPrompt = String.format(
                    "请将以下视频生成提示词翻译成英文。要求：\n" +
                            "1. 保持所有视觉描述细节不变\n" +
                            "2. 使用专业的影视制作术语\n" +
                            "3. 保持提示词的结构和格式\n" +
                            "4. 只输出翻译结果，不要添加任何解释\n\n" +
                            "原始提示词：\n%s",
                    prompt);

            // 使用AI Provider Service调用LLM进行翻译
            String translated = aiProviderService.callChatCompletion(
                    "你是一个专业的影视制作翻译助手。",
                    translationPrompt,
                    0.3, // 低温度确保准确翻译
                    2000 // 最大token数
            );

            if (translated != null && !translated.isEmpty()) {
                log.info("提示词翻译成功: 原始长度={}, 翻译后长度={}", prompt.length(), translated.length());
                return translated.trim();
            }
        } catch (Exception e) {
            log.error("提示词翻译失败，使用原始提示词: {}", e.getMessage());
        }

        // 如果翻译失败，返回原始提示词
        return prompt;
    }

    /**
     * 优化英文提示词长度，确保在VEO最佳范围内(200-400字符)
     */
    private String optimizeEnglishPromptLength(String prompt, int targetLength) {
        if (prompt.length() <= targetLength && prompt.length() >= 200) {
            return prompt;
        }

        // 将换行符替换为空格，VEO不接受换行符
        String cleaned = prompt.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ").trim();

        // 按句子分割
        String[] sentences = cleaned.split("(?<=[.!?])\\s+");
        StringBuilder optimized = new StringBuilder();

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty())
                continue;

            // 如果添加这个句子会超过目标长度，停止添加
            if (optimized.length() + trimmed.length() + 1 > targetLength) {
                break;
            }

            if (optimized.length() > 0) {
                optimized.append(" ");
            }
            optimized.append(trimmed);
        }

        // 如果优化后太短，尝试添加更多句子
        if (optimized.length() < 200 && optimized.length() < cleaned.length()) {
            String remaining = cleaned.substring(optimized.length()).trim();
            int spaceToAdd = Math.min(200 - optimized.length(), remaining.length());
            if (spaceToAdd > 50) {
                optimized.append(" ").append(remaining.substring(0, spaceToAdd));
            }
        }

        return optimized.toString().trim();
    }
}
