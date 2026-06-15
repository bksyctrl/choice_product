package com.ecommerce.workflow.skill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("skill_dependency")
public class SkillDependency {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String skillId;
    
    private String dependencyType;
    
    private String dependencyName;
    
    private Boolean isRequired;
    
    private String versionConstraint;
    
    private LocalDateTime createdAt;
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getSkillId() { return skillId; }
    public void setSkillId(String skillId) { this.skillId = skillId; }
    
    public String getDependencyType() { return dependencyType; }
    public void setDependencyType(String dependencyType) { this.dependencyType = dependencyType; }
    
    public String getDependencyName() { return dependencyName; }
    public void setDependencyName(String dependencyName) { this.dependencyName = dependencyName; }
    
    public Boolean getIsRequired() { return isRequired; }
    public void setIsRequired(Boolean isRequired) { this.isRequired = isRequired; }
    
    public String getVersionConstraint() { return versionConstraint; }
    public void setVersionConstraint(String versionConstraint) { this.versionConstraint = versionConstraint; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
