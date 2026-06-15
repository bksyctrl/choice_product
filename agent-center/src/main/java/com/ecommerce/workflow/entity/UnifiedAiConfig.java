package com.ecommerce.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("unified_ai_config")
public class UnifiedAiConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String configCode;
    private String configName;
    
    private String providerType;
    private String baseUrl;
    private String apiKey;
    private String models;
    private String defaultModel;
    
    private String taskTypes;
    private String routingStrategy;
    private Integer fallbackEnabled;
    private Integer maxFallbackDepth;
    
    private String capabilities;
    private BigDecimal qualityScore;
    private BigDecimal speedScore;
    private BigDecimal costRate;
    
    private String videoParams;
    private String imageParams;
    
    private String requestTemplate;
    private String responseTemplate;
    private String authTemplate;
    
    private Integer priority;
    private Integer enabled;
    private Integer dailyQuota;
    private Integer dailyUsed;
    private Integer failCount;
    private LocalDateTime lastSuccessAt;
    private LocalDateTime lastFailAt;
    
    private Long createdBy;
    private Long updatedBy;
    
    private Long creatorId;
    
    private String extendedParams;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConfigCode() {
        return configCode;
    }

    public void setConfigCode(String configCode) {
        this.configCode = configCode;
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModels() {
        return models;
    }

    public void setModels(String models) {
        this.models = models;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public String getTaskTypes() {
        return taskTypes;
    }

    public void setTaskTypes(String taskTypes) {
        this.taskTypes = taskTypes;
    }

    public String getRoutingStrategy() {
        return routingStrategy;
    }

    public void setRoutingStrategy(String routingStrategy) {
        this.routingStrategy = routingStrategy;
    }

    public Integer getFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(Integer fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    public Integer getMaxFallbackDepth() {
        return maxFallbackDepth;
    }

    public void setMaxFallbackDepth(Integer maxFallbackDepth) {
        this.maxFallbackDepth = maxFallbackDepth;
    }

    public String getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(String capabilities) {
        this.capabilities = capabilities;
    }

    public BigDecimal getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(BigDecimal qualityScore) {
        this.qualityScore = qualityScore;
    }

    public BigDecimal getSpeedScore() {
        return speedScore;
    }

    public void setSpeedScore(BigDecimal speedScore) {
        this.speedScore = speedScore;
    }

    public BigDecimal getCostRate() {
        return costRate;
    }

    public void setCostRate(BigDecimal costRate) {
        this.costRate = costRate;
    }

    public String getVideoParams() {
        return videoParams;
    }

    public void setVideoParams(String videoParams) {
        this.videoParams = videoParams;
    }

    public String getImageParams() {
        return imageParams;
    }

    public void setImageParams(String imageParams) {
        this.imageParams = imageParams;
    }

    public String getRequestTemplate() {
        return requestTemplate;
    }

    public void setRequestTemplate(String requestTemplate) {
        this.requestTemplate = requestTemplate;
    }

    public String getResponseTemplate() {
        return responseTemplate;
    }

    public void setResponseTemplate(String responseTemplate) {
        this.responseTemplate = responseTemplate;
    }

    public String getAuthTemplate() {
        return authTemplate;
    }

    public void setAuthTemplate(String authTemplate) {
        this.authTemplate = authTemplate;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public Integer getDailyQuota() {
        return dailyQuota;
    }

    public void setDailyQuota(Integer dailyQuota) {
        this.dailyQuota = dailyQuota;
    }

    public Integer getDailyUsed() {
        return dailyUsed;
    }

    public void setDailyUsed(Integer dailyUsed) {
        this.dailyUsed = dailyUsed;
    }

    public Integer getFailCount() {
        return failCount;
    }

    public void setFailCount(Integer failCount) {
        this.failCount = failCount;
    }

    public LocalDateTime getLastSuccessAt() {
        return lastSuccessAt;
    }

    public void setLastSuccessAt(LocalDateTime lastSuccessAt) {
        this.lastSuccessAt = lastSuccessAt;
    }

    public LocalDateTime getLastFailAt() {
        return lastFailAt;
    }

    public void setLastFailAt(LocalDateTime lastFailAt) {
        this.lastFailAt = lastFailAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public String getExtendedParams() {
        return extendedParams;
    }

    public void setExtendedParams(String extendedParams) {
        this.extendedParams = extendedParams;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
