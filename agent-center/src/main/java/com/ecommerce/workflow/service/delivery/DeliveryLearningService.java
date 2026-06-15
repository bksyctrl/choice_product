package com.ecommerce.workflow.service.delivery;

import com.ecommerce.workflow.entity.DeliveryData;
import com.ecommerce.workflow.mapper.DeliveryDataMapper;
import com.ecommerce.workflow.service.cache.EcommerceCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class DeliveryLearningService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryLearningService.class);

    @Autowired
    private DeliveryDataMapper deliveryDataMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EcommerceCacheService cacheService;

    @Async("ecommerceTaskExecutor")
    public void recordDeliveryData(DeliveryData data) {
        log.info("记录投放数据: taskNo={}, platform={}", data.getTaskNo(), data.getPlatform());
        
        data.setCreatedAt(LocalDateTime.now());
        data.setDeleted(0);
        
        deliveryDataMapper.insert(data);
        
        cacheService.invalidateProductCache(String.valueOf(data.getVideoTaskId()));
        
        log.info("投放数据记录成功: {}", data.getTaskNo());
    }

    public Map<String, Object> analyzeDeliveryEffect(String videoTaskId) {
        log.info("分析投放效果: videoTaskId={}", videoTaskId);
        
        Object cached = cacheService.getDeliveryCache(videoTaskId);
        if (cached != null) {
            log.info("使用缓存的投放效果数据");
            @SuppressWarnings("unchecked")
            Map<String, Object> cachedMap = (Map<String, Object>) cached;
            return cachedMap;
        }
        
        List<DeliveryData> dataList = deliveryDataMapper.selectByVideoTaskId(videoTaskId);
        
        if (dataList.isEmpty()) {
            log.warn("未找到投放数据: videoTaskId={}", videoTaskId);
            return null;
        }
        
        Map<String, Object> analysis = new HashMap<>();
        
        double totalPlayCount = dataList.stream()
                .mapToDouble(d -> d.getPlayCount() != null ? d.getPlayCount().doubleValue() : 0.0)
                .sum();
        
        double totalLikeCount = dataList.stream()
                .mapToDouble(d -> d.getLikeCount() != null ? d.getLikeCount().doubleValue() : 0.0)
                .sum();
        
        double totalGmv = dataList.stream()
                .mapToDouble(d -> d.getGmv() != null ? d.getGmv().doubleValue() : 0.0)
                .sum();
        
        double avgCvr = dataList.stream()
                .filter(d -> d.getCvr() != null)
                .mapToDouble(DeliveryData::getCvr)
                .average()
                .orElse(0.0);
        
        analysis.put("videoTaskId", videoTaskId);
        analysis.put("totalPlayCount", totalPlayCount);
        analysis.put("totalLikeCount", totalLikeCount);
        analysis.put("totalGmv", totalGmv);
        analysis.put("avgCvr", avgCvr);
        analysis.put("dataCount", dataList.size());
        analysis.put("analyzedAt", LocalDateTime.now());
        
        cacheService.putDeliveryCache(videoTaskId, analysis);
        
        log.info("投放效果分析完成: videoTaskId={}, playCount={:.0f}, gmv={:.2f}, avgCvr={:.2f}%", 
                videoTaskId, totalPlayCount, totalGmv, avgCvr * 100);
        
        return analysis;
    }

    @Async("learningTaskExecutor")
    public void analyzeDeliveryEffectAsync(String videoTaskId) {
        log.info("异步分析投放效果: videoTaskId={}", videoTaskId);
        analyzeDeliveryEffect(videoTaskId);
    }

    public List<Map<String, Object>> findHighPerformingVideos(String category, int limit) {
        log.info("查找高转化视频: category={}, limit={}", category, limit);
        
        List<Map<String, Object>> highPerforming = jdbcTemplate.queryForList(
                "SELECT v.id, v.product_id, v.opening_type, v.narrative_frame, v.emotion_frame, " +
                "       AVG(d.cvr) as avg_cvr, SUM(d.gmv) as total_gmv, COUNT(d.id) as delivery_count " +
                "FROM biz_video_task v " +
                "LEFT JOIN biz_delivery_data d ON v.id = d.video_task_id " +
                "WHERE v.category = ? AND v.status = 'completed' " +
                "GROUP BY v.id, v.product_id, v.opening_type, v.narrative_frame, v.emotion_frame " +
                "HAVING avg_cvr > 0.03 " +
                "ORDER BY avg_cvr DESC " +
                "LIMIT ?", 
                category, limit);
        
        log.info("找到 {} 个高转化视频", highPerforming.size());
        
        return highPerforming;
    }

    public Map<String, Object> generateOptimizationSuggestions(String videoTaskId) {
        log.info("生成优化建议: videoTaskId={}", videoTaskId);
        
        Map<String, Object> suggestions = new HashMap<>();
        
        Map<String, Object> currentEffect = analyzeDeliveryEffect(videoTaskId);
        if (currentEffect == null) {
            suggestions.put("error", "未找到投放数据");
            return suggestions;
        }
        
        Double avgCvr = (Double) currentEffect.get("avgCvr");
        
        List<String> suggestions_list = new ArrayList<>();
        
        if (avgCvr < 0.02) {
            suggestions_list.add("CVR过低，建议优化视频开头钩子");
            suggestions_list.add("尝试使用悬念或冲突型开头提升点击率");
        } else if (avgCvr < 0.05) {
            suggestions_list.add("CVR中等，建议优化卖点和逼单话术");
            suggestions_list.add("增加信任背书和效果验证环节");
        } else {
            suggestions_list.add("CVR表现良好，建议扩大投放规模");
        }
        
        suggestions.put("currentEffect", currentEffect);
        suggestions.put("suggestions", suggestions_list);
        suggestions.put("generatedAt", LocalDateTime.now());
        
        log.info("优化建议生成完成: videoTaskId={}, 建议数={}", videoTaskId, suggestions_list.size());
        
        return suggestions;
    }

    public Map<String, Object> getDeliveryStatistics(String category, String startDate, String endDate) {
        log.info("获取投放统计: category={}, startDate={}, endDate={}", category, startDate, endDate);
        
        Map<String, Object> stats = new HashMap<>();
        
        try {
            Integer totalVideos = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT v.id) FROM biz_video_task v " +
                    "LEFT JOIN biz_delivery_data d ON v.id = d.video_task_id " +
                    "WHERE v.category = ? AND d.data_date BETWEEN ? AND ?", 
                    Integer.class, category, startDate, endDate);
            
            Double totalGmv = jdbcTemplate.queryForObject(
                    "SELECT SUM(d.gmv) FROM biz_video_task v " +
                    "LEFT JOIN biz_delivery_data d ON v.id = d.video_task_id " +
                    "WHERE v.category = ? AND d.data_date BETWEEN ? AND ?", 
                    Double.class, category, startDate, endDate);
            
            Double avgCvr = jdbcTemplate.queryForObject(
                    "SELECT AVG(d.cvr) FROM biz_video_task v " +
                    "LEFT JOIN biz_delivery_data d ON v.id = d.video_task_id " +
                    "WHERE v.category = ? AND d.data_date BETWEEN ? AND ?", 
                    Double.class, category, startDate, endDate);
            
            stats.put("totalVideos", totalVideos != null ? totalVideos : 0);
            stats.put("totalGmv", totalGmv != null ? totalGmv : 0.0);
            stats.put("avgCvr", avgCvr != null ? avgCvr : 0.0);
        } catch (Exception e) {
            log.error("获取投放统计失败", e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }
}
