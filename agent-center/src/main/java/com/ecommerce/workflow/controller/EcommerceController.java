package com.ecommerce.workflow.controller;

import com.ecommerce.workflow.service.causal.CausalAttributionService;
import com.ecommerce.workflow.service.delivery.DeliveryLearningService;
import com.ecommerce.workflow.service.learning.LearningCoordinator;
import com.ecommerce.workflow.service.product.ProductSelectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ecommerce")
public class EcommerceController {

    private static final Logger log = LoggerFactory.getLogger(EcommerceController.class);

    @Autowired
    private ProductSelectionService productSelectionService;

    @Autowired
    private CausalAttributionService causalAttributionService;

    @Autowired
    private DeliveryLearningService deliveryLearningService;

    @Autowired
    private LearningCoordinator learningCoordinator;

    @PostMapping("/product-selection")
    public ApiResponse<Map<String, Object>> executeProductSelection(
            @RequestParam String category,
            @RequestParam(required = false, defaultValue = "douyin") String platform) {
        log.info("执行智能选品: category={}, platform={}", category, platform);
        
        try {
            Map<String, Object> result = productSelectionService.executeProductSelection(category, platform);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("智能选品失败", e);
            return ApiResponse.error("智能选品失败: " + e.getMessage());
        }
    }

    @GetMapping("/product-analysis/{productId}")
    public ApiResponse<Map<String, Object>> analyzeProductPotential(@PathVariable String productId) {
        log.info("分析产品潜力: productId={}", productId);
        
        try {
            Map<String, Object> result = productSelectionService.analyzeProductPotential(productId);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("产品潜力分析失败", e);
            return ApiResponse.error("产品潜力分析失败: " + e.getMessage());
        }
    }

    @GetMapping("/causal-attribution/{productId}")
    public ApiResponse<Map<String, Object>> analyzeExplosiveProduct(@PathVariable String productId) {
        log.info("爆款因果归因分析: productId={}", productId);
        
        try {
            Map<String, Object> result = causalAttributionService.analyzeExplosiveProduct(productId);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("爆款因果归因分析失败", e);
            return ApiResponse.error("爆款因果归因分析失败: " + e.getMessage());
        }
    }

    @PostMapping("/delivery-learning")
    public ApiResponse<Map<String, Object>> executeDeliveryLearning(@RequestParam String videoTaskId) {
        log.info("执行投放学习: videoTaskId={}", videoTaskId);
        
        try {
            Map<String, Object> result = learningCoordinator.executeDeliveryLearningCycle(videoTaskId);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("投放学习失败", e);
            return ApiResponse.error("投放学习失败: " + e.getMessage());
        }
    }

    @PostMapping("/full-learning-cycle")
    public ApiResponse<Map<String, Object>> executeFullLearningCycle(
            @RequestParam String productId,
            @RequestParam(required = false, defaultValue = "default") String category) {
        log.info("执行完整学习周期: productId={}, category={}", productId, category);
        
        try {
            Map<String, Object> result = learningCoordinator.executeFullLearningCycle(productId, category);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("完整学习周期失败", e);
            return ApiResponse.error("完整学习周期失败: " + e.getMessage());
        }
    }

    @GetMapping("/delivery-statistics")
    public ApiResponse<Map<String, Object>> getDeliveryStatistics(
            @RequestParam String category,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        log.info("获取投放统计: category={}, startDate={}, endDate={}", category, startDate, endDate);
        
        try {
            Map<String, Object> result = deliveryLearningService.getDeliveryStatistics(category, startDate, endDate);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取投放统计失败", e);
            return ApiResponse.error("获取投放统计失败: " + e.getMessage());
        }
    }

    @GetMapping("/high-performing-videos")
    public ApiResponse<Map<String, Object>> findHighPerformingVideos(
            @RequestParam String category,
            @RequestParam(defaultValue = "20") int limit) {
        log.info("查找高转化视频: category={}, limit={}", category, limit);
        
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("videos", deliveryLearningService.findHighPerformingVideos(category, limit));
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查找高转化视频失败", e);
            return ApiResponse.error("查找高转化视频失败: " + e.getMessage());
        }
    }
}
