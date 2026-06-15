package com.ecommerce.workflow.config;

import com.ecommerce.workflow.service.ai.AiProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class AiConfigInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AiConfigInitializer.class);
    private final AiProviderService aiProviderService;
    private final JdbcTemplate jdbcTemplate;

    public AiConfigInitializer(AiProviderService aiProviderService, JdbcTemplate jdbcTemplate) {
        this.aiProviderService = aiProviderService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureTableExists();
            log.info("正在初始化AI提供商默认配置..");
            aiProviderService.initDefaultProviders();
            log.info("AI提供商配置初始化完成");
        } catch (Exception e) {
            log.error("AI提供商配置初始化失败", e);
        }
    }

    private void ensureTableExists() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'ai_provider_config'",
                    Integer.class
            );
            if (count != null && count > 0) {
                log.info("AI提供商配置表已存在");
                return;
            }
        } catch (Exception e) {
            log.warn("检查表是否存在时出错，尝试创建表");
        }

        String createSql = """
                CREATE TABLE IF NOT EXISTS ai_provider_config (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                    provider_name VARCHAR(100) NOT NULL COMMENT '提供商名称',
                    provider_type VARCHAR(20) NOT NULL COMMENT '类型: LLM/VIDEO/EMBEDDING/IMAGE/SPEECH等，可自定义扩展',
                    base_url VARCHAR(500) NOT NULL COMMENT 'API基础URL',
                    api_key VARCHAR(500) NOT NULL COMMENT 'API密钥',
                    models JSON COMMENT '可用模型列表(JSON数组)',
                    priority INT DEFAULT 0 COMMENT '优先级: 数字越小越优先',
                    enabled TINYINT DEFAULT 1 COMMENT '是否启用: 0-禁用 1-启用',
                    default_model VARCHAR(100) COMMENT '默认模型',
                    max_retries INT DEFAULT 2 COMMENT '最大重试次数',
                    timeout_ms BIGINT DEFAULT 60000 COMMENT '超时时间(毫秒)',
                    extra_params JSON COMMENT '额外参数',
                    created_by BIGINT COMMENT '创建人ID',
                    updated_by BIGINT COMMENT '更新人ID',
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
                    INDEX idx_provider_type (provider_type),
                    INDEX idx_enabled (enabled),
                    INDEX idx_priority (priority)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI鎻愪緵鍟嗛厤缃〃'
                """;
        jdbcTemplate.execute(createSql);
        log.info("AI鎻愪緵鍟嗛厤缃〃鍒涘缓鎴愬姛");
    }
}
