package com.ecommerce.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("sys_workflow_instance")
public class WorkflowInstance {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("instance_no")
    private String instanceNo;
    private Long workflowId;
    private String workflowName;
    private String status;
    private Long currentNodeId;
    private String triggerType;
    private Long triggerUserId;
    private Long agentSessionId;
    private Long productId;
    private Long renderTaskId;
    private Long deliveryTaskId;
    private String inputParams;
    private String outputResult;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private BigDecimal progress;
    private String contextData;
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

    public String getInstanceNo() {
        return instanceNo;
    }

    public Long getWorkflowId() {
        return workflowId;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public String getStatus() {
        return status;
    }

    public Long getCurrentNodeId() {
        return currentNodeId;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public Long getTriggerUserId() {
        return triggerUserId;
    }

    public Long getAgentSessionId() {
        return agentSessionId;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getRenderTaskId() {
        return renderTaskId;
    }

    public Long getDeliveryTaskId() {
        return deliveryTaskId;
    }

    public String getInputParams() {
        return inputParams;
    }

    public String getOutputResult() {
        return outputResult;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public BigDecimal getProgress() {
        return progress;
    }

    public String getContextData() {
        return contextData;
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

    public void setInstanceNo(String instanceNo) {
        this.instanceNo = instanceNo;
    }

    public void setWorkflowId(Long workflowId) {
        this.workflowId = workflowId;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCurrentNodeId(Long currentNodeId) {
        this.currentNodeId = currentNodeId;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public void setTriggerUserId(Long triggerUserId) {
        this.triggerUserId = triggerUserId;
    }

    public void setAgentSessionId(Long agentSessionId) {
        this.agentSessionId = agentSessionId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public void setRenderTaskId(Long renderTaskId) {
        this.renderTaskId = renderTaskId;
    }

    public void setDeliveryTaskId(Long deliveryTaskId) {
        this.deliveryTaskId = deliveryTaskId;
    }

    public void setInputParams(String inputParams) {
        this.inputParams = inputParams;
    }

    public void setOutputResult(String outputResult) {
        this.outputResult = outputResult;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public void setProgress(BigDecimal progress) {
        this.progress = progress;
    }

    public void setContextData(String contextData) {
        this.contextData = contextData;
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
