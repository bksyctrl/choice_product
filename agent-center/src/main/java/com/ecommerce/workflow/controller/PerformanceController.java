package com.ecommerce.workflow.controller;

import com.ecommerce.workflow.service.ai.AiRateLimiter;
import com.ecommerce.workflow.service.cache.ImageAnalysisCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/performance")
public class PerformanceController {

    @Autowired
    private AiRateLimiter aiRateLimiter;

    @Autowired
    private ImageAnalysisCache imageAnalysisCache;

    @GetMapping("/status")
    public Map<String, Object> getPerformanceStatus() {
        Map<String, Object> status = new HashMap<>();
        
        Map<String, Object> rateLimiter = new HashMap<>();
        rateLimiter.put("availablePermits", aiRateLimiter.getAvailablePermits());
        status.put("rateLimiter", rateLimiter);

        Map<String, Object> cache = new HashMap<>();
        cache.put("size", imageAnalysisCache.size());
        status.put("cache", cache);

        Map<String, Object> memory = new HashMap<>();
        Runtime runtime = Runtime.getRuntime();
        memory.put("totalMemoryMB", runtime.totalMemory() / 1024 / 1024);
        memory.put("freeMemoryMB", runtime.freeMemory() / 1024 / 1024);
        memory.put("maxMemoryMB", runtime.maxMemory() / 1024 / 1024);
        memory.put("usedMemoryMB", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
        status.put("memory", memory);

        return status;
    }
}
