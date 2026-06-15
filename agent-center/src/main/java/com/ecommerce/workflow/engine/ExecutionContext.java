package com.ecommerce.workflow.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExecutionContext {
    private Long instanceId;
    private String instanceNo;
    private Long workflowId;
    private Map<String, Object> variables = new ConcurrentHashMap<>();
    private Map<String, Object> nodeOutputs = new ConcurrentHashMap<>();
    private Map<String, Object> metadata = new ConcurrentHashMap<>();

    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key) {
        return (T) variables.get(key);
    }

    public void setNodeOutput(String nodeCode, Object output) {
        nodeOutputs.put(nodeCode, output);
    }

    @SuppressWarnings("unchecked")
    public <T> T getNodeOutput(String nodeCode) {
        return (T) nodeOutputs.get(nodeCode);
    }

    public boolean hasVariable(String key) {
        return variables.containsKey(key);
    }

    // Getters
    public Long getInstanceId() {
        return instanceId;
    }

    public String getInstanceNo() {
        return instanceNo;
    }

    public Long getWorkflowId() {
        return workflowId;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public Map<String, Object> getNodeOutputs() {
        return nodeOutputs;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    // Setters
    public void setInstanceId(Long instanceId) {
        this.instanceId = instanceId;
    }

    public void setInstanceNo(String instanceNo) {
        this.instanceNo = instanceNo;
    }

    public void setWorkflowId(Long workflowId) {
        this.workflowId = workflowId;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public void setNodeOutputs(Map<String, Object> nodeOutputs) {
        this.nodeOutputs = nodeOutputs;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
