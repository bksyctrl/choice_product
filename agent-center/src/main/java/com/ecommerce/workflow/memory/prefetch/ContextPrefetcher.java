package com.ecommerce.workflow.memory.prefetch;

import com.ecommerce.workflow.memory.manager.MemoryManager;
import com.ecommerce.workflow.memory.provider.MemoryProvider.MemoryItem;
import com.ecommerce.workflow.service.config.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ContextPrefetcher {
    
    private static final Logger log = LoggerFactory.getLogger(ContextPrefetcher.class);
    
    @Autowired
    private MemoryManager memoryManager;
    
    @Autowired
    private SysConfigService sysConfigService;
    
    private final Map<String, List<MemoryItem>> prefetchedCache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastPrefetchTime = new ConcurrentHashMap<>();
    
    public List<MemoryItem> prefetchContext(String sessionId, String currentQuery, int topK) {
        String cacheKey = sessionId + ":" + currentQuery.hashCode();
        
        if (isCacheValid(cacheKey)) {
            log.debug("使用预取缓存: sessionId={}", sessionId);
            return prefetchedCache.get(cacheKey);
        }
        
        List<String> keywords = extractKeywords(currentQuery);
        Set<MemoryItem> allResults = new HashSet<>();
        
        for (String keyword : keywords) {
            List<MemoryItem> results = memoryManager.searchAll(keyword, topK / keywords.size());
            allResults.addAll(results);
        }
        
        List<MemoryItem> sortedResults = new ArrayList<>(allResults);
        sortedResults.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        
        if (sortedResults.size() > topK) {
            sortedResults = sortedResults.subList(0, topK);
        }
        
        prefetchedCache.put(cacheKey, sortedResults);
        lastPrefetchTime.put(cacheKey, System.currentTimeMillis());
        
        log.info("预取上下文完成: sessionId={}, keywords={}, results={}", 
                sessionId, keywords.size(), sortedResults.size());
        
        return sortedResults;
    }
    
    public List<MemoryItem> prefetchByIntent(String sessionId, String intent, int topK) {
        String cacheKey = sessionId + ":intent:" + intent;
        
        if (isCacheValid(cacheKey)) {
            return prefetchedCache.get(cacheKey);
        }
        
        Map<String, String> intentKeywords = getIntentKeywords();
        String keywords = intentKeywords.getOrDefault(intent, intent);
        
        List<MemoryItem> results = memoryManager.searchAll(keywords, topK);
        
        prefetchedCache.put(cacheKey, results);
        lastPrefetchTime.put(cacheKey, System.currentTimeMillis());
        
        log.info("按意图预取上下文: sessionId={}, intent={}, results={}", 
                sessionId, intent, results.size());
        
        return results;
    }
    
    public void invalidateCache(String sessionId) {
        prefetchedCache.keySet().removeIf(key -> key.startsWith(sessionId + ":"));
        lastPrefetchTime.keySet().removeIf(key -> key.startsWith(sessionId + ":"));
        log.debug("失效预取缓存: sessionId={}", sessionId);
    }
    
    public void clearAllCache() {
        prefetchedCache.clear();
        lastPrefetchTime.clear();
        log.info("清除所有预取缓存");
    }
    
    private boolean isCacheValid(String cacheKey) {
        Long lastTime = lastPrefetchTime.get(cacheKey);
        if (lastTime == null) {
            return false;
        }
        
        return System.currentTimeMillis() - lastTime < sysConfigService.getLongConfig("prefetch_cache_ttl_ms", 300000);
    }
    
    private List<String> extractKeywords(String query) {
        List<String> keywords = new ArrayList<>();
        
        String[] words = query.split("[\\s,，。、！？；：\"'【】《》（）\\-]+");
        
        for (String word : words) {
            if (word.length() >= 2) {
                keywords.add(word);
            }
        }
        
        if (keywords.isEmpty()) {
            keywords.add(query);
        }
        
        return keywords;
    }
    
    private Map<String, String> getIntentKeywords() {
        Map<String, String> map = new HashMap<>();
        map.put("VIDEO_GENERATION", "视频 生成 制作 创作");
        map.put("LEARN_PROMPT", "学习 prompt 优化 提示词");
        map.put("LEARN_KNOWLEDGE", "知识 学习 经验 总结");
        map.put("CONFIG_SKILL", "技能 skill 配置 参数");
        map.put("QUERY_SKILL", "查询 搜索 skill 配置");
        map.put("QUERY_KNOWLEDGE", "知识 检索 搜索");
        map.put("GENERAL_CHAT", "聊天 对话 问答");
        return map;
    }
}
