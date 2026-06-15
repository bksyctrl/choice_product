package com.ecommerce.workflow.service.template;

import com.ecommerce.workflow.entity.PromptTemplate;
import com.ecommerce.workflow.mapper.PromptTemplateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PromptOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(PromptOptimizationService.class);

    private final PromptTemplateMapper templateMapper;
    private final PromptTemplateService templateService;
    
    private final Map<String, List<TemplateUsageData>> usageDataMap = new ConcurrentHashMap<>();
    private final Map<String, ABTestGroup> abTestGroups = new ConcurrentHashMap<>();

    public PromptOptimizationService(PromptTemplateMapper templateMapper, 
                                    PromptTemplateService templateService) {
        this.templateMapper = templateMapper;
        this.templateService = templateService;
    }

    public void recordTemplateUsage(String templateCode, TemplateUsageData usageData) {
        List<TemplateUsageData> usageList = usageDataMap.computeIfAbsent(
            templateCode, k -> new ArrayList<>()
        );
        usageList.add(usageData);
        
        if (usageList.size() >= 100) {
            analyzeAndOptimize(templateCode);
        }
    }

    private void analyzeAndOptimize(String templateCode) {
        List<TemplateUsageData> usageList = usageDataMap.get(templateCode);
        if (usageList == null || usageList.isEmpty()) {
            return;
        }

        double avgSuccessRate = calculateAverageSuccessRate(usageList);
        double avgUserRating = calculateAverageUserRating(usageList);
        
        log.info("模板性能分析: 模板代码={}, 平均成功率={}, 平均用户评分={}", 
            templateCode, avgSuccessRate, avgUserRating);

        if (avgSuccessRate < 0.7 || avgUserRating < 3.5) {
            log.info("模板需要优化: {}", templateCode);
            triggerOptimization(templateCode, usageList);
        }
    }

    private void triggerOptimization(String templateCode, List<TemplateUsageData> usageList) {
        PromptTemplate currentTemplate = templateService.getTemplate(templateCode);
        if (currentTemplate == null) {
            return;
        }

        String optimizedContent = generateOptimizedContent(currentTemplate, usageList);
        
        PromptTemplate optimizedTemplate = new PromptTemplate();
        optimizedTemplate.setTemplateCode(templateCode + "_optimized");
        optimizedTemplate.setTemplateName(currentTemplate.getTemplateName() + " (优化版)");
        optimizedTemplate.setTemplateContent(optimizedContent);
        optimizedTemplate.setTemplateType(currentTemplate.getTemplateType());
        optimizedTemplate.setVariables(currentTemplate.getVariables());
        optimizedTemplate.setVersion(currentTemplate.getVersion() + 1);
        optimizedTemplate.setAbTestGroup("B");
        
        templateService.registerTemplate(optimizedTemplate);
        
        startABTest(templateCode, optimizedTemplate.getTemplateCode());
    }

    private String generateOptimizedContent(PromptTemplate currentTemplate, 
                                           List<TemplateUsageData> usageList) {
        StringBuilder optimized = new StringBuilder(currentTemplate.getTemplateContent());
        
        Map<String, Integer> failurePatterns = analyzeFailurePatterns(usageList);
        
        if (!failurePatterns.isEmpty()) {
            optimized.append("\n\n## 优化说明\n");
            for (Map.Entry<String, Integer> entry : failurePatterns.entrySet()) {
                optimized.append("- ").append(entry.getKey())
                    .append(" (出现次数: ").append(entry.getValue()).append(")\n");
            }
        }
        
        return optimized.toString();
    }

    private Map<String, Integer> analyzeFailurePatterns(List<TemplateUsageData> usageList) {
        Map<String, Integer> patterns = new HashMap<>();
        
        for (TemplateUsageData data : usageList) {
            if (!data.isSuccess()) {
                String failureReason = data.getFailureReason();
                if (failureReason != null) {
                    patterns.merge(failureReason, 1, Integer::sum);
                }
            }
        }
        
        return patterns;
    }

    private void startABTest(String originalCode, String optimizedCode) {
        ABTestGroup testGroup = new ABTestGroup();
        testGroup.setOriginalCode(originalCode);
        testGroup.setOptimizedCode(optimizedCode);
        testGroup.setStartTime(new Date());
        testGroup.setSampleSize(100);
        
        abTestGroups.put(originalCode, testGroup);
        
        log.info("A/B测试开始: 原始版本={}, 优化版本={}", originalCode, optimizedCode);
    }

    public String getTemplateForABTest(String templateCode) {
        ABTestGroup testGroup = abTestGroups.get(templateCode);
        if (testGroup == null) {
            return templateCode;
        }

        if (testGroup.shouldUseOptimized()) {
            return testGroup.getOptimizedCode();
        }
        
        return templateCode;
    }

    public ABTestResult validateABTest(String templateCode) {
        ABTestGroup testGroup = abTestGroups.get(templateCode);
        if (testGroup == null) {
            return null;
        }

        List<TemplateUsageData> originalUsage = usageDataMap.get(templateCode);
        List<TemplateUsageData> optimizedUsage = usageDataMap.get(testGroup.getOptimizedCode());
        
        if (originalUsage == null || optimizedUsage == null) {
            return null;
        }

        double originalSuccessRate = calculateAverageSuccessRate(originalUsage);
        double optimizedSuccessRate = calculateAverageSuccessRate(optimizedUsage);
        
        boolean isSignificant = isStatisticallySignificant(
            originalSuccessRate, optimizedSuccessRate,
            originalUsage.size(), optimizedUsage.size()
        );

        ABTestResult result = new ABTestResult();
        result.setTemplateCode(templateCode);
        result.setOriginalSuccessRate(originalSuccessRate);
        result.setOptimizedSuccessRate(optimizedSuccessRate);
        result.setSignificant(isSignificant);
        result.setShouldDeploy(isSignificant && optimizedSuccessRate > originalSuccessRate);
        
        return result;
    }

    private double calculateAverageSuccessRate(List<TemplateUsageData> usageList) {
        if (usageList == null || usageList.isEmpty()) {
            return 0.0;
        }
        
        return usageList.stream()
            .mapToDouble(data -> data.isSuccess() ? 1.0 : 0.0)
            .average()
            .orElse(0.0);
    }

    private double calculateAverageUserRating(List<TemplateUsageData> usageList) {
        if (usageList == null || usageList.isEmpty()) {
            return 0.0;
        }
        
        return usageList.stream()
            .filter(data -> data.getUserRating() > 0)
            .mapToDouble(TemplateUsageData::getUserRating)
            .average()
            .orElse(0.0);
    }

    private boolean isStatisticallySignificant(double rate1, double rate2, 
                                               int size1, int size2) {
        if (size1 < 30 || size2 < 30) {
            return false;
        }
        
        double difference = Math.abs(rate1 - rate2);
        double pooledRate = (rate1 * size1 + rate2 * size2) / (size1 + size2);
        double standardError = Math.sqrt(
            pooledRate * (1 - pooledRate) * (1.0/size1 + 1.0/size2)
        );
        
        double zScore = difference / standardError;
        
        return zScore > 1.96;
    }

    public static class TemplateUsageData {
        private boolean success;
        private double userRating;
        private String failureReason;
        private long executionTime;
        private Map<String, Object> metadata;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public double getUserRating() {
            return userRating;
        }

        public void setUserRating(double userRating) {
            this.userRating = userRating;
        }

        public String getFailureReason() {
            return failureReason;
        }

        public void setFailureReason(String failureReason) {
            this.failureReason = failureReason;
        }

        public long getExecutionTime() {
            return executionTime;
        }

        public void setExecutionTime(long executionTime) {
            this.executionTime = executionTime;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }

    public static class ABTestGroup {
        private String originalCode;
        private String optimizedCode;
        private Date startTime;
        private int sampleSize;
        private Random random = new Random();

        public String getOriginalCode() {
            return originalCode;
        }

        public void setOriginalCode(String originalCode) {
            this.originalCode = originalCode;
        }

        public String getOptimizedCode() {
            return optimizedCode;
        }

        public void setOptimizedCode(String optimizedCode) {
            this.optimizedCode = optimizedCode;
        }

        public Date getStartTime() {
            return startTime;
        }

        public void setStartTime(Date startTime) {
            this.startTime = startTime;
        }

        public int getSampleSize() {
            return sampleSize;
        }

        public void setSampleSize(int sampleSize) {
            this.sampleSize = sampleSize;
        }

        public boolean shouldUseOptimized() {
            return random.nextDouble() < 0.5;
        }
    }

    public static class ABTestResult {
        private String templateCode;
        private double originalSuccessRate;
        private double optimizedSuccessRate;
        private boolean significant;
        private boolean shouldDeploy;

        public String getTemplateCode() {
            return templateCode;
        }

        public void setTemplateCode(String templateCode) {
            this.templateCode = templateCode;
        }

        public double getOriginalSuccessRate() {
            return originalSuccessRate;
        }

        public void setOriginalSuccessRate(double originalSuccessRate) {
            this.originalSuccessRate = originalSuccessRate;
        }

        public double getOptimizedSuccessRate() {
            return optimizedSuccessRate;
        }

        public void setOptimizedSuccessRate(double optimizedSuccessRate) {
            this.optimizedSuccessRate = optimizedSuccessRate;
        }

        public boolean isSignificant() {
            return significant;
        }

        public void setSignificant(boolean significant) {
            this.significant = significant;
        }

        public boolean isShouldDeploy() {
            return shouldDeploy;
        }

        public void setShouldDeploy(boolean shouldDeploy) {
            this.shouldDeploy = shouldDeploy;
        }
    }
}
