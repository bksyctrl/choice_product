package com.ecommerce.workflow.memory.manager;

import com.ecommerce.workflow.memory.provider.MemoryProvider;
import com.ecommerce.workflow.memory.provider.MemoryProvider.MemoryItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MemoryManager {
    
    private static final Logger log = LoggerFactory.getLogger(MemoryManager.class);
    
    private final Map<String, MemoryProvider> providers = new ConcurrentHashMap<>();
    
    public MemoryManager(List<MemoryProvider> providerList) {
        for (MemoryProvider provider : providerList) {
            providers.put(provider.getProviderName(), provider);
            log.info("注册记忆提供者: {}", provider.getProviderName());
        }
    }
    
    public void store(String providerName, String key, String value, Map<String, Object> metadata) {
        MemoryProvider provider = providers.get(providerName);
        if (provider != null) {
            provider.store(key, value, metadata);
            log.debug("存储记忆: provider={}, key={}", providerName, key);
        } else {
            log.warn("未找到记忆提供者: {}", providerName);
        }
    }
    
    public String retrieve(String providerName, String key) {
        MemoryProvider provider = providers.get(providerName);
        if (provider != null) {
            return provider.retrieve(key);
        }
        return null;
    }
    
    public List<MemoryItem> search(String providerName, String query, int topK) {
        MemoryProvider provider = providers.get(providerName);
        if (provider != null) {
            return provider.search(query, topK);
        }
        return new ArrayList<>();
    }
    
    public List<MemoryItem> searchAll(String query, int topK) {
        List<MemoryItem> allResults = new ArrayList<>();
        
        for (MemoryProvider provider : providers.values()) {
            List<MemoryItem> results = provider.search(query, topK);
            allResults.addAll(results);
        }
        
        allResults.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        
        if (allResults.size() > topK) {
            return allResults.subList(0, topK);
        }
        
        return allResults;
    }
    
    public void delete(String providerName, String key) {
        MemoryProvider provider = providers.get(providerName);
        if (provider != null) {
            provider.delete(key);
        }
    }
    
    public void clear(String providerName) {
        MemoryProvider provider = providers.get(providerName);
        if (provider != null) {
            provider.clear();
        }
    }
    
    public void clearAll() {
        for (MemoryProvider provider : providers.values()) {
            provider.clear();
        }
    }
    
    public Map<String, Object> getProviderStats(String providerName) {
        MemoryProvider provider = providers.get(providerName);
        if (provider != null) {
            return provider.getStats();
        }
        return new HashMap<>();
    }
    
    public Map<String, Map<String, Object>> getAllStats() {
        Map<String, Map<String, Object>> allStats = new HashMap<>();
        for (Map.Entry<String, MemoryProvider> entry : providers.entrySet()) {
            allStats.put(entry.getKey(), entry.getValue().getStats());
        }
        return allStats;
    }
    
    public Set<String> getProviderNames() {
        return new HashSet<>(providers.keySet());
    }
    
    public void registerProvider(MemoryProvider provider) {
        providers.put(provider.getProviderName(), provider);
        log.info("动态注册记忆提供者: {}", provider.getProviderName());
    }
    
    public void unregisterProvider(String providerName) {
        providers.remove(providerName);
        log.info("注销记忆提供者: {}", providerName);
    }
}
