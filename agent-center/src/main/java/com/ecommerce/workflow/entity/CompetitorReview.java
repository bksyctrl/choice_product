package com.ecommerce.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@TableName("biz_competitor_review")
public class CompetitorReview {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String reviewId;
    private String competitorId;
    private String reviewType;
    private String reviewContent;
    private Double sentimentScore;
    private List<String> painPoints;
    private List<String> sellingPoints;
    private Integer helpfulCount;
    private LocalDateTime reviewDate;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getReviewId() { return reviewId; }
    public void setReviewId(String reviewId) { this.reviewId = reviewId; }
    
    public String getCompetitorId() { return competitorId; }
    public void setCompetitorId(String competitorId) { this.competitorId = competitorId; }
    
    public String getReviewType() { return reviewType; }
    public void setReviewType(String reviewType) { this.reviewType = reviewType; }
    
    public String getReviewContent() { return reviewContent; }
    public void setReviewContent(String reviewContent) { this.reviewContent = reviewContent; }
    
    public Double getSentimentScore() { return sentimentScore; }
    public void setSentimentScore(Double sentimentScore) { this.sentimentScore = sentimentScore; }
    
    public List<String> getPainPoints() { return painPoints; }
    public void setPainPoints(List<String> painPoints) { this.painPoints = painPoints; }
    
    public List<String> getSellingPoints() { return sellingPoints; }
    public void setSellingPoints(List<String> sellingPoints) { this.sellingPoints = sellingPoints; }
    
    public Integer getHelpfulCount() { return helpfulCount; }
    public void setHelpfulCount(Integer helpfulCount) { this.helpfulCount = helpfulCount; }
    
    public LocalDateTime getReviewDate() { return reviewDate; }
    public void setReviewDate(LocalDateTime reviewDate) { this.reviewDate = reviewDate; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
