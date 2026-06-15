package com.ecommerce.workflow.agent.handler;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.ecommerce.workflow.agent.AgentRequest;
import com.ecommerce.workflow.agent.AgentResponse;
import com.ecommerce.workflow.entity.ChatSession;

/**
 * 数据库查询员：负责查询数据库表结构或简单的状态
 */
@Component
public class DatabaseQueryHandler extends AbstractIntentHandler {

    private static final Logger log = LoggerFactory.getLogger(DatabaseQueryHandler.class);
    
    private final JdbcTemplate jdbcTemplate;

    public DatabaseQueryHandler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String getIntentCode() {
        return "database_query";
    }

    @Override
    public AgentResponse handle(AgentRequest request, ChatSession session, IntentResult intent) throws Exception {
        log.info("执行 DatabaseQueryHandler: 准备查询数据库...");
        
        Map<String, Object> entities = intent.getEntities();
        String tableName = null;
        
        if (entities != null && entities.containsKey("table_name")) {
            tableName = String.valueOf(entities.get("table_name"));
        }
        
        if (tableName == null || tableName.trim().isEmpty()) {
            // 简单的正则或者 indexOf 提取表名
            String message = request.getMessage();
            String[] words = message.split("\\s+");
            for (String word : words) {
                if (word.contains("_") && !word.contains("\\")) {
                    tableName = word;
                    break;
                }
            }
        }

        if (tableName == null || tableName.trim().isEmpty()) {
            return AgentResponse.failure("未提取到目标表名，无法执行 DDL 查询。");
        }
        
        // 简单的防注入清洗
        tableName = tableName.replaceAll("[^a-zA-Z0-9_]", "");

        try {
            log.info("尝试执行 SHOW CREATE TABLE {}", tableName);
            List<Map<String, Object>> result = jdbcTemplate.queryForList("SHOW CREATE TABLE " + tableName);
            if (result != null && !result.isEmpty()) {
                Map<String, Object> row = result.get(0);
                String ddl = (String) row.get("Create Table");
                String reply = String.format("成功查询到表 [%s] 的结构定义：\n\n```sql\n%s\n```", tableName, ddl);
                return AgentResponse.success(reply);
            } else {
                return AgentResponse.failure("表 " + tableName + " 不存在或无法查询到其结构。");
            }
        } catch (Exception e) {
            log.error("查询数据库表结构异常: {}", tableName, e);
            return AgentResponse.failure("执行查询异常：" + e.getMessage());
        }
    }
}
