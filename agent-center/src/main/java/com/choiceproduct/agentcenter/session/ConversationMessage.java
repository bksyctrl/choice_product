package com.choiceproduct.agentcenter.session;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class ConversationMessage {
    private String role;
    private String content;
    private LocalDateTime createdAt = LocalDateTime.now();
    private Map<String, Object> metadata = new HashMap<>();

    public ConversationMessage() {
    }

    public ConversationMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new HashMap<>() : metadata;
    }
}
