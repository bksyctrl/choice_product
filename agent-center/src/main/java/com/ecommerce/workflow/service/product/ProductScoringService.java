package com.ecommerce.workflow.service.product;

import com.ecommerce.workflow.entity.Product;
import com.ecommerce.workflow.mapper.ProductMapper;
import com.ecommerce.workflow.service.cache.EcommerceCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ProductScoringService {

    private static final Logger log = LoggerFactory.getLogger(ProductScoringService.class);

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private EcommerceCacheService cacheService;

    public Map<String, Object> scoreProduct(String productId) {
        log.info("开始6维爆品评分: productId={}", productId);
        
        Object cached = cacheService.getProductCache(productId + ":score");
        if (cached != null) {
            log.info("使用缓存的产品评分数据");
            @SuppressWarnings("unchecked")
            Map<String, Object> cachedMap = (Map<String, Object>) cached;
            return cachedMap;
        }
        
        Product product = productMapper.selectByProductId(productId);
        
        if (product == null) {
            log.warn("产品不存在: {}", productId);
            return null;
        }
        
        Map<String, Object> scores = new HashMap<>();
        
        double demandScore = calculateDemandScore(product);
        double competitionScore = calculateCompetitionScore(product);
        double profitScore = calculateProfitScore(product);
        double supplyChainScore = calculateSupplyChainScore(product);
        double lifecycleScore = calculateLifecycleScore(product);
        double innovationScore = calculateInnovationScore(product);
        
        double totalScore = (demandScore * 0.25 + 
                           competitionScore * 0.20 + 
                           profitScore * 0.20 + 
                           supplyChainScore * 0.15 + 
                           lifecycleScore * 0.10 + 
                           innovationScore * 0.10);
        
        scores.put("productId", productId);
        scores.put("demandScore", demandScore);
        scores.put("competitionScore", competitionScore);
        scores.put("profitScore", profitScore);
        scores.put("supplyChainScore", supplyChainScore);
        scores.put("lifecycleScore", lifecycleScore);
        scores.put("innovationScore", innovationScore);
        scores.put("totalScore", totalScore);
        scores.put("level", getScoreLevel(totalScore));
        scores.put("scoredAt", LocalDateTime.now());
        
        product.setSixDimensionScore(totalScore);
        product.setScoreLevel(getScoreLevel(totalScore));
        product.setUpdatedAt(LocalDateTime.now());
        
        productMapper.update(product);
        
        cacheService.putProductCache(productId + ":score", scores);
        
        log.info("6维爆品评分完成: productId={}, totalScore={:.2f}, level={}", 
                productId, totalScore, getScoreLevel(totalScore));
        
        return scores;
    }

    @Async("ecommerceTaskExecutor")
    public List<Map<String, Object>> scoreProducts(List<String> productIds) {
        log.info("批量评分: count={}", productIds.size());
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (String productId : productIds) {
            try {
                Map<String, Object> score = scoreProduct(productId);
                if (score != null) {
                    results.add(score);
                }
            } catch (Exception e) {
                log.error("产品评分失败: productId={}", productId, e);
            }
        }
        
        results.sort((a, b) -> Double.compare(
                (Double) b.get("totalScore"), 
                (Double) a.get("totalScore")));
        
        log.info("批量评分完成: 成功={}/{}", results.size(), productIds.size());
        
        return results;
    }

    public List<Product> getPotentialExplosiveProducts(String category, String level, int limit) {
        log.info("获取潜力爆品: category={}, level={}, limit={}", category, level, limit);
        
        List<Product> products = productMapper.selectPotentialExplosiveProducts(category, level, limit);
        
        log.info("找到 {} 个潜力爆品", products.size());
        
        return products;
    }

    private double calculateDemandScore(Product product) {
        double score = 50.0;
        
        if (product.getSearchGrowthRate() != null) {
            if (product.getSearchGrowthRate() > 50) score += 30;
            else if (product.getSearchGrowthRate() > 20) score += 20;
            else if (product.getSearchGrowthRate() > 10) score += 10;
        }
        
        if (product.getSupplyDemandRatio() != null) {
            if (product.getSupplyDemandRatio() < 0.5) score += 20;
            else if (product.getSupplyDemandRatio() < 1.0) score += 10;
        }
        
        return Math.min(100.0, score);
    }

    private double calculateCompetitionScore(Product product) {
        double score = 50.0;
        
        if (product.getCompetitionLevel() != null) {
            switch (product.getCompetitionLevel()) {
                case "LOW": score += 40; break;
                case "MEDIUM": score += 20; break;
                case "HIGH": score -= 10; break;
            }
        }
        
        if (product.getHeadMonopolyDegree() != null) {
            if (product.getHeadMonopolyDegree() < 30) score += 10;
        }
        
        return Math.min(100.0, Math.max(0.0, score));
    }

    private double calculateProfitScore(Product product) {
        double score = 50.0;
        
        if (product.getProfitMargin() != null) {
            if (product.getProfitMargin() > 50) score += 40;
            else if (product.getProfitMargin() > 30) score += 30;
            else if (product.getProfitMargin() > 20) score += 20;
            else if (product.getProfitMargin() > 10) score += 10;
        }
        
        return Math.min(100.0, score);
    }

    private double calculateSupplyChainScore(Product product) {
        double score = 50.0;
        
        if (product.getSupplyChainStability() != null) {
            switch (product.getSupplyChainStability()) {
                case "HIGH": score += 40; break;
                case "MEDIUM": score += 20; break;
                case "LOW": score -= 10; break;
            }
        }
        
        return Math.min(100.0, Math.max(0.0, score));
    }

    private double calculateLifecycleScore(Product product) {
        double score = 50.0;
        
        if (product.getLifecycleStage() != null) {
            switch (product.getLifecycleStage()) {
                case "GROWTH": score += 40; break;
                case "INTRODUCTION": score += 30; break;
                case "MATURITY": score += 10; break;
                case "DECLINE": score -= 20; break;
            }
        }
        
        return Math.min(100.0, Math.max(0.0, score));
    }

    private double calculateInnovationScore(Product product) {
        double score = 50.0;
        
        if (product.getInnovationSpace() != null) {
            if (product.getInnovationSpace() > 80) score += 40;
            else if (product.getInnovationSpace() > 60) score += 30;
            else if (product.getInnovationSpace() > 40) score += 20;
        }
        
        return Math.min(100.0, score);
    }

    private String getScoreLevel(double score) {
        if (score >= 85) return "S";
        if (score >= 75) return "A";
        if (score >= 65) return "B";
        if (score >= 55) return "C";
        return "D";
    }
}
