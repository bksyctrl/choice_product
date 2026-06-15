package com.ecommerce.workflow.service.product;

import com.ecommerce.workflow.entity.CompetitorData;
import com.ecommerce.workflow.entity.CompetitorReview;
import com.ecommerce.workflow.mapper.CompetitorDataMapper;
import com.ecommerce.workflow.mapper.CompetitorReviewMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CompetitorAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(CompetitorAnalysisService.class);

    @Autowired
    private CompetitorDataMapper competitorDataMapper;

    @Autowired
    private CompetitorReviewMapper competitorReviewMapper;

    public List<CompetitorData> findCompetitorsByCategory(String category, String platform, int limit) {
        log.info("查找竞品: category={}, platform={}, limit={}", category, platform, limit);
        
        List<CompetitorData> competitors = competitorDataMapper.selectByCategoryAndPlatform(category, platform, limit);
        
        log.info("找到 {} 个竞品", competitors.size());
        return competitors;
    }

    public CompetitorData analyzeCompetitor(String competitorId) {
        log.info("分析竞品详情: competitorId={}", competitorId);
        
        CompetitorData competitor = competitorDataMapper.selectByCompetitorId(competitorId);
        
        if (competitor == null) {
            log.warn("竞品不存在: {}", competitorId);
            return null;
        }
        
        return competitor;
    }

    public Map<String, Object> extractSuccessFactors(String competitorId) {
        log.info("提取竞品成功因子: competitorId={}", competitorId);
        
        List<CompetitorReview> reviews = competitorReviewMapper.selectByCompetitorId(competitorId);
        
        Map<String, Object> analysis = new HashMap<>();
        
        List<String> sellingPoints = new ArrayList<>();
        List<String> painPoints = new ArrayList<>();
        double avgSentiment = 0.0;
        
        if (!reviews.isEmpty()) {
            avgSentiment = reviews.stream()
                    .mapToDouble(r -> r.getSentimentScore() != null ? r.getSentimentScore() : 0.0)
                    .average()
                    .orElse(0.0);
            
            sellingPoints = reviews.stream()
                    .filter(r -> "POSITIVE".equals(r.getReviewType()))
                    .map(CompetitorReview::getSellingPoints)
                    .filter(Objects::nonNull)
                    .flatMap(list -> list.stream())
                    .distinct()
                    .collect(Collectors.toList());
            
            painPoints = reviews.stream()
                    .filter(r -> "NEGATIVE".equals(r.getReviewType()))
                    .map(CompetitorReview::getPainPoints)
                    .filter(Objects::nonNull)
                    .flatMap(list -> list.stream())
                    .distinct()
                    .collect(Collectors.toList());
        }
        
        analysis.put("competitorId", competitorId);
        analysis.put("avgSentiment", avgSentiment);
        analysis.put("sellingPoints", sellingPoints);
        analysis.put("painPoints", painPoints);
        analysis.put("reviewCount", reviews.size());
        
        log.info("成功因子提取完成: 卖点={}, 痛点={}, 情感得分={:.2f}", 
                sellingPoints.size(), painPoints.size(), avgSentiment);
        
        return analysis;
    }

    public void recordCompetitor(CompetitorData competitor) {
        log.info("记录竞品数据: name={}, category={}", competitor.getProductName(), competitor.getCategory());
        
        competitor.setCreatedAt(LocalDateTime.now());
        competitor.setUpdatedAt(LocalDateTime.now());
        competitor.setDeleted(0);
        
        competitorDataMapper.insert(competitor);
        
        log.info("竞品数据记录成功: {}", competitor.getCompetitorId());
    }

    public void recordCompetitorReview(CompetitorReview review) {
        log.info("记录竞品评论: competitorId={}, type={}", review.getCompetitorId(), review.getReviewType());
        
        review.setCreatedAt(LocalDateTime.now());
        review.setDeleted(0);
        
        competitorReviewMapper.insert(review);
        
        log.info("竞品评论记录成功: {}", review.getReviewId());
    }

    public Map<String, Object> getCompetitorStatistics(String category) {
        log.info("获取竞品统计: category={}", category);
        
        Map<String, Object> stats = new HashMap<>();
        
        int totalCount = competitorDataMapper.countByCategory(category);
        int activeCount = competitorDataMapper.countActiveByCategory(category);
        
        stats.put("totalCount", totalCount);
        stats.put("activeCount", activeCount);
        
        log.info("竞品统计: total={}, active={}", totalCount, activeCount);
        
        return stats;
    }

    public List<Map<String, Object>> findDifferentiationOpportunities(String category) {
        log.info("查找差异化机会: category={}", category);
        
        List<CompetitorData> competitors = competitorDataMapper.selectByCategoryAndPlatform(category, null, 50);
        
        Map<String, Object> commonPainPoints = new HashMap<>();
        Map<String, Object> commonSellingPoints = new HashMap<>();
        
        for (CompetitorData competitor : competitors) {
            Map<String, Object> factors = extractSuccessFactors(competitor.getCompetitorId());
            
            @SuppressWarnings("unchecked")
            List<String> painPoints = (List<String>) factors.get("painPoints");
            @SuppressWarnings("unchecked")
            List<String> sellingPoints = (List<String>) factors.get("sellingPoints");
            
            for (String painPoint : painPoints) {
                commonPainPoints.merge(painPoint, 1, (oldVal, newVal) -> (Integer) oldVal + 1);
            }
            
            for (String sellingPoint : sellingPoints) {
                commonSellingPoints.merge(sellingPoint, 1, (oldVal, newVal) -> (Integer) oldVal + 1);
            }
        }
        
        List<Map<String, Object>> opportunities = new ArrayList<>();
        
        commonPainPoints.entrySet().stream()
                .filter(e -> ((Integer) e.getValue()) >= 3)
                .sorted((e1, e2) -> Integer.compare((Integer) e2.getValue(), (Integer) e1.getValue()))
                .limit(10)
                .forEach(e -> {
                    Map<String, Object> opp = new HashMap<>();
                    opp.put("type", "PAIN_POINT");
                    opp.put("factor", e.getKey());
                    opp.put("frequency", e.getValue());
                    opp.put("opportunity", "解决此痛点可获得差异化优势");
                    opportunities.add(opp);
                });
        
        log.info("找到 {} 个差异化机会", opportunities.size());
        
        return opportunities;
    }
}
