package com.ecommerce.workflow.agent;

import java.util.Map;

public class AgentRequest {
    private String sessionId;
    private Long userId;
    private String message;
    private Map<String, Object> context;
    private Map<String, Object> parameters;
    private Map<String, Object> metadata;

    // Getters
    public String getSessionId() {
        return sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    // Setters
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final AgentRequest request = new AgentRequest();

        public Builder sessionId(String sessionId) {
            request.setSessionId(sessionId);
            return this;
        }

        public Builder userId(Long userId) {
            request.setUserId(userId);
            return this;
        }

        public Builder message(String message) {
            request.setMessage(message);
            return this;
        }

        public Builder context(Map<String, Object> context) {
            request.setContext(context);
            return this;
        }

        public Builder parameters(Map<String, Object> parameters) {
            request.setParameters(parameters);
            return this;
        }

        public Builder parameter(String key, Object value) {
            if (request.getParameters() == null) {
                request.setParameters(new java.util.HashMap<>());
            }
            request.getParameters().put(key, value);
            return this;
        }

        public Builder metadata(String key, Object value) {
            if (request.getMetadata() == null) {
                request.setMetadata(new java.util.HashMap<>());
            }
            request.getMetadata().put(key, value);
            return this;
        }

        public AgentRequest build() {
            return request;
        }
    }
}
