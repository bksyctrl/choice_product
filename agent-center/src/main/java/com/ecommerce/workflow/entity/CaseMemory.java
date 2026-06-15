package com.ecommerce.workflow.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("biz_case_memory")
public class CaseMemory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String caseNo;
    private String caseType;
    private Long productId;
    private String productName;
    private String category;
    private String platform;
    private Long workflowInstanceId;
    private Long videoTaskId;
    private String skillVersionSnapshot;
    private String inputParams;
    private String outputResult;
    private BigDecimal playCount;
    private BigDecimal likeCount;
    private BigDecimal shareCount;
    private BigDecimal commentCount;
    private BigDecimal collectCount;
    private BigDecimal gmv;
    private Double cvr;
    private Integer conversionCount;
    private String qualityTag;
    private String failureReason;
    private String lessonLearned;
    private Double vectorScore;
    private Integer learned;
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;

    // Getters
    public Long getId() {
        return id;
    }

    public String getCaseNo() {
        return caseNo;
    }

    public String getCaseType() {
        return caseType;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public String getCategory() {
        return category;
    }

    public String getPlatform() {
        return platform;
    }

    public Long getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public Long getVideoTaskId() {
        return videoTaskId;
    }

    public String getSkillVersionSnapshot() {
        return skillVersionSnapshot;
    }

    public String getInputParams() {
        return inputParams;
    }

    public String getOutputResult() {
        return outputResult;
    }

    public BigDecimal getPlayCount() {
        return playCount;
    }

    public BigDecimal getLikeCount() {
        return likeCount;
    }

    public BigDecimal getShareCount() {
        return shareCount;
    }

    public BigDecimal getCommentCount() {
        return commentCount;
    }

    public BigDecimal getCollectCount() {
        return collectCount;
    }

    public BigDecimal getGmv() {
        return gmv;
    }

    public Double getCvr() {
        return cvr;
    }

    public Integer getConversionCount() {
        return conversionCount;
    }

    public String getQualityTag() {
        return qualityTag;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getLessonLearned() {
        return lessonLearned;
    }

    public Double getVectorScore() {
        return vectorScore;
    }

    public Integer getLearned() {
        return learned;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Integer getDeleted() {
        return deleted;
    }

    // Setters
    public void setId(Long id) {
        this.id = id;
    }

    public void setCaseNo(String caseNo) {
        this.caseNo = caseNo;
    }

    public void setCaseType(String caseType) {
        this.caseType = caseType;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public void setWorkflowInstanceId(Long workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
    }

    public void setVideoTaskId(Long videoTaskId) {
        this.videoTaskId = videoTaskId;
    }

    public void setSkillVersionSnapshot(String skillVersionSnapshot) {
        this.skillVersionSnapshot = skillVersionSnapshot;
    }

    public void setInputParams(String inputParams) {
        this.inputParams = inputParams;
    }

    public void setOutputResult(String outputResult) {
        this.outputResult = outputResult;
    }

    public void setPlayCount(BigDecimal playCount) {
        this.playCount = playCount;
    }

    public void setLikeCount(BigDecimal likeCount) {
        this.likeCount = likeCount;
    }

    public void setShareCount(BigDecimal shareCount) {
        this.shareCount = shareCount;
    }

    public void setCommentCount(BigDecimal commentCount) {
        this.commentCount = commentCount;
    }

    public void setCollectCount(BigDecimal collectCount) {
        this.collectCount = collectCount;
    }

    public void setGmv(BigDecimal gmv) {
        this.gmv = gmv;
    }

    public void setCvr(Double cvr) {
        this.cvr = cvr;
    }

    public void setConversionCount(Integer conversionCount) {
        this.conversionCount = conversionCount;
    }

    public void setQualityTag(String qualityTag) {
        this.qualityTag = qualityTag;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public void setLessonLearned(String lessonLearned) {
        this.lessonLearned = lessonLearned;
    }

    public void setVectorScore(Double vectorScore) {
        this.vectorScore = vectorScore;
    }

    public void setLearned(Integer learned) {
        this.learned = learned;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
