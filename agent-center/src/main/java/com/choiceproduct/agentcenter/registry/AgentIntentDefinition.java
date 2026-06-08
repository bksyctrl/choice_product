package com.choiceproduct.agentcenter.registry;

import com.choiceproduct.agentcenter.brain.ExecutionMode;
import java.util.List;

public class AgentIntentDefinition {
    private final String intentCode;
    private final String handlerCode;
    private final String name;
    private final String description;
    private final ExecutionMode defaultMode;
    private final List<String> triggerKeywords;
    private final List<String> requiredParams;
    private final List<String> requiredTools;
    private final List<String> requiredExperts;
    private final String riskLevel;
    private final int priority;

    public AgentIntentDefinition(
            String intentCode,
            String handlerCode,
            String name,
            String description,
            ExecutionMode defaultMode,
            List<String> triggerKeywords,
            List<String> requiredParams,
            List<String> requiredTools,
            List<String> requiredExperts,
            String riskLevel,
            int priority) {
        this.intentCode = intentCode;
        this.handlerCode = handlerCode;
        this.name = name;
        this.description = description;
        this.defaultMode = defaultMode;
        this.triggerKeywords = triggerKeywords;
        this.requiredParams = requiredParams;
        this.requiredTools = requiredTools;
        this.requiredExperts = requiredExperts;
        this.riskLevel = riskLevel;
        this.priority = priority;
    }

    public String getIntentCode() {
        return intentCode;
    }

    public String getHandlerCode() {
        return handlerCode;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ExecutionMode getDefaultMode() {
        return defaultMode;
    }

    public List<String> getTriggerKeywords() {
        return triggerKeywords;
    }

    public List<String> getRequiredParams() {
        return requiredParams;
    }

    public List<String> getRequiredTools() {
        return requiredTools;
    }

    public List<String> getRequiredExperts() {
        return requiredExperts;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public int getPriority() {
        return priority;
    }
}
