package com.ecommerce.workflow.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/database")
public class DatabaseIndexController {

    private static final Logger log = LoggerFactory.getLogger(DatabaseIndexController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/indexes/check")
    public ApiResponse<Map<String, Object>> checkIndexes() {
        try {
            Map<String, Object> result = new HashMap<>();
            
            String[] tables = {
                "sys_dynamic_skill",
                "sys_knowledge", 
                "sys_chat_session",
                "sys_chat_message",
                "sys_dynamic_workflow",
                "sys_skill_config",
                "biz_case_memory",
                "sys_skill_evolution_log",
                "sys_workflow_instance",
                "ai_provider_config",
                "sys_user_profile",
                "biz_delivery_data"
            };
            
            Map<String, Integer> indexCounts = new HashMap<>();
            List<String> missingIndexes = new ArrayList<>();
            
            for (String table : tables) {
                try {
                    Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ?",
                        Integer.class,
                        table
                    );
                    indexCounts.put(table, count);
                    
                    if (count < 5) {
                        missingIndexes.add(table + " (当前索引: " + count + "个)");
                    }
                } catch (Exception e) {
                    log.warn("检查表 {} 索引时出错: {}", table, e.getMessage());
                }
            }
            
            result.put("indexCounts", indexCounts);
            result.put("missingIndexes", missingIndexes);
            result.put("status", missingIndexes.isEmpty() ? "COMPLETE" : "INCOMPLETE");
            result.put("message", missingIndexes.isEmpty() ? 
                "所有表索引检查完成" : 
                "以下表缺少索引: " + String.join(", ", missingIndexes));
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("检查数据库索引失败", e);
            return ApiResponse.error("检查数据库索引失败: " + e.getMessage());
        }
    }

