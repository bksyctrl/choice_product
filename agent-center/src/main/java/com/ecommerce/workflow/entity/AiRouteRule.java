package com.ecommerce.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("ai_route_rule")
public class AiRouteRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleName;
    private String taskType;
    private String strategy;
    private String preferredModels;
    private String excludedModels;
    private Integer fallbackEnabled;
    private Integer maxFallbackDepth;
    private Integer priority;
    private Integer enabled;
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

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getPreferredModels() {
        return preferredModels;
    }

    public void setPreferredModels(String preferredModels) {
        this.preferredModels = preferredModels;
    }

    public String getExcludedModels() {
        return excludedModels;
    }

    public void setExcludedModels(String excludedModels) {
        this.excludedModels = excludedModels;
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
