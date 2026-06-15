package com.ecommerce.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.LocalDateTime;
import java.util.List;

@TableName(value = "memory_relation", autoResultMap = true)
public class MemoryRelation {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long sourceMemoryId;
    
    private Long targetMemoryId;
    
    private String relationType;
    
    private Double strength;
    
    private String source;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    
    private Integer accessCount;
    
    private LocalDateTime lastAccessedAt;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSourceMemoryId() {
        return sourceMemoryId;
    }

    public void setSourceMemoryId(Long sourceMemoryId) {
        this.sourceMemoryId = sourceMemoryId;
    }

    public Long getTargetMemoryId() {
        return targetMemoryId;
    }

    public void setTargetMemoryId(Long targetMemoryId) {
        this.targetMemoryId = targetMemoryId;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public Double getStrength() {
        return strength;
    }

    public void setStrength(Double strength) {
        this.strength = strength;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Integer getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(Integer accessCount) {
        this.accessCount = accessCount;
    }

    public LocalDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(LocalDateTime lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
