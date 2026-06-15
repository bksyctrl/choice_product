package com.ecommerce.workflow.service.ai;

import com.ecommerce.workflow.entity.AiModelCapability;
import com.ecommerce.workflow.mapper.AiModelCapabilityMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ModelDisplayNameService {
    
    private static final Logger log = LoggerFactory.getLogger(ModelDisplayNameService.class);
    
    @Autowired
    private AiModelCapabilityMapper capabilityMapper;
    
    // 缓存：modelName -> displayName
    private final Map<String, String> displayNameCache = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        refreshCache();
        log.info("模型显示名称缓存初始化完成，共加载 {} 个模型", displayNameCache.size());
    }
    
    /**
     * 每小时刷新一次缓存
     */
    @Scheduled(fixedRate = 3600000)
    public void refreshCache() {
        try {
            QueryWrapper<AiModelCapability> wrapper = new QueryWrapper<>();
            wrapper.select("DISTINCT model_name, display_name")
                   .isNotNull("display_name");
            
            List<AiModelCapability> capabilities = capabilityMapper.selectList(wrapper);
            
            Map<String, String> newCache = new ConcurrentHashMap<>();
            for (AiModelCapability cap : capabilities) {
                if (cap.getDisplayName() != null && !cap.getDisplayName().isEmpty()) {
                    newCache.put(cap.getModelName(), cap.getDisplayName());
                }
            }
            
            synchronized (displayNameCache) {
                displayNameCache.clear();
                displayNameCache.putAll(newCache);
            }
            
            log.debug("模型显示名称缓存刷新完成，共 {} 个模型", displayNameCache.size());
        } catch (Exception e) {
            log.error("刷新模型显示名称缓存失败: {}", e.getMessage());
        }
    }
    
    /**
     * 获取模型的显示名称
     * @param modelName 模型名称
     * @return 显示名称，如果数据库中没有配置则返回原模型名
     */
    public String getDisplayName(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return modelName;
        }
        
        // 1. 先从缓存查询
        String displayName = displayNameCache.get(modelName);
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }
        
        // 2. 缓存未命中，尝试从数据库实时查询
        try {
            QueryWrapper<AiModelCapability> wrapper = new QueryWrapper<>();
            wrapper.eq("model_name", modelName)
                   .isNotNull("display_name")
                   .orderByDesc("updated_at")
                   .last("LIMIT 1");
            
            AiModelCapability cap = capabilityMapper.selectOne(wrapper);
            if (cap != null && cap.getDisplayName() != null && !cap.getDisplayName().isEmpty()) {
                // 更新缓存
                displayNameCache.put(modelName, cap.getDisplayName());
                return cap.getDisplayName();
            }
        } catch (Exception e) {
            log.warn("实时查询模型显示名称失败: {}", e.getMessage());
        }
        
        // 3. 数据库中没有配置，返回原模型名
        return modelName;
    }
    
    /**
     * 更新模型的显示名称
     * @param modelName 模型名称
     * @param displayName 显示名称
     */
    public void updateDisplayName(String modelName, String displayName) {
        try {
            QueryWrapper<AiModelCapability> wrapper = new QueryWrapper<>();
            wrapper.eq("model_name", modelName);
            
            List<AiModelCapability> capabilities = capabilityMapper.selectList(wrapper);
            
            for (AiModelCapability cap : capabilities) {
                cap.setDisplayName(displayName);
                capabilityMapper.updateById(cap);
            }
            
            // 更新缓存
            displayNameCache.put(modelName, displayName);
            
            log.info("更新模型显示名称: {} -> {}", modelName, displayName);
        } catch (Exception e) {
            log.error("更新模型显示名称失败: {}", e.getMessage());
            throw new RuntimeException("更新模型显示名称失败", e);
        }
    }
    
    /**
     * 获取所有模型的显示名称映射
     * @return 模型名 -> 显示名称 的映射
     */
    public Map<String, String> getAllDisplayNames() {
        return new ConcurrentHashMap<>(displayNameCache);
    }
}
