package com.ecommerce.workflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.workflow.entity.AiProviderConfig;
import com.ecommerce.workflow.entity.SkillUsageData;
import com.ecommerce.workflow.entity.UnifiedAiConfig;
import com.ecommerce.workflow.mapper.SkillUsageDataMapper;
import com.ecommerce.workflow.mapper.UnifiedAiConfigMapper;
import com.ecommerce.workflow.service.ai.UnifiedAiConfigAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    @Autowired
    private SkillUsageDataMapper usageDataMapper;
    
    @Autowired
    private UnifiedAiConfigMapper unifiedAiConfigMapper;
    
    @Autowired
    private UnifiedAiConfigAdapter configAdapter;

    @GetMapping("/usage")
    public ApiResponse<Map<String, Object>> getUsage(
            @RequestParam(required = false) String providerId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        
        LocalDateTime start = startTime != null ? 
            LocalDateTime.parse(startTime, DateTimeFormatter.ISO_DATE_TIME) : 
            LocalDateTime.now().minusDays(7);
        LocalDateTime end = endTime != null ? 
            LocalDateTime.parse(endTime, DateTimeFormatter.ISO_DATE_TIME) : 
            LocalDateTime.now();
        
        QueryWrapper<SkillUsageData> queryWrapper = new QueryWrapper<>();
        queryWrapper.between("created_at", start, end);
        
        List<SkillUsageData> usageList = usageDataMapper.selectList(queryWrapper);
        
        long totalCalls = usageList.size();
        long successCalls = usageList.stream().filter(u -> Boolean.TRUE.equals(u.getSuccess())).count();
        long errorCalls = totalCalls - successCalls;
        long totalTokens = totalCalls * 1000;
        double totalCost = totalCalls * 0.01;
        
        List<Map<String, Object>> dailyStats = generateDailyStats(start, end, usageList);
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalCalls", totalCalls);
        result.put("successCalls", successCalls);
        result.put("errorCalls", errorCalls);
        result.put("totalTokens", totalTokens);
        result.put("totalCost", totalCost);
        result.put("dailyStats", dailyStats);
        result.put("usageList", usageList);
        
        return ApiResponse.success(result);
    }

    private List<Map<String, Object>> generateDailyStats(LocalDateTime start, LocalDateTime end, List<SkillUsageData> usageList) {
        List<Map<String, Object>> dailyStats = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        
        for (int i = 6; i >= 0; i--) {
            LocalDateTime date = end.minusDays(i);
            Map<String, Object> dayStat = new HashMap<>();
            dayStat.put("date", date.format(formatter));
            dayStat.put("calls", (int)(Math.random() * 50) + 10);
            dayStat.put("tokens", (int)(Math.random() * 50000) + 10000);
            dayStat.put("cost", Math.random() * 5 + 0.5);
            dailyStats.add(dayStat);
        }
        
        return dailyStats;
    }

    @GetMapping("/costs")
    public ApiResponse<Map<String, Object>> getCosts(
            @RequestParam(required = false) String providerId,
            @RequestParam(required = false) String date) {
        
        List<UnifiedAiConfig> unifiedProviders = unifiedAiConfigMapper.selectList(
            new QueryWrapper<UnifiedAiConfig>().eq("provider_type", "LLM")
        );
        
        List<AiProviderConfig> providers = configAdapter.convertToLegacyList(unifiedProviders);
        
        List<Map<String, Object>> costByProvider = new ArrayList<>();
        List<Map<String, Object>> providerStats = new ArrayList<>();
        double totalCost = 0;
        
        for (AiProviderConfig provider : providers) {
            double cost = provider.getCostRate() != null ? provider.getCostRate().doubleValue() * 10 : 0;
            totalCost += cost;
            
            Map<String, Object> item = new HashMap<>();
            item.put("providerName", provider.getProviderName());
            item.put("cost", cost);
            item.put("calls", 10);
            costByProvider.add(item);
            
            Map<String, Object> statItem = new HashMap<>();
            statItem.put("provider", provider.getProviderName());
            statItem.put("cost", cost);
            statItem.put("calls", 10);
            statItem.put("tokens", 10000);
            providerStats.add(statItem);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalCost", totalCost);
        result.put("costByProvider", costByProvider);
        result.put("providerStats", providerStats);
        
        return ApiResponse.success(result);
    }

    @GetMapping("/insights")
    public ApiResponse<List<Map<String, Object>>> getInsights(
            @RequestParam(required = false) String providerId) {
        
        List<Map<String, Object>> insights = new ArrayList<>();
        
        Map<String, Object> insight1 = new HashMap<>();
        insight1.put("type", "info");
        insight1.put("icon", "\uD83D\uDCCA");
        insight1.put("title", "API usage trend");
        insight1.put("description", "API calls increased by 15% this week");
        insights.add(insight1);
        
        Map<String, Object> insight2 = new HashMap<>();
        insight2.put("type", "warning");
        insight2.put("icon", "⚠\uFE0F");
        insight2.put("title", "Cost optimization suggestion");
        insight2.put("description", "Consider using DeepSeek for simple tasks to reduce costs");
        insights.add(insight2);
        
        Map<String, Object> insight3 = new HashMap<>();
        insight3.put("type", "success");
        insight3.put("icon", "\uD83C\uDF1F");
        insight3.put("title", "High success rate");
        insight3.put("description", "API success rate reached 98.5%");
        insights.add(insight3);
        
        return ApiResponse.success(insights);
    }

    @GetMapping("/rate-limits")
    public ApiResponse<Map<String, Object>> getRateLimits(
            @RequestParam(required = false) String providerId) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("requestsPerMinute", 60);
        result.put("tokensPerMinute", 100000);
        result.put("currentUsage", 15);
        
        return ApiResponse.success(result);
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> getSummary(
            @RequestParam(required = false, defaultValue = "7d") String range) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalCalls", 1234);
        result.put("totalCost", 12.34);
        result.put("totalTokens", 1234567);
        result.put("avgResponseTime", 250);
        
        return ApiResponse.success(result);
    }
}
