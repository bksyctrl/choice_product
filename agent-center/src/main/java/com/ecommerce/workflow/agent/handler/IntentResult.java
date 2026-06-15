package com.ecommerce.workflow.agent.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IntentResult {
    private final String intent;
    private final double confidence;
    private final Map<String, Object> entities;
    private final List<String> missingParams;
    private final String suggestedWorkflow;

    public IntentResult(String intent, double confidence, Map<String, Object> entities, 
                       List<String> missingParams, String suggestedWorkflow) {
        this.intent = intent;
        this.confidence = confidence;
        this.entities = entities != null ? entities : new HashMap<>();
        this.missingParams = missingParams != null ? missingParams : List.of();
        this.suggestedWorkflow = suggestedWorkflow;
    }

    public String getIntent() { return intent; }
    public double getConfidence() { return confidence; }
    public Map<String, Object> getEntities() { return entities; }
    public List<String> getMissingParams() { return missingParams; }
    public String getSuggestedWorkflow() { return suggestedWorkflow; }
}
