package com.ecommerce.workflow.entity;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_knowledge")
public class Knowledge {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String knowledgeId;
    private String type;
    private String title;
    private String content;
    @TableField(exist = false)
    private String contentHash;
    private String tags;
    private String embeddingId;
    private String source;
    private String sourceId;
    private Double confidence;
    private Integer applyCount;
    private Integer successCount;
    private String status;
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }
    public String getKnowledgeId() {
        return knowledgeId;
    }
    public String getType() {
        return type;
    }
    public String getTitle() {
        return title;
    }
    public String getContent() {
        return content;
    }
    public String getContentHash() {
        return contentHash;
    }
    public String getTags() {
        return tags;
    }
    public String getEmbeddingId() {
        return embeddingId;
    }
    public String getSource() {
        return source;
    }
    public String getSourceId() {
        return sourceId;
    }
    public Double getConfidence() {
        return confidence;
    }
    public Integer getApplyCount() {
        return applyCount;
    }
    public Integer getSuccessCount() {
        return successCount;
    }
    public String getStatus() {
        return status;
    }
    public Long getCreatedBy() {
        return createdBy;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    public Integer getDeleted() {
        return deleted;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public void setKnowledgeId(String knowledgeId) {
        this.knowledgeId = knowledgeId;
    }
    public void setType(String type) {
        this.type = type;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public void setContent(String content) {
        this.content = content;
    }
    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }
    public void setTags(String tags) {
        this.tags = tags;
    }
    public void setEmbeddingId(String embeddingId) {
        this.embeddingId = embeddingId;
    }
    public void setSource(String source) {
        this.source = source;
    }
    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }
    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }
    public void setApplyCount(Integer applyCount) {
        this.applyCount = applyCount;
    }
    public void setSuccessCount(Integer successCount) {
        this.successCount = successCount;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
