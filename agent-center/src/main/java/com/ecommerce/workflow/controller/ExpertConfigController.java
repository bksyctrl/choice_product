package com.ecommerce.workflow.controller;

import com.ecommerce.workflow.service.learning.ExpertConfigCacheManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expert-config")
public class ExpertConfigController {

    @Autowired
    private ExpertConfigCacheManager cacheManager;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostMapping("/refresh")
    public Map<String, Object> refreshCache() {
        Map<String, Object> result = new HashMap<>();
        try {
            cacheManager.refreshCache();
            result.put("success", true);
            result.put("message", "专家配置缓存已刷新");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "刷新失败: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/status")
    public Map<String, Object> getCacheStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("cacheExpired", cacheManager.isCacheExpired());
        result.put("roleCount", cacheManager.getAllRoleConfigs().size());
        return result;
    }
    
    @GetMapping("/db-check")
    public Map<String, Object> checkDatabase() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 检查表是否存在
            Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'expert_role_config'",
                Integer.class
            );
            result.put("tableExists", tableCount != null && tableCount > 0);
            
            if (tableCount != null && tableCount > 0) {
                // 检查数据数量
                Integer roleCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM expert_role_config WHERE deleted = 0",
                    Integer.class
                );
                result.put("dbRoleCount", roleCount);
                
                // 获取所有角色列表
                List<Map<String, Object>> roles = jdbcTemplate.queryForList(
                    "SELECT role_code, role_name, status FROM expert_role_config WHERE deleted = 0"
                );
                result.put("roles", roles);
            }
            
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
    
    @GetMapping("/detail")
    public Map<String, Object> getDetailStats() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 统计维度数量
            Integer dimensionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM expert_analysis_dimension WHERE deleted = 0 AND is_active = 1",
                Integer.class
            );
            result.put("dimensionCount", dimensionCount);
            
            // 统计关键词数量
            Integer keywordCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM expert_trigger_keyword WHERE deleted = 0 AND is_active = 1",
                Integer.class
            );
            result.put("keywordCount", keywordCount);
            
            // 按角色统计维度
            List<Map<String, Object>> dimensionsByRole = jdbcTemplate.queryForList(
                "SELECT role_code, COUNT(*) as count FROM expert_analysis_dimension WHERE deleted = 0 AND is_active = 1 GROUP BY role_code"
            );
            result.put("dimensionsByRole", dimensionsByRole);
            
            // 按角色统计关键词
            List<Map<String, Object>> keywordsByRole = jdbcTemplate.queryForList(
                "SELECT role_code, COUNT(*) as count FROM expert_trigger_keyword WHERE deleted = 0 AND is_active = 1 GROUP BY role_code"
            );
            result.put("keywordsByRole", keywordsByRole);
            
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
}
