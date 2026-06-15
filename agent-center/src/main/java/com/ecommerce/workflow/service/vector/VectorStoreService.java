package com.ecommerce.workflow.service.vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.workflow.entity.Knowledge;
import com.ecommerce.workflow.mapper.KnowledgeMapper;
import com.ecommerce.workflow.service.rag.EmbeddingService;

@Service
public class VectorStoreService {
    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);
    
    private final KnowledgeMapper knowledgeMapper;
    private final EmbeddingService embeddingService;
    private final ReentrantLock cacheLock = new ReentrantLock();
    
    private final TTLCache<String, float[]> vectorCache;
    private final Map<String, Map<String, Object>> knowledgeIndex;
    
    public VectorStoreService(KnowledgeMapper knowledgeMapper, EmbeddingService embeddingService) {
        this.knowledgeMapper = knowledgeMapper;
        this.embeddingService = embeddingService;
        this.vectorCache = new TTLCache<>(1000, 60);
        this.knowledgeIndex = new java.util.concurrent.ConcurrentHashMap<>();
    }
    
    public void storeKnowledgeVector(String knowledgeId, String content) {
        log.info("存储知识向量: {}", knowledgeId);
        try {
            float[] embedding = embeddingService.embed(content);
            if (embedding == null || embedding.length == 0) {
                log.warn("嵌入向量生成失败");
                return;
            }
            
            Knowledge knowledge = knowledgeMapper.selectOne(
                new QueryWrapper<Knowledge>()
                    .eq("knowledge_id", knowledgeId)
                    .eq("deleted", 0)
            );
            
            if (knowledge != null) {
                knowledge.setEmbeddingId(knowledgeId);
                knowledgeMapper.updateById(knowledge);
                
                cacheLock.lock();
                try {
                    vectorCache.putValue(knowledgeId, embedding);
                } finally {
                    cacheLock.unlock();
                }
                
                knowledgeIndex.put(knowledgeId, Map.of(
                    "knowledgeId", knowledgeId,
                    "embedding", embedding,
                    "content", content
                ));
                
                log.info("知识向量存储成功: {}, 缓存大小: {}", knowledgeId, vectorCache.size());
            }
        } catch (Exception e) {
            log.error("存储知识向量失败", e);
        }
    }
    
    public List<Map<String, Object>> searchSimilar(float[] queryVector, int topK) {
        log.debug("搜索相似知识: topK={}", topK);
        List<Map<String, Object>> results = new ArrayList<>();
        
        vectorCache.cleanExpired();
        
        List<String> knowledgeIds;
        cacheLock.lock();
        try {
            knowledgeIds = new ArrayList<>(vectorCache.keySet());
        } finally {
            cacheLock.unlock();
        }
        
        for (String knowledgeId : knowledgeIds) {
            float[] storedVector;
            cacheLock.lock();
            try {
                storedVector = vectorCache.getValue(knowledgeId);
            } finally {
                cacheLock.unlock();
            }
            
            if (storedVector == null) continue;
            
            double similarity = cosineSimilarity(queryVector, storedVector);
            if (similarity > 0.3) {
                Knowledge knowledge = knowledgeMapper.selectOne(
                    new QueryWrapper<Knowledge>()
                        .eq("knowledge_id", knowledgeId)
                        .eq("deleted", 0)
                );
                
                if (knowledge != null) {
                    Map<String, Object> result = new java.util.LinkedHashMap<>();
                    result.put("knowledgeId", knowledgeId);
                    result.put("title", knowledge.getTitle());
                    result.put("type", knowledge.getType());
                    result.put("similarity", similarity);
                    result.put("content", knowledge.getContent());
                    results.add(result);
                }
            }
        }
        
        results.sort((a, b) -> Double.compare((Double) b.get("similarity"), (Double) a.get("similarity")));
        
        if (results.size() > topK) {
            return results.subList(0, topK);
        }
        return results;
    }
    
    private double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null || vectorA.length != vectorB.length) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }
        normA = Math.sqrt(normA);
        normB = Math.sqrt(normB);
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dotProduct / (normA * normB);
    }
    
    public void removeFromCache(String knowledgeId) {
        cacheLock.lock();
        try {
            vectorCache.removeValue(knowledgeId);
        } finally {
            cacheLock.unlock();
        }
        knowledgeIndex.remove(knowledgeId);
    }
    
    public void clearCache() {
        cacheLock.lock();
        try {
            vectorCache.clearCache();
        } finally {
            cacheLock.unlock();
        }
        knowledgeIndex.clear();
        log.info("向量存储缓存已清空");
    }
    
    public int getCacheSize() {
        cacheLock.lock();
        try {
            return vectorCache.size();
        } finally {
            cacheLock.unlock();
        }
    }
    
    public void cleanExpiredCache() {
        int removed = vectorCache.cleanExpired();
        if (removed > 0) {
            log.info("清理过期缓存: {} 条记录", removed);
        }
    }
    
    public void rebuildIndex() {
        log.info("重建知识索引...");
        knowledgeIndex.clear();
        
        List<Knowledge> allKnowledge = knowledgeMapper.selectList(
            new QueryWrapper<Knowledge>()
                .eq("deleted", 0)
                .isNotNull("embedding_id")
        );
        
        int count = 0;
        for (Knowledge knowledge : allKnowledge) {
            if (knowledge.getEmbeddingId() != null) {
                float[] embedding;
                cacheLock.lock();
                try {
                    embedding = vectorCache.getValue(knowledge.getEmbeddingId());
                } finally {
                    cacheLock.unlock();
                }
                
                if (embedding != null) {
                    knowledgeIndex.put(knowledge.getEmbeddingId(), Map.of(
                        "knowledgeId", knowledge.getKnowledgeId(),
                        "embedding", embedding,
                        "content", knowledge.getContent()
                    ));
                    count++;
                }
            }
        }
        log.info("重建知识索引完成，共处理 {} 条记录", count);
    }
    
    private static class TTLCache<K, V> {
        private final Map<K, CacheEntry<V>> cache;
        private final int maxSize;
        private final long ttlMillis;
        
        public TTLCache(int maxSize, int ttlMinutes) {
            this.cache = new LinkedHashMap<>(maxSize, 0.75f, true);
            this.maxSize = maxSize;
            this.ttlMillis = ttlMinutes * 60L * 1000L;
        }
        
        public synchronized void putValue(K key, V value) {
            if (cache.size() >= maxSize) {
                K oldestKey = cache.keySet().iterator().next();
                cache.remove(oldestKey);
            }
            cache.put(key, new CacheEntry<>(value, System.currentTimeMillis()));
        }
        
        public synchronized V getValue(K key) {
            CacheEntry<V> entry = cache.get(key);
            if (entry == null) return null;
            if (System.currentTimeMillis() - entry.timestamp > ttlMillis) {
                cache.remove(key);
                return null;
            }
            return entry.value;
        }
        
        public synchronized int cleanExpired() {
            long now = System.currentTimeMillis();
            List<K> expiredKeys = new ArrayList<>();
            for (Map.Entry<K, CacheEntry<V>> entry : cache.entrySet()) {
                if (now - entry.getValue().timestamp > ttlMillis) {
                    expiredKeys.add(entry.getKey());
                }
            }
            for (K key : expiredKeys) {
                cache.remove(key);
            }
            return expiredKeys.size();
        }
        
        public synchronized java.util.Set<K> keySet() {
            return new java.util.HashSet<>(cache.keySet());
        }
        
        public synchronized int size() {
            return cache.size();
        }
        
        public synchronized void clearCache() {
            cache.clear();
        }
        
        public synchronized void removeValue(K key) {
            cache.remove(key);
        }
        
        private static class CacheEntry<V> {
            final V value;
            final long timestamp;
            
            CacheEntry(V value, long timestamp) {
                this.value = value;
                this.timestamp = timestamp;
            }
        }
    }
}
