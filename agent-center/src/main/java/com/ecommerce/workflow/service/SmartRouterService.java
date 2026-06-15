package com.ecommerce.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.workflow.entity.AiCallLog;
import com.ecommerce.workflow.entity.AiProviderConfig;
import com.ecommerce.workflow.entity.AiRouteRule;
import com.ecommerce.workflow.mapper.AiCallLogMapper;
import com.ecommerce.workflow.mapper.AiModelCapabilityMapper;
import com.ecommerce.workflow.mapper.AiProviderConfigMapper;
import com.ecommerce.workflow.mapper.AiRouteRuleMapper;
import com.ecommerce.workflow.service.config.SysConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SmartRouterService {
    
    private static final Logger logger = LoggerFactory.getLogger(SmartRouterService.class);
    private static final String CACHE_KEY_PREFIX = "smart_router:";
    
    @Autowired
    private SysConfigService sysConfigService;
    
    @Autowired
    private AiProviderConfigMapper providerMapper;
    
    @Autowired
    private AiRouteRuleMapper routeRuleMapper;
    
    @Autowired
    private AiModelCapabilityMapper capabilityMapper;
    
    @Autowired
    private AiCallLogMapper callLogMapper;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private final Map<String, List<AiProviderConfig>> providerCache = new ConcurrentHashMap<>();
    private final Map<String, AiRouteRule> ruleCache = new ConcurrentHashMap<>();
    
    public static class RouteResult {
        private AiProviderConfig provider;
        private String modelName;
        private String strategy;
        private String traceId;
        
        public AiProviderConfig getProvider() {
            return provider;
        }
        
        public void setProvider(AiProviderConfig provider) {
            this.provider = provider;
        }
        
        public String getModelName() {
            return modelName;
        }
        
        public void setModelName(String modelName) {
            this.modelName = modelName;
        }
        
        public String getStrategy() {
            return strategy;
        }
        
        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }
        
        public String getTraceId() {
            return traceId;
        }
        
        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }
    }
    
    public static class RouteRequest {
        private String taskType;
        private String strategy;
        private String preferredModel;
        private String excludeProvider;
        private Map<String, Object> context;
        
        public String getTaskType() {
            return taskType;
        }
        
        public void setTaskType(String taskType) {
            this.taskType = taskType;
        }
        
        public String getStrategy() {
            return strategy;
        }
        
        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }
        
        public String getPreferredModel() {
            return preferredModel;
        }
        
        public void setPreferredModel(String preferredModel) {
            this.preferredModel = preferredModel;
        }
        
        public String getExcludeProvider() {
            return excludeProvider;
        }
        
        public void setExcludeProvider(String excludeProvider) {
            this.excludeProvider = excludeProvider;
        }
        
        public Map<String, Object> getContext() {
            return context;
        }
        
        public void setContext(Map<String, Object> context) {
            this.context = context;
        }
    }
    
    public RouteResult route(RouteRequest request) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        RouteResult result = new RouteResult();
        result.setTraceId(traceId);
        
        // Try Redis cache first
        String cacheKey = CACHE_KEY_PREFIX + request.getTaskType() + ":" + request.getStrategy();
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof RouteResult) {
                RouteResult cachedResult = (RouteResult) cached;
                result.setProvider(cachedResult.getProvider());
                result.setModelName(cachedResult.getModelName());
                result.setStrategy(cachedResult.getStrategy());
                logger.debug("Smart router cache hit: key={}", cacheKey);
                return result;
            }
        } catch (Exception e) {
            logger.debug("Redis cache read failed, fallback to direct routing: {}", e.getMessage());
        }
        
        try {
            String taskType = request.getTaskType() != null ? request.getTaskType() : "chat";
            String strategy = request.getStrategy();
            
            AiRouteRule rule = routeRuleMapper.findBestRuleByTaskType(taskType);
            if (rule != null && strategy == null) {
                strategy = rule.getStrategy();
            }
            if (strategy == null) {
                strategy = "balanced";
            }
            
            result.setStrategy(strategy);
            
            List<AiProviderConfig> providers = getAvailableProviders(request.getExcludeProvider());
            if (providers.isEmpty()) {
                logger.error("No available AI providers found");
                return null;
            }
            
            List<String> preferredModels = new ArrayList<>();
            List<String> excludedModels = new ArrayList<>();
            
            if (rule != null) {
                preferredModels = parseJsonArray(rule.getPreferredModels());
                excludedModels = parseJsonArray(rule.getExcludedModels());
            }
            
            if (request.getPreferredModel() != null) {
                preferredModels.add(0, request.getPreferredModel());
            }
            
            AiProviderConfig selectedProvider = null;
            String selectedModel = null;
            
            if (!preferredModels.isEmpty()) {
                for (String model : preferredModels) {
                    if (excludedModels.contains(model)) {
                        continue;
                    }
                    selectedProvider = findProviderForModel(providers, model);
                    if (selectedProvider != null) {
                        selectedModel = model;
                        break;
                    }
                }
            }
            
            if (selectedProvider == null) {
                selectedProvider = selectByStrategy(providers, taskType, strategy, excludedModels);
                if (selectedProvider != null) {
                    selectedModel = selectModelByStrategy(selectedProvider, taskType, strategy);
                }
            }
            
            if (selectedProvider == null) {
                selectedProvider = providers.get(0);
                selectedModel = selectedProvider.getDefaultModel();
            }
            
            result.setProvider(selectedProvider);
            result.setModelName(selectedModel);
            
            logger.info("Smart router selected: provider={}, model={}, strategy={}, taskType={}", 
                selectedProvider.getProviderName(), selectedModel, strategy, taskType);
            
            // Cache the routing result
            try {
                RouteResult cacheResult = new RouteResult();
                cacheResult.setProvider(selectedProvider);
                cacheResult.setModelName(selectedModel);
                cacheResult.setStrategy(strategy);
                long cacheTtlSeconds = sysConfigService.getLongConfig("smart_router_cache_ttl_seconds", 300);
                redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + taskType + ":" + strategy, cacheResult, 
                        java.time.Duration.ofSeconds(cacheTtlSeconds));
            } catch (Exception e) {
                logger.debug("Redis cache write failed: {}", e.getMessage());
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Smart router error: {}", e.getMessage(), e);
            return null;
        }
    }
    
    private AiProviderConfig selectByStrategy(List<AiProviderConfig> providers, String taskType, String strategy, List<String> excludedModels) {
        List<AiProviderConfig> candidates = providers.stream()
            .filter(p -> p.getEnabled() == 1)
            .filter(p -> !isProviderExcluded(p, excludedModels))
            .collect(Collectors.toList());
        
        if (candidates.isEmpty()) {
            return null;
        }
        
        switch (strategy) {
            case "cost_first":
                return selectByCost(candidates);
            case "quality_first":
                return selectByQuality(candidates, taskType);
            case "speed_first":
                return selectBySpeed(candidates);
            case "balanced":
            default:
                return selectByBalanced(candidates, taskType);
        }
    }
    
    private AiProviderConfig selectByCost(List<AiProviderConfig> providers) {
        return providers.stream()
            .min(Comparator.comparing(p -> {
                BigDecimal rate = p.getCostRate();
                return rate != null ? rate : BigDecimal.ONE;
            }))
            .orElse(null);
    }
    
    private AiProviderConfig selectByQuality(List<AiProviderConfig> providers, String taskType) {
        return providers.stream()
            .max(Comparator.comparing(p -> {
                BigDecimal quality = p.getQualityScore();
                BigDecimal capability = getModelCapabilityScore(p.getDefaultModel(), taskType);
                return quality.multiply(capability);
            }))
            .orElse(null);
    }
    
    private AiProviderConfig selectBySpeed(List<AiProviderConfig> providers) {
        return providers.stream()
            .max(Comparator.comparing(p -> {
                BigDecimal speed = p.getSpeedScore();
                return speed != null ? speed : new BigDecimal("0.8");
            }))
            .orElse(null);
    }
    
    private AiProviderConfig selectByBalanced(List<AiProviderConfig> providers, String taskType) {
        return providers.stream()
            .max(Comparator.comparing(p -> {
                BigDecimal quality = p.getQualityScore() != null ? p.getQualityScore() : new BigDecimal("0.8");
                BigDecimal speed = p.getSpeedScore() != null ? p.getSpeedScore() : new BigDecimal("0.8");
                BigDecimal costRate = p.getCostRate() != null ? p.getCostRate() : BigDecimal.ONE;
                BigDecimal capability = getModelCapabilityScore(p.getDefaultModel(), taskType);
                
                BigDecimal costScore = BigDecimal.ONE.divide(costRate, 4, RoundingMode.HALF_UP);
                
                return quality.multiply(new BigDecimal("0.3"))
                    .add(speed.multiply(new BigDecimal("0.2")))
                    .add(costScore.multiply(new BigDecimal("0.3")))
                    .add(capability.multiply(new BigDecimal("0.2")));
            }))
            .orElse(null);
    }
    
    private String selectModelByStrategy(AiProviderConfig provider, String taskType, String strategy) {
        List<String> models = parseJsonArray(provider.getModels());
        if (models.isEmpty()) {
            return provider.getDefaultModel();
        }
        
        if ("quality_first".equals(strategy)) {
            return models.stream()
                .max(Comparator.comparing(m -> getModelCapabilityScore(m, taskType)))
                .orElse(provider.getDefaultModel());
        }
        
        return provider.getDefaultModel();
    }
    
    private BigDecimal getModelCapabilityScore(String modelName, String taskType) {
        if (modelName == null || taskType == null) {
            return new BigDecimal("0.8");
        }
        
        try {
            BigDecimal score = capabilityMapper.getScore(modelName, taskType);
            return score != null ? score : new BigDecimal("0.8");
        } catch (Exception e) {
            return new BigDecimal("0.8");
        }
    }
    
    private AiProviderConfig findProviderForModel(List<AiProviderConfig> providers, String modelName) {
        return providers.stream()
            .filter(p -> {
                List<String> models = parseJsonArray(p.getModels());
                return models.contains(modelName);
            })
            .filter(p -> p.getEnabled() == 1)
            .findFirst()
            .orElse(null);
    }
    
    private boolean isProviderExcluded(AiProviderConfig provider, List<String> excludedModels) {
        if (excludedModels == null || excludedModels.isEmpty()) {
            return false;
        }
        List<String> providerModels = parseJsonArray(provider.getModels());
        return excludedModels.stream().anyMatch(providerModels::contains);
    }
    
    private List<AiProviderConfig> getAvailableProviders(String excludeProviderId) {
        LambdaQueryWrapper<AiProviderConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiProviderConfig::getEnabled, 1)
               .eq(AiProviderConfig::getDeleted, 0);
        
        if (excludeProviderId != null && !excludeProviderId.isEmpty()) {
            wrapper.ne(AiProviderConfig::getId, Long.parseLong(excludeProviderId));
        }
        
        wrapper.orderByAsc(AiProviderConfig::getPriority);
        
        return providerMapper.selectList(wrapper);
    }
    
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            logger.warn("Failed to parse JSON array: {}", json);
            return new ArrayList<>();
        }
    }
    
    @Async
    public void logCall(AiCallLog log) {
        try {
            callLogMapper.insert(log);
        } catch (Exception e) {
            logger.error("Failed to log AI call: {}", e.getMessage());
        }
    }
    
    public AiCallLog createCallLog(RouteResult result, Integer inputTokens, Integer outputTokens, 
                                    Integer latencyMs, boolean success, String errorMessage) {
        AiCallLog log = new AiCallLog();
        log.setTraceId(result.getTraceId());
        log.setProviderId(result.getProvider().getId());
        log.setProviderName(result.getProvider().getProviderName());
        log.setModelName(result.getModelName());
        log.setStrategy(result.getStrategy());
        log.setInputTokens(inputTokens);
        log.setOutputTokens(outputTokens);
        log.setLatencyMs(latencyMs);
        log.setSuccess(success ? 1 : 0);
        log.setErrorMessage(errorMessage);
        log.setCreatedAt(LocalDateTime.now());
        
        BigDecimal cost = calculateCost(result.getModelName(), inputTokens, outputTokens, result.getProvider().getCostRate());
        log.setCostAmount(cost);
        
        return log;
    }
    
    private BigDecimal calculateCost(String modelName, Integer inputTokens, Integer outputTokens, BigDecimal costRate) {
        BigDecimal baseCost = new BigDecimal("0.001");
        
        if (modelName != null) {
            if (modelName.contains("gpt-4")) {
                baseCost = new BigDecimal("0.03");
            } else if (modelName.contains("gpt-3.5")) {
                baseCost = new BigDecimal("0.001");
            } else if (modelName.contains("claude-3-opus")) {
                baseCost = new BigDecimal("0.015");
            } else if (modelName.contains("claude-3-5")) {
                baseCost = new BigDecimal("0.003");
            } else if (modelName.contains("deepseek")) {
                baseCost = new BigDecimal("0.0001");
            } else if (modelName.contains("gemini-1.5-pro")) {
                baseCost = new BigDecimal("0.0035");
            }
        }
        
        BigDecimal tokenCost = baseCost.multiply(new BigDecimal(inputTokens + outputTokens))
            .divide(new BigDecimal("1000"), 6, RoundingMode.HALF_UP);
        
        if (costRate != null) {
            tokenCost = tokenCost.multiply(costRate);
        }
        
        return tokenCost;
    }
    
    public void markProviderSuccess(Long providerId) {
        try {
            AiProviderConfig provider = providerMapper.selectById(providerId);
            if (provider != null) {
                provider.setFailCount(0);
                provider.setLastSuccessAt(LocalDateTime.now());
                providerMapper.updateById(provider);
            }
        } catch (Exception e) {
            logger.error("Failed to mark provider success: {}", e.getMessage());
        }
    }
    
    public void markProviderFailure(Long providerId) {
        try {
            AiProviderConfig provider = providerMapper.selectById(providerId);
            if (provider != null) {
                provider.setFailCount(provider.getFailCount() + 1);
                provider.setLastFailAt(LocalDateTime.now());
                
                if (provider.getFailCount() >= 3) {
                    provider.setEnabled(0);
                    logger.warn("Provider {} disabled due to consecutive failures", provider.getProviderName());
                }
                
                providerMapper.updateById(provider);
            }
        } catch (Exception e) {
            logger.error("Failed to mark provider failure: {}", e.getMessage());
        }
    }
    
    public void refreshCache() {
        providerCache.clear();
        ruleCache.clear();
        logger.info("Smart router cache refreshed");
    }
}
