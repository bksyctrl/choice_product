package com.ecommerce.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("biz_market_opportunity")
public class MarketOpportunity {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String opportunityId;
    private String category;
    private String subCategory;
    private String opportunityType;
    private Double supplyDemandRatio;
    private String competitionLevel;
    private BigDecimal marketSize;
    private Double growthRate;
    private Double profitMargin;
    private Double opportunityScore;
    private String recommendation;
    private String dataSource;
    private String status;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getOpportunityId() { return opportunityId; }
    public void setOpportunityId(String opportunityId) { this.opportunityId = opportunityId; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public String getSubCategory() { return subCategory; }
    public void setSubCategory(String subCategory) { this.subCategory = subCategory; }
    
    public String getOpportunityType() { return opportunityType; }
    public void setOpportunityType(String opportunityType) { this.opportunityType = opportunityType; }
    
    public Double getSupplyDemandRatio() { return supplyDemandRatio; }
    public void setSupplyDemandRatio(Double supplyDemandRatio) { this.supplyDemandRatio = supplyDemandRatio; }
    
    public String getCompetitionLevel() { return competitionLevel; }
    public void setCompetitionLevel(String competitionLevel) { this.competitionLevel = competitionLevel; }
    
    public BigDecimal getMarketSize() { return marketSize; }
    public void setMarketSize(BigDecimal marketSize) { this.marketSize = marketSize; }
    
    public Double getGrowthRate() { return growthRate; }
    public void setGrowthRate(Double growthRate) { this.growthRate = growthRate; }
    
    public Double getProfitMargin() { return profitMargin; }
    public void setProfitMargin(Double profitMargin) { this.profitMargin = profitMargin; }
    
    public Double getOpportunityScore() { return opportunityScore; }
    public void setOpportunityScore(Double opportunityScore) { this.opportunityScore = opportunityScore; }
    
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
