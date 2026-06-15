package com.ecommerce.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("biz_dimension_definition")
public class DimensionDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String dimensionCode;
    private String dimensionName;
    private String dimensionCategory;
    private String dimensionType;
    private String valueType;
    private String valueOptions;
    private BigDecimal defaultWeight;
    private Integer importance;
    private String description;
    private Integer enabled;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDimensionCode() { return dimensionCode; }
    public void setDimensionCode(String dimensionCode) { this.dimensionCode = dimensionCode; }
    public String getDimensionName() { return dimensionName; }
    public void setDimensionName(String dimensionName) { this.dimensionName = dimensionName; }
    public String getDimensionCategory() { return dimensionCategory; }
    public void setDimensionCategory(String dimensionCategory) { this.dimensionCategory = dimensionCategory; }
    public String getDimensionType() { return dimensionType; }
    public void setDimensionType(String dimensionType) { this.dimensionType = dimensionType; }
    public String getValueType() { return valueType; }
    public void setValueType(String valueType) { this.valueType = valueType; }
    public String getValueOptions() { return valueOptions; }
    public void setValueOptions(String valueOptions) { this.valueOptions = valueOptions; }
    public BigDecimal getDefaultWeight() { return defaultWeight; }
    public void setDefaultWeight(BigDecimal defaultWeight) { this.defaultWeight = defaultWeight; }
    public Integer getImportance() { return importance; }
    public void setImportance(Integer importance) { this.importance = importance; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
