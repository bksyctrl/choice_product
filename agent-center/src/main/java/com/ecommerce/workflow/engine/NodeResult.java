package com.ecommerce.workflow.engine;

import java.util.Map;

public class NodeResult {
    private boolean success;
    private Map<String, Object> output;
    private String message;
    private Object data;

    public static NodeResult success(Map<String, Object> output) {
        NodeResult result = new NodeResult();
        result.setSuccess(true);
        result.setOutput(output);
        return result;
    }

    public static NodeResult success(String message) {
        NodeResult result = new NodeResult();
        result.setSuccess(true);
        result.setMessage(message);
        return result;
    }

    public static NodeResult failure(String message) {
        NodeResult result = new NodeResult();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }

    public static NodeResult failure(String message, Object data) {
        NodeResult result = new NodeResult();
        result.setSuccess(false);
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    // Getters
    public boolean isSuccess() {
        return success;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }

    // Setters
    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
