package com.ecommerce.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.workflow.entity.UnifiedAiConfig;
import com.ecommerce.workflow.mapper.UnifiedAiConfigMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AiConfigUnifiedService {
    
    private static final Logger logger = LoggerFactory.getLogger(AiConfigUnifiedService.class);
    private static final String CACHE_KEY_PREFIX = "unified_ai_config:";
    private static final long CACHE_EXPIRE_HOURS = 1;
    
    @Autowired
    private UnifiedAiConfigMapper configMapper;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 获取所有启用的配置（带缓存）
     */
    public List<UnifiedAiConfig> getAllEnabledConfigs() {
        String cacheKey = CACHE_KEY_PREFIX + "all_enabled";
        
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.convertValue(cached, new TypeReference<List<UnifiedAiConfig>>() {});
            }
        } catch (Exception e) {
            logger.warn("读取缓存失败: {}", e.getMessage());
        }
        
        List<UnifiedAiConfig> configs = configMapper.selectList(
            new LambdaQueryWrapper<UnifiedAiConfig>()
                .eq(UnifiedAiConfig::getEnabled, 1)
                .orderByAsc(UnifiedAiConfig::getPriority)
        );
        
        try {
            redisTemplate.opsForValue().set(cacheKey, configs, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            logger.warn("写入缓存失败: {}", e.getMessage());
        }
        
        return configs;
    }
    
    /**
     * 按类型获取配置（带缓存）
     */
    public List<UnifiedAiConfig> getConfigsByType(String providerType) {
        String cacheKey = CACHE_KEY_PREFIX + "type:" + providerType;
        
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.convertValue(cached, new TypeReference<List<UnifiedAiConfig>>() {});
            }
        } catch (Exception e) {
            logger.warn("读取缓存失败: {}", e.getMessage());
        }
        
        List<UnifiedAiConfig> configs = configMapper.selectList(
            new LambdaQueryWrapper<UnifiedAiConfig>()
                .eq(UnifiedAiConfig::getProviderType, providerType)
                .eq(UnifiedAiConfig::getEnabled, 1)
                .orderByAsc(UnifiedAiConfig::getPriority)
        );
        
        try {
            redisTemplate.opsForValue().set(cacheKey, configs, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            logger.warn("写入缓存失败: {}", e.getMessage());
        }
        
        return configs;
    }
    
    /**
     * 根据任务类型获取符合条件的配置
     */
    public List<UnifiedAiConfig> getEligibleConfigs(String taskType) {
        List<UnifiedAiConfig> allConfigs = getAllEnabledConfigs();
        
        return allConfigs.stream()
            .filter(config -> supportsTaskType(config, taskType))
            .filter(this::isHealthy)
            .collect(Collectors.toList());
    }
    
    /**
     * 检查配置是否支持指定任务类型
     */
    private boolean supportsTaskType(UnifiedAiConfig config, String taskType) {
        if (config.getTaskTypes() == null || config.getTaskTypes().isEmpty()) {
            return false;
        }
        
        try {
            List<String> taskTypes = objectMapper.readValue(
                config.getTaskTypes(), 
                new TypeReference<List<String>>() {}
            );
            return taskTypes.contains(taskType.toLowerCase());
        } catch (Exception e) {
            logger.warn("解析taskTypes失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查配置是否健康（未超过最大失败次数）
     */
    private boolean isHealthy(UnifiedAiConfig config) {
        Integer failCount = config.getFailCount();
        return failCount == null || failCount < 3;
    }
    
    /**
     * 智能选择最优配置 - 核心方法
     */
    public UnifiedAiConfig selectBestConfig(String taskType, String strategy) {
        List<UnifiedAiConfig> eligibleConfigs = getEligibleConfigs(taskType);
        
        if (eligibleConfigs.isEmpty()) {
            throw new RuntimeException("无可用AI配置，任务类型: " + taskType);
        }
        
        switch (strategy.toLowerCase()) {
            case "priority":
                return eligibleConfigs.stream()
                    .min(Comparator.comparingInt(c -> c.getPriority() != null ? c.getPriority() : 100))
                    .orElse(eligibleConfigs.get(0));
                    
            case "quality":
                return eligibleConfigs.stream()
                    .max(Comparator.comparingDouble(c -> 
                        c.getQualityScore() != null ? c.getQualityScore().doubleValue() : 0.80))
                    .orElse(eligibleConfigs.get(0));
                    
            case "cost":
                return eligibleConfigs.stream()
                    .min(Comparator.comparingDouble(c ->
                        c.getCostRate() != null ? c.getCostRate().doubleValue() : 1.0000))
                    .orElse(eligibleConfigs.get(0));
                    
            default: // balanced策略
                return balancedSelection(eligibleConfigs);
        }
    }
    
    /**
     * 平衡策略：综合考虑质量、速度、成本
     */
    private UnifiedAiConfig balancedSelection(List<UnifiedAiConfig> configs) {
        return configs.stream()
            .max(Comparator.comparingDouble(c -> calculateScore(c)))
            .orElse(configs.get(0));
    }
    
    /**
     * 计算综合评分
     */
    private double calculateScore(UnifiedAiConfig config) {
        double quality = config.getQualityScore() != null ? config.getQualityScore().doubleValue() : 0.80;
        double speed = config.getSpeedScore() != null ? config.getSpeedScore().doubleValue() : 0.80;
        double cost = config.getCostRate() != null ? config.getCostRate().doubleValue() : 1.0000;
        
        // 成本越低越好，所以取倒数
        double costScore = 1.0 / cost;
        
        // 综合评分：质量40% + 速度30% + 成本30%
        return quality * 0.4 + speed * 0.3 + costScore * 0.3;
    }
    
    /**
     * 故障转移机制 - 自动切换到下一个可用配置
     */
    public UnifiedAiConfig fallback(String taskType, Long excludeId) {
        List<UnifiedAiConfig> eligibleConfigs = getEligibleConfigs(taskType);
        
        return eligibleConfigs.stream()
            .filter(c -> !c.getId().equals(excludeId))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("故障转移失败：无可用备用配置"));
    }
    
    /**
     * 更新使用统计（异步）
     */
    @Async
    public void updateUsageStats(Long configId, boolean success) {
        try {
            UnifiedAiConfig config = configMapper.selectById(configId);
            if (config == null) {
                logger.warn("配置不存在: {}", configId);
                return;
            }
            
            if (success) {
                int dailyUsed = config.getDailyUsed() != null ? config.getDailyUsed() + 1 : 1;
                config.setDailyUsed(dailyUsed);
                config.setLastSuccessAt(LocalDateTime.now());
                config.setFailCount(0); // 重置失败计数
                
                logger.info("配置 {} 调用成功，今日已用 {} 次", configId, dailyUsed);
            } else {
                int failCount = config.getFailCount() != null ? config.getFailCount() + 1 : 1;
                config.setFailCount(failCount);
                config.setLastFailAt(LocalDateTime.now());
                
                logger.warn("配置 {} 调用失败，连续失败 {} 次", configId, failCount);
                
                if (failCount >= 3) {
                    logger.error("配置 {} 连续失败{}次，标记为不健康", configId, failCount);
                }
            }
            
            configMapper.updateById(config);
            
            // 清除缓存，下次查询会重新加载
            clearCache();
            
        } catch (Exception e) {
            logger.error("更新使用统计失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 清除所有缓存
     */
    public void clearCache() {
        try {
            Set<String> keys = redisTemplate.keys(CACHE_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                logger.info("清除缓存完成，共 {} 个key", keys.size());
            }
        } catch (Exception e) {
            logger.warn("清除缓存失败: {}", e.getMessage());
        }
    }
    
    /**
     * 创建新配置
     */
    public UnifiedAiConfig createConfig(UnifiedAiConfig config) {
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        config.setDeleted(0);
        config.setEnabled(config.getEnabled() != null ? config.getEnabled() : 1);
        config.setDailyUsed(0);
        config.setFailCount(0);
        
        configMapper.insert(config);
        clearCache();
        
        logger.info("创建配置成功: {} ({})", config.getConfigName(), config.getId());
        return config;
    }
    
    /**
     * 根据ID获取配置
     */
    public UnifiedAiConfig getConfigById(Long id) {
        return configMapper.selectById(id);
    }
    
    /**
     * 更新配置
     */
    public UnifiedAiConfig updateConfig(Long id, UnifiedAiConfig config) {
        UnifiedAiConfig existing = configMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("配置不存在: " + id);
        }
        
        config.setId(id);
        config.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(config);
        clearCache();
        
        logger.info("更新配置成功: {} ({})", config.getConfigName(), id);
        return config;
    }
    
    /**
     * 删除配置（逻辑删除）
     */
    public void deleteConfig(Long id) {
        UnifiedAiConfig existing = configMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("配置不存在: " + id);
        }
        
        existing.setDeleted(1);
        existing.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(existing);
        clearCache();
        
        logger.info("删除配置成功: {} ({})", existing.getConfigName(), id);
    }
}
