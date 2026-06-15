package com.ecommerce.workflow.agent.handler;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ecommerce.workflow.agent.AgentRequest;
import com.ecommerce.workflow.agent.AgentResponse;
import com.ecommerce.workflow.checkpoint.entity.FilesystemSnapshot;
import com.ecommerce.workflow.entity.ChatSession;
import com.ecommerce.workflow.service.checkpoint.AutoSnapshotService;
import com.ecommerce.workflow.service.memory.UnifiedMemoryService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class SnapshotIntentHandler extends AbstractIntentHandler {

    private static final Logger log = LoggerFactory.getLogger(SnapshotIntentHandler.class);

    @Autowired
    private AutoSnapshotService autoSnapshotService;

    @Autowired
    private UnifiedMemoryService unifiedMemoryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String getIntentCode() {
        return "snapshot_manage";
    }

    @Override
    public AgentResponse handle(AgentRequest request, ChatSession session, IntentResult intent) throws Exception {
        log.info("快照管理意图处理: message={}", request.getMessage());

        String action = resolveSnapshotAction(request.getMessage());

        return switch (action) {
            case "create" -> handleCreate(request, session, intent);
            case "list" -> handleList(request, session);
            case "rollback" -> handleRollback(request, session, intent);
            case "config" -> handleConfig(request, session, intent);
            default -> handleAutoAction(request, session, intent);
        };
    }

    private String resolveSnapshotAction(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("创建") || lower.contains("新建") || lower.contains("备份")) {
            return "create";
        }
        if (lower.contains("列表") || lower.contains("查看") || lower.contains("历史")) {
            return "list";
        }
        if (lower.contains("回滚") || lower.contains("恢复") || lower.contains("还原")) {
            return "rollback";
        }
        if (lower.contains("配置") || lower.contains("设置") || lower.contains("自动")) {
            return "config";
        }
        return "auto";
    }

    private AgentResponse handleCreate(AgentRequest request, ChatSession session, IntentResult intent) {
        try {
            Map<String, Object> params = intent.getEntities() != null ? intent.getEntities() : new HashMap<>();
            String description = (String) params.getOrDefault("description", "用户手动创建快照");

            FilesystemSnapshot snapshot = autoSnapshotService.autoSnapshot(
                    "manual", description, Map.of("userId", request.getUserId()));

            String reply;
            if (snapshot != null) {
                reply = String.format("快照创建成功!\n- 快照ID: %s\n- 描述: %s\n- 时间: %s\n",
                        snapshot.getSnapshotId(), snapshot.getDescription(), snapshot.getCreatedAt());
            } else {
                reply = "快照创建失败，请稍后重试";
            }

            sessionService.saveMessage(session.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        } catch (Exception e) {
            log.error("创建快照失败", e);
            String reply = "创建快照失败: " + e.getMessage();
            sessionService.saveMessage(session.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        }
    }

    private AgentResponse handleList(AgentRequest request, ChatSession session) {
        try {
            List<Map<String, Object>> config = autoSnapshotService.getAutoSnapshotConfig();

            StringBuilder reply = new StringBuilder("快照管理状态:\n\n");
            reply.append("自动快照: ").append(autoSnapshotService.isEnabled() ? "已启用" : "已禁用").append("\n\n");
            reply.append("自动快照配置:\n");
            for (Map<String, Object> c : config) {
                reply.append(String.format("- %s: %s (自动: %s)\n",
                        c.get("action_type"), c.get("action_name"),
                        Integer.parseInt(c.get("auto_snapshot").toString()) == 1 ? "是" : "否"));
            }

            String replyStr = reply.toString();
            sessionService.saveMessage(session.getSessionId(), "assistant", replyStr);
            return AgentResponse.success(replyStr);
        } catch (Exception e) {
            log.error("获取快照配置失败", e);
            String reply = "获取快照配置失败: " + e.getMessage();
            sessionService.saveMessage(session.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        }
    }

    private AgentResponse handleRollback(AgentRequest request, ChatSession session, IntentResult intent) {
        try {
            Map<String, Object> params = intent.getEntities() != null ? intent.getEntities() : new HashMap<>();
            String snapshotId = (String) params.get("snapshotId");

            if (snapshotId == null) {
                String reply = "请指定要回滚的快照ID";
                sessionService.saveMessage(session.getSessionId(), "assistant", reply);
                return AgentResponse.success(reply);
            }

            String reply = "快照回滚功能需要确认操作。快照ID: " + snapshotId;
            sessionService.saveMessage(session.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        } catch (Exception e) {
            log.error("回滚快照失败", e);
            String reply = "回滚快照失败: " + e.getMessage();
            sessionService.saveMessage(session.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        }
    }

    private AgentResponse handleConfig(AgentRequest request, ChatSession session, IntentResult intent) {
        try {
            Map<String, Object> params = intent.getEntities() != null ? intent.getEntities() : new HashMap<>();
            String action = (String) params.getOrDefault("configAction", "");

            if (action.equals("enable") || request.getMessage().toLowerCase().contains("启用")) {
                autoSnapshotService.setEnabled(true);
                String reply = "自动快照已启用！系统将在关键操作前自动创建快照";
                sessionService.saveMessage(session.getSessionId(), "assistant", reply);
                return AgentResponse.success(reply);
            }

            if (action.equals("disable") || request.getMessage().toLowerCase().contains("禁用")) {
                autoSnapshotService.setEnabled(false);
                String reply = "自动快照已禁用";
                sessionService.saveMessage(session.getSessionId(), "assistant", reply);
                return AgentResponse.success(reply);
            }

            String actionType = (String) params.get("actionType");
            if (actionType != null) {
                if (request.getMessage().toLowerCase().contains("添加") || request.getMessage().toLowerCase().contains("注册")) {
                    autoSnapshotService.registerAutoAction(actionType, actionType + "自动快照");
                    String reply = "已注册自动快照动作: " + actionType;
                    sessionService.saveMessage(session.getSessionId(), "assistant", reply);
                    return AgentResponse.success(reply);
                }
                if (request.getMessage().toLowerCase().contains("移除") || request.getMessage().toLowerCase().contains("取消")) {
                    autoSnapshotService.unregisterAutoAction(actionType);
                    String reply = "已取消自动快照动作: " + actionType;
                    sessionService.saveMessage(session.getSessionId(), "assistant", reply);
                    return AgentResponse.success(reply);
                }
            }

            return handleList(request, session);
        } catch (Exception e) {
            log.error("配置快照失败", e);
            String reply = "配置快照失败: " + e.getMessage();
            sessionService.saveMessage(session.getSessionId(), "assistant", reply);
            return AgentResponse.success(reply);
        }
    }

    private AgentResponse handleAutoAction(AgentRequest request, ChatSession session, IntentResult intent) {
        return handleList(request, session);
    }
}
