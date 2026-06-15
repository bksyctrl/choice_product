package com.ecommerce.workflow.service.causal;

import com.ecommerce.workflow.service.cache.EcommerceCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CausalAttributionService {

    private static final Logger log = LoggerFactory.getLogger(CausalAttributionService.class);

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    private EcommerceCacheService cacheService;

    public CausalAttributionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> analyzeExplosiveProduct(String productId) {
        log.info("开始爆款因果归因分析: productId={}", productId);
        
        Object cached = cacheService.getCausalCache(productId);
        if (cached != null) {
            log.info("使用缓存的因果归因数据");
            @SuppressWarnings("unchecked")
            Map<String, Object> cachedMap = (Map<String, Object>) cached;
            return cachedMap;
        }
        
        Map<String, Object> result = new HashMap<>();
        
        Map<String, Object> productData = recallProductData(productId);
        result.put("productData", productData);
        
        Map<String, Object> featureImportance = calculateFeatureImportance(productData);
        result.put("featureImportance", featureImportance);
        
        List<Map<String, Object>> causalFactors = extractCausalFactors(featureImportance);
        result.put("causalFactors", causalFactors);
        
        List<Map<String, Object>> negativeFactors = extractNegativeFactors(productData);
        result.put("negativeFactors", negativeFactors);
        
        Map<String, Object> audienceProfile = analyzeAudienceProfile(productId);
        result.put("audienceProfile", audienceProfile);
        
        cacheService.putCausalCache(productId, result);
        
        log.info("爆款因果归因分析完成: productId={}, 核心因子={}, 负向因子={}", 
                productId, causalFactors.size(), negativeFactors.size());
        
        return result;
    }

    @Async("learningTaskExecutor")
    public void analyzeExplosiveProductAsync(String productId) {
        log.info("异步执行爆款因果归因分析: productId={}", productId);
        analyzeExplosiveProduct(productId);
    }

    private Map<String, Object> recallProductData(String productId) {
        log.info("召回产品全量数据: productId={}", productId);
        
        Map<String, Object> data = new HashMap<>();
        
        try {
            Map<String, Object> basicInfo = jdbcTemplate.queryForMap(
                    "SELECT * FROM biz_product WHERE product_id = ? AND deleted = 0", productId);
            data.put("basicInfo", basicInfo);
        } catch (Exception e) {
            log.warn("获取产品基础信息失败: {}", e.getMessage());
            data.put("basicInfo", new HashMap<>());
        }
        
        try {
            List<Map<String, Object>> videoData = jdbcTemplate.queryForList(
                    "SELECT * FROM biz_video_task WHERE product_id = ? AND status = 'completed' ORDER BY created_at DESC LIMIT 50", 
                    productId);
            data.put("videoData", videoData);
        } catch (Exception e) {
            log.warn("获取视频数据失败: {}", e.getMessage());
            data.put("videoData", new ArrayList<>());
        }
        
        try {
            List<Map<String, Object>> deliveryData = jdbcTemplate.queryForList(
                    "SELECT * FROM biz_delivery_data WHERE video_task_id IN " +
                    "(SELECT id FROM biz_video_task WHERE product_id = ?) ORDER BY data_date DESC LIMIT 100", 
                    productId);
            data.put("deliveryData", deliveryData);
        } catch (Exception e) {
            log.warn("获取投放数据失败: {}", e.getMessage());
            data.put("deliveryData", new ArrayList<>());
        }
        
        return data;
    }

    private Map<String, Object> calculateFeatureImportance(Map<String, Object> productData) {
        log.info("计算特征重要性权重");
        
        Map<String, Object> importance = new HashMap<>();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> videoData = (List<Map<String, Object>>) productData.getOrDefault("videoData", new ArrayList<>());
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> deliveryData = (List<Map<String, Object>>) productData.getOrDefault("deliveryData", new ArrayList<>());
        
        Map<String, Double> featureScores = new HashMap<>();
        Map<String, Integer> featureCounts = new HashMap<>();
        
        for (Map<String, Object> video : videoData) {
            String openingType = (String) video.get("opening_type");
            String narrativeFrame = (String) video.get("narrative_frame");
            String emotionFrame = (String) video.get("emotion_frame");
            Integer duration = (Integer) video.get("duration");
            
            if (openingType != null) {
                featureScores.merge(openingType, 1.0, Double::sum);
                featureCounts.merge(openingType, 1, Integer::sum);
            }
            
            if (narrativeFrame != null) {
                featureScores.merge(narrativeFrame, 1.0, Double::sum);
                featureCounts.merge(narrativeFrame, 1, Integer::sum);
            }
            
            if (emotionFrame != null) {
                featureScores.merge(emotionFrame, 1.0, Double::sum);
                featureCounts.merge(emotionFrame, 1, Integer::sum);
            }
            
            if (duration != null) {
                String durationRange = getDurationRange(duration);
                featureScores.merge(durationRange, 1.0, Double::sum);
                featureCounts.merge(durationRange, 1, Integer::sum);
            }
        }
        
        for (Map<String, Object> delivery : deliveryData) {
            Double cvr = (Double) delivery.get("cvr");
            Double playCount = (Double) delivery.get("play_count");
            
            if (cvr != null && cvr > 0.05) {
                for (Map.Entry<String, Double> entry : featureScores.entrySet()) {
                    featureScores.put(entry.getKey(), entry.getValue() * (1 + cvr));
                }
            }
        }
        
        importance.put("featureScores", featureScores);
        importance.put("featureCounts", featureCounts);
        
        return importance;
    }

    private List<Map<String, Object>> extractCausalFactors(Map<String, Object> featureImportance) {
        log.info("提取核心因果因子");
        
        @SuppressWarnings("unchecked")
        Map<String, Double> featureScores = (Map<String, Double>) featureImportance.getOrDefault("featureScores", new HashMap<>());
        
        List<Map<String, Object>> factors = new ArrayList<>();
        
        featureScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> {
                    Map<String, Object> factor = new HashMap<>();
                    factor.put("feature", entry.getKey());
                    factor.put("weight", entry.getValue());
                    factor.put("rank", factors.size() + 1);
                    factors.add(factor);
                });
        
        return factors;
    }

    private List<Map<String, Object>> extractNegativeFactors(Map<String, Object> productData) {
        log.info("提取负向踩坑因子");
        
        List<Map<String, Object>> negativeFactors = new ArrayList<>();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> deliveryData = (List<Map<String, Object>>) productData.getOrDefault("deliveryData", new ArrayList<>());
        
        Map<String, Integer> negativePatterns = new HashMap<>();
        
        for (Map<String, Object> delivery : deliveryData) {
            Double cvr = (Double) delivery.get("cvr");
            String openingType = (String) delivery.get("opening_type");
            
            if (cvr != null && cvr < 0.01 && openingType != null) {
                negativePatterns.merge(openingType, 1, Integer::sum);
            }
        }
        
        negativePatterns.entrySet().stream()
                .filter(e -> e.getValue() >= 3)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> {
                    Map<String, Object> factor = new HashMap<>();
                    factor.put("pattern", entry.getKey());
                    factor.put("occurrenceCount", entry.getValue());
                    factor.put("recommendation", "避免使用此模式");
                    negativeFactors.add(factor);
                });
        
        return negativeFactors;
    }

    private Map<String, Object> analyzeAudienceProfile(String productId) {
        log.info("分析目标受众画像: productId={}", productId);
        
        Map<String, Object> profile = new HashMap<>();
        
        try {
            List<Map<String, Object>> audienceData = jdbcTemplate.queryForList(
                    "SELECT age_group, gender, city_level, COUNT(*) as count " +
                    "FROM biz_user_behavior " +
                    "WHERE product_id = ? AND behavior_type IN ('CLICK', 'CART', 'ORDER') " +
                    "GROUP BY age_group, gender, city_level " +
                    "ORDER BY count DESC LIMIT 10", 
                    productId);
            
            profile.put("audienceData", audienceData);
        } catch (Exception e) {
            log.warn("获取受众数据失败: {}", e.getMessage());
            profile.put("audienceData", new ArrayList<>());
        }
        
        return profile;
    }

    private String getDurationRange(int duration) {
        if (duration <= 15) return "0-15s";
        if (duration <= 30) return "15-30s";
        if (duration <= 60) return "30-60s";
        return "60s+";
    }
}
