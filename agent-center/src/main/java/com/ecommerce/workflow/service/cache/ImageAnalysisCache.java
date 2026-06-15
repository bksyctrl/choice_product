package com.ecommerce.workflow.service.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ImageAnalysisCache {
    private static final Logger log = LoggerFactory.getLogger(ImageAnalysisCache.class);

    private static final long CACHE_EXPIRE_MS = 30 * 60 * 1000;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public Map<String, Object> get(String imageHash) {
        CacheEntry entry = cache.get(imageHash);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.timestamp > CACHE_EXPIRE_MS) {
            cache.remove(imageHash);
            log.info("图片分析缓存过期: hash={}", imageHash.substring(0, Math.min(16, imageHash.length())));
            return null;
        }
        log.info("图片分析缓存命中: hash={}", imageHash.substring(0, Math.min(16, imageHash.length())));
        // 返回副本，防止外部修改影响缓存数据
        return new HashMap<>(entry.data);
    }

    public void put(String imageHash, Map<String, Object> data) {
        // 存储副本，防止外部修改影响缓存数据
        cache.put(imageHash, new CacheEntry(new HashMap<>(data)));
        log.info("图片分析缓存写入: hash={}, size={}", 
                imageHash.substring(0, Math.min(16, imageHash.length())), cache.size());
    }

    public int size() {
        cleanupExpired();
        return cache.size();
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> now - entry.getValue().timestamp > CACHE_EXPIRE_MS);
    }

    public static String computeHash(String base64Data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(base64Data.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(base64Data.hashCode());
        }
    }

    private static class CacheEntry {
        final Map<String, Object> data;
        final long timestamp;

        CacheEntry(Map<String, Object> data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
