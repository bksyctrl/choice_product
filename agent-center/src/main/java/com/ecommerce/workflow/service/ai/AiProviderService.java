package com.ecommerce.workflow.service.ai;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.workflow.config.AiProvidersProperties;
import com.ecommerce.workflow.entity.AiProviderConfig;
import com.ecommerce.workflow.entity.UnifiedAiConfig;
import com.ecommerce.workflow.mapper.AiProviderConfigMapper;
import com.ecommerce.workflow.mapper.UnifiedAiConfigMapper;
import com.ecommerce.workflow.service.config.SysConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AiProviderService {

    private static final Logger log = LoggerFactory.getLogger(AiProviderService.class);
    private final UnifiedAiConfigMapper unifiedAiConfigMapper;
    private final AiProviderConfigMapper aiProviderConfigMapper;
    private final UnifiedAiConfigAdapter configAdapter;
    private final AiProvidersProperties providersProperties;
    private final SysConfigService sysConfigService;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiRateLimiter aiRateLimiter;
    private final UniversalApiAdapterService universalAdapter;

    public AiProviderService(UnifiedAiConfigMapper unifiedAiConfigMapper,
            AiProviderConfigMapper aiProviderConfigMapper,
            UnifiedAiConfigAdapter configAdapter,
            AiProvidersProperties providersProperties,
            RestTemplate restTemplate,
            SysConfigService sysConfigService,
            AiRateLimiter aiRateLimiter,
            UniversalApiAdapterService universalAdapter) {
        this.unifiedAiConfigMapper = unifiedAiConfigMapper;
        this.aiProviderConfigMapper = aiProviderConfigMapper;
        this.configAdapter = configAdapter;
        this.providersProperties = providersProperties;
        this.restTemplate = restTemplate;
        this.sysConfigService = sysConfigService;
        this.aiRateLimiter = aiRateLimiter;
        this.universalAdapter = universalAdapter;
    }

    private final Map<String, List<AiProviderConfig>> configCache = new ConcurrentHashMap<>();
    private long cacheTimestamp = 0;

    public List<AiProviderConfig> getProvidersByType(String type) {
        refreshCacheIfNeeded();
        return configCache.getOrDefault(type, Collections.emptyList());
    }

    public List<AiProviderConfig> getAllProviders() {
        refreshCacheIfNeeded();
        List<AiProviderConfig> all = new ArrayList<>();
        for (List<AiProviderConfig> list : configCache.values()) {
            all.addAll(list);
        }
        return all;
    }

    public AiProviderConfig getProviderById(Long id) {
        UnifiedAiConfig unified = unifiedAiConfigMapper.selectById(id);
        return configAdapter.convertToLegacy(unified);
    }

    public String chatWithFallback(String systemPrompt, String userMessage, Double temperature, Integer maxTokens) {
        return chat(systemPrompt, userMessage, null, null, temperature, maxTokens);
    }

    public String chatWithHistory(String systemPrompt, String userMessage,
            List<Map<String, String>> historyMessages,
            Double temperature, Integer maxTokens) {
        return chat(systemPrompt, userMessage, null, historyMessages, temperature, maxTokens);
    }

    public String callChatCompletion(String systemPrompt, String userMessage, Double temperature, Integer maxTokens) {
        return chat(systemPrompt, userMessage, null, null, temperature, maxTokens);
    }

    public String chatWithModel(String systemPrompt, String userMessage, String preferredModel,
            Double temperature, Integer maxTokens) {
        return chat(systemPrompt, userMessage, preferredModel, null, temperature, maxTokens);
    }

    private String chat(String systemPrompt, String userMessage, String preferredModel,
            List<Map<String, String>> historyMessages,
            Double temperature, Integer maxTokens) {
        List<AiProviderConfig> providers = getProvidersByType("LLM");
        if (providers.isEmpty()) {
            throw new RuntimeException("未配置可用的LLM提供商");
        }

        if (preferredModel != null && !preferredModel.isEmpty()) {
            providers.sort((a, b) -> {
                int priorityA = a.getPriority() != null ? a.getPriority() : Integer.MAX_VALUE;
                int priorityB = b.getPriority() != null ? b.getPriority() : Integer.MAX_VALUE;
                return Integer.compare(priorityA, priorityB);
            });
        }

        List<String> errors = new ArrayList<>();

        if (preferredModel != null && !preferredModel.isEmpty()) {
            for (AiProviderConfig provider : providers) {
                if (provider.getEnabled() == null || provider.getEnabled() != 1)
                    continue;
                List<String> models = getProviderModels(provider);
                if (models.contains(preferredModel)) {
                    try {
                        String result = executeChatApi(provider, systemPrompt, userMessage, preferredModel,
                                historyMessages, temperature, maxTokens);
                        log.info("AI调用成功(指定模型): provider={}, model={}", provider.getProviderName(), preferredModel);
                        return result;
                    } catch (Exception e) {
                        log.warn("指定模型调用失败: provider={}, model={}, error={}", provider.getProviderName(),
                                preferredModel, e.getMessage());
                        errors.add(provider.getProviderName() + "/" + preferredModel + ": " + e.getMessage());
                    }
                }
            }
            log.info("指定模型 {} 不可用，尝试其他模型", preferredModel);
        }

        for (AiProviderConfig provider : providers) {
            if (provider.getEnabled() == null || provider.getEnabled() != 1)
                continue;

            List<String> models = getProviderModels(provider);
            for (String model : models) {
                if (preferredModel != null && model.equals(preferredModel))
                    continue;
                try {
                    String result = executeChatApi(provider, systemPrompt, userMessage, model, historyMessages,
                            temperature, maxTokens);
                    log.info("AI调用成功: provider={}, model={}, historySize={}",
                            provider.getProviderName(), model, historyMessages != null ? historyMessages.size() : 0);
                    return result;
                } catch (Exception e) {
                    log.warn("AI调用失败: provider={}, model={}, error={}", provider.getProviderName(), model,
                            e.getMessage());
                    errors.add(provider.getProviderName() + "/" + model + ": " + e.getMessage());
                }
            }
        }
        throw new RuntimeException("所有LLM提供商调用失败: " + String.join("; ", errors));
    }

    public void chatStreamWithFallback(String systemPrompt, String userMessage,
            GptChatService.StreamCallback callback) {
        List<AiProviderConfig> providers = getProvidersByType("LLM");
        if (providers.isEmpty()) {
            callback.onError(new RuntimeException("未配置可用的LLM提供商"));
            return;
        }
        List<String> errors = new ArrayList<>();
        for (AiProviderConfig provider : providers) {
            if (provider.getEnabled() == null || provider.getEnabled() != 1)
                continue;
            try {
                callChatStreamApi(provider, systemPrompt, userMessage, callback);
                log.info("AI流式调用成功: provider={}", provider.getProviderName());
                return;
            } catch (Exception e) {
                log.warn("AI流式调用失败，切换到下一个提供商: provider={}, error={}", provider.getProviderName(), e.getMessage());
                errors.add(provider.getProviderName() + ": " + e.getMessage());
            }
        }
        callback.onError(new RuntimeException("所有LLM提供商调用失败: " + String.join("; ", errors)));
    }

    public String analyzeImageWithFallback(String imageUrl, String prompt, boolean isBase64) {
        // 优先使用配置的首选图像分析提供商
        String preferredProvider = sysConfigService.getConfig("ai_vision_preferred_provider", null);

        List<AiProviderConfig> providers = getProvidersByType("LLM");
        if (providers.isEmpty()) {
            throw new RuntimeException("未配置可用的LLM提供商");
        }

        log.info("图像分析开始: 找到{}个LLM提供商", providers.size());
        for (AiProviderConfig p : providers) {
            log.info("  - 提供商: {}, enabled: {}, supportsVision: {}",
                    p.getProviderName(), p.getEnabled(), getExtraParamAsBool(p, "supportsVision", false));
        }

        // 如果配置了首选提供商，将其排在最前面
        if (preferredProvider != null && !preferredProvider.isEmpty()) {
            providers.sort((a, b) -> {
                boolean aMatch = a.getProviderName().equalsIgnoreCase(preferredProvider);
                boolean bMatch = b.getProviderName().equalsIgnoreCase(preferredProvider);
                return Boolean.compare(bMatch, aMatch);
            });
            log.info("图像分析使用首选提供商: {}", preferredProvider);
        }

        List<String> errors = new ArrayList<>();
        for (AiProviderConfig provider : providers) {
            if (provider.getEnabled() == null || provider.getEnabled() != 1) {
                log.info("跳过未启用的提供商: {}", provider.getProviderName());
                continue;
            }
            // 检查提供商是否支持图像分析
            boolean supportsVision = getExtraParamAsBool(provider, "supportsVision", false);
            log.info("尝试提供商: {}, supportsVision: {}, providers.size: {}",
                    provider.getProviderName(), supportsVision, providers.size());
            if (!supportsVision && providers.size() > 1) {
                log.warn("提供商 {} 未标记支持图像分析，跳过", provider.getProviderName());
                continue;
            }
            try {
                log.info("调用视觉API: provider={}, model={}", provider.getProviderName(), provider.getDefaultModel());
                String result = callVisionApi(provider, imageUrl, prompt, isBase64);
                log.info("图像分析成功: provider={}", provider.getProviderName());
                return result;
            } catch (Exception e) {
                log.warn("图像分析失败，切换到下一个提供商: provider={}, error={}", provider.getProviderName(), e.getMessage(), e);
                errors.add(provider.getProviderName() + ": " + e.getMessage());
            }
        }
        log.error("所有LLM提供商图像分析调用失败，错误列表: {}", errors);
        throw new RuntimeException("所有LLM提供商图像分析调用失败: " + String.join("; ", errors));
    }

    public Map<String, Object> generateImageWithFallback(String prompt, String model, Integer width, Integer height,
            String referenceImageUrl) {
        List<AiProviderConfig> providers = getProvidersByType("IMAGE");
        if (providers.isEmpty()) {
            throw new RuntimeException("未配置可用的图片生成提供商");
        }

        List<String> errors = new ArrayList<>();
        for (AiProviderConfig provider : providers) {
            if (provider.getEnabled() == null || provider.getEnabled() != 1) {
                continue;
            }
            try {
                String useModel = (model != null && !model.isEmpty()) ? model : provider.getDefaultModel();
                Map<String, Object> result;

                // 检查是否使用模板配置（配置化方式）
                // 所有供应商都可以通过前端配置 requestConfig 来使用模板方式
                if (universalAdapter.isTemplateBased(provider)) {
                    log.info("使用模板配置调用图片生成: provider={}", provider.getProviderName());
                    String size = width != null && height != null ? width + "x" + height : "1024x1024";
                    result = universalAdapter.createImageWithTemplate(provider, prompt, useModel, referenceImageUrl,
                            size);
                } else {
                    // 使用传统方式（OpenAI格式，无配置时兼容）
                    log.info("使用传统方式调用图片生成: provider={}", provider.getProviderName());
                    result = callImageCreateApi(provider, prompt, useModel, width, height, referenceImageUrl);
                }

                log.info("图片生成调用成功: provider={}, model={}", provider.getProviderName(), useModel);
                return result;
            } catch (Exception e) {
                log.warn("图片生成调用失败，切换到下一个提供商: provider={}, error={}", provider.getProviderName(), e.getMessage());
                errors.add(provider.getProviderName() + ": " + e.getMessage());
            }
        }
        throw new RuntimeException("所有图片生成提供商调用失败: " + String.join("; ", errors));
    }

    public Map<String, Object> createVideoWithFallback(String prompt, String model, Boolean enhancePrompt,
            Boolean enableUpsample, List<String> images, String aspectRatio) {
        List<AiProviderConfig> providers = getProvidersByType("VIDEO");
        if (providers.isEmpty()) {
            throw new RuntimeException("未配置可用的视频生成提供商");
        }

        List<String> errors = new ArrayList<>();
        for (AiProviderConfig provider : providers) {
            if (provider.getEnabled() == null || provider.getEnabled() != 1) {
                continue;
            }
            try {
                // model为null时使用provider的defaultModel
                String useModel = (model != null && !model.isEmpty()) ? model : provider.getDefaultModel();

                Map<String, Object> result;

                // 检查是否使用模板配置（配置化方式）
                if (universalAdapter.isTemplateBased(provider)) {
                    // 使用通用模板适配器
                    log.info("使用模板配置调用视频生成: provider={}", provider.getProviderName());

                    // 处理图片：如果是 KIE.AI 且包含 base64，先上传获取 URL
                    List<String> processedImages = universalAdapter.processImagesForProvider(provider, images);

                    result = universalAdapter.createVideoWithTemplate(provider, prompt, useModel, processedImages,
                            aspectRatio);
                } else {
                    // 使用传统方式（OpenAI格式，无配置时兼容）
                    boolean useEnhancePrompt = enhancePrompt != null ? enhancePrompt
                            : getExtraParamAsBool(provider, "enhancePrompt", true);
                    boolean useEnableUpsample = enableUpsample != null ? enableUpsample
                            : getExtraParamAsBool(provider, "enableUpsample", true);
                    result = callVideoCreateApi(provider, prompt, useModel, useEnhancePrompt, useEnableUpsample, images,
                            aspectRatio);
                }

                // 记录成功使用的提供商名称，用于后续状态查询
                result.put("providerName", provider.getProviderName());
                log.info("VEO视频生成调用成功: provider={}, model={}", provider.getProviderName(), useModel);
                return result;
            } catch (Exception e) {
                log.warn("VEO视频生成调用失败，切换到下一个提供商: provider={}, error={}", provider.getProviderName(), e.getMessage());
                errors.add(provider.getProviderName() + ": " + e.getMessage());
            }
        }
        throw new RuntimeException("所有视频提供商调用失败: " + String.join("; ", errors));
    }

    public Map<String, Object> checkVideoStatusWithFallback(String taskId) {
        return checkVideoStatusWithFallback(taskId, null);
    }

    public Map<String, Object> checkVideoStatusWithFallback(String taskId, String providerName) {
        // 如果指定了providerName，只查询该供应商
        if (providerName != null && !providerName.isEmpty()) {
            AiProviderConfig provider = getProviderByName(providerName);
            if (provider == null) {
                throw new RuntimeException("指定的视频提供商不存在: " + providerName);
            }
            try {
                // 使用模板配置查询状态（配置化方式）
                if (universalAdapter.isTemplateBased(provider)) {
                    log.debug("使用模板配置查询视频状态: provider={}, taskId={}", providerName, taskId);
                    return universalAdapter.checkVideoStatusWithTemplate(provider, taskId);
                } else {
                    return callVideoStatusApi(provider, taskId);
                }
            } catch (Exception e) {
                log.warn("查询视频状态失败: provider={}, error={}", providerName, e.getMessage());
                throw new RuntimeException("查询视频状态失败: " + e.getMessage());
            }
        }

        // 否则遍历所有供应商（兼容旧逻辑）
        List<AiProviderConfig> providers = getProvidersByType("VIDEO");
        for (AiProviderConfig provider : providers) {
            try {
                // 使用模板配置查询状态（配置化方式）
                if (universalAdapter.isTemplateBased(provider)) {
                    log.debug("使用模板配置查询视频状态: provider={}, taskId={}", provider.getProviderName(), taskId);
                    return universalAdapter.checkVideoStatusWithTemplate(provider, taskId);
                } else {
                    return callVideoStatusApi(provider, taskId);
                }
            } catch (Exception e) {
                log.warn("查询视频状态失败: provider={}, error={}", provider.getProviderName(), e.getMessage());
            }
        }
        throw new RuntimeException("查询视频状态失败");
    }

    private AiProviderConfig getProviderByName(String providerName) {
        List<AiProviderConfig> providers = getProvidersByType("VIDEO");
        for (AiProviderConfig provider : providers) {
            if (providerName.equals(provider.getProviderName())) {
                return provider;
            }
        }
        return null;
    }

    public boolean testConnection(AiProviderConfig provider) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setBearerAuth(provider.getApiKey());

            Map<String, Object> body = new HashMap<>();
            body.put("model", provider.getDefaultModel());
            body.put("messages", List.of(Map.of("role", "user", "content", "hi")));
            body.put("max_tokens", 5);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    provider.getBaseUrl() + getExtraParam(provider, "chatEndpoint", "/v1/chat/completions"),
                    HttpMethod.POST,
                    entity,
                    String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("测试连接失败: provider={}", provider.getProviderName(), e);
            return false;
        }
    }

    @Transactional
    public AiProviderConfig createProvider(AiProviderConfig config) {
        // 验证必填字段
        if (config.getBaseUrl() == null || config.getBaseUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("base_url不能为空");
        }

        UnifiedAiConfig unified = new UnifiedAiConfig();
        unified.setConfigCode("PROV-" + System.currentTimeMillis());
        unified.setConfigName(config.getProviderName());
        unified.setProviderType(config.getProviderType());
        unified.setBaseUrl(config.getBaseUrl());
        unified.setApiKey(config.getApiKey());
        unified.setModels(config.getModels());
        unified.setDefaultModel(config.getDefaultModel());
        unified.setPriority(config.getPriority());
        unified.setEnabled(config.getEnabled());

        unified.setRequestTemplate(config.getRequestConfig());
        unified.setResponseTemplate(config.getResponseConfig());
        unified.setAuthTemplate(config.getAuthConfig());

        unified.setCapabilities(config.getCapabilities());
        unified.setQualityScore(config.getQualityScore());
        unified.setSpeedScore(config.getSpeedScore());
        unified.setCostRate(config.getCostRate());

        unified.setCreatedAt(java.time.LocalDateTime.now());
        unified.setUpdatedAt(java.time.LocalDateTime.now());
        unified.setDeleted(0);

        if (unified.getEnabled() == null)
            unified.setEnabled(1);
        if (unified.getPriority() == null)
            unified.setPriority(100);

        String taskTypes = "['chat']";
        if ("VIDEO".equals(config.getProviderType())) {
            taskTypes = "['video']";
        } else if ("IMAGE".equals(config.getProviderType())) {
            taskTypes = "['image']";
        } else if ("LLM".equals(config.getProviderType())) {
            taskTypes = "['chat', 'vision']";
        }
        unified.setTaskTypes(taskTypes);
        unified.setRoutingStrategy("balanced");

        unifiedAiConfigMapper.insert(unified);
        config.setId(unified.getId());

        clearCache();
        log.info("创建AI配置成功 (新架构): id={}, name={}", unified.getId(), unified.getConfigName());
        return config;
    }

    @Transactional
    public AiProviderConfig updateProvider(AiProviderConfig config) {
        UnifiedAiConfig existing = unifiedAiConfigMapper.selectById(config.getId());
        if (existing != null) {
            existing.setConfigName(config.getProviderName());
            existing.setProviderType(config.getProviderType());
            existing.setBaseUrl(config.getBaseUrl());
            existing.setApiKey(config.getApiKey());
            existing.setModels(config.getModels());
            existing.setDefaultModel(config.getDefaultModel());
            existing.setPriority(config.getPriority());
            existing.setEnabled(config.getEnabled());

            existing.setRequestTemplate(config.getRequestConfig());
            existing.setResponseTemplate(config.getResponseConfig());
            existing.setAuthTemplate(config.getAuthConfig());

            existing.setCapabilities(config.getCapabilities());
            existing.setQualityScore(config.getQualityScore());
            existing.setSpeedScore(config.getSpeedScore());
            existing.setCostRate(config.getCostRate());

            existing.setUpdatedAt(java.time.LocalDateTime.now());

            unifiedAiConfigMapper.updateById(existing);
            log.info("更新AI配置成功 (新架构): id={}, name={}", existing.getId(), existing.getConfigName());
        }

        clearCache();
        return config;
    }

    public void deleteProvider(Long id) {
        unifiedAiConfigMapper.deleteById(id);
        clearCache();
        log.info("删除AI配置成功 (新架构): id={}", id);
    }

    public void initDefaultProviders() {
        List<AiProvidersProperties.ProviderDefinition> definitions = providersProperties.getProviders();
        if (definitions == null || definitions.isEmpty()) {
            log.info("未配置AI.providers，跳过初始化提供商");
            return;
        }

        log.info("从YAML配置初始化AI提供商，共{}个配置项...", definitions.size());
        int created = 0;

        for (AiProvidersProperties.ProviderDefinition def : definitions) {
            if (def.getName() == null || def.getType() == null) {
                log.warn("跳过无效配置：缺少name或type字段: {}", def);
                continue;
            }

            // 检查数据库中是否已存在同名同类型的提供商
            Long existCount = aiProviderConfigMapper.selectCount(
                    new QueryWrapper<AiProviderConfig>()
                            .eq("deleted", 0)
                            .eq("provider_name", def.getName())
                            .eq("provider_type", def.getType()));
            if (existCount > 0) {
                log.debug("提供商已存在，跳过: name={}, type={}", def.getName(), def.getType());
                continue;
            }

            AiProviderConfig config = new AiProviderConfig();
            config.setProviderName(def.getName());
            config.setProviderType(def.getType());
            config.setBaseUrl(def.getBaseUrl());
            config.setApiKey(def.getApiKey());
            config.setDefaultModel(def.getDefaultModel());
            config.setModels(
                    def.getModels() != null ? toJsonArray(def.getModels()) : toJsonArray(def.getDefaultModel()));
            config.setPriority(def.getPriority() != null ? def.getPriority() : 1);
            config.setEnabled(def.getEnabled() != null && def.getEnabled() ? 1 : 0);
            config.setMaxRetries(def.getMaxRetries() != null ? def.getMaxRetries() : 2);
            config.setTimeoutMs(def.getTimeoutMs() != null ? def.getTimeoutMs() : 60000L);
            if (def.getCapabilities() != null) {
                config.setCapabilities(toJsonArray(def.getCapabilities()));
            }
            if (def.getExtraParams() != null) {
                config.setExtraParams(def.getExtraParams());
            }
            createProvider(config);
            created++;
            log.info("创建提供商配置: name={}, type={}, model={}", def.getName(), def.getType(), def.getDefaultModel());
        }

        log.info("AI提供商初始化完成: 创建{}个, 跳过{}个已存在", created, definitions.size() - created);
    }

    private String executeChatApi(AiProviderConfig provider, String systemPrompt, String userMessage,
            String model, List<Map<String, String>> historyMessages,
            Double temperature, Integer maxTokens) throws Exception {
        if (!aiRateLimiter.tryAcquire(5000)) {
            throw new RuntimeException("AI调用限流，请稍后重试");
        }
        try {
            String useModel = (model != null && !model.isEmpty()) ? model : provider.getDefaultModel();
            String chatEndpoint = getExtraParam(provider, "chatEndpoint", "/v1/chat/completions");
            String apiUrl = provider.getBaseUrl() + chatEndpoint;

            // 检查是否是KIE.AI的特殊格式（使用input而不是messages）
            boolean isKieFormat = "kie-ai-llm".equalsIgnoreCase(provider.getProviderName()) ||
                    "/codex/v1/responses".equals(chatEndpoint);

            Map<String, Object> requestBody;
            if (isKieFormat) {
                // KIE.AI格式：使用input数组，content也是数组格式
                List<Map<String, Object>> input = new ArrayList<>();
                if (systemPrompt != null && !systemPrompt.isEmpty()) {
                    Map<String, Object> systemMsg = new HashMap<>();
                    systemMsg.put("role", "system");
                    List<Map<String, Object>> systemContent = new ArrayList<>();
                    Map<String, Object> systemText = new HashMap<>();
                    systemText.put("type", "input_text");
                    systemText.put("text", systemPrompt);
                    systemContent.add(systemText);
                    systemMsg.put("content", systemContent);
                    input.add(systemMsg);
                }
                if (historyMessages != null && !historyMessages.isEmpty()) {
                    for (Map<String, String> histMsg : historyMessages) {
                        String role = histMsg.getOrDefault("role", "user");
                        String content = histMsg.getOrDefault("content", "");
                        if (!content.isEmpty()) {
                            Map<String, Object> histItem = new HashMap<>();
                            histItem.put("role", role);
                            List<Map<String, Object>> histContent = new ArrayList<>();
                            Map<String, Object> histText = new HashMap<>();
                            histText.put("type", "input_text");
                            histText.put("text", content);
                            histContent.add(histText);
                            histItem.put("content", histContent);
                            input.add(histItem);
                        }
                    }
                }
                Map<String, Object> userMsg = new HashMap<>();
                userMsg.put("role", "user");
                List<Map<String, Object>> userContent = new ArrayList<>();
                Map<String, Object> userText = new HashMap<>();
                userText.put("type", "input_text");
                userText.put("text", userMessage);
                userContent.add(userText);
                userMsg.put("content", userContent);
                input.add(userMsg);

                requestBody = new HashMap<>();
                requestBody.put("model", useModel);
                requestBody.put("input", input);
                requestBody.put("stream", false);
            } else {
                // 标准OpenAI格式
                List<Map<String, Object>> messages = new ArrayList<>();
                if (systemPrompt != null && !systemPrompt.isEmpty()) {
                    messages.add(Map.of("role", "system", "content", systemPrompt));
                }
                if (historyMessages != null && !historyMessages.isEmpty()) {
                    for (Map<String, String> histMsg : historyMessages) {
                        String role = histMsg.getOrDefault("role", "user");
                        String content = histMsg.getOrDefault("content", "");
                        if (!content.isEmpty()) {
                            messages.add(Map.of("role", role, "content", content));
                        }
                    }
                }
                messages.add(Map.of("role", "user", "content", userMessage));

                requestBody = new HashMap<>();
                requestBody.put("model", useModel);
                requestBody.put("messages", messages);
                requestBody.put("temperature", temperature != null ? temperature : 0.7);
                int defaultMaxTokens = sysConfigService.getIntConfig("ai_default_max_tokens", 4000);
                requestBody.put("max_tokens", maxTokens != null ? maxTokens : defaultMaxTokens);
                requestBody.put("stream", false);
            }

            HttpHeaders headers = createHeaders(provider);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.debug("调用AI API: provider={}, model={}, url={}, format={}",
                    provider.getProviderName(), useModel, apiUrl, isKieFormat ? "KIE" : "OpenAI");

            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

            JsonNode jsonResponse = objectMapper.readTree(response.getBody());

            // 尝试多种响应格式
            if (jsonResponse.has("choices") && jsonResponse.get("choices").size() > 0) {
                String content = jsonResponse.path("choices").get(0).path("message").path("content").asText();
                String finishReason = jsonResponse.path("choices").get(0).path("finish_reason").asText("unknown");
                if (content == null || content.trim().isEmpty()) {
                    log.warn("AI返回内容为空: finish_reason={}, model={}", finishReason, useModel);
                    throw new RuntimeException("AI返回内容为空(finish_reason=" + finishReason + ")");
                }
                return content;
            } else if (jsonResponse.has("output") && jsonResponse.get("output").isArray()
                    && jsonResponse.get("output").size() > 0) {
                // KIE.AI格式：output数组
                JsonNode output = jsonResponse.get("output").get(0);
                if (output.has("content") && output.get("content").isArray()) {
                    StringBuilder result = new StringBuilder();
                    for (JsonNode contentItem : output.get("content")) {
                        if (contentItem.has("text")) {
                            result.append(contentItem.get("text").asText());
                        }
                    }
                    if (result.length() > 0) {
                        return result.toString();
                    }
                }
            } else if (jsonResponse.has("reply")) {
                return jsonResponse.get("reply").asText();
            } else if (jsonResponse.has("data") && jsonResponse.get("data").has("text")) {
                return jsonResponse.get("data").get("text").asText();
            }
            throw new RuntimeException("AI API返回格式无法识别: " + response.getBody());
        } finally {
            aiRateLimiter.release();
        }
    }

    private void callChatStreamApi(AiProviderConfig provider, String systemPrompt, String userMessage,
            GptChatService.StreamCallback callback) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", provider.getDefaultModel());
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.7);
        int streamMaxTokens = sysConfigService.getIntConfig("ai_stream_max_tokens", 8000);
        requestBody.put("max_tokens", streamMaxTokens);
        requestBody.put("stream", true);
        requestBody.put("stream_options", Map.of("include_usage", true));

        HttpHeaders headers = createHeaders(provider);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        log.debug("开始流式调用: provider={}, model={}", provider.getProviderName(), provider.getDefaultModel());

        restTemplate.execute(
                provider.getBaseUrl() + getExtraParam(provider, "chatEndpoint", "/v1/chat/completions"),
                HttpMethod.POST,
                request -> {
                    request.getHeaders().addAll(headers);
                    objectMapper.writeValue(request.getBody(), requestBody);
                },
                response -> {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody()));
                    String line;
                    int chunkCount = 0;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if (!data.equals("[DONE]")) {
                                try {
                                    JsonNode chunk = objectMapper.readTree(data);
                                    JsonNode choices = chunk.path("choices");
                                    if (choices.isArray() && choices.size() > 0) {
                                        String content = choices.get(0).path("delta").path("content").asText("");
                                        if (!content.isEmpty()) {
                                            callback.onContent(content);
                                            chunkCount++;
                                        }
                                    }
                                } catch (Exception e) {
                                    log.warn("解析流式响应块失败: {}", data, e);
                                }
                            } else {
                                log.debug("流式调用完成: provider={}, chunks={}", provider.getProviderName(), chunkCount);
                                callback.onComplete();
                            }
                        }
                    }
                    return null;
                });
    }

    private String callVisionApi(AiProviderConfig provider, String imageUrl, String prompt, boolean isBase64)
            throws Exception {
        if (!aiRateLimiter.tryAcquire(5000)) {
            throw new RuntimeException("AI调用限流，请稍后重试");
        }
        try {
            String visionModel = getExtraParam(provider, "visionModel", provider.getDefaultModel());
            String chatEndpoint = getExtraParam(provider, "chatEndpoint", "/v1/chat/completions");
            String apiUrl = provider.getBaseUrl() + chatEndpoint;

            // 检查是否是KIE.AI的特殊格式
            boolean isKieFormat = "kie-ai-llm".equalsIgnoreCase(provider.getProviderName()) ||
                    "/codex/v1/responses".equals(chatEndpoint);

            Map<String, Object> requestBody;
            if (isKieFormat) {
                // KIE.AI格式：使用input数组，content也是数组格式
                List<Map<String, Object>> input = new ArrayList<>();

                Map<String, Object> userMsg = new HashMap<>();
                userMsg.put("role", "user");

                List<Map<String, Object>> contentList = new ArrayList<>();
                // 添加文本提示
                Map<String, Object> textContent = new HashMap<>();
                textContent.put("type", "input_text");
                textContent.put("text", prompt);
                contentList.add(textContent);

                // 添加图片 - 如果是本地URL，需要转换为base64
                String imageData = imageUrl;
                if (!isBase64 && (imageUrl.contains("localhost") || imageUrl.contains("127.0.0.1"))) {
                    try {
                        log.info("【图像分析】检测到本地URL，开始转换为base64: {}", imageUrl);
                        String base64Image = convertLocalImageToBase64(imageUrl);
                        if (base64Image != null) {
                            imageData = base64Image;
                            log.info("【图像分析】图片转换成功，base64长度: {}", base64Image.length());
                        } else {
                            log.warn("【图像分析】本地图片转换为base64失败: {}", imageUrl);
                        }
                    } catch (Exception e) {
                        log.error("【图像分析】转换图片为base64时出错", e);
                    }
                }

                Map<String, Object> imageContent = new HashMap<>();
                imageContent.put("type", "input_image");
                // KIE.AI可能需要base64格式的图片数据
                if (imageData.startsWith("data:image")) {
                    // 已经是base64格式，直接使用
                    imageContent.put("image_url", imageData);
                } else if (imageData.startsWith("http://") || imageData.startsWith("https://")) {
                    // URL格式
                    imageContent.put("image_url", imageData);
                } else {
                    // 可能是base64数据但没有前缀，添加前缀
                    imageContent.put("image_url", "data:image/jpeg;base64," + imageData);
                }
                contentList.add(imageContent);

                userMsg.put("content", contentList);
                input.add(userMsg);

                requestBody = new HashMap<>();
                requestBody.put("model", visionModel);
                requestBody.put("input", input);
            } else {
                // 标准OpenAI格式
                List<Map<String, Object>> contentList = new ArrayList<>();
                contentList.add(Map.of("type", "text", "text", prompt));
                contentList.add(Map.of("type", "image_url", "image_url", Map.of("url", imageUrl)));

                List<Map<String, Object>> messages = List.of(Map.of("role", "user", "content", contentList));

                requestBody = new HashMap<>();
                requestBody.put("model", visionModel);
                requestBody.put("messages", messages);
                int visionMaxTokens = sysConfigService.getIntConfig("ai_vision_max_tokens", 2000);
                requestBody.put("max_tokens", visionMaxTokens);
            }

            HttpHeaders headers = createHeaders(provider);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.debug("调用图像分析API: url={}, model={}, format={}, imageUrl={}",
                    apiUrl, visionModel, isKieFormat ? "KIE" : "OpenAI",
                    isBase64 ? "base64图片" : imageUrl);

            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    String.class);

            log.debug("图像分析API响应状态: {}", response.getStatusCode());

            // 处理SSE格式的响应
            String responseBody = response.getBody();
            String jsonContent = responseBody;

            // 记录原始响应（用于调试）
            log.debug("KIE.AI图像分析原始响应长度: {}", responseBody != null ? responseBody.length() : 0);
            if (responseBody != null && responseBody.length() < 5000) {
                log.debug("KIE.AI图像分析原始响应: {}", responseBody);
            }

            // 如果响应以"event:"开头，说明是SSE格式，需要特殊处理
            if (responseBody != null && responseBody.startsWith("event:")) {
                log.debug("检测到SSE格式响应，开始解析...");

                // KIE.AI的流式响应格式：每个event包含一个delta片段
                // 我们需要提取所有response.output_text.delta事件中的delta字段并拼接
                String[] lines = responseBody.split("\n");
                StringBuilder fullText = new StringBuilder();
                StringBuilder lastEventData = new StringBuilder();
                String currentEvent = "";

                for (String line : lines) {
                    line = line.trim();
                    if (line.startsWith("event:")) {
                        currentEvent = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        lastEventData.setLength(0);
                        lastEventData.append(data);

                        // 如果是response.output_text.delta事件，提取delta字段
                        if ("response.output_text.delta".equals(currentEvent)) {
                            try {
                                JsonNode deltaJson = objectMapper.readTree(data);
                                if (deltaJson.has("delta")) {
                                    fullText.append(deltaJson.get("delta").asText());
                                }
                            } catch (Exception e) {
                                log.debug("解析delta片段失败: {}", e.getMessage());
                            }
                        }
                    }
                }

                if (fullText.length() > 0) {
                    jsonContent = fullText.toString();
                    log.info("从SSE流式响应中提取的完整文本长度: {}", jsonContent.length());
                    if (jsonContent.length() < 3000) {
                        log.info("从SSE中提取的完整文本: {}", jsonContent);
                    }
                    // 直接返回文本，不需要再解析JSON
                    String result = jsonContent.trim();
                    if (result.isEmpty()) {
                        log.warn("图像分析返回内容为空");
                        throw new RuntimeException("图像分析返回内容为空");
                    }
                    log.debug("图像分析API返回结果长度: {}", result.length());
                    return result;
                } else {
                    log.warn("SSE响应中未找到有效的delta片段");
                }
            }

            JsonNode jsonResponse = objectMapper.readTree(jsonContent);
            log.debug("解析后的JSON结构: {}", jsonResponse.getNodeType());

            // 尝试多种响应格式
            String result = null;

            // 标准OpenAI格式
            if (jsonResponse.has("choices") && jsonResponse.get("choices").isArray()
                    && jsonResponse.get("choices").size() > 0) {
                log.debug("匹配到OpenAI choices格式");
                JsonNode choice = jsonResponse.get("choices").get(0);
                if (choice != null && choice.has("message") && choice.get("message").has("content")) {
                    result = choice.get("message").get("content").asText();
                }
            }
            // KIE.AI格式：output数组
            else if (jsonResponse.has("output") && jsonResponse.get("output").isArray()
                    && jsonResponse.get("output").size() > 0) {
                log.debug("匹配到KIE.AI output格式");
                JsonNode output = jsonResponse.get("output").get(0);
                if (output != null && output.has("content") && output.get("content").isArray()) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode contentItem : output.get("content")) {
                        if (contentItem.has("text")) {
                            sb.append(contentItem.get("text").asText());
                        }
                    }
                    if (sb.length() > 0) {
                        result = sb.toString();
                    }
                }
            }
            // 其他格式
            else if (jsonResponse.has("reply")) {
                log.debug("匹配到reply格式");
                result = jsonResponse.get("reply").asText();
            } else if (jsonResponse.has("data") && jsonResponse.get("data").has("text")) {
                log.debug("匹配到data.text格式");
                result = jsonResponse.get("data").get("text").asText();
            }
            // 尝试直接获取text字段
            else if (jsonResponse.has("text")) {
                log.debug("匹配到text格式");
                result = jsonResponse.get("text").asText();
            }
            // 尝试获取content字段（可能是字符串）
            else if (jsonResponse.has("content")) {
                log.debug("匹配到content格式");
                JsonNode contentNode = jsonResponse.get("content");
                if (contentNode.isTextual()) {
                    result = contentNode.asText();
                } else if (contentNode.isArray() && contentNode.size() > 0) {
                    // content是数组，尝试提取第一个元素的text
                    JsonNode firstItem = contentNode.get(0);
                    if (firstItem.has("text")) {
                        result = firstItem.get("text").asText();
                    }
                }
            }

            log.debug("最终解析结果: {}", result != null ? "成功，长度=" + result.length() : "失败");

            if (result == null || result.trim().isEmpty()) {
                log.warn("图像分析返回内容为空，完整响应: {}", response.getBody());
                throw new RuntimeException("图像分析返回内容为空");
            }

            log.debug("图像分析API返回结果长度: {}", result.length());
            return result;
        } finally {
            aiRateLimiter.release();
        }
    }

    private Map<String, Object> callImageCreateApi(AiProviderConfig provider, String prompt, String model,
            Integer width, Integer height, String referenceImageUrl) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model != null ? model : provider.getDefaultModel());
        requestBody.put("prompt", prompt);
        if (width != null) {
            requestBody.put("width", width);
        }
        if (height != null) {
            requestBody.put("height", height);
        }
        boolean referenceImageIncluded = false;
        int referenceImageLength = 0;
        String referenceImageFormat = "none";

        if (referenceImageUrl != null && !referenceImageUrl.isEmpty()) {
            referenceImageIncluded = true;
            if (referenceImageUrl.startsWith("data:image")) {
                // 已经是base64格式，直接使用
                requestBody.put("image", referenceImageUrl);
                referenceImageLength = referenceImageUrl.length();
                referenceImageFormat = "base64";
                log.info("【参考图传输】图片生成包含参考图片(base64格式), 长度: {}", referenceImageUrl.length());
            } else if (referenceImageUrl.startsWith("http://") || referenceImageUrl.startsWith("https://")) {
                // 检查是否是本地服务器地址（localhost/127.0.0.1），需要转换为base64
                if (referenceImageUrl.contains("localhost") || referenceImageUrl.contains("127.0.0.1")) {
                    try {
                        log.info("【参考图传输】检测到本地URL，开始转换为base64: {}", referenceImageUrl);
                        String base64Image = convertLocalImageToBase64(referenceImageUrl);
                        if (base64Image != null) {
                            requestBody.put("image", base64Image);
                            referenceImageLength = base64Image.length();
                            referenceImageFormat = "base64_converted";
                            log.info("【参考图传输】图片生成包含参考图片(本地URL转base64), 原始URL: {}, base64长度: {}",
                                    referenceImageUrl, base64Image.length());
                        } else {
                            log.warn("【参考图传输】本地图片转换为base64失败: {}", referenceImageUrl);
                            referenceImageIncluded = false;
                        }
                    } catch (Exception e) {
                        log.warn("【参考图传输】转换本地图片为base64失败: {}, 错误: {}", referenceImageUrl, e.getMessage());
                        referenceImageIncluded = false;
                    }
                } else {
                    // 外部URL，直接使用image_url
                    requestBody.put("image_url", referenceImageUrl);
                    referenceImageLength = referenceImageUrl.length();
                    referenceImageFormat = "external_url";
                    log.info("【参考图传输】图片生成包含参考图片(外部HTTP URL): {}", referenceImageUrl);
                }
            }
        } else {
            log.warn("【参考图传输】参考图片URL为空或null，本次生成不包含参考图！");
        }

        // 记录完整的请求参数（用于调试）
        log.info("【参考图传输】场景图生成参数: provider={}, model={}, referenceImageIncluded={}, " +
                "referenceImageLength={}, referenceImageFormat={}, promptLength={}",
                provider.getProviderName(), model, referenceImageIncluded,
                referenceImageLength, referenceImageFormat,
                prompt != null ? prompt.length() : 0);

        HttpHeaders headers = createHeaders(provider);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String imageCreateEndpoint = getExtraParam(provider, "imageCreateEndpoint", "/v1/images/generations");
        ResponseEntity<String> response = restTemplate.exchange(
                provider.getBaseUrl() + imageCreateEndpoint,
                HttpMethod.POST,
                entity,
                String.class);

        JsonNode json = objectMapper.readTree(response.getBody());
        Map<String, Object> result = new HashMap<>();

        if (json.has("data") && json.path("data").isArray() && json.path("data").size() > 0) {
            JsonNode firstImage = json.path("data").get(0);
            if (firstImage.has("url")) {
                result.put("url", firstImage.path("url").asText());
            } else if (firstImage.has("b64_json")) {
                result.put("b64_json", firstImage.path("b64_json").asText());
            }
        }

        if (!result.containsKey("url") && !result.containsKey("b64_json")) {
            throw new RuntimeException("图片生成API返回格式不支持: " + response.getBody());
        }

        log.info("图片生成API调用成功: provider={}", provider.getProviderName());
        return result;
    }

    private Map<String, Object> callVideoCreateApi(AiProviderConfig provider, String prompt, String model,
            Boolean enhancePrompt, Boolean enableUpsample,
            List<String> images, String aspectRatio) throws Exception {
        // 从extraParams读取视频创建端点，默认为/v1/video/create
        String videoCreateEndpoint = getExtraParam(provider, "videoCreateEndpoint", "/v1/video/create");

        // 检测是否为无忧科技(wuyin)类型的API
        boolean isWuyinApi = provider.getProviderName().contains("wuyin") ||
                videoCreateEndpoint.contains("wuyinkeji");

        Map<String, Object> requestBody = new HashMap<>();

        log.info("🎬🎬🎬 callVideoCreateApi 接收参数: provider={}, model={}, prompt长度={}, images数量={}, aspectRatio={}",
                provider.getProviderName(), model, prompt != null ? prompt.length() : 0,
                images != null ? images.size() : 0, aspectRatio);

        if (images != null && !images.isEmpty()) {
            String firstImg = images.get(0);
            log.info("  - 第一张图片: 类型={}, 长度={}",
                    firstImg != null ? (firstImg.startsWith("data:image") ? "base64" : "URL") : "null",
                    firstImg != null ? firstImg.length() : 0);
        }

        if (isWuyinApi) {
            // 无忧科技 Veo 3.1 Fast API 格式
            // https://api.wuyinkeji.com/api/async/video_veo3.1_fast
            requestBody.put("prompt", prompt);
            if (images != null && !images.isEmpty()) {
                // 使用firstFrameUrl作为首帧图片（根据API文档）
                String firstImageUrl = images.get(0);
                // 如果是base64格式，需要上传到服务器获取URL
                if (firstImageUrl.startsWith("data:image")) {
                    log.error("❌❌❌ 无忧科技API不支持base64格式图片！图片将被跳过！图片长度={}", firstImageUrl.length());
                    log.error("  这会导致视频生成无法参考场景图，生成结果可能与预期不符！");
                } else {
                    requestBody.put("firstFrameUrl", firstImageUrl);
                    log.info("✅✅✅ 无忧科技 Veo 视频生成包含首帧图片: {}", firstImageUrl);
                }
            } else {
                log.warn("⚠️⚠️⚠️ 没有图片传递给视频生成API！");
            }
            // 比例参数
            String finalAspectRatio = aspectRatio != null && !aspectRatio.isEmpty() ? aspectRatio
                    : getExtraParam(provider, "aspectRatio", "9:16");
            requestBody.put("aspectRatio", finalAspectRatio);
            // 清晰度参数
            String size = getExtraParam(provider, "size", "1080p");
            requestBody.put("size", size);
        } else {
            // 标准 Veo API 格式
            requestBody.put("model", model != null ? model : provider.getDefaultModel());
            requestBody.put("prompt", prompt);
            requestBody.put("enhance_prompt", enhancePrompt != null && enhancePrompt);
            requestBody.put("enable_upsample", enableUpsample != null && enableUpsample);
            if (images != null && !images.isEmpty()) {
                requestBody.put("images", images);
                log.info("VEO视频生成包含参考图片: {} 张", images.size());
                for (int i = 0; i < images.size(); i++) {
                    String img = images.get(i);
                    if (img != null && img.startsWith("data:image")) {
                        log.info("  图片[{}]: base64格式, 长度={}", i, img.length());
                    } else {
                        log.info("  图片[{}]: URL格式={}", i, img);
                    }
                }
            }
            if (aspectRatio != null && !aspectRatio.isEmpty()) {
                requestBody.put("aspect_ratio", aspectRatio);
            }
        }

        HttpHeaders headers = createHeaders(provider);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        log.info("VEO视频生成请求详情: url={}, provider={}, model={}, prompt_length={}",
                provider.getBaseUrl() + videoCreateEndpoint, provider.getProviderName(), model, prompt.length());
        log.debug("VEO视频生成请求体: {}", objectMapper.writeValueAsString(requestBody));

        ResponseEntity<String> response = restTemplate.exchange(
                provider.getBaseUrl() + videoCreateEndpoint,
                HttpMethod.POST,
                entity,
                String.class);

        log.info("VEO视频生成响应: status={}, body={}", response.getStatusCode(), response.getBody());

        JsonNode json = objectMapper.readTree(response.getBody());
        Map<String, Object> result = new HashMap<>();

        // 处理不同API的响应格式
        if (isWuyinApi) {
            // 无忧科技响应格式: {"code": 200, "msg": "成功", "data": {"id": "video_xxx", "count":
            // 10}}
            int code = json.path("code").asInt(0);
            if (code != 200) {
                String msg = json.path("msg").asText("未知错误");
                throw new RuntimeException("无忧科技API错误: " + msg);
            }
            JsonNode data = json.path("data");
            String taskId = data.path("id").asText("");
            result.put("id", taskId);
            result.put("status", "pending");
            result.put("status_update_time", System.currentTimeMillis() / 1000);
            result.put("provider", provider.getProviderName());
        } else {
            // 1. 标准响应: {"id":"veo3.1-fast:xxx","status":"pending","status_update_time":xxx}
            // 2. OpenAI格式响应:
            // {"id":"task_xxx","task_id":"task_xxx","object":"video","status":"queued","progress":0,"created_at":xxx}
            String taskId = json.path("id").asText("");
            if (json.has("task_id") && !json.path("task_id").asText("").isEmpty()) {
                taskId = json.path("task_id").asText();
            }
            result.put("id", taskId);

            String status = json.path("status").asText("");
            if ("queued".equals(status)) {
                status = "pending";
            }
            result.put("status", status);

            long updateTime = json.path("status_update_time").asLong(0);
            if (updateTime == 0) {
                updateTime = json.path("created_at").asLong(0);
            }
            result.put("status_update_time", updateTime);
            result.put("enhanced_prompt", json.path("enhanced_prompt").asText(""));

            if (json.has("progress")) {
                result.put("progress", json.path("progress").asInt(0));
            }
            if (json.has("size")) {
                result.put("size", json.path("size").asText(""));
            }
        }

        log.info("视频生成调用成功: provider={}, model={}, id={}, status={}",
                provider.getProviderName(), model, result.get("id"), result.get("status"));
        return result;
    }

    private Map<String, Object> callVideoStatusApi(AiProviderConfig provider, String taskId) throws Exception {
        // 检测是否为无忧科技(wuyin)类型的API
        boolean isWuyinApi = provider.getProviderName().contains("wuyin");

        HttpHeaders headers = createHeaders(provider);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // 从extraParams读取视频查询端点，默认为/v1/video/query?id=
        String videoQueryEndpoint = getExtraParam(provider, "videoQueryEndpoint", "/v1/video/query?id=");
        String url;

        if (isWuyinApi && videoQueryEndpoint.contains("?")) {
            // 无忧科技格式: /api/async/detail?key=xxx&id=
            url = provider.getBaseUrl() + videoQueryEndpoint + taskId;
        } else {
            url = provider.getBaseUrl() + videoQueryEndpoint + taskId;
        }
        log.debug("查询视频状态: url={}", url);

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                String.class);

        JsonNode json = objectMapper.readTree(response.getBody());
        Map<String, Object> result = new HashMap<>();

        // 处理不同API的响应格式
        if (isWuyinApi) {
            // 无忧科技响应格式: {"code": 200, "msg": "成功", "data": {"id": "video_xxx", "url":
            // "...", "status": "completed"}}
            int code = json.path("code").asInt(0);
            if (code != 200) {
                String msg = json.path("msg").asText("查询失败");
                throw new RuntimeException("无忧科技API查询错误: " + msg);
            }

            JsonNode data = json.path("data");
            result.put("id", data.path("id").asText(taskId));

            String status = data.path("status").asText("pending");
            // 映射无忧科技状态
            if ("completed".equals(status) || "success".equals(status)) {
                status = "completed";
            } else if ("failed".equals(status) || "error".equals(status)) {
                status = "failed";
            } else {
                status = "pending";
            }
            result.put("status", status);
            result.put("status_update_time", System.currentTimeMillis() / 1000);

            // 提取视频URL
            String videoUrl = data.path("url").asText("");
            if (videoUrl.isEmpty()) {
                videoUrl = data.path("video_url").asText("");
            }
            if (!videoUrl.isEmpty() && !"null".equals(videoUrl)) {
                result.put("video_url", videoUrl);
                log.info("无忧科技 - 提取到视频URL: {}",
                        videoUrl.length() > 100 ? videoUrl.substring(0, 100) + "..." : videoUrl);
            }

            return result;
        }

        // 标准VEO API响应格式处理
        // 标准响应: {"id":"xxx","status":"completed","video_url":"..."}
        // OpenAI格式响应:
        // {"id":"task_xxx","status":"completed","detail":{...,"output":{...,"video_url":"..."}}}
        String resultId = json.path("id").asText("");
        if (json.has("task_id") && !json.path("task_id").asText("").isEmpty()) {
            resultId = json.path("task_id").asText();
        }
        result.put("id", resultId);

        String status = json.path("status").asText("");
        // 处理VEO特有状态码
        if ("video_generation_completed".equals(status) || "video_upsampling_completed".equals(status)) {
            status = "completed";
        } else if ("video_generation_failed".equals(status) || "video_upsampling_failed".equals(status)) {
            status = "failed";
        } else if ("video_generating".equals(status) || "video_upsampling".equals(status)
                || "image_downloading".equals(status)) {
            status = "pending";
        } else if ("queued".equals(status)) {
            status = "pending";
        } else if ("succeeded".equals(status)) {
            status = "completed";
        }
        result.put("status", status);

        if ("failed".equals(status)) {
            log.error("VEO任务失败，taskId={}, 完整响应: {}", taskId, response.getBody());
        }

        long updateTime = json.path("status_update_time").asLong(0);
        if (updateTime == 0) {
            updateTime = json.path("created_at").asLong(0);
        }
        result.put("status_update_time", updateTime);

        // 提取视频URL
        String videoUrl = json.path("video_url").asText("");

        if ((videoUrl.isEmpty() || "null".equals(videoUrl)) && json.has("detail")) {
            JsonNode detail = json.path("detail");
            String detailVideoUrl = detail.path("video_url").asText("");
            if (!detailVideoUrl.isEmpty() && !"null".equals(detailVideoUrl)) {
                videoUrl = detailVideoUrl;
            }
            if ((videoUrl.isEmpty() || "null".equals(videoUrl)) && detail.has("output")) {
                JsonNode output = detail.path("output");
                videoUrl = output.path("video_url").asText("");
            }
            String upsampleUrl = detail.path("upsample_video_url").asText("");
            if (!upsampleUrl.isEmpty() && !"null".equals(upsampleUrl)) {
                videoUrl = upsampleUrl;
            }
        }
        if (!videoUrl.isEmpty() && !"null".equals(videoUrl)) {
            result.put("video_url", videoUrl);
            log.info("提取到视频URL: {}", videoUrl.length() > 100 ? videoUrl.substring(0, 100) + "..." : videoUrl);
        }

        // 从detail中读取错误信息
        if (json.has("detail")) {
            JsonNode detail = json.path("detail");
            String errorMsg = detail.path("error_message").asText("");
            String videoGenError = detail.path("video_generation_error").asText("");
            String failureReason = detail.path("failure_reason").asText("");
            if (!errorMsg.isEmpty())
                result.put("error", errorMsg);
            if (!videoGenError.isEmpty())
                result.put("video_generation_error", videoGenError);
            if (!failureReason.isEmpty())
                result.put("error", failureReason);
        }

        // 提取进度信息
        if (json.has("progress")) {
            result.put("progress", json.path("progress").asInt(0));
        }
        if (json.has("detail") && json.path("detail").has("pending_info")) {
            JsonNode pendingInfo = json.path("detail").path("pending_info");
            if (pendingInfo.has("progress_pct")) {
                result.put("progress_pct", pendingInfo.path("progress_pct").asDouble(0));
            }
        }

        if (json.has("error"))
            result.put("error", json.path("error").asText());
        return result;
    }

    private HttpHeaders createHeaders(AiProviderConfig provider) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        // 从extraParams读取认证头格式，默认为bearer
        // authHeaderFormat: "bearer" (默认), "x-api-key", "api-key", "plain" 等
        String authFormat = getExtraParam(provider, "authHeaderFormat", "bearer");
        switch (authFormat.toLowerCase()) {
            case "x-api-key":
                headers.set("X-API-Key", provider.getApiKey());
                break;
            case "api-key":
                headers.set("Api-Key", provider.getApiKey());
                break;
            case "plain":
                // 无忧科技格式: Authorization: 接口密钥
                headers.set("Authorization", provider.getApiKey());
                break;
            case "bearer":
            default:
                headers.setBearerAuth(provider.getApiKey());
                break;
        }

        return headers;
    }

    private void refreshCacheIfNeeded() {
        long now = System.currentTimeMillis();
        long cacheTtlMs = sysConfigService.getLongConfig("ai_provider_cache_ttl_ms", 30000);
        if (now - cacheTimestamp < cacheTtlMs && !configCache.isEmpty()) {
            return;
        }
        synchronized (configCache) {
            if (now - cacheTimestamp >= cacheTtlMs || configCache.isEmpty()) {
                loadConfigs();
                cacheTimestamp = now;
            }
        }
    }

    private void loadConfigs() {
        List<UnifiedAiConfig> allUnified = unifiedAiConfigMapper.selectList(
                new QueryWrapper<UnifiedAiConfig>()
                        .eq("deleted", 0)
                        .eq("enabled", 1)
                        .orderByAsc("priority")
                        .orderByDesc("id"));

        List<AiProviderConfig> allLegacy = configAdapter.convertToLegacyList(allUnified);

        Map<String, List<AiProviderConfig>> grouped = new LinkedHashMap<>();
        for (AiProviderConfig cfg : allLegacy) {
            grouped.computeIfAbsent(cfg.getProviderType(), k -> new ArrayList<>()).add(cfg);
        }
        configCache.clear();
        configCache.putAll(grouped);
        log.info("从unified_ai_config加载AI配置: 共 {} 个有效配置 (新架构)", allLegacy.size());
    }

    public void refreshCache() {
        clearCache();
    }

    private void clearCache() {
        synchronized (configCache) {
            configCache.clear();
            cacheTimestamp = 0;
        }
    }

    /**
     * 从provider的extraParams(JSON)中读取字符串类型的配置值
     * extraParams示例:
     * {"chatEndpoint":"/v1/text/chatcompletions_v2","visionModel":"gpt-4o","enhancePrompt":true}
     */
    private String getExtraParam(AiProviderConfig provider, String key, String defaultValue) {
        if (provider.getExtraParams() == null || provider.getExtraParams().isEmpty()) {
            return defaultValue;
        }
        try {
            JsonNode node = objectMapper.readTree(provider.getExtraParams());
            if (node.has(key)) {
                String val = node.get(key).asText(defaultValue);
                return val != null && !val.isEmpty() && !"null".equals(val) ? val : defaultValue;
            }
        } catch (Exception e) {
            log.debug("解析extraParams失败: provider={}, key={}", provider.getProviderName(), key);
        }
        return defaultValue;
    }

    /**
     * 从provider的extraParams(JSON)中读取布尔类型的配置值
     */
    private boolean getExtraParamAsBool(AiProviderConfig provider, String key, boolean defaultValue) {
        if (provider.getExtraParams() == null || provider.getExtraParams().isEmpty()) {
            return defaultValue;
        }
        try {
            JsonNode node = objectMapper.readTree(provider.getExtraParams());
            if (node.has(key)) {
                return node.get(key).asBoolean(defaultValue);
            }
        } catch (Exception e) {
            log.debug("解析extraParams失败: provider={}, key={}", provider.getProviderName(), key);
        }
        return defaultValue;
    }

    /**
     * 将逗号分隔的字符串转换为JSON数组字符串格式
     * 示例: "a,b,c" -> "[\"a\",\"b\",\"c\"]"
     */
    private String toJsonArray(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.trim().isEmpty()) {
            return "[]";
        }
        String[] parts = commaSeparated.split(",");
        List<String> list = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    public List<Map<String, Object>> getAvailableModels() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<AiProviderConfig> providers = getAllProviders();

        for (AiProviderConfig provider : providers) {
            if (provider.getEnabled() == null || provider.getEnabled() != 1) {
                continue;
            }

            Map<String, Object> providerInfo = new HashMap<>();
            providerInfo.put("providerId", provider.getId());
            providerInfo.put("providerName", provider.getProviderName());
            providerInfo.put("providerType", provider.getProviderType());
            providerInfo.put("defaultModel", provider.getDefaultModel());
            providerInfo.put("priority", provider.getPriority());

            List<String> models = new ArrayList<>();
            if (provider.getModels() != null && !provider.getModels().isEmpty()) {
                try {
                    models = objectMapper.readValue(provider.getModels(), new TypeReference<List<String>>() {
                    });
                } catch (Exception e) {
                    log.warn("解析模型列表失败，使用默认模型: {}", provider.getModels());
                    models.add(provider.getDefaultModel());
                }
            } else {
                models.add(provider.getDefaultModel());
            }
            providerInfo.put("models", models);

            result.add(providerInfo);
        }

        return result;
    }

    private List<String> getProviderModels(AiProviderConfig provider) {
        List<String> models = new ArrayList<>();
        if (provider.getModels() != null && !provider.getModels().isEmpty()) {
            try {
                models = objectMapper.readValue(provider.getModels(), new TypeReference<List<String>>() {
                });
            } catch (Exception e) {
                models.add(provider.getDefaultModel());
            }
        } else {
            models.add(provider.getDefaultModel());
        }
        return models;
    }

    private String callChatApiWithModel(AiProviderConfig provider, String systemPrompt, String userMessage,
            String model, Double temperature, Integer maxTokens, boolean stream) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", temperature != null ? temperature : 0.7);
        int defaultMaxTokens = sysConfigService.getIntConfig("ai_default_max_tokens", 4000);
        requestBody.put("max_tokens", maxTokens != null ? maxTokens : defaultMaxTokens);
        requestBody.put("stream", stream);

        HttpHeaders headers = createHeaders(provider);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String chatEndpoint = getExtraParam(provider, "chatEndpoint", "/v1/chat/completions");
        String apiUrl = provider.getBaseUrl() + chatEndpoint;

        log.debug("调用AI API: provider={}, model={}, url={}", provider.getProviderName(), model, apiUrl);

        ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                entity,
                String.class);

        log.debug("AI API 响应: status={}, body={}", response.getStatusCode(),
                response.getBody() != null && response.getBody().length() > 500
                        ? response.getBody().substring(0, 500) + "..."
                        : response.getBody());

        JsonNode jsonResponse = objectMapper.readTree(response.getBody());

        if (jsonResponse.has("choices") && jsonResponse.get("choices").size() > 0) {
            String content = jsonResponse.path("choices").get(0).path("message").path("content").asText();
            String finishReason = jsonResponse.path("choices").get(0).path("finish_reason").asText("unknown");
            log.info("AI返回内容长度: {}, finish_reason={}", content.length(), finishReason);

            if (content == null || content.trim().isEmpty()) {
                log.warn("AI返回内容为空: finish_reason={}, model={}", finishReason, model);
                throw new RuntimeException("AI返回内容为空(finish_reason=" + finishReason + ")");
            }

            return content;
        } else if (jsonResponse.has("reply")) {
            return jsonResponse.get("reply").asText();
        } else if (jsonResponse.has("data") && jsonResponse.get("data").has("text")) {
            return jsonResponse.get("data").get("text").asText();
        } else {
            log.warn("AI API 返回格式无法识别: {}", response.getBody());
            throw new RuntimeException("AI API返回格式无法识别: " + response.getBody());
        }
    }

    /**
     * 将本地图片URL转换为base64格式
     * 用于外部AI服务无法访问本地localhost地址的情况
     */
    private String convertLocalImageToBase64(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }

        try {
            byte[] imageBytes = null;
            String mimeType = "image/png";

            // 从本地HTTP URL下载图片
            if (imageUrl.startsWith("http://localhost") || imageUrl.startsWith("http://127.0.0.1")) {
                java.net.URL url = new java.net.URL(imageUrl);
                try (java.io.InputStream is = url.openStream()) {
                    imageBytes = is.readAllBytes();
                }
                // 从URL路径推断MIME类型
                String path = url.getPath();
                mimeType = getMimeTypeFromFileName(path);
            } else {
                log.warn("不支持的本地图片URL格式: {}", imageUrl);
                return null;
            }

            if (imageBytes != null && imageBytes.length > 0) {
                String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
                return "data:" + mimeType + ";base64," + base64;
            }
        } catch (Exception e) {
            log.warn("转换本地图片为base64失败: {}, 错误: {}", imageUrl, e.getMessage());
        }
        return null;
    }

    /**
     * 从文件名获取MIME类型
     */
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
}
