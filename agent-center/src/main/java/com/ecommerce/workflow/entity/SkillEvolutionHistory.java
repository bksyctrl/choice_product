package com.ecommerce.workflow.entity;

import java.time.LocalDateTime;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

@TableName(value = "skill_evolution_history", autoResultMap = true)
public class SkillEvolutionHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String targetType;

    private String targetCode;

    private String evolutionType;

    private String beforeState;

    private String afterState;

    private String reason;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metricsJson;

    private String status;

    private String result;

    private LocalDateTime completedAt;

    private String skillId;

    private Integer fromVersion;

    private Integer toVersion;

    private String triggerReason;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object optimizationData;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object abTestResult;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object effectComparison;

    private Boolean deployed;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetCode() {
        return targetCode;
    }

    public void setTargetCode(String targetCode) {
        this.targetCode = targetCode;
    }

    public String getEvolutionType() {
        return evolutionType;
    }

    public void setEvolutionType(String evolutionType) {
        this.evolutionType = evolutionType;
    }

    public String getBeforeState() {
        return beforeState;
    }

    public void setBeforeState(String beforeState) {
        this.beforeState = beforeState;
    }

    public String getAfterState() {
        return afterState;
    }

    public void setAfterState(String afterState) {
        this.afterState = afterState;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Map<String, Object> getMetricsJson() {
        return metricsJson;
    }

    public void setMetricsJson(Map<String, Object> metricsJson) {
        this.metricsJson = metricsJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public Integer getFromVersion() {
        return fromVersion;
    }

    public void setFromVersion(Integer fromVersion) {
        this.fromVersion = fromVersion;
    }

    public Integer getToVersion() {
        return toVersion;
    }

    public void setToVersion(Integer toVersion) {
        this.toVersion = toVersion;
    }

    public String getTriggerReason() {
        return triggerReason;
    }

    public void setTriggerReason(String triggerReason) {
        this.triggerReason = triggerReason;
    }

    public Object getOptimizationData() {
        return optimizationData;
    }

    public void setOptimizationData(Object optimizationData) {
        this.optimizationData = optimizationData;
    }

    public Object getAbTestResult() {
        return abTestResult;
    }

    public void setAbTestResult(Object abTestResult) {
        this.abTestResult = abTestResult;
    }

    public Object getEffectComparison() {
        return effectComparison;
    }

    public void setEffectComparison(Object effectComparison) {
        this.effectComparison = effectComparison;
    }

    public Boolean getDeployed() {
        return deployed;
    }

    public void setDeployed(Boolean deployed) {
        this.deployed = deployed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
