package com.ecommerce.workflow.service.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class EcommerceCacheService {

    private static final Logger log = LoggerFactory.getLogger(EcommerceCacheService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String PRODUCT_CACHE_PREFIX = "ecommerce:product:";
    private static final String OPPORTUNITY_CACHE_PREFIX = "ecommerce:opportunity:";
    private static final String COMPETITOR_CACHE_PREFIX = "ecommerce:competitor:";
    private static final String CAUSAL_CACHE_PREFIX = "ecommerce:causal:";
    private static final String DELIVERY_CACHE_PREFIX = "ecommerce:delivery:";

    private static final long PRODUCT_CACHE_TTL = 30;
    private static final long OPPORTUNITY_CACHE_TTL = 60;
    private static final long COMPETITOR_CACHE_TTL = 60;
    private static final long CAUSAL_CACHE_TTL = 120;
    private static final long DELIVERY_CACHE_TTL = 30;

    public void putProductCache(String productId, Object data) {
        String key = PRODUCT_CACHE_PREFIX + productId;
        redisTemplate.opsForValue().set(key, data, PRODUCT_CACHE_TTL, TimeUnit.MINUTES);
        log.debug("产品缓存已更新: {}", key);
    }

    public Object getProductCache(String productId) {
        String key = PRODUCT_CACHE_PREFIX + productId;
        Object data = redisTemplate.opsForValue().get(key);
        log.debug("产品缓存查询: {}, 结果={}", key, data != null ? "命中" : "未命中");
        return data;
    }

    public void putOpportunityCache(String category, Object data) {
        String key = OPPORTUNITY_CACHE_PREFIX + category;
        redisTemplate.opsForValue().set(key, data, OPPORTUNITY_CACHE_TTL, TimeUnit.MINUTES);
        log.debug("机会缓存已更新: {}", key);
    }

    public Object getOpportunityCache(String category) {
        String key = OPPORTUNITY_CACHE_PREFIX + category;
        Object data = redisTemplate.opsForValue().get(key);
        log.debug("机会缓存查询: {}, 结果={}", key, data != null ? "命中" : "未命中");
        return data;
    }

    public void putCompetitorCache(String category, Object data) {
        String key = COMPETITOR_CACHE_PREFIX + category;
        redisTemplate.opsForValue().set(key, data, COMPETITOR_CACHE_TTL, TimeUnit.MINUTES);
        log.debug("竞品缓存已更新: {}", key);
    }

    public Object getCompetitorCache(String category) {
        String key = COMPETITOR_CACHE_PREFIX + category;
        Object data = redisTemplate.opsForValue().get(key);
        log.debug("竞品缓存查询: {}, 结果={}", key, data != null ? "命中" : "未命中");
        return data;
    }

    public void putCausalCache(String productId, Object data) {
        String key = CAUSAL_CACHE_PREFIX + productId;
        redisTemplate.opsForValue().set(key, data, CAUSAL_CACHE_TTL, TimeUnit.MINUTES);
        log.debug("因果归因缓存已更新: {}", key);
    }

    public Object getCausalCache(String productId) {
        String key = CAUSAL_CACHE_PREFIX + productId;
        Object data = redisTemplate.opsForValue().get(key);
        log.debug("因果归因缓存查询: {}, 结果={}", key, data != null ? "命中" : "未命中");
        return data;
    }

    public void putDeliveryCache(String videoTaskId, Object data) {
        String key = DELIVERY_CACHE_PREFIX + videoTaskId;
        redisTemplate.opsForValue().set(key, data, DELIVERY_CACHE_TTL, TimeUnit.MINUTES);
        log.debug("投放数据缓存已更新: {}", key);
    }

    public Object getDeliveryCache(String videoTaskId) {
        String key = DELIVERY_CACHE_PREFIX + videoTaskId;
        Object data = redisTemplate.opsForValue().get(key);
        log.debug("投放数据缓存查询: {}, 结果={}", key, data != null ? "命中" : "未命中");
        return data;
    }

    public void invalidateProductCache(String productId) {
        String key = PRODUCT_CACHE_PREFIX + productId;
        redisTemplate.delete(key);
        log.info("产品缓存已失效: {}", key);
    }

    public void invalidateCategoryCache(String category) {
        redisTemplate.delete(OPPORTUNITY_CACHE_PREFIX + category);
        redisTemplate.delete(COMPETITOR_CACHE_PREFIX + category);
        log.info("品类缓存已失效: {}", category);
    }
}
