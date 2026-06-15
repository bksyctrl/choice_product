package com.ecommerce.workflow.service.video;

import com.ecommerce.workflow.entity.AiVideoConfig;
import com.ecommerce.workflow.entity.VideoPromptTemplate;
import com.ecommerce.workflow.service.config.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service("videoPromptFrameworkService")
public class VideoPromptFrameworkService {

    private static final Logger log = LoggerFactory.getLogger(VideoPromptFrameworkService.class);

    private final SysConfigService configService;
    private final VideoPromptTemplateLearningService templateLearningService;

    public VideoPromptFrameworkService(SysConfigService configService,
            VideoPromptTemplateLearningService templateLearningService) {
        this.configService = configService;
        this.templateLearningService = templateLearningService;
    }

    private Long lastSelectedTemplateId = null;

    public String buildVideoPrompt(AiVideoConfig config, String explosiveRules, String avoidanceRules,
            String knowledgeCases, Map<String, Object> optimizedParams, String expertAdvice) {
        
        VideoPromptTemplate selectedTemplate = templateLearningService.selectBestTemplate(config);
        
        String rules;
        String systemPrefix;
        
        if (selectedTemplate != null) {
            log.info("使用学习模板: code={}, category={}", selectedTemplate.getTemplateCode(), selectedTemplate.getCategory());
            rules = selectedTemplate.getRules();
            systemPrefix = selectedTemplate.getSystemPrefix();
            lastSelectedTemplateId = selectedTemplate.getId();
        } else {
            rules = getOrDefault("video_prompt_rules", getDefaultRules());
            systemPrefix = getOrDefault("video_prompt_system_prefix", getDefaultSystemPrefix());
            lastSelectedTemplateId = null;
        }

        StringBuilder prompt = new StringBuilder();

        prompt.append(systemPrefix).append("\n\n");

        if (expertAdvice != null && !expertAdvice.isEmpty()) {
            prompt.append(expertAdvice).append("\n\n");
        }

        if (knowledgeCases != null && !knowledgeCases.isEmpty()) {
            prompt.append(knowledgeCases).append("\n\n");
        }

        if (explosiveRules != null && !explosiveRules.isEmpty()) {
            prompt.append(explosiveRules).append("\n\n");
        }

        if (avoidanceRules != null && !avoidanceRules.isEmpty()) {
            prompt.append(avoidanceRules).append("\n\n");
        }

        prompt.append(rules);

        return prompt.toString();
    }

    public Long getLastSelectedTemplateId() {
        return lastSelectedTemplateId;
    }

    public String buildControllerPrompt(Map<String, Object> params, List<String> imageUrls,
            String currentPrompt, String modificationRequest) {
        
        VideoPromptTemplate selectedTemplate = templateLearningService.selectBestTemplateFromParams(params);
        
        String rules;
        String systemPrefix;
        
        if (selectedTemplate != null) {
            log.info("使用学习模板(参数模式): code={}, category={}", selectedTemplate.getTemplateCode(), selectedTemplate.getCategory());
            rules = selectedTemplate.getRules();
            systemPrefix = selectedTemplate.getSystemPrefix();
        } else {
            rules = getOrDefault("video_prompt_rules", getDefaultRules());
            systemPrefix = getOrDefault("video_prompt_system_prefix", getDefaultSystemPrefix());
        }

        StringBuilder prompt = new StringBuilder();

        prompt.append(systemPrefix).append("\n\n");

        if (imageUrls != null && !imageUrls.isEmpty()) {
            prompt.append("参考图片: ").append(imageUrls.size()).append("张\n");
            prompt.append("请根据图片内容提取产品特征、材质、颜色、设计细节等信息，融入到提示词中。\n\n");
        }

        if (modificationRequest != null && !modificationRequest.isEmpty()) {
            prompt.append("修改要求: ").append(modificationRequest).append("\n\n");
        }
        
        prompt.append(rules);

        return prompt.toString();
    }

