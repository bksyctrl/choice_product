package com.ecommerce.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("sys_workflow_definition")
public class WorkflowDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String workflowName;
    @TableField("workflow_code")
    private String workflowCode;
    private String description;
    private Integer version;
    private Integer status;
    private String dagConfig;
    private String nodes;
    private String edges;
    private String variables;
    private Integer nodeCount;
    private String businessType;
    private Long productId;
    private Long categoryId;
    private Long sopTemplateId;
    private String skillId;
    private String triggerType;
    private String triggerConfig;
    private String source;
    private Integer timeoutSeconds;
    private String retryPolicy;
    private Integer executionCount;
    private Integer successCount;
    private Integer avgDuration;
    private Long createdBy;
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    // Getters
    public Long getId() {
        return id;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public String getWorkflowCode() {
        return workflowCode;
    }

    public String getDescription() {
        return description;
    }

    public Integer getVersion() {
        return version;
    }

    public Integer getStatus() {
        return status;
    }

    public String getDagConfig() {
        return dagConfig;
    }

    public String getNodes() {
        return nodes;
    }

    public String getEdges() {
        return edges;
    }

    public String getVariables() {
        return variables;
    }

    public Integer getNodeCount() {
        return nodeCount;
    }

    public String getBusinessType() {
        return businessType;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public Long getSopTemplateId() {
        return sopTemplateId;
    }

    public String getSkillId() {
        return skillId;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public String getTriggerConfig() {
        return triggerConfig;
    }

    public String getSource() {
        return source;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public String getRetryPolicy() {
        return retryPolicy;
    }

    public Integer getExecutionCount() {
        return executionCount;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public Integer getAvgDuration() {
        return avgDuration;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Integer getDeleted() {
        return deleted;
    }

    // Setters
    public void setId(Long id) {
        this.id = id;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    public void setWorkflowCode(String workflowCode) {
        this.workflowCode = workflowCode;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public void setDagConfig(String dagConfig) {
        this.dagConfig = dagConfig;
    }

    public void setNodes(String nodes) {
        this.nodes = nodes;
    }

    public void setEdges(String edges) {
        this.edges = edges;
    }

    public void setVariables(String variables) {
        this.variables = variables;
    }

    public void setNodeCount(Integer nodeCount) {
        this.nodeCount = nodeCount;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public void setSopTemplateId(Long sopTemplateId) {
        this.sopTemplateId = sopTemplateId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public void setTriggerConfig(String triggerConfig) {
        this.triggerConfig = triggerConfig;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public void setRetryPolicy(String retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    public void setExecutionCount(Integer executionCount) {
        this.executionCount = executionCount;
    }

    public void setSuccessCount(Integer successCount) {
        this.successCount = successCount;
    }

    public void setAvgDuration(Integer avgDuration) {
        this.avgDuration = avgDuration;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
