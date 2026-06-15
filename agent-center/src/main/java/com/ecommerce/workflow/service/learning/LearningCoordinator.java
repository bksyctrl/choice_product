package com.ecommerce.workflow.service.learning;

import com.ecommerce.workflow.service.causal.CausalAttributionService;
import com.ecommerce.workflow.service.delivery.DeliveryLearningService;
import com.ecommerce.workflow.service.evolution.EvolutionCoreService;
import com.ecommerce.workflow.service.product.ProductSelectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class LearningCoordinator {

    private static final Logger log = LoggerFactory.getLogger(LearningCoordinator.class);

    @Autowired
    private ProductSelectionService productSelectionService;

    @Autowired
    private CausalAttributionService causalAttributionService;

    @Autowired
    private DeliveryLearningService deliveryLearningService;

    @Autowired
    private EvolutionCoreService evolutionCoreService;

    @Autowired
    private LearningCoreService learningCoreService;

    public Map<String, Object> executeFullLearningCycle(String productId, String category) {
        log.info("=== 开始完整学习周期: productId={}, category={} ===", productId, category);
        
        Map<String, Object> result = new HashMap<>();
        
        log.info("步骤1: 选品分析");
        Map<String, Object> selectionResult = productSelectionService.analyzeProductPotential(productId);
        result.put("selectionResult", selectionResult);
        
        log.info("步骤2: 因果归因分析");
        Map<String, Object> causalResult = causalAttributionService.analyzeExplosiveProduct(productId);
        result.put("causalResult", causalResult);
        
        log.info("步骤3: 统一学习（案例学习+专家分析+规律发现）");
        learningCoreService.executeFullLearningCycle();
        result.put("unifiedLearningCompleted", true);
        
        log.info("步骤4: 强化学习更新");
        evolutionCoreService.runLearningIteration();
        result.put("reinforcementLearningCompleted", true);
        
        result.put("cycleCompleted", true);
        result.put("completedAt", LocalDateTime.now());
        
        log.info("=== 完整学习周期完成: productId={} ===", productId);
        
        return result;
    }

    public Map<String, Object> executeDeliveryLearningCycle(String videoTaskId) {
        log.info("=== 开始投放学习周期: videoTaskId={} ===", videoTaskId);
        
        Map<String, Object> result = new HashMap<>();
        
        log.info("步骤1: 投放效果分析");
        Map<String, Object> deliveryEffect = deliveryLearningService.analyzeDeliveryEffect(videoTaskId);
        result.put("deliveryEffect", deliveryEffect);
        
        log.info("步骤2: 生成优化建议");
        Map<String, Object> optimizationSuggestions = deliveryLearningService.generateOptimizationSuggestions(videoTaskId);
        result.put("optimizationSuggestions", optimizationSuggestions);
        
        log.info("步骤3: 统一学习（基于投放数据）");
        learningCoreService.executeFullLearningCycle();
        result.put("unifiedLearningCompleted", true);
        
        result.put("cycleCompleted", true);
        result.put("completedAt", LocalDateTime.now());
        
        log.info("=== 投放学习周期完成: videoTaskId={} ===", videoTaskId);
        
        return result;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledFullLearningCycle() {
        log.info("=== 开始每日完整学习周期 ===");
        
        try {
            List<String> productIds = getActiveProductIds();
            
            for (String productId : productIds) {
                try {
                    executeFullLearningCycle(productId, "default");
                } catch (Exception e) {
                    log.error("产品学习周期失败: productId={}", productId, e);
                }
            }
            
            log.info("=== 每日完整学习周期完成 ===");
        } catch (Exception e) {
            log.error("每日完整学习周期失败", e);
        }
    }

    @Scheduled(cron = "0 0 4 * * ?")
    public void scheduledDeliveryLearningCycle() {
        log.info("=== 开始每日投放学习周期 ===");
        
        try {
            List<String> videoTaskIds = getRecentVideoTaskIds();
            
            for (String videoTaskId : videoTaskIds) {
                try {
                    executeDeliveryLearningCycle(videoTaskId);
                } catch (Exception e) {
                    log.error("投放学习周期失败: videoTaskId={}", videoTaskId, e);
                }
            }
            
            log.info("=== 每日投放学习周期完成 ===");
        } catch (Exception e) {
            log.error("每日投放学习周期失败", e);
        }
    }

    private List<String> getActiveProductIds() {
        List<String> productIds = new ArrayList<>();

        try {
            List<Map<String, Object>> products = evolutionCoreService.getJdbcTemplate().queryForList(
                    "SELECT product_id FROM biz_product WHERE status = 'ACTIVE' AND deleted = 0 LIMIT 20");

            for (Map<String, Object> product : products) {
                productIds.add((String) product.get("product_id"));
            }
        } catch (Exception e) {
            log.error("获取活跃产品ID失败", e);
        }

        return productIds;
    }

    private List<String> getRecentVideoTaskIds() {
        List<String> videoTaskIds = new ArrayList<>();

        try {
            List<Map<String, Object>> tasks = evolutionCoreService.getJdbcTemplate().queryForList(
                    "SELECT id FROM biz_video_task WHERE status = 'completed' " +
                    "AND created_at > DATE_SUB(NOW(), INTERVAL 7 DAY) LIMIT 50");
            
            for (Map<String, Object> task : tasks) {
                videoTaskIds.add(task.get("id").toString());
            }
        } catch (Exception e) {
            log.error("获取近期视频任务ID失败", e);
        }
        
        return videoTaskIds;
    }
}
