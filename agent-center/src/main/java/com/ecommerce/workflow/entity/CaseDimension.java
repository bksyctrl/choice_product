package com.ecommerce.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("biz_case_dimension")
public class CaseDimension {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseId;
    private String dimensionCode;
    private String dimensionValue;
    private BigDecimal dimensionScore;
    private BigDecimal weight;
    private String dimensionNote;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }
    public String getDimensionCode() { return dimensionCode; }
    public void setDimensionCode(String dimensionCode) { this.dimensionCode = dimensionCode; }
    public String getDimensionValue() { return dimensionValue; }
    public void setDimensionValue(String dimensionValue) { this.dimensionValue = dimensionValue; }
    public BigDecimal getDimensionScore() { return dimensionScore; }
    public void setDimensionScore(BigDecimal dimensionScore) { this.dimensionScore = dimensionScore; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public String getDimensionNote() { return dimensionNote; }
    public void setDimensionNote(String dimensionNote) { this.dimensionNote = dimensionNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
