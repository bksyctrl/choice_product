package com.ecommerce.workflow.service.evolution;

import com.ecommerce.workflow.entity.SkillUsageData;
import com.ecommerce.workflow.mapper.SkillUsageDataMapper;
import com.ecommerce.workflow.service.config.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SkillUsageTracker {
    
    private static final Logger log = LoggerFactory.getLogger(SkillUsageTracker.class);
    
    @Autowired
    private SkillUsageDataMapper skillUsageDataMapper;
    
    @Autowired
    private SysConfigService sysConfigService;
    
    private final Map<String, UsageStats> statsCache = new ConcurrentHashMap<>();
    private long cacheTimestamp = 0;
    
    public Long recordUsage(String skillCode, String skillName, String intentType,
                           String sessionId, Long userId, boolean success,
                           long executionTimeMs, Map<String, Object> inputParams,
                           Map<String, Object> outputData, String errorMessage,
                           String expertRole, String promptTemplateId) {
        
        SkillUsageData usageData = new SkillUsageData();
        usageData.setSkillCode(skillCode);
        usageData.setSkillName(skillName);
        usageData.setIntentType(intentType);
        usageData.setSessionId(sessionId);
        usageData.setUserId(userId);
        usageData.setSuccess(success);
        usageData.setExecutionTimeMs(executionTimeMs);
        usageData.setInputParams(inputParams != null ? inputParams : new HashMap<>());
        usageData.setOutputData(outputData != null ? outputData : new HashMap<>());
        usageData.setErrorMessage(errorMessage);
        usageData.setExpertRole(expertRole);
        usageData.setPromptTemplateId(promptTemplateId);
        usageData.setCreatedAt(LocalDateTime.now());
        
        skillUsageDataMapper.insert(usageData);
        
        invalidateStatsCache(skillCode);
        
        log.debug("记录技能使用情况: skillCode={}, success={}, time={}ms", 
                skillCode, success, executionTimeMs);
        
        return usageData.getId();
    }
    
    @Async
    public void recordUsageAsync(String skillCode, String skillName, String intentType,
                                 String sessionId, Long userId, boolean success,
                                 long executionTimeMs, Map<String, Object> inputParams,
                                 Map<String, Object> outputData, String errorMessage,
                                 String expertRole, String promptTemplateId) {
        recordUsage(skillCode, skillName, intentType, sessionId, userId, success,
                executionTimeMs, inputParams, outputData, errorMessage, expertRole, promptTemplateId);
    }
    
    public void recordUserFeedback(Long usageId, Double rating, String feedback) {
        SkillUsageData usageData = skillUsageDataMapper.selectById(usageId);
        if (usageData != null) {
            usageData.setUserRating(rating);
            usageData.setUserFeedback(feedback);
            skillUsageDataMapper.updateById(usageData);
            
            invalidateStatsCache(usageData.getSkillCode());
            
            log.debug("记录用户反馈: usageId={}, rating={}", usageId, rating);
        }
    }
    
    public UsageStats getUsageStats(String skillCode) {
        refreshStatsCacheIfNeeded();
        
        UsageStats stats = statsCache.get(skillCode);
        if (stats == null) {
            stats = calculateStats(skillCode);
            statsCache.put(skillCode, stats);
        }
        
        return stats;
    }
    
    public Map<String, UsageStats> getAllUsageStats() {
        refreshStatsCacheIfNeeded();
        return new HashMap<>(statsCache);
    }
    
    public List<Map<String, Object>> getUsageStatsBySkill(LocalDateTime startTime) {
        return skillUsageDataMapper.getUsageStatsBySkill(startTime);
    }
    
    public List<Map<String, Object>> getUsageStatsByIntent(LocalDateTime startTime) {
        return skillUsageDataMapper.getUsageStatsByIntent(startTime);
    }
    
    public List<SkillUsageData> getRecentUsage(String skillCode, int limit) {
        return skillUsageDataMapper.getRecentUsageBySkill(skillCode, limit);
    }
    
    public double getSuccessRate(String skillCode) {
        int total = skillUsageDataMapper.countTotalUsage(skillCode);
        if (total == 0) return 0.0;
        
        int success = skillUsageDataMapper.countSuccessfulUsage(skillCode);
        return (double) success / total;
    }
    
    private UsageStats calculateStats(String skillCode) {
        UsageStats stats = new UsageStats();
        stats.setSkillCode(skillCode);
        
        int total = skillUsageDataMapper.countTotalUsage(skillCode);
        int success = skillUsageDataMapper.countSuccessfulUsage(skillCode);
        
        stats.setTotalUsage(total);
        stats.setSuccessfulUsage(success);
        stats.setSuccessRate(total > 0 ? (double) success / total : 0.0);
        
        return stats;
    }
    
    private void refreshStatsCacheIfNeeded() {
        long now = System.currentTimeMillis();
        long cacheTtlMs = sysConfigService.getLongConfig("skill_usage_cache_ttl_ms", 60000);
        if (now - cacheTimestamp < cacheTtlMs && !statsCache.isEmpty()) {
            return;
        }
        
        synchronized (statsCache) {
            if (now - cacheTimestamp >= cacheTtlMs || statsCache.isEmpty()) {
                LocalDateTime startTime = LocalDateTime.now().minusDays(30);
                List<Map<String, Object>> statsList = skillUsageDataMapper.getUsageStatsBySkill(startTime);
                
                for (Map<String, Object> stat : statsList) {
                    String skillCode = (String) stat.get("skill_code");
                    UsageStats stats = new UsageStats();
                    stats.setSkillCode(skillCode);
                    stats.setTotalUsage(((Number) stat.get("count")).longValue());
                    stats.setSuccessfulUsage(((Number) stat.get("success_count")).longValue());
                    stats.setSuccessRate(stats.getTotalUsage() > 0 ? 
                            (double) stats.getSuccessfulUsage() / stats.getTotalUsage() : 0.0);
                    
                    if (stat.get("avg_time") != null) {
                        stats.setAvgExecutionTimeMs(((Number) stat.get("avg_time")).doubleValue());
                    }
                    if (stat.get("avg_rating") != null) {
                        stats.setAvgUserRating(((Number) stat.get("avg_rating")).doubleValue());
                    }
                    
                    statsCache.put(skillCode, stats);
                }
                
                cacheTimestamp = now;
            }
        }
    }
    
    private void invalidateStatsCache(String skillCode) {
        statsCache.remove(skillCode);
    }
    
    public void clearCache() {
        statsCache.clear();
        cacheTimestamp = 0;
    }
    
    public static class UsageStats {
        private String skillCode;
        private long totalUsage;
        private long successfulUsage;
        private double successRate;
        private double avgExecutionTimeMs;
        private double avgUserRating;
        
        public String getSkillCode() { return skillCode; }
        public void setSkillCode(String skillCode) { this.skillCode = skillCode; }
        public long getTotalUsage() { return totalUsage; }
        public void setTotalUsage(long totalUsage) { this.totalUsage = totalUsage; }
        public long getSuccessfulUsage() { return successfulUsage; }
        public void setSuccessfulUsage(long successfulUsage) { this.successfulUsage = successfulUsage; }
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
        public double getAvgExecutionTimeMs() { return avgExecutionTimeMs; }
        public void setAvgExecutionTimeMs(double avgExecutionTimeMs) { this.avgExecutionTimeMs = avgExecutionTimeMs; }
        public double getAvgUserRating() { return avgUserRating; }
        public void setAvgUserRating(double avgUserRating) { this.avgUserRating = avgUserRating; }
    }
}
