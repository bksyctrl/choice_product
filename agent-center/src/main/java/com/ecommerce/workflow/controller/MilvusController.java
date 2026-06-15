package com.ecommerce.workflow.controller;

import com.ecommerce.workflow.service.vector.MilvusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/milvus")
public class MilvusController {
    private static final Logger log = LoggerFactory.getLogger(MilvusController.class);
    
    @Autowired
    private MilvusService milvusService;
    
    @GetMapping("/test")
    public ApiResponse<Map<String, Object>> testConnection() {
        log.info("测试 Milvus 连接");
        
        Map<String, Object> result = new HashMap<>();
        boolean connected = milvusService.testConnection();
        
        result.put("connected", connected);
        result.put("timestamp", System.currentTimeMillis());
        
        if (connected) {
            result.put("message", "Milvus 连接成功");
            return ApiResponse.success(result);
        } else {
            result.put("message", "Milvus 连接失败");
            return ApiResponse.error("Milvus 连接失败");
        }
    }
    
    @GetMapping("/collection/{name}/exists")
    public ApiResponse<Boolean> hasCollection(@PathVariable String name) {
        log.info("检查集合是否存在: {}", name);
        
        boolean exists = milvusService.hasCollection(name);
        return ApiResponse.success(exists);
    }
}
