package com.ecommerce.workflow.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

@TableName(value = "expert_role_config", autoResultMap = true)
public class ExpertRoleConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String roleCode;

    private String roleName;

    private String description;

    private String promptTemplate;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object analysisDimensions;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> configJson;

    private BigDecimal weight;

    private String status;

    private Integer version;

    private Integer cacheVersion;

    private LocalDateTime lastLoadedAt;

    private Boolean isHotReload;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPromptTemplate() {
        return promptTemplate;
    }

    public void setPromptTemplate(String promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    public Object getAnalysisDimensions() {
        return analysisDimensions;
    }

    public void setAnalysisDimensions(Object analysisDimensions) {
        this.analysisDimensions = analysisDimensions;
    }

    public Map<String, Object> getConfigJson() {
        return configJson;
    }

    public void setConfigJson(Map<String, Object> configJson) {
        this.configJson = configJson;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public Integer getCacheVersion() {
        return cacheVersion;
    }

    public void setCacheVersion(Integer cacheVersion) {
        this.cacheVersion = cacheVersion;
    }

    public LocalDateTime getLastLoadedAt() {
        return lastLoadedAt;
    }

    public void setLastLoadedAt(LocalDateTime lastLoadedAt) {
        this.lastLoadedAt = lastLoadedAt;
    }

    public Boolean getIsHotReload() {
        return isHotReload;
    }

    public void setIsHotReload(Boolean isHotReload) {
        this.isHotReload = isHotReload;
    }
}
