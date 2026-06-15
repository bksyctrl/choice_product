package com.ecommerce.workflow.service.ai;

import com.ecommerce.workflow.entity.AiProviderConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通用 API 适配器服务
 * 支持通过配置模板来适配不同的 API 格式
 */
@Service
public class UniversalApiAdapterService {

    private static final Logger log = LoggerFactory.getLogger(UniversalApiAdapterService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;

    // 变量匹配模式: {{variableName}}
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    public UniversalApiAdapterService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 判断提供商是否使用模板配置
     */
    public boolean isTemplateBased(AiProviderConfig provider) {
        return provider.getRequestConfig() != null && !provider.getRequestConfig().isEmpty();
    }

    /**
     * 使用模板创建视频
     */
    public Map<String, Object> createVideoWithTemplate(AiProviderConfig provider,
            String prompt,
            String model,
            List<String> images,
            String aspectRatio) throws Exception {
        // 准备变量
        Map<String, String> variables = new HashMap<>();
        variables.put("prompt", prompt != null ? prompt : "");
        variables.put("model", model != null ? model : provider.getDefaultModel());
        variables.put("aspectRatio", aspectRatio != null ? aspectRatio : "9:16");
        variables.put("resolution", "1080p");
        variables.put("apiKey", provider.getApiKey());

        // 处理图片数组
        if (images != null && !images.isEmpty()) {
            variables.put("images", objectMapper.writeValueAsString(images));
        } else {
            variables.put("images", "[]");
        }

        // 发送模板请求
        ResponseEntity<String> response = executeTemplateRequest(provider, variables, "视频生成");

        // 解析响应
        return parseResponse(response.getBody(), provider.getResponseConfig());
    }

    /**
     * 使用模板创建图片
     */
    public Map<String, Object> createImageWithTemplate(AiProviderConfig provider,
            String prompt,
            String model,
            String referenceImageUrl,
            String size) throws Exception {
        // 准备变量
        Map<String, String> variables = new HashMap<>();
        variables.put("prompt", prompt != null ? prompt : "");
        variables.put("model", model != null ? model : provider.getDefaultModel());
        variables.put("resolution", size != null ? size : "1024x1024");
        variables.put("apiKey", provider.getApiKey());

        // 处理参考图片 - Banana API使用urls数组
        List<String> images = new ArrayList<>();
        boolean referenceImageIncluded = false;
        int referenceImageLength = 0;

        if (referenceImageUrl != null && !referenceImageUrl.isEmpty()) {
            referenceImageIncluded = true;
            // 如果是本地URL，需要转换为base64
            String processedImageUrl = referenceImageUrl;
            if (referenceImageUrl.startsWith("http://localhost") || referenceImageUrl.startsWith("http://127.0.0.1")) {
                log.info("【参考图传输】检测到本地URL，开始转换为base64: {}", referenceImageUrl);
                String base64Image = convertLocalImageToBase64(referenceImageUrl);
                if (base64Image != null) {
                    processedImageUrl = base64Image;
                    referenceImageLength = base64Image.length();
                    log.info("【参考图传输】本地URL已转换为base64: 原始URL={}, base64长度={}",
                            referenceImageUrl, base64Image.length());
                } else {
                    log.warn("【参考图传输】本地图片转换为base64失败: {}", referenceImageUrl);
                    referenceImageIncluded = false;
                }
            } else {
                referenceImageLength = referenceImageUrl.length();
                log.info("【参考图传输】使用外部URL: length={}", referenceImageUrl.length());
            }

            if (referenceImageIncluded) {
                images.add(processedImageUrl);
                log.info("【参考图传输】图片生成包含参考图片: 原始URL长度={}, 处理后长度={}, 格式={}",
                        referenceImageUrl.length(), processedImageUrl.length(),
                        processedImageUrl.startsWith("data:image") ? "base64" : "url");
            }
        } else {
            log.warn("【参考图传输】参考图片URL为空或null，本次生成不包含参考图！");
        }

        String imagesJson = objectMapper.writeValueAsString(images);
        variables.put("images", imagesJson);

        // 记录完整的请求参数（用于调试）
        log.info("【参考图传输】场景图生成参数: provider={}, model={}, referenceImageIncluded={}, " +
                "referenceImageLength={}, imagesJsonLength={}, promptLength={}",
                provider.getProviderName(), model, referenceImageIncluded,
                referenceImageLength, imagesJson.length(),
                prompt != null ? prompt.length() : 0);

        // 发送模板请求
        ResponseEntity<String> response = executeTemplateRequest(provider, variables, "图片生成");

        // 解析响应 - 图片生成返回的是同步结果
        return parseImageResponse(response.getBody(), provider.getResponseConfig());
    }

    /**
     * 解析图片生成响应
     */
    private Map<String, Object> parseImageResponse(String responseBody, String responseConfigJson) throws Exception {
        Map<String, Object> result = new HashMap<>();

        if (responseBody == null || responseBody.isEmpty()) {
            result.put("status", "failed");
            result.put("error", "空响应");
            return result;
        }

        // 解析响应配置
        Map<String, Object> responseConfig = parseJson(responseConfigJson);

        try {
            // 使用 JsonPath 提取字段
            String taskIdPath = (String) responseConfig.get("taskIdPath");
            String imageUrlPath = (String) responseConfig.get("imageUrlPath");

            if (taskIdPath != null) {
                try {
                    String taskId = JsonPath.read(responseBody, taskIdPath);
                    result.put("id", taskId);
                } catch (PathNotFoundException e) {
                    log.debug("未找到任务ID路径: {}", taskIdPath);
                }
            }

            if (imageUrlPath != null) {
                try {
                    String imageUrl = JsonPath.read(responseBody, imageUrlPath);
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        result.put("url", imageUrl);
                    }
                } catch (PathNotFoundException e) {
                    log.debug("未找到图片URL路径: {}", imageUrlPath);
                }
            }

            // 如果没有配置路径，尝试常见格式
            if (!result.containsKey("url")) {
                JsonNode json = objectMapper.readTree(responseBody);
                if (json.has("data") && json.path("data").has("url")) {
                    result.put("url", json.path("data").path("url").asText());
                } else if (json.has("data") && json.path("data").isArray() && json.path("data").size() > 0) {
                    JsonNode firstImage = json.path("data").get(0);
                    if (firstImage.has("url")) {
                        result.put("url", firstImage.path("url").asText());
                    }
                }
            }

            result.put("status", result.containsKey("url") ? "completed" : "pending");

        } catch (Exception e) {
            log.error("解析图片响应失败: {}", responseBody, e);
            result.put("status", "failed");
            result.put("error", "解析失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 执行模板HTTP请求（公共方法）
     */
    private ResponseEntity<String> executeTemplateRequest(AiProviderConfig provider,
            Map<String, String> variables,
            String operationName) throws Exception {
        // 解析配置
        Map<String, Object> requestConfig = parseJson(provider.getRequestConfig());
        Map<String, Object> authConfig = parseJson(provider.getAuthConfig());

        // 构建请求URL
        String method = (String) requestConfig.getOrDefault("method", "POST");
        String endpoint = renderTemplate((String) requestConfig.get("endpoint"), variables);
        String url = provider.getBaseUrl() + endpoint;

        // 构建 headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 应用认证配置
        if (authConfig != null) {
            applyAuthConfig(headers, authConfig, variables);
        }

        // 应用自定义 headers
        Map<String, String> customHeaders = (Map<String, String>) requestConfig.get("headers");
        if (customHeaders != null) {
            customHeaders.forEach((key, value) -> {
                headers.set(key, renderTemplate(value, variables));
            });
        }

        // 构建请求体
        Map<String, Object> bodyTemplate = (Map<String, Object>) requestConfig.get("bodyTemplate");
        Map<String, Object> requestBody = renderBodyTemplate(bodyTemplate, variables);

        log.info("模板API{}请求: provider={}, url={}, method={}", operationName, provider.getProviderName(), url, method);
        log.debug("请求体: {}", objectMapper.writeValueAsString(requestBody));

        // 发送请求
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response;

        switch (method.toUpperCase()) {
            case "GET":
                response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                break;
            case "POST":
                response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                break;
            case "PUT":
                response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
                break;
            default:
                throw new IllegalArgumentException("不支持的HTTP方法: " + method);
        }

        log.info("模板API{}响应: status={}, body={}", operationName, response.getStatusCode(),
                response.getBody() != null && response.getBody().length() > 200
                        ? response.getBody().substring(0, 200) + "..."
                        : response.getBody());

        return response;
    }

    /**
     * 使用模板查询视频状态
     */
    public Map<String, Object> checkVideoStatusWithTemplate(AiProviderConfig provider, String taskId) throws Exception {
        Map<String, Object> requestConfig = parseJson(provider.getRequestConfig());
        Map<String, Object> authConfig = parseJson(provider.getAuthConfig());

        // 准备变量
        Map<String, String> variables = new HashMap<>();
        variables.put("taskId", taskId);
        variables.put("apiKey", provider.getApiKey());

        // 构建查询 URL
        String videoQueryEndpoint = (String) requestConfig.get("videoQueryEndpoint");
        if (videoQueryEndpoint == null) {
            videoQueryEndpoint = "/api/async/detail?key={{apiKey}}&id={{taskId}}";
        }

        String endpoint = renderTemplate(videoQueryEndpoint, variables);
        String url = provider.getBaseUrl() + endpoint;

        // 构建 headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (authConfig != null) {
            applyAuthConfig(headers, authConfig, variables);
        }

        // 应用自定义 headers
        Map<String, String> customHeaders = (Map<String, String>) requestConfig.get("headers");
        if (customHeaders != null) {
            customHeaders.forEach((key, value) -> {
                headers.set(key, renderTemplate(value, variables));
            });
        }

        // 获取查询方法（默认GET）
        String queryMethod = (String) requestConfig.getOrDefault("queryMethod", "GET");

        log.info("模板API查询状态: provider={}, url={}, method={}", provider.getProviderName(), url, queryMethod);

        // 根据配置的方法发送请求
        ResponseEntity<String> response;
        if ("POST".equalsIgnoreCase(queryMethod)) {
            // POST请求需要构建请求体
            Map<String, Object> queryBodyTemplate = (Map<String, Object>) requestConfig.get("queryBodyTemplate");
            Map<String, Object> requestBody;
            if (queryBodyTemplate != null) {
                requestBody = renderBodyTemplate(queryBodyTemplate, variables);
            } else {
                // 默认请求体
                requestBody = new HashMap<>();
                requestBody.put("id", taskId);
                requestBody.put("key", provider.getApiKey());
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        } else {
            // GET请求
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        }

        log.debug("状态查询响应: {}", response.getBody());

        // 解析响应
        return parseResponse(response.getBody(), provider.getResponseConfig());
    }

    /**
     * 应用认证配置
     */
    private void applyAuthConfig(HttpHeaders headers, Map<String, Object> authConfig, Map<String, String> variables) {
        String type = (String) authConfig.getOrDefault("type", "bearer");
        String headerName = (String) authConfig.getOrDefault("headerName", "Authorization");
        String headerValue = renderTemplate((String) authConfig.get("headerValue"), variables);

        switch (type.toLowerCase()) {
            case "bearer":
                headers.setBearerAuth(headerValue.replace("Bearer ", ""));
                break;
            case "plain":
                headers.set(headerName, headerValue);
                break;
            case "x-api-key":
                headers.set("X-API-Key", headerValue);
                break;
            case "api-key":
                headers.set("Api-Key", headerValue);
                break;
            default:
                headers.set(headerName, headerValue);
        }
    }

    /**
     * 渲染模板字符串
     */
    private String renderTemplate(String template, Map<String, String> variables) {
        if (template == null)
            return "";

        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = variables.getOrDefault(varName, "");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 渲染请求体模板
     */
    private Map<String, Object> renderBodyTemplate(Map<String, Object> template, Map<String, String> variables) {
        if (template == null)
            return new HashMap<>();

        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : template.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String) {
                String strValue = (String) value;
                // 处理特殊变量 {{images}} - 需要转为数组
                if ("{{images}}".equals(strValue)) {
                    String imagesJson = variables.get("images");
                    try {
                        result.put(key, objectMapper.readValue(imagesJson, List.class));
                    } catch (Exception e) {
                        result.put(key, new ArrayList<>());
                    }
                } else {
                    result.put(key, renderTemplate(strValue, variables));
                }
            } else if (value instanceof Map) {
                result.put(key, renderBodyTemplate((Map<String, Object>) value, variables));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * 解析 JSON 字符串
     */
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isEmpty())
            return new HashMap<>();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.error("解析JSON失败: {}", json, e);
            return new HashMap<>();
        }
    }

    /**
     * 解析 API 响应
     */
    private Map<String, Object> parseResponse(String responseBody, String responseConfigJson) throws Exception {
        Map<String, Object> result = new HashMap<>();

        if (responseBody == null || responseBody.isEmpty()) {
            result.put("status", "failed");
            result.put("error", "空响应");
            return result;
        }

        // 解析响应配置
        Map<String, Object> responseConfig = parseJson(responseConfigJson);
        if (responseConfig.isEmpty()) {
            // 没有配置，返回原始响应
            result.put("rawResponse", responseBody);
            return result;
        }

        try {
            // 使用 JsonPath 提取字段
            String taskIdPath = (String) responseConfig.get("taskIdPath");
            String statusPath = (String) responseConfig.get("statusPath");
            String videoUrlPath = (String) responseConfig.get("videoUrlPath");

            if (taskIdPath != null) {
                try {
                    String taskId = JsonPath.read(responseBody, taskIdPath);
                    result.put("id", taskId);
                } catch (PathNotFoundException e) {
                    log.debug("未找到任务ID路径: {}", taskIdPath);
                }
            }

            if (statusPath != null) {
                try {
                    String status = JsonPath.read(responseBody, statusPath);
                    // 映射状态
                    String mappedStatus = mapStatus(status, responseConfig);
                    result.put("status", mappedStatus);
                } catch (PathNotFoundException e) {
                    log.debug("未找到状态路径: {}", statusPath);
                    result.put("status", "pending");
                }
            }

            if (videoUrlPath != null) {
                try {
                    String videoUrl = JsonPath.read(responseBody, videoUrlPath);
                    if (videoUrl != null && !videoUrl.isEmpty()) {
                        result.put("video_url", videoUrl);
                    }
                } catch (PathNotFoundException e) {
                    log.debug("未找到视频URL路径: {}", videoUrlPath);
                }
            }

            result.put("status_update_time", System.currentTimeMillis() / 1000);

        } catch (Exception e) {
            log.error("解析响应失败: {}", responseBody, e);
            result.put("status", "failed");
            result.put("error", "解析失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 映射状态码
     */
    private String mapStatus(String originalStatus, Map<String, Object> responseConfig) {
        if (originalStatus == null)
            return "pending";

        Map<String, List<String>> statusMapping = (Map<String, List<String>>) responseConfig.get("statusMapping");
        if (statusMapping == null)
            return originalStatus.toLowerCase();

        String lowerStatus = originalStatus.toLowerCase();

        for (Map.Entry<String, List<String>> entry : statusMapping.entrySet()) {
            String mappedStatus = entry.getKey();
            List<String> possibleValues = entry.getValue();

            for (String value : possibleValues) {
                if (value.toLowerCase().equals(lowerStatus)) {
                    return mappedStatus;
                }
            }
        }

        return lowerStatus;
    }

    /**
     * 将本地图片URL转换为base64格式
     * 用于外部AI服务无法访问本地localhost地址的情况
     */
    private String convertLocalImageToBase64(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }

        try {
            byte[] imageBytes = null;
            String mimeType = "image/png";

            // 从本地HTTP URL下载图片
            if (imageUrl.startsWith("http://localhost") || imageUrl.startsWith("http://127.0.0.1")) {
                java.net.URL url = new java.net.URL(imageUrl);
                try (java.io.InputStream is = url.openStream()) {
                    imageBytes = is.readAllBytes();
                }
                // 从URL路径推断MIME类型
                String path = url.getPath();
                mimeType = getMimeTypeFromFileName(path);
            } else {
                log.warn("不支持的本地图片URL格式: {}", imageUrl);
                return null;
            }

            if (imageBytes != null && imageBytes.length > 0) {
                String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
                return "data:" + mimeType + ";base64," + base64;
            }
        } catch (Exception e) {
            log.warn("转换本地图片为base64失败: {}, 错误: {}", imageUrl, e.getMessage());
        }
        return null;
    }

    /**
     * 从文件名推断MIME类型
     */
    private String getMimeTypeFromFileName(String fileName) {
        if (fileName == null)
            return "image/png";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lower.endsWith(".png")) {
            return "image/png";
        } else if (lower.endsWith(".webp")) {
            return "image/webp";
        } else if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/png";
    }

    /**
     * KIE.AI 文件上传 - 将 base64 图片上传到 KIE.AI 服务器获取 URL
     * 
     * @param provider   KIE.AI 提供商配置
     * @param base64Data base64 编码的图片数据（可以是纯 base64 或 data URL 格式）
     * @param uploadPath 上传路径，如 "images/video-frames"
     * @param fileName   文件名（可选），如 "frame-001.png"
     * @return 上传后的文件 URL
     */
    public String uploadBase64ToKie(AiProviderConfig provider, String base64Data,
            String uploadPath, String fileName) throws Exception {
        if (base64Data == null || base64Data.isEmpty()) {
            throw new IllegalArgumentException("base64Data 不能为空");
        }

        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("base64Data", base64Data);
        requestBody.put("uploadPath", uploadPath != null ? uploadPath : "images/video-frames");

        if (fileName != null && !fileName.isEmpty()) {
            requestBody.put("fileName", fileName);
        }

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(provider.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // 调用 KIE.AI 文件上传 API
        String uploadUrl = provider.getBaseUrl() + "/api/file-base64-upload";
        log.info("📤 上传 base64 图片到 KIE.AI: url={}, path={}, fileName={}",
                uploadUrl, uploadPath, fileName);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uploadUrl,
                    HttpMethod.POST,
                    entity,
                    String.class);

            // 解析响应
            JsonNode json = objectMapper.readTree(response.getBody());
            boolean success = json.path("success").asBoolean(false);
            int code = json.path("code").asInt(0);

            if (!success || code != 200) {
                String msg = json.path("msg").asText("未知错误");
                throw new RuntimeException("KIE.AI 文件上传失败: " + msg);
            }

            // 提取下载 URL
            JsonNode data = json.path("data");
            String downloadUrl = data.path("downloadUrl").asText("");

            if (downloadUrl.isEmpty()) {
                throw new RuntimeException("KIE.AI 文件上传成功但未返回 downloadUrl");
            }

            log.info("✅ KIE.AI 文件上传成功: url={}, size={}",
                    downloadUrl, data.path("fileSize").asLong(0));

            return downloadUrl;

        } catch (Exception e) {
            log.error("❌ KIE.AI 文件上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("KIE.AI 文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理图片列表 - 如果是 base64 格式且提供商是 KIE.AI，则先上传获取 URL
     * 
     * @param provider 提供商配置
     * @param images   原始图片列表（可能包含 base64 或 URL）
     * @return 处理后的图片 URL 列表
     */
    public List<String> processImagesForProvider(AiProviderConfig provider, List<String> images) throws Exception {
        if (images == null || images.isEmpty()) {
            return images;
        }

        // 检查是否为 KIE.AI 提供商
        boolean isKieProvider = provider.getBaseUrl() != null &&
                provider.getBaseUrl().contains("kie.ai");

        if (!isKieProvider) {
            // 非 KIE.AI 提供商，直接返回原列表
            return images;
        }

        // KIE.AI 提供商，需要处理 base64 图片
        List<String> processedImages = new ArrayList<>();

        for (int i = 0; i < images.size(); i++) {
            String image = images.get(i);

            if (image == null || image.isEmpty()) {
                continue;
            }

            // 如果已经是 URL，直接使用
            if (image.startsWith("http://") || image.startsWith("https://")) {
                processedImages.add(image);
                log.debug("图片[{}] 已是 URL，直接使用: {}", i, image);
            }
            // 如果是 base64 格式，需要上传到 KIE.AI
            else if (image.startsWith("data:image")) {
                log.info("🔄 图片[{}] 是 base64 格式，开始上传到 KIE.AI...", i);

                // 生成文件名
                String fileName = "frame-" + String.format("%03d", i) + ".png";
                String uploadPath = "images/video-frames";

                try {
                    String uploadedUrl = uploadBase64ToKie(provider, image, uploadPath, fileName);
                    processedImages.add(uploadedUrl);
                    log.info("✅ 图片[{}] 上传成功: {}", i, uploadedUrl);
                } catch (Exception e) {
                    log.error("❌ 图片[{}] 上传失败: {}", i, e.getMessage());
                    throw new RuntimeException("图片上传失败: " + e.getMessage(), e);
                }
            }
            // 其他格式，尝试直接使用
            else {
                processedImages.add(image);
                log.warn("⚠️ 图片[{}] 格式未知，尝试直接使用: {}", i, image.substring(0, Math.min(50, image.length())));
            }
        }

        log.info("📊 图片处理完成: 原始{}张, 处理后{}张", images.size(), processedImages.size());
        return processedImages;
    }
}
