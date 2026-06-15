package com.ecommerce.workflow.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.workflow.service.learning.DimensionService;

@RestController
@RequestMapping("/api/learning")
public class DimensionLearningController {
    
    private static final Logger log = LoggerFactory.getLogger(DimensionLearningController.class);
    
    @Autowired
    private DimensionService dimensionService;
    
    @GetMapping("/dimensions/categories")
    public ApiResponse<Map<String, Object>> getDimensionCategories() {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("PRODUCT", buildCategoryData("PRODUCT", "产品维度",
                dimensionService.getDimensionsByCategory("PRODUCT")));
            result.put("VIDEO", buildCategoryData("VIDEO", "视频维度",
                dimensionService.getDimensionsByCategory("VIDEO")));
            result.put("IMAGE_TEXT", buildCategoryData("IMAGE_TEXT", "图文维度",
                dimensionService.getDimensionsByCategory("IMAGE_TEXT")));
            result.put("CONTEXT", buildCategoryData("CONTEXT", "场景上下文维度",
                dimensionService.getDimensionsByCategory("CONTEXT")));
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取维度分类失败", e);
            return ApiResponse.error("获取维度分类失败: " + e.getMessage());
        }
    }
    
    private Map<String, Object> buildCategoryData(String code, String name, 
                                                    List<?> dimensions) {
        Map<String, Object> category = new HashMap<>();
        category.put("code", code);
        category.put("categoryName", name);
        category.put("dimensions", dimensions);
        return category;
    }
    
    @GetMapping("/weights")
    public ApiResponse<List<Map<String, Object>>> getLearnedWeights(
            @RequestParam String scenarioType) {
        try {
            List<Map<String, Object>> weights = dimensionService.getLearnedWeights(scenarioType);
            return ApiResponse.success(weights);
        } catch (Exception e) {
            log.error("获取学习权重失败", e);
            return ApiResponse.error("获取学习权重失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/correlations")
    public ApiResponse<List<Map<String, Object>>> getCorrelations(
            @RequestParam String scenarioType) {
        try {
            List<Map<String, Object>> correlations = dimensionService.getDimensionCorrelations(scenarioType);
            return ApiResponse.success(correlations);
        } catch (Exception e) {
            log.error("获取维度关联性失败", e);
            return ApiResponse.error("获取维度关联性失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/patterns")
    public ApiResponse<List<Map<String, Object>>> getSuccessPatterns() {
        try {
            List<Map<String, Object>> patterns = dimensionService.getSuccessPatterns();
            return ApiResponse.success(patterns);
        } catch (Exception e) {
            log.error("获取成功模式失败", e);
            return ApiResponse.error("获取成功模式失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/cases")
    public ApiResponse<String> recordCase(@RequestBody Map<String, Object> caseData) {
        try {
            String scenarioType = (String) caseData.get("scenarioType");
            Boolean isSuccess = (Boolean) caseData.get("isSuccess");
            Map<String, String> dimensions = (Map<String, String>) caseData.get("dimensions");
            
            dimensionService.recordCaseDimensions(
                System.currentTimeMillis(),
                dimensions,
                scenarioType,
                isSuccess != null && isSuccess
            );
            
            return ApiResponse.success("案例记录成功");
        } catch (Exception e) {
            log.error("记录案例失败", e);
            return ApiResponse.error("记录案例失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/cases/batch")
    public ApiResponse<Map<String, Object>> batchRecordCases(
            @RequestBody Map<String, Object> request) {
        try {
            List<Map<String, Object>> cases = (List<Map<String, Object>>) request.get("cases");
            
            int successCount = 0;
            int failCount = 0;
            
            for (Map<String, Object> caseData : cases) {
                try {
                    String scenarioType = (String) caseData.get("scenarioType");
                    Boolean isSuccess = (Boolean) caseData.get("isSuccess");
                    Map<String, String> dimensions = (Map<String, String>) caseData.get("dimensions");
                    
                    dimensionService.recordCaseDimensions(
                        System.currentTimeMillis(),
                        dimensions,
                        scenarioType,
                        isSuccess != null && isSuccess
                    );
                    
                    successCount++;
                } catch (Exception e) {
                    log.error("批量记录案例失败", e);
                    failCount++;
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("total", cases.size());
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("批量记录案例失败", e);
            return ApiResponse.error("批量记录案例失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/extract/video")
    public ApiResponse<Map<String, String>> extractVideoDimensions(
            @RequestBody Map<String, String> videoData) {
        try {
            String title = videoData.get("title");
            String description = videoData.get("description");
            String tags = videoData.get("tags");
            String content = videoData.get("content");
            
            Map<String, String> dimensions = dimensionService.extractFromVideo(
                title, description, tags, content
            );
            
            return ApiResponse.success(dimensions);
        } catch (Exception e) {
            log.error("提取视频维度失败", e);
            return ApiResponse.error("提取视频维度失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/extract/image-text")
    public ApiResponse<Map<String, String>> extractImageTextDimensions(
            @RequestBody Map<String, String> imageData) {
        try {
            String title = imageData.get("title");
            String content = imageData.get("content");
            String images = imageData.get("images");
            String layout = imageData.get("layout");
            
            Map<String, String> dimensions = dimensionService.extractFromImageText(
                title, content, images, layout
            );
            
            return ApiResponse.success(dimensions);
        } catch (Exception e) {
            log.error("提取图文维度失败", e);
            return ApiResponse.error("提取图文维度失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/extract/product")
    public ApiResponse<Map<String, String>> extractProductDimensions(
            @RequestBody Map<String, String> productData) {
        try {
            String productName = productData.get("productName");
            String category = productData.get("category");
            String description = productData.get("description");
            String price = productData.get("price");
            String targetAudience = productData.get("targetAudience");
            
            Map<String, String> dimensions = dimensionService.extractFromProduct(
                productName, category, description, price, targetAudience
            );
            
            return ApiResponse.success(dimensions);
        } catch (Exception e) {
            log.error("提取产品维度失败", e);
            return ApiResponse.error("提取产品维度失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/recommend")
    public ApiResponse<Map<String, Object>> recommendDimensions(
            @RequestBody Map<String, Object> request) {
        try {
            String scenarioType = (String) request.get("scenarioType");
            Map<String, String> currentDimensions = (Map<String, String>) request.get("dimensions");
            Double minSuccessRate = (Double) request.getOrDefault("minSuccessRate", 0.6);
            
            Map<String, Object> recommendation = dimensionService.recommendDimensions(
                scenarioType, currentDimensions, minSuccessRate
            );
            
            return ApiResponse.success(recommendation);
        } catch (Exception e) {
            log.error("维度推荐失败", e);
            return ApiResponse.error("维度推荐失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/predict")
    public ApiResponse<Map<String, Object>> predictSuccessRate(
            @RequestBody Map<String, Object> request) {
        try {
            String scenarioType = (String) request.get("scenarioType");
            Map<String, String> dimensions = (Map<String, String>) request.get("dimensions");
            
            Map<String, Object> prediction = dimensionService.predictSuccessRate(
                scenarioType, dimensions
            );
            
            return ApiResponse.success(prediction);
        } catch (Exception e) {
            log.error("预测成功率失败", e);
            return ApiResponse.error("预测成功率失败: " + e.getMessage());
        }
    }
}
