package com.choiceproduct.agentcenter.agent;

import com.choiceproduct.agentcenter.brain.DispatchDecision;
import java.util.HashMap;
import java.util.Map;

public class AgentResponse {
    private boolean success;
    private String sessionId;
    private String message;
    private String intent;
    private DispatchDecision decision;
    private Map<String, Object> data = new HashMap<>();
    private Map<String, Object> actions = new HashMap<>();
    private boolean requiresHumanReview;
    private String reviewReason;
    private String error;

    public static AgentResponse success(String message) {
        AgentResponse response = new AgentResponse();
        response.setSuccess(true);
        response.setMessage(message);
        return response;
    }

    public static AgentResponse failure(String error) {
        AgentResponse response = new AgentResponse();
        response.setSuccess(false);
        response.setError(error);
        response.setMessage(error);
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public DispatchDecision getDecision() {
        return decision;
    }

    public void setDecision(DispatchDecision decision) {
        this.decision = decision;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data == null ? new HashMap<>() : data;
    }

    public Map<String, Object> getActions() {
        return actions;
    }

    public void setActions(Map<String, Object> actions) {
        this.actions = actions == null ? new HashMap<>() : actions;
    }

    public boolean isRequiresHumanReview() {
        return requiresHumanReview;
    }

    public void setRequiresHumanReview(boolean requiresHumanReview) {
        this.requiresHumanReview = requiresHumanReview;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public void setReviewReason(String reviewReason) {
        this.reviewReason = reviewReason;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
