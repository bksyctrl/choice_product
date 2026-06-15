package com.ecommerce.workflow.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai")
public class AiProvidersProperties {

    private List<ProviderDefinition> providers = new ArrayList<>();

    public List<ProviderDefinition> getProviders() {
        return providers;
    }

    public void setProviders(List<ProviderDefinition> providers) {
        this.providers = providers;
    }

    public static class ProviderDefinition {
        private String name;
        private String type;
        private String baseUrl;
        private String apiKey;
        private String defaultModel;
        private String models;
        private Integer priority = 1;
        private Boolean enabled = true;
        private Integer maxRetries = 2;
        private Long timeoutMs = 60000L;
        private String capabilities;
        private String extraParams;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getDefaultModel() { return defaultModel; }
        public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
        public String getModels() { return models; }
        public void setModels(String models) { this.models = models; }
        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public Integer getMaxRetries() { return maxRetries; }
        public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
        public Long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(Long timeoutMs) { this.timeoutMs = timeoutMs; }
        public String getCapabilities() { return capabilities; }
        public void setCapabilities(String capabilities) { this.capabilities = capabilities; }
        public String getExtraParams() { return extraParams; }
        public void setExtraParams(String extraParams) { this.extraParams = extraParams; }
    }
}
