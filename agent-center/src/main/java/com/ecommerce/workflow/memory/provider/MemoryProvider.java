package com.ecommerce.workflow.memory.provider;

import java.util.List;
import java.util.Map;

public interface MemoryProvider {
    
    String getProviderName();
    
    void store(String key, String value, Map<String, Object> metadata);
    
    String retrieve(String key);
    
    List<MemoryItem> search(String query, int topK);
    
    void delete(String key);
    
    boolean exists(String key);
    
    void clear();
    
    long size();
    
    Map<String, Object> getStats();
    
    class MemoryItem {
        private String key;
        private String value;
        private double score;
        private Map<String, Object> metadata;
        
        public MemoryItem() {}
        
        public MemoryItem(String key, String value, double score) {
            this.key = key;
            this.value = value;
            this.score = score;
        }
        
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
}