    private String extractParamString(Object value) {
        if (value == null) return null;
        if (value instanceof String) return (String) value;
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Object v = map.get("value");
            return v != null ? v.toString() : null;
        }
        return value.toString();
    }

    private String extractMapParamValue(Object value) {
        if (value == null) return null;
        if (value instanceof String) return (String) value;
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Object v = map.get("value");
            return v != null ? v.toString() : null;
        }
        return null;
    }

    private String getDefaultRules() {
        return """
            【重要生成要求】
            1. 输出格式必须是纯JSON格式，包含scenes数组！
            2. 提示词总长度必须严格控制在800字符以内！
            3. 每个分镜的description字段限制在150字符以内
            4. 人物描述必须精简，突出关键特征
            5. 场景描述必须简洁，只保留核心元素
            6. 多场景分镜必须保持人物、场景、光线的连贯性
            7. 追求原生写实主义，不磨皮、不美颜、无滤镜
            8. 杜绝商业广告感、摆拍感、棚拍痕迹
            9. 视频中禁止出现任何文字（包括字幕、标题、标签、水印等）
            10. 必须使用中文输出
            11. JSON结构必须包含：language, ethnicity, framework, scenes数组, rules数组
            12. 每个scene必须包含：scene_type, description, camera_movement, lighting, sound
            13. 产品展示必须真实，禁止穿模、悬浮、变形、比例失真等物理错误
            14. 产品与人物手部、桌面等接触面必须自然贴合，禁止穿透或悬浮
            """;
    }

    private String getDefaultSystemPrefix() {
        return "你是世界顶级的视频内容创作专家，擅长生成简洁精准的视频提示词JSON。请根据用户配置，生成符合VEO API要求的JSON格式提示词。总长度严格控制在800字符以内，每个分镜description限制在150字符以内。确保描述精准简洁，包含人物、场景、运镜、动作等核心信息。";
    }

    private String getRaceDescription(String race) {
        if (race == null) return "东亚人";
        
        switch (race.toLowerCase()) {
            case "caucasian":
            case "白人":
                return "白人";
            case "african":
            case "黑人":
                return "黑人";
            case "east-asian":
            case "东亚人":
            case "亚洲人":
                return "东亚人";
            case "latino":
            case "拉丁":
                return "拉丁裔";
            case "south-asian":
            case "印度":
                return "南亚人";
            case "mixed":
            case "混血":
                return "混血";
            default:
                return race;
        }
    }

    private String determineStyleFromFramework(String framework) {
        if (framework == null) return "原生写实主义/raw realism";
        if (framework.contains("tech") || framework.contains("unbox")) {
            return "原生写实主义/raw realism，科技产品真实展示";
        }
        if (framework.contains("fashion") || framework.contains("clothing")) {
            return "原生写实主义/raw realism，服装穿搭真实展示";
        }
        if (framework.contains("food")) {
            return "原生写实主义/raw realism，美食真实呈现";
        }
        return "原生写实主义/raw realism，追求真实质感";
    }

    private String determineLightingFromScene(String scene) {
        if (scene == null) return "柔和自然光，轻微阴影增加立体感";
        if (scene.contains("室内") || scene.contains("办公") || scene.contains("居家")) {
            return "自然光从窗户射入，柔和自然光，轻微阴影";
        }
        if (scene.contains("户外") || scene.contains("室外")) {
            return "自然阳光，真实户外光线";
        }
        if (scene.contains("夜景") || scene.contains("夜晚")) {
            return "柔和人造光源，真实夜间光线";
        }
        return "柔和自然光，轻微阴影增加立体感";
    }

    private String determineCameraFromFramework(String framework) {
        if (framework == null) return "自然展示运镜";
        if (framework.contains("tech") || framework.contains("unbox")) {
            return "开箱展示运镜，多角度切换";
        }
        if (framework.contains("review") || framework.contains("test")) {
            return "评测展示运镜，细节特写";
        }
        if (framework.contains("lifestyle")) {
            return "生活记录运镜，自然跟随";
        }
        return "自然展示运镜，平稳流畅";
    }

    public Map<String, Object> getTemplateInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("rules", getOrDefault("video_prompt_rules", getDefaultRules()));
        info.put("systemPrefix", getOrDefault("video_prompt_system_prefix", getDefaultSystemPrefix()));
        info.put("version", configService.getConfig("video_prompt_version", "1.0.0"));
        return info;
    }

    private String getOrDefault(String key, String defaultValue) {
        String value = configService.getConfig(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value;
    }

    public void updateTemplate(String field, String value) {
        configService.setConfig("video_prompt", field, value, "TEXT", "视频提示词模板-" + field);
        log.info("提示词模板已更新: {} = {} 字符", field, value.length());
    }

    public void updateVersion(String version) {
        configService.setConfig("video_prompt", "video_prompt_version", version, "STRING", "视频提示词模板版本");
        log.info("提示词模板版本已更新: {}", version);
    }
}
