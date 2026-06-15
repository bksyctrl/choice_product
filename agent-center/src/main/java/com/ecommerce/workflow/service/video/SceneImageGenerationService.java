package com.ecommerce.workflow.service.video;

import com.ecommerce.workflow.entity.AiVideoConfig;
import com.ecommerce.workflow.service.ai.AiProviderService;
import com.ecommerce.workflow.service.prompt.PromptTemplateEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 场景图生成服务
 * 负责根据产品图片和配置参数动态生成场景图提示词，并调用AI模型生成场景图
 *
 * 重构说明：
 * - 所有硬编码提示词已迁移到 PromptTemplateEngine 和 JSON 模板文件
 * - 模板位置：resources/prompts/scene_image.json
 * - 通过模板变量实现动态内容生成
 */
@Service
public class SceneImageGenerationService {
    private static final Logger log = LoggerFactory.getLogger(SceneImageGenerationService.class);

    @Autowired
    private AiProviderService aiProviderService;

    @Autowired
    private VideoPromptTemplateLearningService templateLearningService;

    @Autowired
    private PromptTemplateEngine promptTemplateEngine;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("sceneImageExecutor")
    private Executor sceneImageExecutor;

    @Value("${app.upload.path:./uploads}")
    private String uploadPath;

    // ==================== 核心公共方法 ====================

    /**
     * 构建场景图生成提示词（使用模板引擎）
     */
    public String buildSceneImagePrompt(AiVideoConfig config,
                                        Map<String, Object> imageAnalysis,
                                        int sceneNumber,
                                        int totalScenes,
                                        List<Map<String, Object>> previousScenes) {
        try {
            log.info("构建场景图提示词: 场景{}/{}", sceneNumber, totalScenes);

            StringBuilder prompt = new StringBuilder();
            int maxLength = 800;

            // 1. 基础提示词（使用模板引擎）
            String basePrompt = buildBaseImagePrompt(config, imageAnalysis, sceneNumber, totalScenes);
            prompt.append(basePrompt);

            // 2. 如果提示词涉及人物，添加原生主义风格描述（使用模板引擎）
            if (containsPerson(basePrompt)) {
                String nativeStyleDesc = buildNativeStyleDescription();
                prompt.append(" ").append(nativeStyleDesc);
            }

            // 3. 多场景连贯性要求（使用模板引擎）
            if (totalScenes > 1 && !previousScenes.isEmpty()) {
                String continuity = buildContinuityRequirements(previousScenes, sceneNumber);
                prompt.append(" ").append(continuity);
            }

            // 截断到最大长度
            if (prompt.length() > maxLength) {
                prompt.setLength(maxLength);
            }

            String fullPrompt = prompt.toString();
            log.info("场景图提示词构建完成: 长度={}, 包含人物={}", fullPrompt.length(), containsPerson(basePrompt));
            log.info("========== 场景{} 图像生成完整提示词 ==========\n{}\n========== 提示词结束 ==========", sceneNumber, fullPrompt);
            return fullPrompt;

        } catch (Exception e) {
            log.error("构建场景图提示词失败: {}", e.getMessage(), e);
            return buildFallbackScenePrompt(config, sceneNumber);
        }
    }

    /**
     * 获取人物描述（供视频生成使用）
     */
    public String getCharacterDescriptionForVideo(AiVideoConfig config, Map<String, Object> imageAnalysis) {
        return buildCharacterDescription(config, imageAnalysis);
    }

    /**
     * 构建人物描述（使用模板引擎）
     */
    public String buildCharacterDescription(AiVideoConfig config, Map<String, Object> imageAnalysis) {
        try {
            Map<String, Object> vars = buildCharacterTemplateVars(config);
            String result = promptTemplateEngine.render("scene_image", "character.full", vars);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            log.error("模板渲染人物描述失败，使用备用方案: {}", e.getMessage());
        }
        return buildFallbackCharacterDescription(config);
    }

    /**
     * 构建人物描述（精简版）
     */
    public String buildCharacterDescriptionCompact(AiVideoConfig config, Map<String, Object> imageAnalysis) {
        try {
            Map<String, Object> vars = buildCharacterTemplateVars(config);
            String result = promptTemplateEngine.render("scene_image", "character.compact", vars);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            log.error("模板渲染精简人物描述失败，使用备用方案: {}", e.getMessage());
        }
        return buildFallbackCharacterDescription(config);
    }

