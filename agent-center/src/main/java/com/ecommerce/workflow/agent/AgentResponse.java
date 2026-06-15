package com.ecommerce.workflow.agent;

import java.util.Map;

public class AgentResponse {
    private boolean success;
    private String message;
    private String reply;
    private String intent;
    private Map<String, Object> data;
    private Map<String, Object> actions;
    private Long workflowInstanceId;
    private boolean requiresHumanReview;
    private String reviewReason;
    private Object error;

    // Getters
    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getReply() {
        return reply;
    }

    public String getIntent() {
        return intent;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public Map<String, Object> getActions() {
        return actions;
    }

    public Long getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public boolean isRequiresHumanReview() {
        return requiresHumanReview;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public Object getError() {
        return error;
    }

    // Setters
    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public void setActions(Map<String, Object> actions) {
        this.actions = actions;
    }

    public void setWorkflowInstanceId(Long workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
    }

    public void setRequiresHumanReview(boolean requiresHumanReview) {
        this.requiresHumanReview = requiresHumanReview;
    }

    public void setReviewReason(String reviewReason) {
        this.reviewReason = reviewReason;
    }

    public void setError(Object error) {
        this.error = error;
    }

    public static AgentResponse success(String reply) {
        AgentResponse response = new AgentResponse();
        response.setSuccess(true);
        response.setReply(reply);
        response.setMessage(reply);
        return response;
    }

    public static AgentResponse success(String reply, Map<String, Object> data) {
        AgentResponse response = success(reply);
        response.setData(data);
        return response;
    }

    public static AgentResponse workflowTriggered(String message, Long instanceId) {
        AgentResponse response = success(message);
        response.setWorkflowInstanceId(instanceId);
        return response;
    }

    public static AgentResponse needReview(String message, String reason) {
        AgentResponse response = new AgentResponse();
        response.setSuccess(true);
        response.setMessage(message);
        response.setRequiresHumanReview(true);
        response.setReviewReason(reason);
        return response;
    }

    public static AgentResponse failure(String error) {
        AgentResponse response = new AgentResponse();
        response.setSuccess(false);
        response.setError(error);
        return response;
    }

    public static AgentResponse failure(Exception e) {
        return failure(e.getMessage());
    }
}
