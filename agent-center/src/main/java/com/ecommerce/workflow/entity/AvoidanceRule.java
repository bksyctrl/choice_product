package com.ecommerce.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.time.LocalDateTime;

@TableName("biz_avoidance_rule")
public class AvoidanceRule {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String category;
    private String description;
    @JsonAlias("rule")
    private String problemPattern;
    private String solution;
    private String prevention;
    @JsonDeserialize(using = SeverityDeserializer.class)
    private Integer severity;
    private Integer applyCount;
    private Integer effectiveCount;
    private String tags;
    private String relatedCases;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
    
    public static class SeverityDeserializer extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String value = p.getValueAsString();
            if (value == null || value.isEmpty()) return 2;
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                switch (value.toLowerCase()) {
                    case "high": return 3;
                    case "medium": return 2;
                    case "low": return 1;
                    default: return 2;
                }
            }
        }
    }
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getProblemPattern() { return problemPattern; }
    public void setProblemPattern(String problemPattern) { this.problemPattern = problemPattern; }
    
    public String getSolution() { return solution; }
    public void setSolution(String solution) { this.solution = solution; }
    
    public String getPrevention() { return prevention; }
    public void setPrevention(String prevention) { this.prevention = prevention; }
    
    public Integer getSeverity() { return severity; }
    public void setSeverity(Integer severity) { this.severity = severity; }
    
    public Integer getApplyCount() { return applyCount; }
    public void setApplyCount(Integer applyCount) { this.applyCount = applyCount; }
    
    public Integer getEffectiveCount() { return effectiveCount; }
    public void setEffectiveCount(Integer effectiveCount) { this.effectiveCount = effectiveCount; }
    
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    
    public String getRelatedCases() { return relatedCases; }
    public void setRelatedCases(String relatedCases) { this.relatedCases = relatedCases; }
    
    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
