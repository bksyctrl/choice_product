package com.ecommerce.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("sys_workflow_node")
public class WorkflowNode {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("node_code")
    private String nodeCode;
    private String nodeName;
    private String nodeType;
    private String nodeGroup;
    private String category;
    private String description;
    private String inputSchema;
    private String outputSchema;
    private String executeClass;
    private Integer timeoutSeconds;
    private Integer retryCount;
    private Integer retryInterval;
    private Integer status;
    private Integer isManualReview;
    private String configParams;
    private String apiEndpoint;
    private String icon;
    private String color;
    private Integer sortOrder;
    private Long createdBy;
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

    public String getNodeCode() {
        return nodeCode;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getNodeType() {
        return nodeType;
    }

    public String getNodeGroup() {
        return nodeGroup;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public String getInputSchema() {
        return inputSchema;
    }

    public String getOutputSchema() {
        return outputSchema;
    }

    public String getExecuteClass() {
        return executeClass;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public Integer getRetryInterval() {
        return retryInterval;
    }

    public Integer getStatus() {
        return status;
    }

    public Integer getIsManualReview() {
        return isManualReview;
    }

    public String getConfigParams() {
        return configParams;
    }

    public String getApiEndpoint() {
        return apiEndpoint;
    }

    public String getIcon() {
        return icon;
    }

    public String getColor() {
        return color;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public Long getCreatedBy() {
        return createdBy;
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

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public void setNodeGroup(String nodeGroup) {
        this.nodeGroup = nodeGroup;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setInputSchema(String inputSchema) {
        this.inputSchema = inputSchema;
    }

    public void setOutputSchema(String outputSchema) {
        this.outputSchema = outputSchema;
    }

    public void setExecuteClass(String executeClass) {
        this.executeClass = executeClass;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public void setRetryInterval(Integer retryInterval) {
        this.retryInterval = retryInterval;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public void setIsManualReview(Integer isManualReview) {
        this.isManualReview = isManualReview;
    }

    public void setConfigParams(String configParams) {
        this.configParams = configParams;
    }

    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
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
