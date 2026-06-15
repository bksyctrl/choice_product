package com.ecommerce.workflow.service.evolution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.workflow.entity.ABTestConfig;
import com.ecommerce.workflow.entity.ABTestResult;
import com.ecommerce.workflow.mapper.ABTestConfigMapper;
import com.ecommerce.workflow.mapper.ABTestResultMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ABTestFramework {
    
    private static final Logger log = LoggerFactory.getLogger(ABTestFramework.class);
    
    @Autowired
    private ABTestConfigMapper abTestConfigMapper;
    
    @Autowired
    private ABTestResultMapper abTestResultMapper;
    
    private final Map<String, ABTestConfig> activeTests = new ConcurrentHashMap<>();
    private final Map<String, Integer> groupAssignment = new ConcurrentHashMap<>();
    
    public String assignTestGroup(String testCode, Long userId) {
        ABTestConfig config = getActiveTest(testCode);
        if (config == null) {
            return "control";
        }
        
        String key = testCode + "_" + userId;
        Integer group = groupAssignment.get(key);
        
        if (group != null) {
            return group == 0 ? "control" : "treatment";
        }
        
        double random = Math.random();
        double treatmentRatio = config.getTreatmentRatio() != null ? 
                config.getTreatmentRatio() : 0.5;
        
        int assignedGroup = random < treatmentRatio ? 1 : 0;
        groupAssignment.put(key, assignedGroup);
        
        log.debug("分配测试组: testCode={}, userId={}, group={}", 
                testCode, userId, assignedGroup == 0 ? "control" : "treatment");
        
        return assignedGroup == 0 ? "control" : "treatment";
    }
    
    public void collectTestResult(String testCode, Long userId, String group,
                                  boolean success, Double userRating, 
                                  Map<String, Object> metrics) {
        ABTestResult result = new ABTestResult();
        result.setTestCode(testCode);
        result.setUserId(userId);
        result.setTestGroup(group);
        result.setSuccess(success);
        result.setUserRating(userRating);
        result.setMetricsJson(metrics != null ? metrics : new HashMap<>());
        result.setCreatedAt(LocalDateTime.now());
        
        abTestResultMapper.insert(result);
        
        log.debug("收集测试结果: testCode={}, userId={}, group={}, success={}", 
                testCode, userId, group, success);
    }
    
    public Map<String, Object> validateSignificance(String testCode) {
        Map<String, Object> result = new HashMap<>();
        
        LambdaQueryWrapper<ABTestResult> controlWrapper = new LambdaQueryWrapper<>();
        controlWrapper.eq(ABTestResult::getTestCode, testCode)
                     .eq(ABTestResult::getTestGroup, "control");
        List<ABTestResult> controlResults = abTestResultMapper.selectList(controlWrapper);
        
        LambdaQueryWrapper<ABTestResult> treatmentWrapper = new LambdaQueryWrapper<>();
        treatmentWrapper.eq(ABTestResult::getTestCode, testCode)
                       .eq(ABTestResult::getTestGroup, "treatment");
        List<ABTestResult> treatmentResults = abTestResultMapper.selectList(treatmentWrapper);
        
        if (controlResults.isEmpty() || treatmentResults.isEmpty()) {
            result.put("significant", false);
            result.put("reason", "数据不足");
            return result;
        }
        
        long controlSuccess = controlResults.stream()
                .filter(r -> Boolean.TRUE.equals(r.getSuccess()))
                .count();
        long treatmentSuccess = treatmentResults.stream()
                .filter(r -> Boolean.TRUE.equals(r.getSuccess()))
                .count();
        
        double controlRate = (double) controlSuccess / controlResults.size();
        double treatmentRate = (double) treatmentSuccess / treatmentResults.size();
        
        double zScore = calculateZScore(
                controlSuccess, controlResults.size(),
                treatmentSuccess, treatmentResults.size()
        );
        
        double pValue = calculatePValue(zScore);
        boolean significant = pValue < 0.05;
        
        result.put("significant", significant);
        result.put("pValue", pValue);
        result.put("zScore", zScore);
        result.put("controlRate", controlRate);
        result.put("treatmentRate", treatmentRate);
        result.put("improvement", treatmentRate - controlRate);
        result.put("controlSampleSize", controlResults.size());
        result.put("treatmentSampleSize", treatmentResults.size());
        
        if (significant) {
            result.put("winner", treatmentRate > controlRate ? "treatment" : "control");
        }
        
        return result;
    }
    
    private double calculateZScore(long success1, long total1, long success2, long total2) {
        double p1 = (double) success1 / total1;
        double p2 = (double) success2 / total2;
        
        double pPooled = (double) (success1 + success2) / (total1 + total2);
        
        double se = Math.sqrt(pPooled * (1 - pPooled) * (1.0/total1 + 1.0/total2));
        
        if (se == 0) return 0;
        
        return (p2 - p1) / se;
    }
    
    private double calculatePValue(double zScore) {
        double absZ = Math.abs(zScore);
        double p = 2 * (1 - normalCDF(absZ));
        return Math.max(0, Math.min(1, p));
    }
    
    private double normalCDF(double x) {
        double a1 =  0.254829592;
        double a2 = -0.284496736;
        double a3 =  1.421413741;
        double a4 = -1.453152027;
        double a5 =  1.061405429;
        double p  =  0.3275911;
        
        int sign = x < 0 ? -1 : 1;
        x = Math.abs(x) / Math.sqrt(2);
        
        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);
        
        return 0.5 * (1.0 + sign * y);
    }
    
    public ABTestConfig createTest(String testCode, String testName, String targetType,
                                   String targetCode, String controlValue, String treatmentValue,
                                   Double treatmentRatio) {
        ABTestConfig config = new ABTestConfig();
        config.setTestCode(testCode);
        config.setTestName(testName);
        config.setTargetType(targetType);
        config.setTargetCode(targetCode);
        config.setControlValue(controlValue);
        config.setTreatmentValue(treatmentValue);
        config.setTreatmentRatio(treatmentRatio != null ? treatmentRatio : 0.5);
        config.setStatus("RUNNING");
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        
        abTestConfigMapper.insert(config);
        activeTests.put(testCode, config);
        
        log.info("创建A/B测试: testCode={}, name={}", testCode, testName);
        return config;
    }
    
    public void startTest(String testCode) {
        ABTestConfig config = abTestConfigMapper.selectByTestCode(testCode);
        if (config != null) {
            config.setStatus("RUNNING");
            config.setStartedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            abTestConfigMapper.updateById(config);
            activeTests.put(testCode, config);
            
            log.info("启动A/B测试: testCode={}", testCode);
        }
    }
    
    public void stopTest(String testCode) {
        ABTestConfig config = abTestConfigMapper.selectByTestCode(testCode);
        if (config != null) {
            config.setStatus("COMPLETED");
            config.setCompletedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            abTestConfigMapper.updateById(config);
            activeTests.remove(testCode);
            
            log.info("停止A/B测试: testCode={}", testCode);
        }
    }
    
    public ABTestConfig getActiveTest(String testCode) {
        ABTestConfig config = activeTests.get(testCode);
        if (config == null) {
            config = abTestConfigMapper.selectByTestCode(testCode);
            if (config != null && "RUNNING".equals(config.getStatus())) {
                activeTests.put(testCode, config);
            }
        }
        return config;
    }
    
    public List<ABTestConfig> getRunningTests() {
        LambdaQueryWrapper<ABTestConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ABTestConfig::getStatus, "RUNNING");
        return abTestConfigMapper.selectList(wrapper);
    }
    
    public Map<String, Object> getTestStats(String testCode) {
        Map<String, Object> stats = new HashMap<>();
        
        ABTestConfig config = abTestConfigMapper.selectByTestCode(testCode);
        if (config == null) {
            return stats;
        }
        
        stats.put("testCode", testCode);
        stats.put("testName", config.getTestName());
        stats.put("status", config.getStatus());
        
        LambdaQueryWrapper<ABTestResult> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ABTestResult::getTestCode, testCode);
        List<ABTestResult> results = abTestResultMapper.selectList(wrapper);
        
        Map<String, Long> groupCounts = new HashMap<>();
        Map<String, Long> successCounts = new HashMap<>();
        Map<String, Double> ratingSums = new HashMap<>();
        Map<String, Long> ratingCounts = new HashMap<>();
        
        for (ABTestResult r : results) {
            String group = r.getTestGroup();
            groupCounts.merge(group, 1L, Long::sum);
            
            if (Boolean.TRUE.equals(r.getSuccess())) {
                successCounts.merge(group, 1L, Long::sum);
            }
            
            if (r.getUserRating() != null) {
                ratingSums.merge(group, r.getUserRating(), Double::sum);
                ratingCounts.merge(group, 1L, Long::sum);
            }
        }
        
        stats.put("groupCounts", groupCounts);
        stats.put("successCounts", successCounts);
        
        Map<String, Double> successRates = new HashMap<>();
        for (String group : groupCounts.keySet()) {
            long total = groupCounts.get(group);
            long success = successCounts.getOrDefault(group, 0L);
            successRates.put(group, total > 0 ? (double) success / total : 0.0);
        }
        stats.put("successRates", successRates);
        
        Map<String, Double> avgRatings = new HashMap<>();
        for (String group : ratingSums.keySet()) {
            long count = ratingCounts.getOrDefault(group, 0L);
            double sum = ratingSums.get(group);
            avgRatings.put(group, count > 0 ? sum / count : 0.0);
        }
        stats.put("avgRatings", avgRatings);
        
        if ("RUNNING".equals(config.getStatus())) {
            stats.put("significance", validateSignificance(testCode));
        }
        
        return stats;
    }
}
