package com.choiceproduct.agentcenter.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RagContext {
    private List<String> successExperiences = new ArrayList<>();
    private List<String> avoidanceGuides = new ArrayList<>();
    private List<String> platformRules = new ArrayList<>();
    private Map<String, Object> metadata = new HashMap<>();

    public List<String> getSuccessExperiences() {
        return successExperiences;
    }

    public void setSuccessExperiences(List<String> successExperiences) {
        this.successExperiences = successExperiences == null ? new ArrayList<>() : successExperiences;
    }

    public List<String> getAvoidanceGuides() {
        return avoidanceGuides;
    }

    public void setAvoidanceGuides(List<String> avoidanceGuides) {
        this.avoidanceGuides = avoidanceGuides == null ? new ArrayList<>() : avoidanceGuides;
    }

    public List<String> getPlatformRules() {
        return platformRules;
    }

    public void setPlatformRules(List<String> platformRules) {
        this.platformRules = platformRules == null ? new ArrayList<>() : platformRules;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new HashMap<>() : metadata;
    }
}
