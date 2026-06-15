package com.ecommerce.workflow.memory.provider;

import com.ecommerce.workflow.service.memory.WhiteBoxMemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class BuiltinMemoryProvider implements MemoryProvider {
    
    @Autowired
    private WhiteBoxMemoryService whiteBoxMemoryService;
    
    private final Map<String, String> memoryStore = new HashMap<>();
    private final Map<String, Map<String, Object>> metadataStore = new HashMap<>();
    
    @Override
    public String getProviderName() {
        return "builtin";
    }
    
    @Override
    public void store(String key, String value, Map<String, Object> metadata) {
        memoryStore.put(key, value);
        if (metadata != null) {
            metadataStore.put(key, new HashMap<>(metadata));
        }
        
        whiteBoxMemoryService.appendToMemory("LEARNINGS.md", 
            String.format("## 璁板繂瀛樺偍\n- Key: %s\n- Value: %s\n- Time: %s\n", 
                key, value, new Date()));
    }
    
    @Override
    public String retrieve(String key) {
        return memoryStore.get(key);
    }
    
    @Override
    public List<MemoryItem> search(String query, int topK) {
        List<MemoryItem> results = new ArrayList<>();
        
        String lowerQuery = query.toLowerCase();
        
        for (Map.Entry<String, String> entry : memoryStore.entrySet()) {
            if (entry.getKey().toLowerCase().contains(lowerQuery) ||
                entry.getValue().toLowerCase().contains(lowerQuery)) {
                
                double score = calculateRelevanceScore(query, entry.getKey(), entry.getValue());
                results.add(new MemoryItem(entry.getKey(), entry.getValue(), score));
            }
        }
        
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        
        if (results.size() > topK) {
            results = results.subList(0, topK);
        }
        
        return results;
    }
    
    @Override
    public void delete(String key) {
        memoryStore.remove(key);
        metadataStore.remove(key);
    }
    
    @Override
    public boolean exists(String key) {
        return memoryStore.containsKey(key);
    }
    
    @Override
    public void clear() {
        memoryStore.clear();
        metadataStore.clear();
    }
    
    @Override
    public long size() {
        return memoryStore.size();
    }
    
    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("provider", getProviderName());
        stats.put("totalMemories", memoryStore.size());
        stats.put("totalMetadata", metadataStore.size());
        return stats;
    }
    
    private double calculateRelevanceScore(String query, String key, String value) {
        double score = 0.0;
        String lowerQuery = query.toLowerCase();
        
        if (key.toLowerCase().contains(lowerQuery)) {
            score += 1.0;
        }
        
        if (value.toLowerCase().contains(lowerQuery)) {
            score += 0.5;
        }
        
        int queryLength = query.length();
        int totalLength = key.length() + value.length();
        if (totalLength > 0) {
            score += (double) queryLength / totalLength;
        }
        
        return score;
    }
}
