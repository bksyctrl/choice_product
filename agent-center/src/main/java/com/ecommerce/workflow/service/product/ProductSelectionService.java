package com.ecommerce.workflow.service.product;

import com.ecommerce.workflow.entity.CompetitorData;
import com.ecommerce.workflow.entity.MarketOpportunity;
import com.ecommerce.workflow.entity.Product;
import com.ecommerce.workflow.mapper.ProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ProductSelectionService {

    private static final Logger log = LoggerFactory.getLogger(ProductSelectionService.class);

    @Autowired
    private MarketOpportunityService marketOpportunityService;

    @Autowired
    private CompetitorAnalysisService competitorAnalysisService;

    @Autowired
    private ProductScoringService productScoringService;

    @Autowired
    private ProductMapper productMapper;

    public Map<String, Object> executeProductSelection(String category, String platform) {
        log.info("执行智能选品: category={}, platform={}", category, platform);
        
        Map<String, Object> result = new HashMap<>();
        
        List<MarketOpportunity> opportunities = marketOpportunityService.findBlueOceanOpportunities(category, 20);
        result.put("opportunities", opportunities);
        result.put("opportunityCount", opportunities.size());
        
        List<CompetitorData> competitors = competitorAnalysisService.findCompetitorsByCategory(category, platform, 50);
        result.put("competitors", competitors);
        result.put("competitorCount", competitors.size());
        
        List<Map<String, Object>> differentiationOpportunities = competitorAnalysisService.findDifferentiationOpportunities(category);
        result.put("differentiationOpportunities", differentiationOpportunities);
        
        List<String> productIds = extractProductIdsFromOpportunities(opportunities);
        List<Map<String, Object>> scoredProducts = productScoringService.scoreProducts(productIds);
        result.put("scoredProducts", scoredProducts);
        
        List<Product> potentialExplosiveProducts = productScoringService.getPotentialExplosiveProducts(category, "S", 10);
        result.put("sLevelProducts", potentialExplosiveProducts);
        
        List<Product> aLevelProducts = productScoringService.getPotentialExplosiveProducts(category, "A", 20);
        result.put("aLevelProducts", aLevelProducts);
        
        result.put("selectionCompleted", true);
        result.put("completedAt", LocalDateTime.now());
        
        log.info("智能选品完成: 机会={}, 竞品={}, S级={}, A级={}", 
                opportunities.size(), competitors.size(), 
                potentialExplosiveProducts.size(), aLevelProducts.size());
        
        return result;
    }

    public Map<String, Object> analyzeProductPotential(String productId) {
        log.info("分析产品潜力: productId={}", productId);
        
        Map<String, Object> analysis = new HashMap<>();
        
        Product product = productMapper.selectByProductId(productId);
        if (product == null) {
            log.warn("产品不存在: {}", productId);
            return null;
        }
        
        Map<String, Object> scores = productScoringService.scoreProduct(productId);
        analysis.put("scores", scores);
        
        Map<String, Object> competitorAnalysis = competitorAnalysisService.extractSuccessFactors(productId);
        analysis.put("competitorAnalysis", competitorAnalysis);
        
        String category = product.getCategory();
        List<Map<String, Object>> differentiationOpportunities = competitorAnalysisService.findDifferentiationOpportunities(category);
        analysis.put("differentiationOpportunities", differentiationOpportunities);
        
        analysis.put("analyzedAt", LocalDateTime.now());
        
        log.info("产品潜力分析完成: productId={}, score={:.2f}, level={}", 
                productId, scores.get("totalScore"), scores.get("level"));
        
        return analysis;
    }

    private List<String> extractProductIdsFromOpportunities(List<MarketOpportunity> opportunities) {
        List<String> productIds = new ArrayList<>();
        
        for (MarketOpportunity opportunity : opportunities) {
            if (opportunity.getRecommendation() != null && !opportunity.getRecommendation().isEmpty()) {
                String[] parts = opportunity.getRecommendation().split(",");
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        productIds.add(trimmed);
                    }
                }
            }
        }
        
        return productIds;
    }
}
