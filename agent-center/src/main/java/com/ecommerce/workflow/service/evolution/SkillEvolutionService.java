package com.ecommerce.workflow.service.evolution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.workflow.entity.EvolutionTrigger;
import com.ecommerce.workflow.entity.SkillEvolutionHistory;
import com.ecommerce.workflow.entity.SkillUsageData;
import com.ecommerce.workflow.mapper.EvolutionTriggerMapper;
import com.ecommerce.workflow.mapper.SkillEvolutionHistoryMapper;
import com.ecommerce.workflow.mapper.SkillUsageDataMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class SkillEvolutionService {
    
    private static final Logger log = LoggerFactory.getLogger(SkillEvolutionService.class);
    
    @Autowired
    private SkillUsageDataMapper skillUsageDataMapper;
    
    @Autowired
    private SkillEvolutionHistoryMapper evolutionHistoryMapper;
    
    @Autowired
    private EvolutionTriggerMapper evolutionTriggerMapper;
    
    @Autowired
    private SkillUsageTracker skillUsageTracker;
    
    @Value("${evolution.auto-trigger:true}")
    private boolean autoTrigger;
    
    @Value("${evolution.min-sample-size:10}")
    private int defaultMinSampleSize;
    
    public boolean shouldEvolve(String targetType, String targetCode) {
        List<EvolutionTrigger> triggers = getActiveTriggers(targetType, targetCode);
        
        for (EvolutionTrigger trigger : triggers) {
            if (checkTriggerCondition(trigger, targetType, targetCode)) {
                log.info("演进触发条件满足: type={}, code={}, trigger={}", 
                        targetType, targetCode, trigger.getTriggerType());
                return true;
            }
        }
        
        return false;
    }
    
    private List<EvolutionTrigger> getActiveTriggers(String targetType, String targetCode) {
        LambdaQueryWrapper<EvolutionTrigger> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvolutionTrigger::getIsActive, 1)
               .and(w -> w
                   .eq(EvolutionTrigger::getTargetType, targetType)
                   .and(inner -> inner
                       .eq(EvolutionTrigger::getTargetCode, targetCode)
                       .or()
                       .eq(EvolutionTrigger::getTargetCode, "*")
                   )
               );
        
        return evolutionTriggerMapper.selectList(wrapper);
    }
    
    private boolean checkTriggerCondition(EvolutionTrigger trigger, String targetType, String targetCode) {
        LocalDateTime startTime = LocalDateTime.now().minusDays(trigger.getTimeWindowDays());
        
        Map<String, Object> metrics = calculateMetrics(targetType, targetCode, startTime);
        
        long sampleSize = metrics.containsKey("totalUsage") ? 
                ((Number) metrics.get("totalUsage")).longValue() : 0;
        
        if (sampleSize < trigger.getMinSampleSize()) {
            return false;
        }
        
        double currentValue = 0.0;
        switch (trigger.getTriggerType()) {
            case "SUCCESS_RATE":
                currentValue = metrics.containsKey("successRate") ? 
                        ((Number) metrics.get("successRate")).doubleValue() : 0.0;
                break;
            case "USER_RATING":
                currentValue = metrics.containsKey("avgRating") ? 
                        ((Number) metrics.get("avgRating")).doubleValue() : 0.0;
                break;
            case "ERROR_RATE":
                currentValue = metrics.containsKey("errorRate") ? 
                        ((Number) metrics.get("errorRate")).doubleValue() : 0.0;
                break;
            case "USAGE_COUNT":
                currentValue = sampleSize;
                break;
            default:
                return false;
        }
        
        return compareValues(currentValue, trigger.getThresholdValue(), trigger.getComparisonOperator());
    }
    
    private boolean compareValues(double current, double threshold, String operator) {
        switch (operator) {
            case "<": return current < threshold;
            case ">": return current > threshold;
            case "=": return Math.abs(current - threshold) < 0.001;
            case "<=": return current <= threshold;
            case ">=": return current >= threshold;
            default: return false;
        }
    }
    
    private Map<String, Object> calculateMetrics(String targetType, String targetCode, LocalDateTime startTime) {
        Map<String, Object> metrics = new HashMap<>();
        
        LambdaQueryWrapper<SkillUsageData> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(SkillUsageData::getCreatedAt, startTime);
        
        if ("SKILL".equals(targetType)) {
            wrapper.eq(SkillUsageData::getSkillCode, targetCode);
        } else if ("PROMPT_TEMPLATE".equals(targetType)) {
            wrapper.eq(SkillUsageData::getPromptTemplateId, targetCode);
        }
        
        List<SkillUsageData> usageData = skillUsageDataMapper.selectList(wrapper);
        
        if (usageData.isEmpty()) {
            return metrics;
        }
        
        long totalUsage = usageData.size();
        long successCount = usageData.stream().filter(d -> Boolean.TRUE.equals(d.getSuccess())).count();
        long errorCount = totalUsage - successCount;
        
        double avgRating = usageData.stream()
                .filter(d -> d.getUserRating() != null)
                .mapToDouble(SkillUsageData::getUserRating)
                .average()
                .orElse(0.0);
        
        double avgTime = usageData.stream()
                .filter(d -> d.getExecutionTimeMs() != null)
                .mapToLong(SkillUsageData::getExecutionTimeMs)
                .average()
                .orElse(0.0);
        
        metrics.put("totalUsage", totalUsage);
        metrics.put("successCount", successCount);
        metrics.put("errorCount", errorCount);
        metrics.put("successRate", (double) successCount / totalUsage);
        metrics.put("errorRate", (double) errorCount / totalUsage);
        metrics.put("avgRating", avgRating);
        metrics.put("avgExecutionTime", avgTime);
        
        return metrics;
    }
    
    public Map<String, Object> analyzeForEvolution(String targetType, String targetCode) {
        Map<String, Object> analysis = new HashMap<>();
        
        analysis.put("targetType", targetType);
        analysis.put("targetCode", targetCode);
        analysis.put("analysisTime", LocalDateTime.now());
        
        LocalDateTime last7Days = LocalDateTime.now().minusDays(7);
        LocalDateTime last30Days = LocalDateTime.now().minusDays(30);
        
        Map<String, Object> metrics7Days = calculateMetrics(targetType, targetCode, last7Days);
        Map<String, Object> metrics30Days = calculateMetrics(targetType, targetCode, last30Days);
        
        analysis.put("metrics7Days", metrics7Days);
        analysis.put("metrics30Days", metrics30Days);
        
        List<String> recommendations = new ArrayList<>();
        
        if (metrics7Days.containsKey("successRate")) {
            double successRate = ((Number) metrics7Days.get("successRate")).doubleValue();
            if (successRate < 0.7) {
                recommendations.add("成功率偏低(" + String.format("%.1f%%", successRate * 100) + ")，建议优化参数或调整策略");
            }
        }
        
        if (metrics7Days.containsKey("avgRating")) {
            double avgRating = ((Number) metrics7Days.get("avgRating")).doubleValue();
            if (avgRating < 3.5 && avgRating > 0) {
                recommendations.add("用户评分较低(" + String.format("%.1f", avgRating) + ")，建议优化输出质量或调整提示词模板");
            }
        }
        
        if (metrics7Days.containsKey("avgExecutionTime")) {
            double avgTime = ((Number) metrics7Days.get("avgExecutionTime")).doubleValue();
            if (avgTime > 5000) {
                recommendations.add("执行时间过长(" + String.format("%.0fms", avgTime) + ")，建议优化参数或减少重试");
            }
        }
        
        analysis.put("recommendations", recommendations);
        analysis.put("shouldEvolve", shouldEvolve(targetType, targetCode));
        
        return analysis;
    }
    
    public Long recordEvolutionHistory(String targetType, String targetCode, String evolutionType,
                                       String beforeState, String afterState, String reason,
                                       Map<String, Object> metrics) {
        SkillEvolutionHistory history = new SkillEvolutionHistory();
        history.setTargetType(targetType);
        history.setTargetCode(targetCode);
        history.setEvolutionType(evolutionType);
        history.setBeforeState(beforeState);
        history.setAfterState(afterState);
        history.setReason(reason);
        history.setMetricsJson(metrics != null ? metrics : new HashMap<>());
        history.setStatus("PENDING");
        history.setCreatedAt(LocalDateTime.now());
        
        evolutionHistoryMapper.insert(history);
        
        log.info("记录演进历史: type={}, code={}, evolutionType={}", 
                targetType, targetCode, evolutionType);
        
        return history.getId();
    }
    
    public void updateEvolutionStatus(Long historyId, String status, String result) {
        SkillEvolutionHistory history = evolutionHistoryMapper.selectById(historyId);
        if (history != null) {
            history.setStatus(status);
            history.setResult(result);
            history.setCompletedAt(LocalDateTime.now());
            evolutionHistoryMapper.updateById(history);
        }
    }
    
    @Scheduled(fixedRate = 3600000)
    public void scheduledEvolutionCheck() {
        if (!autoTrigger) {
            return;
        }
        
        log.info("开始定时演进检查...");
        
        List<Map<String, Object>> skillStats = skillUsageDataMapper.getUsageStatsBySkill(
                LocalDateTime.now().minusDays(7));
        
        for (Map<String, Object> stat : skillStats) {
            String skillCode = (String) stat.get("skill_code");
            if (shouldEvolve("SKILL", skillCode)) {
                log.info("Skill需要演进: {}", skillCode);
            }
        }
    }
    
    public List<SkillEvolutionHistory> getEvolutionHistory(String targetType, String targetCode) {
        LambdaQueryWrapper<SkillEvolutionHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SkillEvolutionHistory::getTargetType, targetType)
               .eq(SkillEvolutionHistory::getTargetCode, targetCode)
               .orderByDesc(SkillEvolutionHistory::getCreatedAt);
        
        return evolutionHistoryMapper.selectList(wrapper);
    }
    
    public List<SkillEvolutionHistory> getPendingEvolutions() {
        LambdaQueryWrapper<SkillEvolutionHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SkillEvolutionHistory::getStatus, "PENDING")
               .orderByAsc(SkillEvolutionHistory::getCreatedAt);
        
        return evolutionHistoryMapper.selectList(wrapper);
    }
}
