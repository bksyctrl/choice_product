package com.ecommerce.workflow.service.checkpoint;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.ecommerce.workflow.checkpoint.entity.FilesystemSnapshot;
import com.ecommerce.workflow.checkpoint.mapper.FilesystemSnapshotMapper;
import com.ecommerce.workflow.service.config.SysConfigService;
import com.ecommerce.workflow.service.memory.UnifiedMemoryService;

import jakarta.annotation.PostConstruct;

@Service
public class AutoSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(AutoSnapshotService.class);

    @Autowired
    private FilesystemSnapshotMapper snapshotMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UnifiedMemoryService unifiedMemoryService;

    @Autowired
    private SysConfigService sysConfigService;

    private final Set<String> autoSnapshotActions = ConcurrentHashMap.newKeySet();
    private int maxAutoSnapshots = 50;
    private boolean enabled = true;

    @PostConstruct
    public void init() {
        initTables();
        loadConfig();
        registerDefaultAutoActions();
        log.info("自动快照服务初始化完成: enabled={}, maxAutoSnapshots={}", enabled, maxAutoSnapshots);
    }

    private void initTables() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_auto_snapshot_config (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    action_type VARCHAR(100) NOT NULL,
                    action_name VARCHAR(200),
                    auto_snapshot INT DEFAULT 1,
                    max_snapshots INT DEFAULT 50,
                    retention_days INT DEFAULT 7,
                    enabled INT DEFAULT 1,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE INDEX uk_action_type (action_type)
                )
                """);
        } catch (Exception e) {
            log.debug("sys_auto_snapshot_config table may already exist");
        }

        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_auto_snapshot_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    snapshot_id VARCHAR(64),
                    trigger_action VARCHAR(100),
                    trigger_description TEXT,
                    auto_created INT DEFAULT 1,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_trigger (trigger_action),
                    INDEX idx_created (created_at)
                )
                """);
        } catch (Exception e) {
            log.debug("sys_auto_snapshot_log table may already exist");
        }
    }

    private void loadConfig() {
        try {
            enabled = sysConfigService.getBooleanConfig("auto_snapshot_enabled", true);
            maxAutoSnapshots = sysConfigService.getIntConfig("max_auto_snapshots", 50);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM sys_auto_snapshot_config WHERE enabled = 1");
            autoSnapshotActions.clear();
            for (Map<String, Object> row : rows) {
                String actionType = (String) row.get("action_type");
                if (row.get("auto_snapshot") != null && ((Number) row.get("auto_snapshot")).intValue() == 1) {
                    autoSnapshotActions.add(actionType);
                }
            }
        } catch (Exception e) {
            log.debug("加载自动快照配置失败，使用默认值", e);
        }
    }

    private void registerDefaultAutoActions() {
        String[] defaultActions = {
                "video_generation", "workflow_execution", "skill_execution",
                "mcp_register", "agent_delegate", "data_import",
                "batch_operation", "config_change"
        };

        for (String action : defaultActions) {
            try {
                jdbcTemplate.update("""
                    INSERT INTO sys_auto_snapshot_config (action_type, action_name, auto_snapshot, max_snapshots, retention_days, enabled)
                    VALUES (?, ?, 1, 50, 7, 1)
                    ON DUPLICATE KEY UPDATE action_name = VALUES(action_name)
                    """, action, action + "自动快照");
                autoSnapshotActions.add(action);
            } catch (Exception e) {
                log.debug("注册自动快照动作失败: {}", action, e);
            }
        }
    }

    public boolean shouldAutoSnapshot(String actionType) {
        if (!enabled) return false;
        return autoSnapshotActions.contains(actionType);
    }

    public FilesystemSnapshot autoSnapshot(String actionType, String description, Map<String, Object> context) {
        if (!shouldAutoSnapshot(actionType)) {
            log.debug("动作{}未配置自动快照", actionType);
            return null;
        }

        try {
            cleanupOldSnapshots();

            FilesystemSnapshot snapshot = new FilesystemSnapshot();
            snapshot.setSnapshotId("AUTO_" + actionType + "_" + System.currentTimeMillis());
            snapshot.setDescription("[自动快照] " + (description != null ? description : actionType));
            snapshot.setStatus("SUCCESS");
            snapshot.setUserId(context != null && context.containsKey("userId")
                    ? Long.valueOf(context.get("userId").toString()) : 0L);
            snapshot.setFileCount(0);
            snapshot.setTotalSize(0L);
            snapshot.setCreatedAt(LocalDateTime.now());

            snapshotMapper.insert(snapshot);

            logAutoSnapshot(snapshot.getSnapshotId(), actionType, description);

            log.info("自动快照创建成功: action={}, snapshotId={}", actionType, snapshot.getSnapshotId());

            unifiedMemoryService.recordBusinessAction("snapshot_manage", "auto_create",
                    Map.of("actionType", actionType, "description", description),
                    Map.of("snapshotId", snapshot.getSnapshotId()),
                    true, null);

            return snapshot;
        } catch (Exception e) {
            log.error("自动快照创建失败: action={}", actionType, e);
            return null;
        }
    }

    public FilesystemSnapshot autoSnapshotBeforeAction(String actionType, String description,
                                                       Map<String, Object> inputParams) {
        return autoSnapshot(actionType, "操作前快照: " + description, inputParams);
    }

    private void logAutoSnapshot(String snapshotId, String triggerAction, String description) {
        try {
            jdbcTemplate.update("""
                INSERT INTO sys_auto_snapshot_log (snapshot_id, trigger_action, trigger_description, auto_created, created_at)
                VALUES (?, ?, ?, 1, NOW())
                """, snapshotId, triggerAction, description);
        } catch (Exception e) {
            log.debug("记录自动快照日志失败", e);
        }
    }

    private void cleanupOldSnapshots() {
        try {
            int count = 0;
            try {
                count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM filesystem_snapshot WHERE description LIKE '[自动快照]%'",
                        Integer.class);
            } catch (Exception e) {
                return;
            }

            if (count != 0 && count > maxAutoSnapshots) {
                int toDelete = count - maxAutoSnapshots;
                jdbcTemplate.update("""
                    DELETE FROM filesystem_snapshot WHERE description LIKE '[自动快照]%'
                    ORDER BY created_at ASC LIMIT ?
                    """, toDelete);
                log.info("清理过期自动快照: {} 条", toDelete);
            }
        } catch (Exception e) {
            log.debug("清理自动快照失败", e);
        }
    }

    public void registerAutoAction(String actionType, String actionName) {
        try {
            jdbcTemplate.update("""
                INSERT INTO sys_auto_snapshot_config (action_type, action_name, auto_snapshot, max_snapshots, retention_days, enabled)
                VALUES (?, ?, 1, 50, 7, 1)
                ON DUPLICATE KEY UPDATE action_name = ?, auto_snapshot = 1, enabled = 1
                """, actionType, actionName, actionName);
            autoSnapshotActions.add(actionType);
            log.info("注册自动快照动作: {}", actionType);
        } catch (Exception e) {
            log.error("注册自动快照动作失败: {}", actionType, e);
        }
    }

    public void unregisterAutoAction(String actionType) {
        try {
            jdbcTemplate.update("""
                UPDATE sys_auto_snapshot_config SET auto_snapshot = 0 WHERE action_type = ?
                """, actionType);
            autoSnapshotActions.remove(actionType);
            log.info("取消自动快照动作: {}", actionType);
        } catch (Exception e) {
            log.error("取消自动快照动作失败: {}", actionType, e);
        }
    }

    public List<Map<String, Object>> getAutoSnapshotConfig() {
        try {
            return jdbcTemplate.queryForList("SELECT * FROM sys_auto_snapshot_config ORDER BY id");
        } catch (Exception e) {
            return List.of();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
