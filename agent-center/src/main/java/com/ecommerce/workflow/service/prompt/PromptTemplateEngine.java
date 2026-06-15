package com.ecommerce.workflow.service.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词模板引擎
 * 集中管理所有AI提示词模板，支持变量替换、条件渲染、模板继承
 *
 * 使用方式:
 * 1. 在 resources/prompts/ 目录下创建 JSON 模板文件
 * 2. 通过 {@link #render(String, Map)} 方法渲染模板
 * 3. 通过 {@link #render(String, Map, String)} 方法渲染指定场景的模板
 *
 * 模板语法:
 * - {{variable}}: 变量替换
 * - {{#if condition}}...{{/if}}: 条件渲染 (condition为true时显示)
 * - {{#unless condition}}...{{/unless}}: 反向条件渲染
 * - {{#each list}}...{{/each}}: 列表循环
 * - {{#block name}}...{{/block}}: 代码块（用于模板继承）
 */
@Service
public class PromptTemplateEngine {
    private static final Logger log = LoggerFactory.getLogger(PromptTemplateEngine.class);

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([^#\\s/][^}]*?)\\s*\\}\\}");
    private static final Pattern IF_PATTERN = Pattern.compile("\\{\\{\\s*#if\\s+([^}]+?)\\s*\\}\\}(.*?)\\{\\{\\s*/if\\s*\\}\\}", Pattern.DOTALL);
    private static final Pattern UNLESS_PATTERN = Pattern.compile("\\{\\{\\s*#unless\\s+([^}]+?)\\s*\\}\\}(.*?)\\{\\{\\s*/unless\\s*\\}\\}", Pattern.DOTALL);
    private static final Pattern EACH_PATTERN = Pattern.compile("\\{\\{\\s*#each\\s+([^}]+?)\\s*\\}\\}(.*?)\\{\\{\\s*/each\\s*\\}\\}", Pattern.DOTALL);
    private static final Pattern BLOCK_PATTERN = Pattern.compile("\\{\\{\\s*#block\\s+([^}]+?)\\s*\\}\\}(.*?)\\{\\{\\s*/block\\s*\\}\\}", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final PathMatchingResourcePatternResolver resourceResolver;
    private final Map<String, JsonNode> templates = new ConcurrentHashMap<>();
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public PromptTemplateEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.resourceResolver = new PathMatchingResourcePatternResolver();
    }

    /**
     * 初始化时加载所有模板文件
     */
    @PostConstruct
    public void loadTemplates() {
        try {
            Resource[] resources = resourceResolver.getResources("classpath:prompts/*.json");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null) {
                    String templateKey = filename.replace(".json", "");
                    String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    JsonNode templateNode = objectMapper.readTree(content);
                    templates.put(templateKey, templateNode);
                    log.info("加载提示词模板: {}", templateKey);
                }
            }
            log.info("共加载 {} 个提示词模板", templates.size());
        } catch (IOException e) {
            log.error("加载提示词模板失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 重新加载模板（支持热更新）
     */
    public void reloadTemplates() {
        templates.clear();
        templateCache.clear();
        loadTemplates();
    }

    /**
     * 渲染模板
     *
     * @param templateKey 模板名称（不含.json后缀）
     * @param variables   变量映射
     * @return 渲染后的提示词文本
     */
    public String render(String templateKey, Map<String, Object> variables) {
        return render(templateKey, null, variables);
    }

    /**
     * 渲染指定场景的模板
     *
     * @param templateKey 模板名称
     * @param scene       场景名称（如 "mix", "single", "modify"）
     * @param variables   变量映射
     * @return 渲染后的提示词文本
     */
    public String render(String templateKey, String scene, Map<String, Object> variables) {
        try {
            JsonNode templateNode = templates.get(templateKey);
            if (templateNode == null) {
                log.error("模板不存在: {}", templateKey);
                return null;
            }

            // 如果指定了场景，获取场景特定模板（支持嵌套路径如 "character.full"）
            String templateContent;
            if (scene != null && !scene.isEmpty()) {
                JsonNode sceneNode = getNestedNode(templateNode, scene);
                if (sceneNode != null && sceneNode.isTextual()) {
                    templateContent = sceneNode.asText();
                } else if (sceneNode != null && sceneNode.isObject()) {
                    JsonNode systemNode = sceneNode.get("system");
                    if (systemNode != null && systemNode.isTextual()) {
                        templateContent = systemNode.asText();
                    } else {
                        log.error("模板 {} 的场景 {} 是对象但缺少 system 字段", templateKey, scene);
                        return null;
                    }
                } else if (templateNode.has("default")) {
                    JsonNode defaultNode = templateNode.get("default");
                    if (defaultNode.isTextual()) {
                        templateContent = defaultNode.asText();
                    } else {
                        // default 可能是对象，尝试从 default 中查找场景
                        sceneNode = getNestedNode(defaultNode, scene);
                        if (sceneNode != null && sceneNode.isTextual()) {
                            templateContent = sceneNode.asText();
                        } else {
                            log.error("模板 {} 中没有找到场景 {} 或默认内容", templateKey, scene);
                            return null;
                        }
                    }
                } else if (templateNode.isTextual()) {
                    templateContent = templateNode.asText();
                } else {
                    log.error("模板 {} 中没有找到场景 {} 或默认内容", templateKey, scene);
                    return null;
                }
            } else if (templateNode.has("default")) {
                JsonNode defaultNode = templateNode.get("default");
                if (defaultNode.isTextual()) {
                    templateContent = defaultNode.asText();
                } else {
                    log.error("模板 {} 的 default 节点不是文本类型", templateKey);
                    return null;
                }
            } else if (templateNode.isTextual()) {
                templateContent = templateNode.asText();
            } else {
                log.error("模板 {} 中没有找到场景 {} 或默认内容", templateKey, scene);
                return null;
            }

            // 渲染模板
            String rendered = renderTemplate(templateContent, variables);

            log.debug("渲染模板 {} (场景: {}), 变量数: {}, 结果长度: {}",
                    templateKey, scene, variables != null ? variables.size() : 0, rendered.length());

            return rendered;

        } catch (Exception e) {
            log.error("渲染模板失败: {} (场景: {}), 错误: {}", templateKey, scene, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取嵌套节点（支持点号路径如 "character.full"）
     */
    private JsonNode getNestedNode(JsonNode root, String path) {
        if (path == null || path.isEmpty()) {
            return root;
        }
        String[] parts = path.split("\\.");
        JsonNode current = root;
        for (String part : parts) {
            if (current != null && current.has(part)) {
                current = current.get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    /**
     * 从模板中获取结构化数据（如JSON配置）
     */
    public JsonNode getTemplateData(String templateKey, String path) {
        JsonNode templateNode = templates.get(templateKey);
        if (templateNode == null) {
            return null;
        }

        if (path == null || path.isEmpty()) {
            return templateNode;
        }

        String[] parts = path.split("\\.");
        JsonNode current = templateNode;
        for (String part : parts) {
            if (current.has(part)) {
                current = current.get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    /**
     * 检查模板是否存在
     */
    public boolean hasTemplate(String templateKey) {
        return templates.containsKey(templateKey);
    }

    /**
     * 获取所有已加载的模板名称
     */
    public Set<String> getTemplateNames() {
        return new HashSet<>(templates.keySet());
    }

    // ==================== 私有渲染方法 ====================

    private String renderTemplate(String template, Map<String, Object> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        String result = template;

        // 1. 处理代码块（用于模板继承）
        result = processBlocks(result, variables);

        // 2. 处理条件渲染 (#if)
        result = processIfConditions(result, variables);

        // 3. 处理反向条件渲染 (#unless)
        result = processUnlessConditions(result, variables);

        // 4. 处理列表循环 (#each)
        result = processEachLoops(result, variables);

        // 5. 处理变量替换
        result = processVariables(result, variables);

        return result;
    }

    private String processBlocks(String template, Map<String, Object> variables) {
        Matcher matcher = BLOCK_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String blockName = matcher.group(1).trim();
            String blockContent = matcher.group(2);

            // 检查是否有覆盖的代码块
            String overrideKey = "block_" + blockName;
            if (variables != null && variables.containsKey(overrideKey)) {
                Object override = variables.get(overrideKey);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(override != null ? override.toString() : blockContent));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(blockContent));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String processIfConditions(String template, Map<String, Object> variables) {
        Matcher matcher = IF_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String condition = matcher.group(1).trim();
            String content = matcher.group(2);

            boolean conditionMet = evaluateCondition(condition, variables);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(conditionMet ? content : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String processUnlessConditions(String template, Map<String, Object> variables) {
        Matcher matcher = UNLESS_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String condition = matcher.group(1).trim();
            String content = matcher.group(2);

            boolean conditionMet = evaluateCondition(condition, variables);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(!conditionMet ? content : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String processEachLoops(String template, Map<String, Object> variables) {
        Matcher matcher = EACH_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String listName = matcher.group(1).trim();
            String itemTemplate = matcher.group(2);

            Object listObj = getVariableValue(listName, variables);
            if (listObj instanceof List) {
                List<Object> list = (List<Object>) listObj;
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    Map<String, Object> itemVars = new HashMap<>(variables != null ? variables : new HashMap<>());
                    itemVars.put("this", item);
                    itemVars.put("@index", i);
                    itemVars.put("@first", i == 0);
                    itemVars.put("@last", i == list.size() - 1);

                    // 如果item是Map，将其键值对也加入变量
                    if (item instanceof Map) {
                        Map<String, Object> itemMap = (Map<String, Object>) item;
                        itemVars.putAll(itemMap);
                    }

                    result.append(renderTemplate(itemTemplate, itemVars));
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(result.toString()));
            } else {
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String processVariables(String template, Map<String, Object> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            Object value = getVariableValue(varName, variables);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value.toString() : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private boolean evaluateCondition(String condition, Map<String, Object> variables) {
        // 支持简单条件判断
        // 1. 直接变量名: {{#if hasCharacter}}
        // 2. 等于判断: {{#if type == 'mix'}}
        // 3. 不等于判断: {{#if type != 'single'}}
        // 4. 存在判断: {{#if imageUrls}}

        condition = condition.trim();

        // 处理等于判断
        if (condition.contains("==")) {
            String[] parts = condition.split("==", 2);
            String left = parts[0].trim();
            String right = parts[1].trim().replace("'", "").replace("\"", "");
            Object leftValue = getVariableValue(left, variables);
            return leftValue != null && leftValue.toString().equals(right);
        }

        // 处理不等于判断
        if (condition.contains("!=")) {
            String[] parts = condition.split("!=", 2);
            String left = parts[0].trim();
            String right = parts[1].trim().replace("'", "").replace("\"", "");
            Object leftValue = getVariableValue(left, variables);
            return leftValue == null || !leftValue.toString().equals(right);
        }

        // 简单变量存在且为真
        Object value = getVariableValue(condition, variables);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return !((String) value).isEmpty();
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0;
        }
        if (value instanceof Collection) {
            return !((Collection<?>) value).isEmpty();
        }
        return true;
    }

    private Object getVariableValue(String varName, Map<String, Object> variables) {
        if (variables == null) {
            return null;
        }

        // 支持嵌套属性访问: user.name
        String[] parts = varName.split("\\.", 2);
        Object value = variables.get(parts[0]);

        if (parts.length > 1 && value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nestedMap = (Map<String, Object>) value;
            return getVariableValue(parts[1], nestedMap);
        }

        return value;
    }
}
