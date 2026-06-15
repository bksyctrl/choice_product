package com.ecommerce.workflow.service.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RenderTaskQueueService {

    private static final Logger log = LoggerFactory.getLogger(RenderTaskQueueService.class);
    public static final String QUEUE_RENDER_TASK = "workflow.render.task.queue";
    public static final String EXCHANGE_RENDER_TASK = "workflow.render.task.exchange";
    public static final String ROUTING_KEY_RENDER = "render.task";

    public static final String QUEUE_RENDER_RESULT = "workflow.render.result.queue";
    public static final String ROUTING_KEY_RESULT = "render.result";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public RenderTaskQueueService(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Bean
    public Queue renderTaskQueue() {
        return new Queue(QUEUE_RENDER_TASK, true, false, false);
    }

    @Bean
    public DirectExchange renderTaskExchange() {
        return new DirectExchange(EXCHANGE_RENDER_TASK, true, false);
    }

    @Bean
    public Binding renderTaskBinding(Queue renderTaskQueue, DirectExchange renderTaskExchange) {
        return BindingBuilder.bind(renderTaskQueue).to(renderTaskExchange).with(ROUTING_KEY_RENDER);
    }

    @Bean
    public Queue renderResultQueue() {
        return new Queue(QUEUE_RENDER_RESULT, true, false, false);
    }

    @Bean
    public Binding renderResultBinding(Queue renderResultQueue, DirectExchange renderTaskExchange) {
        return BindingBuilder.bind(renderResultQueue).to(renderTaskExchange).with(ROUTING_KEY_RESULT);
    }

    public void sendRenderTask(Map<String, Object> taskData) {
        try {
            String message = objectMapper.writeValueAsString(taskData);

            rabbitTemplate.convertAndSend(EXCHANGE_RENDER_TASK, ROUTING_KEY_RENDER, message);

            log.info("渲染任务发送成功: taskId={}, type={}",
                    taskData.get("taskId"),
                    taskData.get("taskType"));
        } catch (Exception e) {
            log.error("渲染任务发送失败", e);
            throw new RuntimeException("渲染任务发送失败", e);
        }
    }

    public void sendRenderResult(Map<String, Object> result) {
        try {
            String message = objectMapper.writeValueAsString(result);

            rabbitTemplate.convertAndSend(EXCHANGE_RENDER_TASK, ROUTING_KEY_RESULT, message);

            log.info("渲染结果发送成功: taskId={}, status={}",
                    result.get("taskId"),
                    result.get("status"));
        } catch (Exception e) {
            log.error("渲染结果发送失败", e);
        }
    }

    public Map<String, Object> createVideoRenderTask(Long videoTaskId, String scriptContent,
                                                       List<String> images, String prompt) {
        Map<String, Object> task = new java.util.HashMap<>();
        task.put("taskId", "RENDER_" + System.currentTimeMillis());
        task.put("videoTaskId", videoTaskId);
        task.put("taskType", "VIDEO_GENERATION");
        task.put("scriptContent", scriptContent);
        task.put("images", images);
        task.put("prompt", prompt);
        task.put("createdAt", java.time.LocalDateTime.now().toString());
        task.put("status", "PENDING");

        sendRenderTask(task);

        return task;
    }

    public Map<String, Object> createImageProcessTask(Long nodeId, String imageUrl, String operation) {
        Map<String, Object> task = new java.util.HashMap<>();
        task.put("taskId", "IMG_PROC_" + System.currentTimeMillis());
        task.put("nodeId", nodeId);
        task.put("taskType", "IMAGE_PROCESS");
        task.put("imageUrl", imageUrl);
        task.put("operation", operation);
        task.put("createdAt", java.time.LocalDateTime.now().toString());
        task.put("status", "PENDING");

        sendRenderTask(task);

        return task;
    }

    public void onRenderResultReceived(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(message, Map.class);

            log.info("渲染结果接收成功: taskId={}, status={}",
                    result.get("taskId"),
                    result.get("status"));

            handleRenderResult(result);
        } catch (Exception e) {
            log.error("处理渲染结果失败", e);
        }
    }

    private void handleRenderResult(Map<String, Object> result) {
        String status = (String) result.getOrDefault("status", "UNKNOWN");

        switch (status.toUpperCase()) {
            case "SUCCESS":
                log.info("渲染任务执行成功: {}", result.get("taskId"));
                break;
            case "FAILED":
                log.error("渲染任务执行失败: {}, error={}", result.get("taskId"), result.get("error"));
                break;
            case "PROGRESS":
                log.debug("渲染任务进度更新: {}, progress={}%", result.get("taskId"), result.get("progress"));
                break;
            default:
                log.warn("未知状态: {}", status);
        }
    }

    public String getQueueStatus() {
        try {
            Message props = rabbitTemplate.sendAndReceive(QUEUE_RENDER_TASK, "", MessageBuilder.withBody("STATUS_CHECK".getBytes()).build());

            if (props != null) {
                return "Queue is responsive";
            }
            return "Queue exists but no response";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
