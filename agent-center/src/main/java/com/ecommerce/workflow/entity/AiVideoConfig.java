package com.ecommerce.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("ai_video_config")
public class AiVideoConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String configName;
    private String race;
    private String role;
    private String topic;
    private String sceneType;
    private String scene;
    private String frameType;
    private String videoName;
    private String aspectRatio;
    private String resolution;
    private Integer frameRate;
    private Integer duration;
    private Integer styleIntensity;
    private Integer creativity;
    private Boolean autoMix;
    
    private String language;
    private String extendedParams;
    
    // AI 生成的场景图提示词（JSON数组格式）
    @TableField(exist = false)
    private String imagePrompts;
    
    // AI 生成的视频提示词（JSON数组格式）
    @TableField(exist = false)
    private String videoPrompts;
    
    @TableField(fill = FieldFill.INSERT)
    private Long creatorId;
    
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

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public String getRace() {
        return race;
    }

    public void setRace(String race) {
        this.race = race;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getSceneType() {
        return sceneType;
    }

    public void setSceneType(String sceneType) {
        this.sceneType = sceneType;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getFrameType() {
        return frameType;
    }

    public void setFrameType(String frameType) {
        this.frameType = frameType;
    }

    public String getVideoName() {
        return videoName;
    }

    public void setVideoName(String videoName) {
        this.videoName = videoName;
    }

    public String getAspectRatio() {
        return aspectRatio;
    }

    public void setAspectRatio(String aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public Integer getFrameRate() {
        return frameRate;
    }

    public void setFrameRate(Integer frameRate) {
        this.frameRate = frameRate;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public Integer getStyleIntensity() {
        return styleIntensity;
    }

    public void setStyleIntensity(Integer styleIntensity) {
        this.styleIntensity = styleIntensity;
    }

    public Integer getCreativity() {
        return creativity;
    }

    public void setCreativity(Integer creativity) {
        this.creativity = creativity;
    }

    public Boolean getAutoMix() {
        return autoMix;
    }

    public void setAutoMix(Boolean autoMix) {
        this.autoMix = autoMix;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
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

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getExtendedParams() {
        return extendedParams;
    }

    public void setExtendedParams(String extendedParams) {
        this.extendedParams = extendedParams;
    }
    
    public String getImagePrompts() {
        return imagePrompts;
    }
    
    public void setImagePrompts(String imagePrompts) {
        this.imagePrompts = imagePrompts;
    }
    
    public String getVideoPrompts() {
        return videoPrompts;
    }
    
    public void setVideoPrompts(String videoPrompts) {
        this.videoPrompts = videoPrompts;
    }
}
