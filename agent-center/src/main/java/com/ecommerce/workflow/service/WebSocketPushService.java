package com.ecommerce.workflow.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class WebSocketPushService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void pushWorkflowStatus(Long instanceId, String status, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "workflow_status");
        payload.put("instanceId", instanceId);
        payload.put("status", status);
        payload.put("message", message);
        payload.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        messagingTemplate.convertAndSend("/topic/workflow/" + instanceId, payload);
    }

    public void pushNodeExecution(Long instanceId, String nodeCode, String nodeStatus, Map<String, Object> output) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "node_execution");
        payload.put("instanceId", instanceId);
        payload.put("nodeCode", nodeCode);
        payload.put("nodeStatus", nodeStatus);
        payload.put("output", output);
        payload.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        messagingTemplate.convertAndSend("/topic/workflow/" + instanceId, payload);
    }

    public void pushAgentMessage(String sessionId, String role, String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "agent_message");
        payload.put("sessionId", sessionId);
        payload.put("role", role);
        payload.put("content", content);
        payload.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        messagingTemplate.convertAndSend("/topic/agent/" + sessionId, payload);
    }

    public void pushSystemNotification(String level, String title, String message) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "system_notification");
        notification.put("level", level);
        notification.put("title", title);
        notification.put("message", message);
        notification.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        messagingTemplate.convertAndSend("/topic/notifications", notification);
    }

    public void pushToUser(Long userId, String destination, Object data) {
        messagingTemplate.convertAndSend("/queue/user/" + userId + "/" + destination, data);
    }

    public void pushMessage(String userId, Object data) {
        messagingTemplate.convertAndSend("/queue/user/" + userId + "/notifications", data);
    }
}
