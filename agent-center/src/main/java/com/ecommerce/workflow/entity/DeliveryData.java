package com.ecommerce.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("biz_delivery_data")
public class DeliveryData {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private Long videoTaskId;
    private String platform;
    private String platformVideoId;
    private BigDecimal playCount;
    private BigDecimal likeCount;
    private BigDecimal commentCount;
    private BigDecimal shareCount;
    private BigDecimal collectCount;
    private BigDecimal followCount;
    private BigDecimal gmv;
    private BigDecimal orderCount;
    private Double cvr;
    private Integer videoDuration;
    private String publishTime;
    private String dataDate;
    private String status;
    private String rawDataJson;
    private Long caseMemoryId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;

    // Getters
    public Long getId() {
        return id;
    }

    public String getTaskNo() {
        return taskNo;
    }

    public Long getVideoTaskId() {
        return videoTaskId;
    }

    public String getPlatform() {
        return platform;
    }

    public String getPlatformVideoId() {
        return platformVideoId;
    }

    public BigDecimal getPlayCount() {
        return playCount;
    }

    public BigDecimal getLikeCount() {
        return likeCount;
    }

    public BigDecimal getCommentCount() {
        return commentCount;
    }

    public BigDecimal getShareCount() {
        return shareCount;
    }

    public BigDecimal getCollectCount() {
        return collectCount;
    }

    public BigDecimal getFollowCount() {
        return followCount;
    }

    public BigDecimal getGmv() {
        return gmv;
    }

    public BigDecimal getOrderCount() {
        return orderCount;
    }

    public Double getCvr() {
        return cvr;
    }

    public Integer getVideoDuration() {
        return videoDuration;
    }

    public String getPublishTime() {
        return publishTime;
    }

    public String getDataDate() {
        return dataDate;
    }

    public String getStatus() {
        return status;
    }

    public String getRawDataJson() {
        return rawDataJson;
    }

    public Long getCaseMemoryId() {
        return caseMemoryId;
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

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
    }

    public void setVideoTaskId(Long videoTaskId) {
        this.videoTaskId = videoTaskId;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public void setPlatformVideoId(String platformVideoId) {
        this.platformVideoId = platformVideoId;
    }

    public void setPlayCount(BigDecimal playCount) {
        this.playCount = playCount;
    }

    public void setLikeCount(BigDecimal likeCount) {
        this.likeCount = likeCount;
    }

    public void setCommentCount(BigDecimal commentCount) {
        this.commentCount = commentCount;
    }

    public void setShareCount(BigDecimal shareCount) {
        this.shareCount = shareCount;
    }

    public void setCollectCount(BigDecimal collectCount) {
        this.collectCount = collectCount;
    }

    public void setFollowCount(BigDecimal followCount) {
        this.followCount = followCount;
    }

    public void setGmv(BigDecimal gmv) {
        this.gmv = gmv;
    }

    public void setOrderCount(BigDecimal orderCount) {
        this.orderCount = orderCount;
    }

    public void setCvr(Double cvr) {
        this.cvr = cvr;
    }

    public void setVideoDuration(Integer videoDuration) {
        this.videoDuration = videoDuration;
    }

    public void setPublishTime(String publishTime) {
        this.publishTime = publishTime;
    }

    public void setDataDate(String dataDate) {
        this.dataDate = dataDate;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setRawDataJson(String rawDataJson) {
        this.rawDataJson = rawDataJson;
    }

    public void setCaseMemoryId(Long caseMemoryId) {
        this.caseMemoryId = caseMemoryId;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
