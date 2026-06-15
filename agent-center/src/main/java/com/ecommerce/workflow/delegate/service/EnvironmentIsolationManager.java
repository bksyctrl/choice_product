package com.ecommerce.workflow.delegate.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EnvironmentIsolationManager {
    
    private static final Logger log = LoggerFactory.getLogger(EnvironmentIsolationManager.class);
    
    private final Map<String, Environment> environments = new ConcurrentHashMap<>();
    
    public String createEnvironment(String parentAgentId, String subAgentId, Map<String, Object> config) {
        String envId = "ENV_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        
        Environment env = new Environment();
        env.setEnvId(envId);
        env.setParentAgentId(parentAgentId);
        env.setSubAgentId(subAgentId);
        env.setConfig(config != null ? config : new HashMap<>());
        env.setVariables(new HashMap<>());
        env.setStatus("active");
        env.setCreatedAt(System.currentTimeMillis());
        
        environments.put(envId, env);
        
        log.info("创建隔离环境: envId={}, parent={}, sub={}", envId, parentAgentId, subAgentId);
        
        return envId;
    }
    
    public void setVariable(String envId, String key, Object value) {
        Environment env = environments.get(envId);
        
        if (env != null) {
            env.getVariables().put(key, value);
            log.debug("设置环境变量: envId={}, key={}", envId, key);
        } else {
            log.warn("环境不存在: envId={}", envId);
        }
    }
    
    public Object getVariable(String envId, String key) {
        Environment env = environments.get(envId);
        
        if (env != null) {
            return env.getVariables().get(key);
        }
        
        return null;
    }
    
    public Map<String, Object> getAllVariables(String envId) {
        Environment env = environments.get(envId);
        
        if (env != null) {
            return new HashMap<>(env.getVariables());
        }
        
        return new HashMap<>();
    }
    
    public void destroyEnvironment(String envId) {
        Environment env = environments.remove(envId);
        
        if (env != null) {
            env.setStatus("destroyed");
            log.info("销毁隔离环境: envId={}", envId);
        }
    }
    
    public Environment getEnvironment(String envId) {
        return environments.get(envId);
    }
    
    public void cleanupOldEnvironments(long maxAgeMs) {
        long currentTime = System.currentTimeMillis();
        
        environments.entrySet().removeIf(entry -> {
            Environment env = entry.getValue();
            if (currentTime - env.getCreatedAt() > maxAgeMs) {
                env.setStatus("expired");
                log.info("清理过期环境: envId={}", entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    public static class Environment {
        private String envId;
        private String parentAgentId;
        private String subAgentId;
        private Map<String, Object> config;
        private Map<String, Object> variables;
        private String status;
        private long createdAt;
        
        public String getEnvId() { return envId; }
        public void setEnvId(String envId) { this.envId = envId; }
        public String getParentAgentId() { return parentAgentId; }
        public void setParentAgentId(String parentAgentId) { this.parentAgentId = parentAgentId; }
        public String getSubAgentId() { return subAgentId; }
        public void setSubAgentId(String subAgentId) { this.subAgentId = subAgentId; }
        public Map<String, Object> getConfig() { return config; }
        public void setConfig(Map<String, Object> config) { this.config = config; }
        public Map<String, Object> getVariables() { return variables; }
        public void setVariables(Map<String, Object> variables) { this.variables = variables; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }
}
