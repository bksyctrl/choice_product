package com.ecommerce.workflow.service.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class PythonWorkerService {

    private static final Logger log = LoggerFactory.getLogger(PythonWorkerService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${python.worker.url:http://localhost:5000/api}")
    private String pythonWorkerUrl;

    public PythonWorkerService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    public Map<String, Object> executeVideoRender(Map<String, Object> renderParams) {
        try {
            log.info("Python Worker开始视频渲染任务");

            Map<String, Object> request = new HashMap<>();
            request.put("action", "render_video");
            request.put("params", renderParams);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    pythonWorkerUrl + "/execute",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);

            log.info("视频渲染任务创建成功: workerTaskId={}", result.get("taskId"));
            return result;
        } catch (Exception e) {
            log.error("Python Worker执行失败", e);
            return createErrorResponse(e.getMessage());
        }
    }

    public Map<String, Object> executeImageProcess(String imageUrl, String operation) {
        try {
            log.info("Python Worker开始图像处理任务: operation={}", operation);

            Map<String, Object> request = new HashMap<>();
            request.put("action", "process_image");
            request.put("imageUrl", imageUrl);
            request.put("operation", operation);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    pythonWorkerUrl + "/execute",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);

            return result;
        } catch (Exception e) {
            log.error("图像处理执行失败", e);
            return createErrorResponse(e.getMessage());
        }
    }

    public Map<String, Object> executeAudioProcess(String audioUrl, String operation) {
        try {
            log.info("Python Worker开始音频处理任务: operation={}", operation);

            Map<String, Object> request = new HashMap<>();
            request.put("action", "process_audio");
            request.put("audioUrl", audioUrl);
            request.put("operation", operation);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    pythonWorkerUrl + "/execute",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);

            return result;
        } catch (Exception e) {
            log.error("音频处理执行失败", e);
            return createErrorResponse(e.getMessage());
        }
    }

    public Map<String, Object> checkWorkerStatus() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    pythonWorkerUrl + "/health",
                    String.class
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> health = objectMapper.readValue(response.getBody(), Map.class);

            return Map.of(
                    "status", "UP",
                    "workerInfo", health,
                    "connected", true
            );
        } catch (Exception e) {
            log.warn("Python Worker健康检查失败: {}", e.getMessage());
            return Map.of(
                    "status", "DOWN",
                    "error", e.getMessage(),
                    "connected", false
            );
        }
    }

    public Map<String, Object> getWorkerTaskStatus(String workerTaskId) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    pythonWorkerUrl + "/task/" + workerTaskId,
                    String.class
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> taskStatus = objectMapper.readValue(response.getBody(), Map.class);

            return taskStatus;
        } catch (Exception e) {
            log.error("获取Worker任务状态失败: taskId={}", workerTaskId, e);
            return createErrorResponse(e.getMessage());
        }
    }

    public void reportProgressToWorker(String taskId, double progress, String message) {
        try {
            Map<String, Object> progressData = new HashMap<>();
            progressData.put("taskId", taskId);
            progressData.put("progress", progress);
            progressData.put("message", message);
            progressData.put("timestamp", System.currentTimeMillis());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(progressData, headers);

            restTemplate.postForEntity(pythonWorkerUrl + "/progress", entity, Void.class);

            log.debug("进度报告成功: taskId={}, progress={}%", taskId, progress * 100);
        } catch (Exception e) {
            log.warn("进度报告失败: {}", e.getMessage());
        }
    }

    private Map<String, Object> createErrorResponse(String errorMessage) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", errorMessage);
        error.put("timestamp", System.currentTimeMillis());
        return error;
    }

    public void setPythonWorkerUrl(String url) {
        this.pythonWorkerUrl = url;
        log.info("Python Worker URL updated to: {}", url);
    }
}
