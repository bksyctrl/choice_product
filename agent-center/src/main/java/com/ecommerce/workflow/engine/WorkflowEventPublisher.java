package com.ecommerce.workflow.engine;

import com.ecommerce.workflow.entity.WorkflowInstance;
import com.ecommerce.workflow.entity.WorkflowNodeExecution;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WorkflowEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEventPublisher.class);
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public WorkflowEventPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishWorkflowCreated(WorkflowInstance instance) {
        log.info("发布事件: 工作流创建 instanceId={}", instance.getId());
        publishEvent("workflow:created", instance);
    }

    public void publishWorkflowStarted(WorkflowInstance instance) {
        log.info("发布事件: 工作流启动 instanceId={}", instance.getId());
        publishEvent("workflow:started", instance);
    }

    public void publishNodeCompleted(WorkflowInstance instance, WorkflowNodeExecution nodeExec, Object result) {
        log.info("发布事件: 节点完成 nodeId={}, status={}", nodeExec.getNodeCode(), nodeExec.getStatus());
        
        Map<String, Object> eventData = Map.of(
                "instanceId", instance.getId(),
                "nodeExecId", nodeExec.getId(),
                "nodeCode", nodeExec.getNodeCode(),
                "status", nodeExec.getStatus(),
                "result", result
        );
        
        publishEvent("node:completed", eventData);
    }

    public void publishNodeFailed(WorkflowInstance instance, WorkflowNodeExecution nodeExec, Exception e) {
        log.error("发布事件: 节点失败 nodeId={}, error={}", nodeExec.getNodeCode(), e.getMessage());
        
        Map<String, Object> eventData = Map.of(
                "instanceId", instance.getId(),
                "nodeExecId", nodeExec.getId(),
                "nodeCode", nodeExec.getNodeCode(),
                "error", e.getMessage()
        );
        
        publishEvent("node:failed", eventData);
    }

    public void publishWorkflowCompleted(WorkflowInstance instance) {
        log.info("发布事件: 工作流完成 instanceId={}, status={}", instance.getId(), instance.getStatus());
        publishEvent("workflow:completed", instance);
    }

    public void publishWorkflowFailed(WorkflowInstance instance, String reason) {
        log.error("发布事件: 工作流失败 instanceId={}, reason={}", instance.getId(), reason);
        
        Map<String, Object> eventData = Map.of(
                "instanceId", instance.getId(),
                "status", instance.getStatus(),
                "reason", reason
        );
        
        publishEvent("workflow:failed", eventData);
    }

    public void publishWorkflowPaused(WorkflowInstance instance, String reason) {
        log.info("发布事件: 工作流暂停 instanceId={}", instance.getId());
        
        Map<String, Object> eventData = Map.of(
                "instanceId", instance.getId(),
                "reason", reason
        );
        
        publishEvent("workflow:paused", eventData);
    }

    public void publishWorkflowCancelled(WorkflowInstance instance) {
        log.info("发布事件: 工作流取消 instanceId={}", instance.getId());
        publishEvent("workflow:cancelled", instance);
    }

    private void publishEvent(String channel, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.convertAndSend(channel, json);
            log.debug("事件已发布到Redis: channel={}, data={}", channel, json.substring(0, Math.min(json.length(), 200)));
        } catch (Exception e) {
            log.error("发布事件失败 channel={}", channel, e);
        }
    }
}