    /**
     * 构建人物原生风格增强描述
     */
    public String buildCharacterNativeStyle(AiVideoConfig config) {
        // 复用 buildCharacterDescription，保持行为一致
        return buildCharacterDescription(config, null);
    }

    /**
     * 构建场景设定（使用模板引擎）
     */
    public String buildSceneSetting(AiVideoConfig config, int sceneNumber, int totalScenes) {
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("sceneNumber", sceneNumber);
            vars.put("totalScenes", totalScenes);
            vars.put("sceneDesc", getSceneDescription(config));
            vars.put("timeOfDay", getTimeOfDayFromConfig(config));

            String templateKey = totalScenes > 1 ? "scene_setting.multi" : "scene_setting.single";
            String result = promptTemplateEngine.render("scene_image", templateKey, vars);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            log.error("模板渲染场景设定失败，使用备用方案: {}", e.getMessage());
        }
        return buildFallbackSceneSetting(config, sceneNumber, totalScenes);
    }

    /**
     * 构建场景设定（精简版）
     */
    public String buildSceneSettingCompact(AiVideoConfig config, int sceneNumber, int totalScenes) {
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("sceneDesc", getSceneDescription(config));
            String result = promptTemplateEngine.render("scene_image", "scene_setting.compact", vars);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            log.error("模板渲染精简场景设定失败，使用备用方案: {}", e.getMessage());
        }
        return buildFallbackSceneSetting(config, sceneNumber, totalScenes);
    }

    /**
     * 构建构图要求（使用模板引擎）
     */
    public String buildCompositionRequirements(AiVideoConfig config, int sceneNumber) {
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("sceneNumber", sceneNumber);
            String result = promptTemplateEngine.render("scene_image", "composition.default", vars);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            log.error("模板渲染构图要求失败，使用备用方案: {}", e.getMessage());
        }
        return buildFallbackComposition();
    }

    /**
     * 构建构图要求（精简版）
     */
    public String buildCompositionRequirementsCompact(AiVideoConfig config, int sceneNumber) {
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("sceneNumber", sceneNumber);
            String result = promptTemplateEngine.render("scene_image", "composition.compact", vars);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            log.error("模板渲染精简构图要求失败，使用备用方案: {}", e.getMessage());
        }
        return buildFallbackComposition();
    }

    /**
     * 构建光线色调（使用模板引擎）
     */
    public String buildLightingAndTone(AiVideoConfig config, int sceneNumber) {
        try {
            String timeOfDay = getTimeOfDayFromConfig(config);
            String lightingKey = resolveLightingKey(timeOfDay);
            Map<String, Object> vars = new HashMap<>();
            vars.put("timeOfDay", timeOfDay);
            String result = promptTemplateEngine.render("scene_image", lightingKey, vars);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            log.error("模板渲染光线色调失败，使用备用方案: {}", e.getMessage());
        }
        return buildFallbackLighting(config);
    }

    /**
     * 构建光线色调（精简版）
     */
    public String buildLightingAndToneCompact(AiVideoConfig config, int sceneNumber) {
        try {
            String timeOfDay = getTimeOfDayFromConfig(config);
            String lightingKey = resolveLightingCompactKey(timeOfDay);
            Map<String, Object> vars = new HashMap<>();
            vars.put("timeOfDay", timeOfDay);
            String result = promptTemplateEngine.render("scene_image", lightingKey, vars);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            log.error("模板渲染精简光线色调失败，使用备用方案: {}", e.getMessage());
        }
        return buildFallbackLighting(config);
    }

    /**
     * 根据时间段解析光照模板键
     */
    private String resolveLightingKey(String timeOfDay) {
        if ("深夜".equals(timeOfDay) || "夜晚".equals(timeOfDay)) {
            return "lighting.night";
        } else if ("清晨".equals(timeOfDay) || "早晨".equals(timeOfDay)) {
            return "lighting.morning";
        } else if ("黄昏".equals(timeOfDay) || "傍晚".equals(timeOfDay)) {
            return "lighting.dusk";
        } else {
            return "lighting.daytime";
        }
    }

    /**
     * 根据时间段解析精简光照模板键
     */
    private String resolveLightingCompactKey(String timeOfDay) {
        if ("深夜".equals(timeOfDay) || "夜晚".equals(timeOfDay)) {
            return "lighting.compact.night";
        } else if ("清晨".equals(timeOfDay) || "早晨".equals(timeOfDay)) {
            return "lighting.compact.morning";
        } else if ("黄昏".equals(timeOfDay) || "傍晚".equals(timeOfDay)) {
            return "lighting.compact.dusk";
        } else {
            return "lighting.compact.daytime";
        }
    }

    /**
     * 构建摄影风格（使用模板引擎）
     */
    public String buildPhotographyStyle(AiVideoConfig config) {
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("sceneType", config.getSceneType() != null ? config.getSceneType() : "");
            String result = promptTemplateEngine.render("scene_image", "photography_style.default", vars);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            log.error("模板渲染摄影风格失败，使用备用方案: {}", e.getMessage());
        }
        return buildFallbackPhotographyStyle();
    }

    /**
     * 构建摄影风格（精简版）
     */
    public String buildPhotographyStyleCompact(AiVideoConfig config) {
        try {
            String result = promptTemplateEngine.render("scene_image", "photography_style.compact", new HashMap<>());
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            log.error("模板渲染精简摄影风格失败，使用备用方案: {}", e.getMessage());
        }
        return buildFallbackPhotographyStyle();
    }

    /**
     * 构建连贯性要求（使用模板引擎）
     */
    public String buildContinuityRequirements(List<Map<String, Object>> previousScenes, int currentSceneNumber) {
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("previousSceneNumber", currentSceneNumber - 1);
            vars.put("currentSceneNumber", currentSceneNumber);

            if (!previousScenes.isEmpty()) {
                Map<String, Object> lastScene = previousScenes.get(previousScenes.size() - 1);
                String lastCharacter = (String) lastScene.getOrDefault("character", "");
                if (!lastCharacter.isEmpty()) {
                    vars.put("lastCharacter", lastCharacter);
                }
            }

            String result = promptTemplateEngine.render("scene_image", "continuity.default", vars);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            log.error("模板渲染连贯性要求失败，使用备用方案: {}", e.getMessage());
        }
        return buildFallbackContinuity(currentSceneNumber);
    }

    /**
     * 构建连贯性要求（精简版）
     */
    public String buildContinuityRequirementsCompact(List<Map<String, Object>> previousScenes, int currentSceneNumber) {
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("previousSceneNumber", currentSceneNumber - 1);
            String result = promptTemplateEngine.render("scene_image", "continuity.compact", vars);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            log.error("模板渲染精简连贯性要求失败，使用备用方案: {}", e.getMessage());
        }
        return buildFallbackContinuity(currentSceneNumber);
    }

    // ==================== 基础提示词构建 ====================

    /**
     * 构建基础图像提示词（使用模板引擎）
     */
    private String buildBaseImagePrompt(AiVideoConfig config, Map<String, Object> imageAnalysis,
                                         int sceneNumber, int totalScenes) {
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("sceneName", generateSceneName(config, sceneNumber));
            vars.put("sceneNumber", sceneNumber);
            vars.put("isContinuous", totalScenes > 1 && sceneNumber > 1);

            if (imageAnalysis != null) {
                String imageDescriptions = (String) imageAnalysis.get("imageDescriptions");
                if (imageDescriptions != null && !imageDescriptions.isEmpty()) {
                    vars.put("imageDescriptions", imageDescriptions);
                }
                String expertAdvice = (String) imageAnalysis.get("expertAdvice");
                if (expertAdvice != null && !expertAdvice.isEmpty()) {
                    vars.put("expertAdvice", expertAdvice);
                }
            }

            if (config.getTopic() != null && !config.getTopic().isEmpty()) {
                vars.put("topic", config.getTopic());
            }
            if (config.getScene() != null && !config.getScene().isEmpty()) {
                vars.put("scene", config.getScene());
            }
            if (config.getRole() != null && !config.getRole().isEmpty()) {
                vars.put("role", config.getRole());
            }

            String result = promptTemplateEngine.render("scene_image", "default.base", vars);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            log.error("模板渲染基础提示词失败，使用备用方案: {}", e.getMessage());
        }
        return buildFallbackBasePrompt(config, sceneNumber, totalScenes);
    }

    /**
     * 原生主义风格描述（使用模板引擎）
     */
    public String buildNativeStyleDescription() {
        try {
            String result = promptTemplateEngine.render("scene_image", "default.native_style", new HashMap<>());
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            log.error("模板渲染原生风格失败，使用备用方案: {}", e.getMessage());
        }
        return "真实原生风格，绝对零美颜零磨皮零滤镜，皮肤保留真实纹理：毛孔、细纹、肤色不均可见，允许淡淡雀斑小痣，非网红非模特，就是身边普通人的真实面貌。";
    }

    // ==================== 辅助方法 ====================

    /**
     * 生成场景名称
     */
    private String generateSceneName(AiVideoConfig config, int sceneNumber) {
        String topic = config.getTopic() != null ? config.getTopic() : "";
        String scene = config.getScene() != null ? config.getScene() : "";
        String sceneType = config.getSceneType() != null ? config.getSceneType() : "";

        if (!topic.isEmpty()) {
            if (sceneNumber == 1) return topic + "（开篇）";
            if (sceneNumber == 2) return topic + "（展示）";
            if (sceneNumber == 3) return topic + "（结尾）";
        }

        if (!sceneType.isEmpty()) {
            return sceneType + "场景" + sceneNumber;
        }

        return "场景" + sceneNumber + "：" + (topic.isEmpty() ? "产品展示" : topic);
    }

    /**
     * 判断提示词是否包含人物
     */
    public boolean containsPerson(String prompt) {
        if (prompt == null || prompt.isEmpty()) {
            return false;
        }
        String lowerPrompt = prompt.toLowerCase();
        String[] personKeywords = {
            "人", "人物", "模特", "演员", "女生", "男生", "女性", "男性", "女士", "男士",
            "woman", "man", "girl", "boy", "person", "people", "model", "actor", "actress",
            "hand", "手", "face", "脸", "body", "身体"
        };
        for (String keyword : personKeywords) {
            if (lowerPrompt.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建人物模板变量
     */
    private Map<String, Object> buildCharacterTemplateVars(AiVideoConfig config) {
        Map<String, Object> vars = new HashMap<>();

        String race = config.getRace() != null ? config.getRace() : "东亚人";
        vars.put("raceDesc", getRaceDescription(race));

        String role = config.getRole() != null ? config.getRole() : "";
        if (!role.isEmpty()) {
            vars.put("role", role);
        }

        String temperament = getTemperamentFromConfig(config);
        if (temperament != null) {
            vars.put("temperament", temperament);
        }

        vars.put("hairStyle", getHairStyleFromConfig(config));
        vars.put("clothing", getClothingFromConfig(config));

        return vars;
    }

    /**
     * 获取人种描述
     */
    private String getRaceDescription(String race) {
        if (race == null) return "东亚人";

        switch (race.toLowerCase()) {
            case "caucasian":
            case "白人":
                return "25-30岁白人";
            case "african":
            case "黑人":
                return "25-30岁黑人";
            case "east-asian":
            case "东亚人":
            case "亚洲人":
                return "25-30岁东亚人";
            case "latino":
            case "拉丁":
                return "25-30岁拉丁裔";
            case "south-asian":
            case "印度":
                return "25-30岁南亚人";
            case "mixed":
            case "混血":
                return "25-30岁混血";
            default:
                return race;
        }
    }

    /**
     * 从配置获取气质描述
     */
    private String getTemperamentFromConfig(AiVideoConfig config) {
        if (config.getExtendedParams() != null) {
            try {
                Map<String, Object> params = objectMapper.readValue(config.getExtendedParams(), Map.class);
                Object emotion = params.get("emotion");
                if (emotion instanceof List && !((List<?>) emotion).isEmpty()) {
                    return ((List<?>) emotion).get(0).toString();
                }
            } catch (Exception e) {
                log.warn("解析扩展参数失败: {}", e.getMessage());
            }
        }
        return null;
    }

    /**
     * 从配置获取发型描述
     */
    private String getHairStyleFromConfig(AiVideoConfig config) {
        String scene = config.getScene() != null ? config.getScene() : "";
        String role = config.getRole() != null ? config.getRole() : "";

        if (scene.contains("居家") || scene.contains("卧室")) {
            return "自然披散的长发，呈现未经刻意打理的微乱感，整体不对称，几缕发丝随性垂落于脸侧、锁骨处，贴合松弛状态，不刻意规整";
        } else if (scene.contains("户外") || scene.contains("运动")) {
            return "束起的马尾或丸子头，利落干练，贴合活动场景";
        } else if (role.contains("职场") || role.contains("商务")) {
            return "整齐梳理的中长发，专业干练，不失女性柔美";
        } else {
            return "自然披散的长发，呈现未经刻意打理的微乱感，整体不对称，几缕发丝随性垂落于脸侧、锁骨处";
        }
    }

    /**
     * 从配置获取穿搭描述
     */
    private String getClothingFromConfig(AiVideoConfig config) {
        String scene = config.getScene() != null ? config.getScene() : "";
        String sceneType = config.getSceneType() != null ? config.getSceneType() : "";

        if (scene.contains("居家") || scene.contains("卧室") || sceneType.contains("居家")) {
            return "贴身居家睡衣或上衣，剪裁简约自然，材质柔软亲肤，贴合身体原生线条，风格保守不暴露，以真实感与疏离感诠释含蓄";
        } else if (scene.contains("户外") || scene.contains("运动")) {
            return "休闲运动装，舒适透气，贴合活动场景，色彩明快";
        } else if (scene.contains("职场") || sceneType.contains("职场")) {
            return "职业套装，剪裁合体，专业干练，色彩沉稳";
        } else {
            return "日常休闲装，简约自然，材质舒适，贴合场景氛围";
        }
    }

    /**
     * 获取场景描述
     */
    private String getSceneDescription(AiVideoConfig config) {
        String scene = config.getScene() != null ? config.getScene() : "";
        String sceneType = config.getSceneType() != null ? config.getSceneType() : "";

        if (!scene.isEmpty()) {
            return scene;
        }
        if (!sceneType.isEmpty()) {
            return sceneType;
        }
        return "私人空间";
    }

    /**
     * 从配置获取时间段
     */
    private String getTimeOfDayFromConfig(AiVideoConfig config) {
        String topic = config.getTopic() != null ? config.getTopic() : "";
        String scene = config.getScene() != null ? config.getScene() : "";

        if (topic.contains("深夜") || topic.contains("夜晚") || scene.contains("深夜") || scene.contains("夜晚")) {
            return "深夜";
        } else if (topic.contains("早晨") || scene.contains("早晨") || scene.contains("早上")) {
            return "清晨";
        } else if (topic.contains("下午") || scene.contains("下午")) {
            return "午后";
        } else if (topic.contains("黄昏") || scene.contains("黄昏") || scene.contains("傍晚")) {
            return "黄昏";
        } else {
            return "日间";
        }
    }

    // ==================== 备用方案（Fallback） ====================

    private String buildFallbackBasePrompt(AiVideoConfig config, int sceneNumber, int totalScenes) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(generateSceneName(config, sceneNumber));
        if (totalScenes > 1 && sceneNumber > 1) {
            prompt.append("（分镜").append(sceneNumber).append("）");
        }
        prompt.append("\n");

        if (config.getTopic() != null && !config.getTopic().isEmpty()) {
            prompt.append("主题：").append(config.getTopic()).append("。");
        }
        if (config.getScene() != null && !config.getScene().isEmpty()) {
            prompt.append("场景：").append(config.getScene()).append("。");
        }
        if (config.getRole() != null && !config.getRole().isEmpty()) {
            prompt.append("角色：").append(config.getRole()).append("。");
        }
        prompt.append("要求：严格保持产品原貌，形状、颜色、材质、logo必须与参考图完全一致，禁止改变产品外观。");
        return prompt.toString();
    }

    private String buildFallbackCharacterDescription(AiVideoConfig config) {
        String race = config.getRace() != null ? config.getRace() : "东亚人";
        return "拍摄主体为" + getRaceDescription(race) +
               "。五官有辨识度，绝非网红脸或模特标准。极致原生感：皮肤可见毛孔细纹肤色不均，允许雀斑痣轻微黑眼圈，零美颜零磨皮。" +
               "表情无管理，神态随意可能走神，无刻意摆拍。完全素颜无修饰，真实到像抓拍的生活瞬间。";
    }

    private String buildFallbackSceneSetting(AiVideoConfig config, int sceneNumber, int totalScenes) {
        return "。背景为" + getSceneDescription(config) + "，带真实生活痕迹，装饰非对称，摒弃样板间规整感。" +
               "人物坐于场景核心，身体正对镜头。核心道具横贯画面，状态自然有使用痕迹。";
    }

    private String buildFallbackComposition() {
        return "。正面构图，镜头与眼平齐，非自拍角度。人物与镜头极近，脸部占比偏大。聚焦胸部以上近景，背景轻微虚化。";
    }

    private String buildFallbackLighting(AiVideoConfig config) {
        String timeOfDay = getTimeOfDayFromConfig(config);
        if ("深夜".equals(timeOfDay) || "夜晚".equals(timeOfDay)) {
            return "。深夜环境，白色灯光，光线略不均匀。色调偏冷或中性，无暖黄滤镜。";
        } else if ("清晨".equals(timeOfDay) || "早晨".equals(timeOfDay)) {
            return "。清晨环境，柔和晨光，带自然光晕。色调偏暖，还原清晨色彩。";
        } else if ("黄昏".equals(timeOfDay) || "傍晚".equals(timeOfDay)) {
            return "。黄昏环境，夕阳余晖，金色光晕。色调暖金色，还原黄昏色彩。";
        } else {
            return "。日间环境，自然光，带轻微阴影。色调自然，无风格化调色。";
        }
    }

    private String buildFallbackPhotographyStyle() {
        return "。写实纪录风，原生写实主义为核心。杜绝商业广告感、摆拍感，保留原生不完美感。";
    }

    private String buildFallbackContinuity(int currentSceneNumber) {
        return "。本镜头为分镜" + (currentSceneNumber - 1) + "的连续画面。沿用同一人物、同一空间及时间线。动作神态与前序镜头自然衔接，无表演感。";
    }

    private String buildFallbackScenePrompt(AiVideoConfig config, int sceneNumber) {
        return "写实摄影风格，" +
               (config.getRace() != null ? config.getRace() : "25-30岁中国女性") +
               "，深夜居家场景，自然光，正面构图，近景，原生质感，无滤镜";
    }

    // ==================== 场景图生成方法 ====================

    /**
     * 生成单张场景图
     */
    public CompletableFuture<Map<String, Object>> generateSingleSceneImage(
            String imagePrompt,
            List<String> imageModels,
            int modelIndex,
            String productImageUrl) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean hasProductImage = productImageUrl != null && !productImageUrl.isEmpty();
                int productImageLength = hasProductImage ? productImageUrl.length() : 0;
                String productImageType = "none";
                if (hasProductImage) {
                    if (productImageUrl.startsWith("data:image")) {
                        productImageType = "base64";
                    } else if (productImageUrl.startsWith("http://localhost") || productImageUrl.startsWith("http://127.0.0.1")) {
                        productImageType = "local_url";
                    } else if (productImageUrl.startsWith("http")) {
                        productImageType = "external_url";
                    } else {
                        productImageType = "path";
                    }
                }

                log.info("【场景图生成】开始生成，模型索引: {}, 参考图是否提供: {}, 参考图长度: {}, 参考图类型: {}, 提示词长度: {}",
                        modelIndex, hasProductImage, productImageLength, productImageType,
                        imagePrompt != null ? imagePrompt.length() : 0);

                if (modelIndex >= imageModels.size()) {
                    throw new RuntimeException("所有图片生成模型都已尝试失败");
                }

                String model = imageModels.get(modelIndex);
                log.info("使用模型生成场景图: {}", model);

                Map<String, Object> result = aiProviderService.generateImageWithFallback(
                        imagePrompt,
                        model,
                        720,
                        1280,
                        productImageUrl);

                String imageUrl = (String) result.get("url");
                String b64Json = (String) result.get("b64_json");

                if (b64Json != null && !b64Json.isEmpty()) {
                    imageUrl = "data:image/png;base64," + b64Json;
                    log.info("场景图生成成功(base64格式): model={}, base64长度={}", model, b64Json.length());
                } else if (imageUrl != null && !imageUrl.isEmpty()) {
                    try {
                        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                            java.net.URL url = new java.net.URL(imageUrl);
                            try (java.io.InputStream is = url.openStream()) {
                                byte[] imageBytes = is.readAllBytes();
                                String base64 = Base64.getEncoder().encodeToString(imageBytes);
                                imageUrl = "data:image/png;base64," + base64;
                                log.info("场景图URL转换为base64成功: model={}, 大小={} bytes", model, imageBytes.length);
                            }
                        } else if (imageUrl.startsWith("/uploads/")) {
                            String localPath = uploadPath + imageUrl.substring("/uploads".length());
                            java.io.File file = new java.io.File(localPath);
                            if (file.exists()) {
                                byte[] imageBytes = java.nio.file.Files.readAllBytes(file.toPath());
                                String base64 = Base64.getEncoder().encodeToString(imageBytes);
                                imageUrl = "data:image/png;base64," + base64;
                                log.info("场景图本地文件转换为base64成功: model={}, 大小={} bytes", model, imageBytes.length);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("场景图转换为base64失败，使用原始URL: {}, 错误: {}", imageUrl, e.getMessage());
                    }
                } else {
                    throw new RuntimeException("图片生成返回为空");
                }

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("imageUrl", imageUrl);
                response.put("model", model);
                response.put("prompt", imagePrompt);
                return response;

            } catch (Exception e) {
                log.warn("场景图生成失败，模型索引: {}, 错误: {}", modelIndex, e.getMessage());

                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", e.getMessage());
                response.put("modelIndex", modelIndex);

                return response;
            }
        }, sceneImageExecutor);
    }

    /**
     * 批量生成场景图（自动混剪模式）
     */
    public List<CompletableFuture<Map<String, Object>>> generateBatchSceneImages(
            List<String> imagePrompts,
            List<String> imageModels,
            String productImageUrl) {

        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();

        for (int i = 0; i < imagePrompts.size(); i++) {
            String prompt = imagePrompts.get(i);
            CompletableFuture<Map<String, Object>> future = generateSingleSceneImage(
                    prompt, imageModels, 0, productImageUrl);
            futures.add(future);
        }

        return futures;
    }

    /**
     * 获取可用的图片生成模型列表
     */
    public List<String> getAvailableImageModels() {
        List<com.ecommerce.workflow.entity.AiProviderConfig> providers =
                aiProviderService.getProvidersByType("IMAGE");

        if (providers.isEmpty()) {
            log.warn("未配置图片生成提供商，使用默认模型列表");
            return Arrays.asList("dall-e-3", "dall-e-2");
        }

        List<String> models = new ArrayList<>();
        for (com.ecommerce.workflow.entity.AiProviderConfig provider : providers) {
            if (provider.getEnabled() != null && provider.getEnabled() == 1) {
                String modelsStr = provider.getModels();
                if (modelsStr != null && !modelsStr.isEmpty()) {
                    if (modelsStr.trim().startsWith("[")) {
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            String[] parsedModels = mapper.readValue(modelsStr, String[].class);
                            for (String model : parsedModels) {
                                if (model != null && !model.trim().isEmpty()) {
                                    models.add(model.trim());
                                }
                            }
                        } catch (Exception e) {
                            log.warn("解析 JSON 格式模型列表失败，尝试逗号分隔解析: {}", e.getMessage());
                            String[] modelArray = modelsStr.split(",");
                            for (String model : modelArray) {
                                String trimmed = model.trim().replaceAll("[\"\\[\\]]", "");
                                if (!trimmed.isEmpty()) {
                                    models.add(trimmed);
                                }
                            }
                        }
                    } else {
                        String[] modelArray = modelsStr.split(",");
                        for (String model : modelArray) {
                            String trimmed = model.trim();
                            if (!trimmed.isEmpty()) {
                                models.add(trimmed);
                            }
                        }
                    }
                }
            }
        }

        if (models.isEmpty()) {
            log.warn("图片生成提供商没有配置模型，使用默认模型列表");
            return Arrays.asList("dall-e-3", "dall-e-2");
        }

        log.info("获取到 {} 个可用的图片生成模型: {}", models.size(), models);
        return models;
    }
}
