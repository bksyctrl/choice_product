package com.ecommerce.workflow.service.schema;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ecommerce.workflow.entity.SkillConfig;
import com.ecommerce.workflow.service.ai.GptChatService;
import com.ecommerce.workflow.service.evolution.SkillConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SchemaAnalyzerService {
    private static final Logger log = LoggerFactory.getLogger(SchemaAnalyzerService.class);
    private final DataSource dataSource;
    private final SkillConfigService skillConfigService;
    private final GptChatService gptChatService;
    private final ObjectMapper objectMapper;

    @Value("${spring.datasource.url}")
    private String dbUrl;
    @Value("${spring.datasource.username}")
    private String dbUsername;
    @Value("${spring.datasource.password}")
    private String dbPassword;

    public SchemaAnalyzerService(DataSource dataSource,
                                   SkillConfigService skillConfigService,
                                   GptChatService gptChatService,
                                   ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.skillConfigService = skillConfigService;
        this.gptChatService = gptChatService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> discoverTables() {
        log.info("开始发现数据库表结构...");
        List<Map<String, Object>> tables = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword)) {
            DatabaseMetaData metaData = conn.getMetaData();
            String dbName = conn.getCatalog();
            ResultSet rs = metaData.getTables(dbName, null, "%", new String[]{"TABLE"});

            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                String remarks = rs.getString("REMARKS");

                if (tableName.startsWith("sys_") || tableName.startsWith("qrtz_")) {
                    continue;
                }

                Map<String, Object> tableInfo = new HashMap<>();
                tableInfo.put("tableName", tableName);
                tableInfo.put("remarks", remarks);
                tableInfo.put("columns", discoverColumns(metaData, dbName, tableName));
                tables.add(tableInfo);
                log.debug("发现表: {}", tableName);
            }
            log.info("发现表数量: {} 个", tables.size());
        } catch (Exception e) {
            log.error("发现表失败", e);
        }
        return tables;
    }

    private List<Map<String, Object>> discoverColumns(DatabaseMetaData metaData, String dbName, String tableName) {
        List<Map<String, Object>> columns = new ArrayList<>();
        try {
            ResultSet rs = metaData.getColumns(dbName, null, tableName, "%");
            while (rs.next()) {
                Map<String, Object> column = new HashMap<>();
                column.put("name", rs.getString("COLUMN_NAME"));
                column.put("type", rs.getString("TYPE_NAME"));
                column.put("size", rs.getInt("COLUMN_SIZE"));
                column.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                column.put("remarks", rs.getString("REMARKS"));
                columns.add(column);
            }
        } catch (Exception e) {
            log.warn("发现表列失败: {}", tableName, e);
        }
        return columns;
    }

    public Map<String, Object> analyzeTable(String tableName) {
        log.info("分析表: {}", tableName);
        Map<String, Object> analysis = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword)) {
            DatabaseMetaData metaData = conn.getMetaData();
            String dbName = conn.getCatalog();
            analysis.put("tableName", tableName);
            analysis.put("columns", discoverColumns(metaData, dbName, tableName));
            analysis.put("primaryKeys", discoverPrimaryKeys(metaData, dbName, tableName));
            analysis.put("foreignKeys", discoverForeignKeys(metaData, dbName, tableName));
            analysis.put("businessMeaning", inferBusinessMeaning(tableName, analysis));
        } catch (Exception e) {
            log.error("分析表失败: {}", tableName, e);
        }
        return analysis;
    }

    private List<String> discoverPrimaryKeys(DatabaseMetaData metaData, String dbName, String tableName) {
        List<String> pks = new ArrayList<>();
        try {
            ResultSet rs = metaData.getPrimaryKeys(dbName, null, tableName);
            while (rs.next()) {
                pks.add(rs.getString("COLUMN_NAME"));
            }
        } catch (Exception e) {
            log.warn("发现表主键失败: {}", tableName, e);
        }
        return pks;
    }

    private List<Map<String, Object>> discoverForeignKeys(DatabaseMetaData metaData, String dbName, String tableName) {
        List<Map<String, Object>> fks = new ArrayList<>();
        try {
            ResultSet rs = metaData.getImportedKeys(dbName, null, tableName);
            while (rs.next()) {
                Map<String, Object> fk = new HashMap<>();
                fk.put("columnName", rs.getString("FKCOLUMN_NAME"));
                fk.put("referencedTable", rs.getString("PKTABLE_NAME"));
                fk.put("referencedColumn", rs.getString("PKCOLUMN_NAME"));
                fks.add(fk);
            }
        } catch (Exception e) {
            log.warn("发现表外键失败: {}", tableName, e);
        }
        return fks;
    }

    public String inferBusinessMeaning(String tableName, Map<String, Object> analysis) {
        try {
            String prompt = buildAnalysisPrompt(tableName, analysis);
            return gptChatService.chat("数据库表结构分析", prompt);
        } catch (Exception e) {
            log.warn("AI分析表结构失败", e);
            return "AI分析表结构失败";
        }
    }

    private String buildAnalysisPrompt(String tableName, Map<String, Object> analysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析以下数据库表结构，推断其业务含义:\n\n");
        sb.append("表名: ").append(tableName).append("\n\n");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> columns = (List<Map<String, Object>>) analysis.get("columns");
        if (columns != null) {
            sb.append("列信息:\n");
            for (Map<String, Object> column : columns) {
                sb.append("- ").append(column.get("name")).append(" (").append(column.get("type"));
                if (column.get("size") != null) {
                    sb.append("(").append(column.get("size")).append(")");
                }
                sb.append(")");
                if (column.get("remarks") != null && !((String) column.get("remarks")).isEmpty()) {
                    sb.append(" - ").append(column.get("remarks"));
                }
                sb.append("\n");
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> indexes = (List<Map<String, Object>>) analysis.get("indexes");
        if (indexes != null && !indexes.isEmpty()) {
            sb.append("\n索引信息:\n");
            for (Map<String, Object> index : indexes) {
                sb.append("- ").append(index.get("name")).append(" (").append(index.get("column"));
                if (Boolean.TRUE.equals(index.get("unique"))) {
                    sb.append(", 唯一");
                }
                sb.append(")\n");
            }
        }

        sb.append("\n请分析以下内容:\n");
        sb.append("1. 表的主要业务用途\n");
        sb.append("2. 关键字段的业务含义\n");
        sb.append("3. 可能的业务场景\n");

        return sb.toString();
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void autoDiscoverAndGenerate() {
        log.info("=== 开始自动发现表结构并生成Skill ===");
        List<Map<String, Object>> tables = discoverTables();

        for (Map<String, Object> table : tables) {
            String tableName = (String) table.get("tableName");
            try {
                String skillCode = "auto_" + tableName.toLowerCase();
                SkillConfig existingSkill = skillConfigService.getActiveSkill(skillCode);
                if (existingSkill != null) {
                    log.debug("表{}已存在对应Skill，跳过", tableName);
                    continue;
                }

                generateDataAccessSkill(table);
                log.info("表{}生成数据访问Skill成功", tableName);
            } catch (Exception e) {
                log.warn("表{}生成Skill失败", tableName, e);
            }
        }
        log.info("=== 自动发现并生成Skill完成 ===");
    }

    public SkillConfig generateDataAccessSkill(Map<String, Object> tableInfo) {
        String tableName = (String) tableInfo.get("tableName");
        log.info("为表{}生成数据访问Skill", tableName);

        String skillCode = "auto_" + tableName.toLowerCase();
        String skillName = tableName + "数据访问";
        String description = "自动生成表" + tableName + "的数据访问Skill";

        Map<String, Object> triggerPattern = new HashMap<>();
        triggerPattern.put("type", "KEYWORD");
        triggerPattern.put("keywords", List.of(tableName, "查询", "数据"));

        Map<String, Object> configParams = new HashMap<>();
        configParams.put("tableName", tableName);
        configParams.put("triggerPattern", triggerPattern);

        try {
            configParams.put("triggerPatternJson", objectMapper.writeValueAsString(triggerPattern));
        } catch (Exception e) {
            log.warn("序列化触发模式失败", e);
        }

        return skillConfigService.createOrUpdateSkill(skillCode, skillName, "data_analysis", configParams, "自动生成数据访问");
    }
}
