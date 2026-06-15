package com.ecommerce.workflow.service.config;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class SysConfigService {

    private static final Logger log = LoggerFactory.getLogger(SysConfigService.class);

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, String> configCache = new ConcurrentHashMap<>();
    private long lastRefreshTime = 0;
    private long refreshIntervalMs = 60000;

    public SysConfigService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        initTables();
        loadAllConfigs();
        registerDefaultConfigs();
        log.info("统一配置服务初始化完成: 配置项数={}", configCache.size());
    }

    private void initTables() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_unified_config (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    config_group VARCHAR(100) NOT NULL,
                    config_key VARCHAR(200) NOT NULL,
                    config_value TEXT,
                    default_value TEXT,
                    value_type VARCHAR(20) DEFAULT 'STRING',
                    description VARCHAR(500),
                    enabled INT DEFAULT 1,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE INDEX uk_group_key (config_group, config_key),
                    INDEX idx_group (config_group)
                )
                """);
        } catch (Exception e) {
            log.debug("sys_unified_config table may already exist");
        }
    }

    private void loadAllConfigs() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT config_key, config_value FROM sys_unified_config WHERE enabled = 1");
            configCache.clear();
            for (Map<String, Object> row : rows) {
                String key = (String) row.get("config_key");
                String value = (String) row.get("config_value");
                if (value != null) {
                    configCache.put(key, value);
                }
            }
            lastRefreshTime = System.currentTimeMillis();
        } catch (Exception e) {
            log.debug("加载统一配置失败", e);
        }
    }

    private void registerDefaultConfigs() {
        registerConfig("brain", "intent_confidence_threshold", "0.6", "DOUBLE",
                "Brain意图识别置信度阈值");
        registerConfig("brain", "llm_intent_enabled", "true", "BOOLEAN",
                "是否启用LLM意图识别");
        registerConfig("brain", "keyword_fallback_enabled", "true", "BOOLEAN",
                "是否启用关键词回退匹配");

        registerConfig("orchestrator", "max_retries", "3", "INT",
                "任务最大重试次数");
        registerConfig("orchestrator", "default_timeout_ms", "120000", "LONG",
                "任务默认超时时间(ms)");
        registerConfig("orchestrator", "max_concurrent_tasks", "10", "INT",
                "最大并发任务数");
        registerConfig("orchestrator", "worker_pool_size", "10", "INT",
                "工作线程池大小");
        registerConfig("orchestrator", "retry_backoff_base", "1000", "LONG",
                "重试退避基础时间(ms)");
        registerConfig("orchestrator", "retry_backoff_max", "30000", "LONG",
                "重试退避最大时间(ms)");

        registerConfig("checkpoint", "auto_snapshot_enabled", "true", "BOOLEAN",
                "是否启用自动快照");
        registerConfig("checkpoint", "max_auto_snapshots", "50", "INT",
                "最大自动快照数");
        registerConfig("checkpoint", "snapshot_retention_days", "7", "INT",
                "快照保留天数");

        registerConfig("heartbeat", "reflection_enabled", "true", "BOOLEAN",
                "是否启用心跳反思");
        registerConfig("heartbeat", "reflection_interval_ms", "1800000", "LONG",
                "心跳反思间隔(ms)");
        registerConfig("heartbeat", "viral_extraction_enabled", "true", "BOOLEAN",
                "是否启用爆款规律自动提取");
        registerConfig("heartbeat", "viral_min_cases", "3", "INT",
                "爆款规律提取最少案例数");
        registerConfig("heartbeat", "viral_cvr_threshold", "0.03", "DOUBLE",
                "爆款规律提取CVR阈值");

        registerConfig("delegate", "max_parallel_tasks", "10", "INT",
                "最大并行子任务数");
        registerConfig("delegate", "task_timeout_seconds", "30", "INT",
                "子任务超时时间(秒)");

        registerConfig("mcp", "auto_discover_on_register", "true", "BOOLEAN",
                "注册MCP时是否自动发现工具");
        registerConfig("mcp", "health_check_enabled", "true", "BOOLEAN",
                "是否启用MCP健康检查");

        registerConfig("memory", "knowledge_extraction_cvr_threshold", "0.05", "DOUBLE",
                "知识提取CVR阈值");
        registerConfig("memory", "knowledge_extraction_gmv_threshold", "100", "DOUBLE",
                "知识提取GMV阈值");
        registerConfig("memory", "relevant_context_top_k", "3", "INT",
                "相关上下文Top-K");

        registerConfig("evolution", "learning_rate", "0.1", "DOUBLE",
                "Q-Learning学习率");
        registerConfig("evolution", "discount_factor", "0.9", "DOUBLE",
                "Q-Learning折扣因子");
        registerConfig("evolution", "exploration_rate_min", "0.05", "DOUBLE",
                "最小探索率");
        registerConfig("evolution", "exploration_decay", "0.99", "DOUBLE",
                "探索率衰减因子");

        registerConfig("compliance", "rule_refresh_interval_ms", "300000", "LONG",
                "合规规则刷新间隔(ms)");
        registerConfig("compliance", "audit_log_enabled", "true", "BOOLEAN",
                "是否启用合规审计日志");

        registerConfig("ai", "chat_max_tokens", "8000", "INT",
                "AI对话最大Token数");
        registerConfig("ai", "chat_thinking_max_tokens", "16000", "INT",
                "AI思考模式最大Token数");
        registerConfig("ai", "stream_enabled", "true", "BOOLEAN",
                "是否启用流式输出");
        registerConfig("ai", "ai_default_max_tokens", "4000", "INT",
                "AI默认最大Token数");
        registerConfig("ai", "ai_stream_max_tokens", "8000", "INT",
                "AI流式输出最大Token数");
        registerConfig("ai", "ai_vision_max_tokens", "2000", "INT",
                "AI图像分析最大Token数");
        registerConfig("ai", "ai_provider_cache_ttl_ms", "30000", "LONG",
                "AI提供商配置缓存TTL(ms)");
        registerConfig("ai", "default_temperature", "0.7", "DOUBLE",
                "AI默认温度参数");
        registerConfig("ai", "default_timeout_ms", "60000", "LONG",
                "AI默认超时时间(ms)");
        registerConfig("ai", "default_max_retries", "2", "INT",
                "AI默认最大重试次数");

        registerConfig("vector", "vector_dimension", "1536", "INT",
                "向量维度");
        registerConfig("vector", "prefetch_cache_ttl_ms", "300000", "LONG",
                "向量预取缓存TTL(ms)");

        registerConfig("skill", "skill_usage_cache_ttl_ms", "60000", "LONG",
                "技能使用统计缓存TTL(ms)");

        registerConfig("video", "video_poll_max_attempts", "120", "INT",
                "视频轮询最大尝试次数");
        registerConfig("video_prompt", "video_prompt_json_schema", "", "TEXT",
                "视频提示词JSON Schema模板（支持{duration},{aspectRatio},{resolution}变量）");
        registerConfig("video_prompt", "video_prompt_rules", "", "TEXT",
                "视频提示词重要规则列表");
        registerConfig("video_prompt", "video_prompt_system_prefix", "", "TEXT",
                "视频提示词系统前缀描述");
        registerConfig("video_prompt", "video_prompt_version", "1.0.0", "STRING",
                "视频提示词模板版本号");

        registerConfig("embedding", "embedding_fallback_dimension", "384", "INT",
                "嵌入向量回退维度");

        registerConfig("router", "smart_router_cache_ttl_seconds", "300", "LONG",
                "智能路由缓存TTL(秒)");
    }

    private void registerConfig(String group, String key, String defaultValue, String valueType, String description) {
        try {
            jdbcTemplate.update("""
                INSERT INTO sys_unified_config (config_group, config_key, config_value, default_value, value_type, description, enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 1, NOW(), NOW())
                ON DUPLICATE KEY UPDATE description = ?, default_value = ?
                """, group, key, defaultValue, defaultValue, valueType, description, description, defaultValue);

            if (!configCache.containsKey(key)) {
                configCache.put(key, defaultValue);
            }
        } catch (Exception e) {
            log.debug("注册配置项失败: {}.{}", group, key, e);
        }
    }

    public String getConfig(String key, String defaultValue) {
        maybeRefresh();
        return configCache.getOrDefault(key, defaultValue);
    }

    public String getConfig(String key) {
        return getConfig(key, null);
    }

    public int getIntConfig(String key, int defaultValue) {
        String val = getConfig(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public long getLongConfig(String key, long defaultValue) {
        String val = getConfig(key);
        if (val == null) return defaultValue;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public double getDoubleConfig(String key, double defaultValue) {
        String val = getConfig(key);
        if (val == null) return defaultValue;
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBooleanConfig(String key, boolean defaultValue) {
        String val = getConfig(key);
        if (val == null) return defaultValue;
        return "true".equalsIgnoreCase(val) || "1".equals(val);
    }

    public void setConfig(String group, String key, String value, String valueType, String description) {
        try {
            jdbcTemplate.update("""
                INSERT INTO sys_unified_config (config_group, config_key, config_value, default_value, value_type, description, enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 1, NOW(), NOW())
                ON DUPLICATE KEY UPDATE config_value = ?, updated_at = NOW()
                """, group, key, value, value, valueType, description, value);
            configCache.put(key, value);
            log.info("配置项更新: {} = {}", key, value);
        } catch (Exception e) {
            log.error("更新配置项失败: {}", key, e);
        }
    }

    public void setConfig(String key, String value) {
        try {
            jdbcTemplate.update("""
                UPDATE sys_unified_config SET config_value = ?, updated_at = NOW() WHERE config_key = ?
                """, value, key);
            configCache.put(key, value);
        } catch (Exception e) {
            log.error("更新配置项失败: {}", key, e);
        }
    }

    private void maybeRefresh() {
        if (System.currentTimeMillis() - lastRefreshTime > refreshIntervalMs) {
            loadAllConfigs();
        }
    }

    public void forceRefresh() {
        loadAllConfigs();
        log.info("配置缓存已强制刷新");
    }

    public List<Map<String, Object>> getAllConfigs() {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT * FROM sys_unified_config ORDER BY config_group, config_key");
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<Map<String, Object>> getConfigsByGroup(String group) {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT * FROM sys_unified_config WHERE config_group = ? ORDER BY config_key", group);
        } catch (Exception e) {
            return List.of();
        }
    }

    public Map<String, String> getConfigCache() {
        maybeRefresh();
        return new HashMap<>(configCache);
    }
}