    @PostMapping("/indexes/create")
    public ApiResponse<Map<String, Object>> createIndexes() {
        try {
            Map<String, Object> result = new HashMap<>();
            List<String> successList = new ArrayList<>();
            List<String> failList = new ArrayList<>();
            
            String[] indexSqls = {
                "CREATE INDEX IF NOT EXISTS idx_skill_status ON sys_dynamic_skill(status)",
                "CREATE INDEX IF NOT EXISTS idx_skill_trigger_type ON sys_dynamic_skill(trigger_type)",
                "CREATE INDEX IF NOT EXISTS idx_skill_created_by ON sys_dynamic_skill(created_by)",
                "CREATE INDEX IF NOT EXISTS idx_skill_deleted ON sys_dynamic_skill(deleted)",
                "CREATE INDEX IF NOT EXISTS idx_skill_status_deleted ON sys_dynamic_skill(status, deleted)",
                
                "CREATE INDEX IF NOT EXISTS idx_knowledge_type ON sys_knowledge(type)",
                "CREATE INDEX IF NOT EXISTS idx_knowledge_status ON sys_knowledge(status)",
                "CREATE INDEX IF NOT EXISTS idx_knowledge_source ON sys_knowledge(source)",
                "CREATE INDEX IF NOT EXISTS idx_knowledge_deleted ON sys_knowledge(deleted)",
                
                "CREATE INDEX IF NOT EXISTS idx_session_user_status ON sys_chat_session(user_id, status)",
                "CREATE INDEX IF NOT EXISTS idx_session_deleted ON sys_chat_session(deleted)",
                
                "CREATE INDEX IF NOT EXISTS idx_message_session ON sys_chat_message(session_id)",
                "CREATE INDEX IF NOT EXISTS idx_message_role ON sys_chat_message(role)",
                "CREATE INDEX IF NOT EXISTS idx_message_deleted ON sys_chat_message(deleted)",
                
                "CREATE INDEX IF NOT EXISTS idx_workflow_skill ON sys_dynamic_workflow(skill_id)",
                "CREATE INDEX IF NOT EXISTS idx_workflow_status ON sys_dynamic_workflow(status)",
                "CREATE INDEX IF NOT EXISTS idx_workflow_deleted ON sys_dynamic_workflow(deleted)",
                
                "CREATE INDEX IF NOT EXISTS idx_skill_code ON sys_skill_config(skill_code)",
                "CREATE INDEX IF NOT EXISTS idx_skill_category ON sys_skill_config(skill_category)",
                "CREATE INDEX IF NOT EXISTS idx_skill_version ON sys_skill_config(version)",
                "CREATE INDEX IF NOT EXISTS idx_skill_status_deleted ON sys_skill_config(status, deleted)",
                "CREATE INDEX IF NOT EXISTS idx_skill_code_version ON sys_skill_config(skill_code, version)",
                
                "CREATE INDEX IF NOT EXISTS idx_case_type ON biz_case_memory(case_type)",
                "CREATE INDEX IF NOT EXISTS idx_case_quality ON biz_case_memory(quality_tag)",
                "CREATE INDEX IF NOT EXISTS idx_case_deleted ON biz_case_memory(deleted)",
                "CREATE INDEX IF NOT EXISTS idx_case_created_at ON biz_case_memory(created_at)",
                
                "CREATE INDEX IF NOT EXISTS idx_evolution_skill ON sys_skill_evolution_log(skill_code)",
                "CREATE INDEX IF NOT EXISTS idx_evolution_status ON sys_skill_evolution_log(status)",
                "CREATE INDEX IF NOT EXISTS idx_evolution_deleted ON sys_skill_evolution_log(deleted)",
                "CREATE INDEX IF NOT EXISTS idx_evolution_skill_version ON sys_skill_evolution_log(skill_code, skill_version_snapshot)",
                
                "CREATE INDEX IF NOT EXISTS idx_instance_status ON sys_workflow_instance(status)",
                "CREATE INDEX IF NOT EXISTS idx_instance_deleted ON sys_workflow_instance(deleted)",
                "CREATE INDEX IF NOT EXISTS idx_instance_created_at ON sys_workflow_instance(created_at)",
                
                "CREATE INDEX IF NOT EXISTS idx_ai_provider_type ON ai_provider_config(provider_type)",
                "CREATE INDEX IF NOT EXISTS idx_ai_provider_enabled ON ai_provider_config(enabled)",
                "CREATE INDEX IF NOT EXISTS idx_ai_provider_deleted ON ai_provider_config(deleted)",
                
                "CREATE INDEX IF NOT EXISTS idx_user_profile_user ON sys_user_profile(user_id)",
                "CREATE INDEX IF NOT EXISTS idx_user_profile_deleted ON sys_user_profile(deleted)",
                
                "CREATE INDEX IF NOT EXISTS idx_delivery_created_at ON biz_delivery_data(created_at)",
                
                "CREATE INDEX IF NOT EXISTS idx_skill_active ON sys_dynamic_skill(status, deleted, created_at)",
                "CREATE INDEX IF NOT EXISTS idx_session_active ON sys_chat_session(user_id, status, deleted, updated_at)",
                "CREATE INDEX IF NOT EXISTS idx_message_session_time ON sys_chat_message(session_id, created_at, deleted)",
                "CREATE INDEX IF NOT EXISTS idx_case_quality_time ON biz_case_memory(quality_tag, created_at, deleted)",
                "CREATE INDEX IF NOT EXISTS idx_evolution_pending ON sys_skill_evolution_log(status, skill_code, deleted)"
            };
            
            for (String sql : indexSqls) {
                try {
                    jdbcTemplate.execute(sql);
                    successList.add(sql.substring(sql.indexOf("ON") + 3).split(" ")[0]);
                } catch (Exception e) {
                    if (!e.getMessage().contains("Duplicate key name")) {
                        failList.add(sql.substring(sql.indexOf("ON") + 3).split(" ")[0] + ": " + e.getMessage());
                    }
                }
            }
            
            result.put("successCount", successList.size());
            result.put("failCount", failList.size());
            result.put("successList", successList);
            result.put("failList", failList);
            result.put("message", "数据库索引优化完成: 成功" + successList.size() + " 个, 失败" + failList.size() + " 个");
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("数据库索引优化失败", e);
            return ApiResponse.error("索引优化失败: " + e.getMessage());
        }
    }
}
