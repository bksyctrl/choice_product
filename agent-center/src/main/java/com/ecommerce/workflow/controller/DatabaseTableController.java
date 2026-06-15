package com.ecommerce.workflow.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/database")
public class DatabaseTableController {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTableController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/tables/check")
    public ApiResponse<Map<String, Object>> checkTables() {
        try {
            Map<String, Object> result = new HashMap<>();
            
            String[] requiredTables = {
                "sys_dynamic_skill",
                "sys_knowledge",
                "sys_chat_session",
                "sys_chat_message",
                "sys_dynamic_workflow",
                "biz_skill_config",
                "biz_case_memory",
                "biz_evolution_log",
                "sys_workflow_instance",
                "sys_ai_provider_config",
                "sys_user_profile",
                "biz_delivery_data"
            };
            
            List<String> existingTables = new ArrayList<>();
            List<String> missingTables = new ArrayList<>();
            
            for (String table : requiredTables) {
                try {
                    Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                        Integer.class,
                        table
                    );
                    
                    if (count != null && count > 0) {
                        existingTables.add(table);
                    } else {
                        missingTables.add(table);
                    }
                } catch (Exception e) {
                    missingTables.add(table + " (检查失败)");
                }
            }
            
            result.put("existingTables", existingTables);
            result.put("missingTables", missingTables);
            result.put("totalRequired", requiredTables.length);
            result.put("existingCount", existingTables.size());
            result.put("missingCount", missingTables.size());
            result.put("status", missingTables.isEmpty() ? "COMPLETE" : "INCOMPLETE");
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("检查数据库表失败", e);
            return ApiResponse.error("检查表失败: " + e.getMessage());
        }
    }
}
