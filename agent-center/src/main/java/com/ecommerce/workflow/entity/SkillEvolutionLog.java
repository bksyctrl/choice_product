package com.ecommerce.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("sys_skill_evolution_log")
public class SkillEvolutionLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String skillCode;
    private String fromVersion;
    private String toVersion;
    private String evolutionType;
    private String paramKey;
    private String fromValue;
    private String toValue;
    private String changeReason;
    private String evidenceData;
    private Double metricBefore;
    private Double metricAfter;
    private Double improvementPct;
    private String status;
    private Boolean rolledBack;
    private String rollbackReason;
    private String userFeedback;
    private String feedbackComment;
    private Long triggeredBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // Getters
    public Long getId() {
        return id;
    }

    public String getSkillCode() {
        return skillCode;
    }

    public String getFromVersion() {
        return fromVersion;
    }

    public String getToVersion() {
        return toVersion;
    }

    public String getEvolutionType() {
        return evolutionType;
    }

    public String getParamKey() {
        return paramKey;
    }

    public String getFromValue() {
        return fromValue;
    }

    public String getToValue() {
        return toValue;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public String getEvidenceData() {
        return evidenceData;
    }

    public Double getMetricBefore() {
        return metricBefore;
    }

    public Double getMetricAfter() {
        return metricAfter;
    }

    public Double getImprovementPct() {
        return improvementPct;
    }

    public String getStatus() {
        return status;
    }

    public Boolean getRolledBack() {
        return rolledBack;
    }

    public String getRollbackReason() {
        return rollbackReason;
    }

    public String getUserFeedback() {
        return userFeedback;
    }

    public String getFeedbackComment() {
        return feedbackComment;
    }

    public Long getTriggeredBy() {
        return triggeredBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // Setters
    public void setId(Long id) {
        this.id = id;
    }

    public void setSkillCode(String skillCode) {
        this.skillCode = skillCode;
    }

    public void setFromVersion(String fromVersion) {
        this.fromVersion = fromVersion;
    }

    public void setToVersion(String toVersion) {
        this.toVersion = toVersion;
    }

    public void setEvolutionType(String evolutionType) {
        this.evolutionType = evolutionType;
    }

    public void setParamKey(String paramKey) {
        this.paramKey = paramKey;
    }

    public void setFromValue(String fromValue) {
        this.fromValue = fromValue;
    }

    public void setToValue(String toValue) {
        this.toValue = toValue;
    }

    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }

    public void setEvidenceData(String evidenceData) {
        this.evidenceData = evidenceData;
    }

    public void setMetricBefore(Double metricBefore) {
        this.metricBefore = metricBefore;
    }

    public void setMetricAfter(Double metricAfter) {
        this.metricAfter = metricAfter;
    }

    public void setImprovementPct(Double improvementPct) {
        this.improvementPct = improvementPct;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setRolledBack(Boolean rolledBack) {
        this.rolledBack = rolledBack;
    }

    public void setRollbackReason(String rollbackReason) {
        this.rollbackReason = rollbackReason;
    }

    public void setUserFeedback(String userFeedback) {
        this.userFeedback = userFeedback;
    }

    public void setFeedbackComment(String feedbackComment) {
        this.feedbackComment = feedbackComment;
    }

    public void setTriggeredBy(Long triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
