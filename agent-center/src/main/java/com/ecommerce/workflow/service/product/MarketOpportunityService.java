package com.ecommerce.workflow.service.product;

import com.ecommerce.workflow.entity.MarketOpportunity;
import com.ecommerce.workflow.mapper.MarketOpportunityMapper;
import com.ecommerce.workflow.service.cache.EcommerceCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class MarketOpportunityService {

    private static final Logger log = LoggerFactory.getLogger(MarketOpportunityService.class);

    @Autowired
    private MarketOpportunityMapper marketOpportunityMapper;

    @Autowired
    private EcommerceCacheService cacheService;

    public List<MarketOpportunity> findBlueOceanOpportunities(String category, int limit) {
        log.info("查找蓝海机会: category={}, limit={}", category, limit);
        
        Object cached = cacheService.getOpportunityCache(category + ":blue_ocean");
        if (cached != null) {
            log.info("使用缓存的蓝海机会数据");
            @SuppressWarnings("unchecked")
            List<MarketOpportunity> cachedList = (List<MarketOpportunity>) cached;
            return cachedList;
        }
        
        List<MarketOpportunity> opportunities = marketOpportunityMapper.selectBlueOceanOpportunities(category, limit);
        
        cacheService.putOpportunityCache(category + ":blue_ocean", opportunities);
        
        log.info("找到 {} 个蓝海机会", opportunities.size());
        return opportunities;
    }

    public List<MarketOpportunity> findTrendOpportunities(String category, int limit) {
        log.info("查找趋势机会: category={}, limit={}", category, limit);
        
        Object cached = cacheService.getOpportunityCache(category + ":trend");
        if (cached != null) {
            log.info("使用缓存的趋势机会数据");
            @SuppressWarnings("unchecked")
            List<MarketOpportunity> cachedList = (List<MarketOpportunity>) cached;
            return cachedList;
        }
        
        List<MarketOpportunity> opportunities = marketOpportunityMapper.selectTrendOpportunities(category, limit);
        
        cacheService.putOpportunityCache(category + ":trend", opportunities);
        
        log.info("找到 {} 个趋势机会", opportunities.size());
        return opportunities;
    }

    public MarketOpportunity analyzeOpportunity(String opportunityId) {
        log.info("分析机会详情: opportunityId={}", opportunityId);
        
        MarketOpportunity opportunity = marketOpportunityMapper.selectByOpportunityId(opportunityId);
        
        if (opportunity == null) {
            log.warn("机会不存在: {}", opportunityId);
            return null;
        }
        
        return opportunity;
    }

    @Async("ecommerceTaskExecutor")
    public void recordOpportunity(MarketOpportunity opportunity) {
        log.info("记录市场机会: category={}, type={}", opportunity.getCategory(), opportunity.getOpportunityType());
        
        opportunity.setCreatedAt(LocalDateTime.now());
        opportunity.setUpdatedAt(LocalDateTime.now());
        opportunity.setDeleted(0);
        
        marketOpportunityMapper.insert(opportunity);
        
        cacheService.invalidateCategoryCache(opportunity.getCategory());
        
        log.info("市场机会记录成功: {}", opportunity.getOpportunityId());
    }

    @Async("ecommerceTaskExecutor")
    public void updateOpportunityStatus(String opportunityId, String status) {
        log.info("更新机会状态: opportunityId={}, status={}", opportunityId, status);
        
        marketOpportunityMapper.updateStatus(opportunityId, status, LocalDateTime.now());
        
        log.info("机会状态更新成功");
    }

    public Map<String, Object> getOpportunityStatistics(String category) {
        log.info("获取机会统计: category={}", category);
        
        Map<String, Object> stats = new HashMap<>();
        
        int totalCount = marketOpportunityMapper.countByCategory(category);
        int blueOceanCount = marketOpportunityMapper.countByType(category, "BLUE_OCEAN");
        int trendCount = marketOpportunityMapper.countByType(category, "TREND");
        int demandGapCount = marketOpportunityMapper.countByType(category, "DEMAND_GAP");
        
        stats.put("totalCount", totalCount);
        stats.put("blueOceanCount", blueOceanCount);
        stats.put("trendCount", trendCount);
        stats.put("demandGapCount", demandGapCount);
        
        log.info("机会统计: total={}, blueOcean={}, trend={}, demandGap={}", 
                totalCount, blueOceanCount, trendCount, demandGapCount);
        
        return stats;
    }
}
