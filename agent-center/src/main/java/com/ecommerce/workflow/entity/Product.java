package com.ecommerce.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("biz_product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String productId;
    private String productName;
    private String category;
    private String subCategory;
    private String platform;
    private BigDecimal price;
    private Integer salesVolume;
    private Double rating;
    private Integer reviewCount;
    private String productUrl;
    private String mainImageUrl;
    private String description;
    private String tags;
    private String status;
    private String lifecycleStage;
    private Double searchGrowthRate;
    private Double supplyDemandRatio;
    private String competitionLevel;
    private Double headMonopolyDegree;
    private Double profitMargin;
    private String supplyChainStability;
    private Double innovationSpace;
    private Double sixDimensionScore;
    private String scoreLevel;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public String getSubCategory() { return subCategory; }
    public void setSubCategory(String subCategory) { this.subCategory = subCategory; }
    
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    
    public Integer getSalesVolume() { return salesVolume; }
    public void setSalesVolume(Integer salesVolume) { this.salesVolume = salesVolume; }
    
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    
    public Integer getReviewCount() { return reviewCount; }
    public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }
    
    public String getProductUrl() { return productUrl; }
    public void setProductUrl(String productUrl) { this.productUrl = productUrl; }
    
    public String getMainImageUrl() { return mainImageUrl; }
    public void setMainImageUrl(String mainImageUrl) { this.mainImageUrl = mainImageUrl; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getLifecycleStage() { return lifecycleStage; }
    public void setLifecycleStage(String lifecycleStage) { this.lifecycleStage = lifecycleStage; }
    
    public Double getSearchGrowthRate() { return searchGrowthRate; }
    public void setSearchGrowthRate(Double searchGrowthRate) { this.searchGrowthRate = searchGrowthRate; }
    
    public Double getSupplyDemandRatio() { return supplyDemandRatio; }
    public void setSupplyDemandRatio(Double supplyDemandRatio) { this.supplyDemandRatio = supplyDemandRatio; }
    
    public String getCompetitionLevel() { return competitionLevel; }
    public void setCompetitionLevel(String competitionLevel) { this.competitionLevel = competitionLevel; }
    
    public Double getHeadMonopolyDegree() { return headMonopolyDegree; }
    public void setHeadMonopolyDegree(Double headMonopolyDegree) { this.headMonopolyDegree = headMonopolyDegree; }
    
    public Double getProfitMargin() { return profitMargin; }
    public void setProfitMargin(Double profitMargin) { this.profitMargin = profitMargin; }
    
    public String getSupplyChainStability() { return supplyChainStability; }
    public void setSupplyChainStability(String supplyChainStability) { this.supplyChainStability = supplyChainStability; }
    
    public Double getInnovationSpace() { return innovationSpace; }
    public void setInnovationSpace(Double innovationSpace) { this.innovationSpace = innovationSpace; }
    
    public Double getSixDimensionScore() { return sixDimensionScore; }
    public void setSixDimensionScore(Double sixDimensionScore) { this.sixDimensionScore = sixDimensionScore; }
    
    public String getScoreLevel() { return scoreLevel; }
    public void setScoreLevel(String scoreLevel) { this.scoreLevel = scoreLevel; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
