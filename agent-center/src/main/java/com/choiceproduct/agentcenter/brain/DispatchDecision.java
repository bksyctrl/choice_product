package com.choiceproduct.agentcenter.brain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DispatchDecision {
    private String intentCode;
    private String handlerCode;
    private ExecutionMode mode = ExecutionMode.CHAT;
    private double confidence;
    private Map<String, Object> entities = new HashMap<>();
    private List<String> missingParams = new ArrayList<>();
    private List<String> requiredTools = new ArrayList<>();
    private List<String> requiredExperts = new ArrayList<>();
    private String executor;
    private boolean canExecuteNow = true;
    private boolean requiresConfirmation;
    private String riskLevel = "LOW";
    private String nextQuestion;
    private String reason;

    public String getIntentCode() {
        return intentCode;
    }

    public void setIntentCode(String intentCode) {
        this.intentCode = intentCode;
    }

    public String getHandlerCode() {
        return handlerCode;
    }

    public void setHandlerCode(String handlerCode) {
        this.handlerCode = handlerCode;
    }

    public ExecutionMode getMode() {
        return mode;
    }

    public void setMode(ExecutionMode mode) {
        this.mode = mode;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public Map<String, Object> getEntities() {
        return entities;
    }

    public void setEntities(Map<String, Object> entities) {
        this.entities = entities == null ? new HashMap<>() : entities;
    }

    public List<String> getMissingParams() {
        return missingParams;
    }

    public void setMissingParams(List<String> missingParams) {
        this.missingParams = missingParams == null ? new ArrayList<>() : missingParams;
    }

    public List<String> getRequiredTools() {
        return requiredTools;
    }

    public void setRequiredTools(List<String> requiredTools) {
        this.requiredTools = requiredTools == null ? new ArrayList<>() : requiredTools;
    }

    public List<String> getRequiredExperts() {
        return requiredExperts;
    }

    public void setRequiredExperts(List<String> requiredExperts) {
        this.requiredExperts = requiredExperts == null ? new ArrayList<>() : requiredExperts;
    }

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }

    public boolean isCanExecuteNow() {
        return canExecuteNow;
    }

    public void setCanExecuteNow(boolean canExecuteNow) {
        this.canExecuteNow = canExecuteNow;
    }

    public boolean isRequiresConfirmation() {
        return requiresConfirmation;
    }

    public void setRequiresConfirmation(boolean requiresConfirmation) {
        this.requiresConfirmation = requiresConfirmation;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getNextQuestion() {
        return nextQuestion;
    }

    public void setNextQuestion(String nextQuestion) {
        this.nextQuestion = nextQuestion;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
