package com.ecommerce.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("sys_workflow_node_exec")
public class WorkflowNodeExecution {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long instanceId;
    private Long nodeId;
    private String nodeCode;
    private String nodeName;
    private Integer execOrder;
    private String status;
    private String inputParams;
    private String outputResult;
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private String logContent;
    private String snapshotData;
    private Long reviewerId;
    private String reviewComment;
    private LocalDateTime reviewTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // Getters
    public Long getId() {
        return id;
    }

    public Long getInstanceId() {
        return instanceId;
    }

    public Long getNodeId() {
        return nodeId;
    }

    public String getNodeCode() {
        return nodeCode;
    }

    public String getNodeName() {
        return nodeName;
    }

    public Integer getExecOrder() {
        return execOrder;
    }

    public String getStatus() {
        return status;
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

    public Integer getRetryCount() {
        return retryCount;
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

    public String getLogContent() {
        return logContent;
    }

    public String getSnapshotData() {
        return snapshotData;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public LocalDateTime getReviewTime() {
        return reviewTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // Setters
    public void setId(Long id) {
        this.id = id;
    }

    public void setInstanceId(Long instanceId) {
        this.instanceId = instanceId;
    }

    public void setNodeId(Long nodeId) {
        this.nodeId = nodeId;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public void setExecOrder(Integer execOrder) {
        this.execOrder = execOrder;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
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

    public void setLogContent(String logContent) {
        this.logContent = logContent;
    }

    public void setSnapshotData(String snapshotData) {
        this.snapshotData = snapshotData;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public void setReviewTime(LocalDateTime reviewTime) {
        this.reviewTime = reviewTime;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
