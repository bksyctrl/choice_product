package com.ecommerce.workflow.entity;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_user_profile")
public class UserProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String preferences;
    private String interests;
    private String behaviorPatterns;
    private String frequentlyUsedSkills;
    private String topicExpertise;
    private String communicationStyle;
    private Integer totalSessions;
    private Integer totalMessages;
    private LocalDateTime lastActiveAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
    public Long getId() {
        return id;
    }
    public Long getUserId() {
        return userId;
    }
    public String getPreferences() {
        return preferences;
    }
    public String getInterests() {
        return interests;
    }
    public String getBehaviorPatterns() {
        return behaviorPatterns;
    }
    public String getFrequentlyUsedSkills() {
        return frequentlyUsedSkills;
    }
    public String getTopicExpertise() {
        return topicExpertise;
    }
    public String getCommunicationStyle() {
        return communicationStyle;
    }
    public Integer getTotalSessions() {
        return totalSessions;
    }
    public Integer getTotalMessages() {
        return totalMessages;
    }
    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
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
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    public void setPreferences(String preferences) {
        this.preferences = preferences;
    }
    public void setInterests(String interests) {
        this.interests = interests;
    }
    public void setBehaviorPatterns(String behaviorPatterns) {
        this.behaviorPatterns = behaviorPatterns;
    }
    public void setFrequentlyUsedSkills(String frequentlyUsedSkills) {
        this.frequentlyUsedSkills = frequentlyUsedSkills;
    }
    public void setTopicExpertise(String topicExpertise) {
        this.topicExpertise = topicExpertise;
    }
    public void setCommunicationStyle(String communicationStyle) {
        this.communicationStyle = communicationStyle;
    }
    public void setTotalSessions(Integer totalSessions) {
        this.totalSessions = totalSessions;
    }
    public void setTotalMessages(Integer totalMessages) {
        this.totalMessages = totalMessages;
    }
    public void setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
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
