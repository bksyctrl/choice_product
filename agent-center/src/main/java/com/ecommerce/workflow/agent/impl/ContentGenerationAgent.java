package com.ecommerce.workflow.agent.impl;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ecommerce.workflow.agent.Agent;
import com.ecommerce.workflow.agent.AgentRequest;
import com.ecommerce.workflow.agent.AgentResponse;
import com.ecommerce.workflow.service.ai.GptChatService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ContentGenerationAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(ContentGenerationAgent.class);
    private final GptChatService gptChatService;
    private final ObjectMapper objectMapper;

    public ContentGenerationAgent(GptChatService gptChatService, ObjectMapper objectMapper) {
        this.gptChatService = gptChatService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getAgentType() {
        return "content_generation";
    }

    @Override
    public String getName() {
        return "内容生成Agent";
    }

    @Override
    public AgentResponse process(AgentRequest request) throws Exception {
        log.info("内容生成Agent处理请求: {}", request.getMessage());

        String action = extractAction(request);

        return switch (action) {
            case "generate_script" -> generateScript(request);
            case "generate_veo_prompt" -> generateVeoPrompt(request);
            case "adapt_platform" -> adaptForPlatform(request);
            case "optimize_content" -> optimizeContent(request);
            default -> handleGeneralRequest(request);
        };
    }

    private AgentResponse generateScript(AgentRequest request) {
        log.info("生成带货脚本");

        Map<String, Object> context = request.getContext() != null ? request.getContext() : new HashMap<>();

        String productInfo = context.containsKey("productInfo") ?
                context.get("productInfo").toString() : "未提供产品信息";

        String causalFactors = context.containsKey("causalFactors") ?
                context.get("causalFactors").toString() : "";

        String audienceProfile = context.containsKey("audienceProfile") ?
                context.get("audienceProfile").toString() : "";

        String platform = context.containsKey("platform") ?
                context.get("platform").toString() : "douyin";

        String systemPrompt = buildScriptGenerationSystemPrompt(platform);

        String userPrompt = String.format("""
                请基于以下信息生成高转化带货脚本:

                【产品信息】
                %s

                【爆款因果因子】
                %s

                【目标受众画像】
                %s

                【其他要求】
                %s

                请生成3个不同风格的脚本版本:
                1. 痛点直击型 - 直接戳中用户痛点
                2. 场景代入型 - 让用户产生共鸣
                3. 利益诱导型 - 强调产品价值

                每个脚本包含:
                - 标题(吸引眼球)
                - 开场白(前3秒黄金时间)
                - 主体内容(产品卖点+使用场景)
                - 行动号召(引导转化)
                """,
                productInfo,
                causalFactors.isEmpty() ? "暂无" : causalFactors,
                audienceProfile.isEmpty() ? "大众消费者" : audienceProfile,
                request.getMessage()
        );

        String response = gptChatService.chatWithThinking(systemPrompt, userPrompt);

        Map<String, Object> resultData = parseScripts(response);

        return AgentResponse.success(
                "已为您生成3个版本的带货脚本,请查看并选择最合适的版本:\n\n" + response,
                resultData
        );
    }

    private AgentResponse generateVeoPrompt(AgentRequest request) {
        log.info("生成精细化VEO提示词");

        Map<String, Object> context = request.getContext() != null ? request.getContext() : new HashMap<>();

        String scriptContent = context.containsKey("scriptContent") ?
                context.get("scriptContent").toString() : request.getMessage();

        String productImages = context.containsKey("productImages") ?
                context.get("productImages").toString() : "";

        String aspectRatio = context.containsKey("aspectRatio") ?
                context.get("aspectRatio").toString() : "9:16";

        String videoStyle = context.containsKey("videoStyle") ?
                context.get("videoStyle").toString() : "写实纪录风";

        String systemPrompt = buildVeoPromptSystemPrompt();

        String userPrompt = String.format("""
                请基于以下信息生成**极度精细化**的VEO视频提示词:

                【脚本内容】
                %s

                【可用素材图片】
                %s

                【视频比例】
                %s

                【视频风格】
                %s

                【核心要求】
                1. **人物描述必须极度精细**:
                   - 年龄范围(精确到岁数区间)
                   - 外貌特征(脸型、五官比例、眉眼结构、鼻唇形态)
                   - 皮肤状态(毛孔、瑕疵、质感)
                   - 发型描述(长度、颜色、造型、凌乱度)
                   - 表情神态(眼神、微笑、情绪张力)
                   - 妆容状态(素颜/淡妆/浓妆)
                   - 穿搭描述(款式、材质、颜色、贴合度)

                2. **场景描述必须极度精细**:
                   - 空间布局(家具位置、墙面状态)
                   - 光线条件(光源类型、色温、阴影)
                   - 色调氛围(冷暖调、饱和度)
                   - 环境细节(生活痕迹、装饰物)

                3. **镜头描述必须极度精细**:
                   - 构图方式(正面/侧面/俯拍/仰拍)
                   - 景别(特写/近景/中景/全景)
                   - 机位高度(平视/俯视/仰视)
                   - 运镜方式(推/拉/摇/移/固定)
                   - 镜头距离(亲密距离/社交距离)

                4. **动作描述必须极度精细**:
                   - 肢体动作(坐姿、站姿、手势)
                   - 表情变化(眼神移动、微笑弧度)
                   - 动作节奏(快/慢/停顿)
                   - 口播内容(语气、停顿、情感)

                5. **输出格式**:
                {
                  "sequenceNumber": 1,
                  "name": "镜头名称",
                  "imagePrompt": "极度精细的图片提示词(中文,包含所有细节)",
                  "videoPrompt": "极度精细的视频提示词(中文,包含动作和口播)"
                }

                注意: imagePrompt和videoPrompt必须简洁精准,总长度严格限制在800字符以内!
                """,
                scriptContent,
                productImages.isEmpty() ? "无" : productImages,
                aspectRatio,
                videoStyle
        );

        String response = gptChatService.chatWithThinking(systemPrompt, userPrompt);

        try {
            Map<String, Object> veoPrompt = objectMapper.readValue(response, Map.class);

            return AgentResponse.success(
                    "已生成精细化VEO提示词:\n\n" + response,
                    veoPrompt
            );
        } catch (Exception e) {
            return AgentResponse.success("VEO提示词生成结果:\n\n" + response);
        }
    }

    private String buildVeoPromptSystemPrompt() {
        return """
                你是世界顶级的AI视频提示词工程师，专注于生成**极度精细化**的VEO视频提示词。

                ═══════════════════════════════════════════════════════════════
                【精细化提示词标准范例】
                ═══════════════════════════════════════════════════════════════

                以下是一个标准的精细化提示词范例，你必须参考这个标准来生成：

                【imagePrompt范例】
                "25-30岁中国女性深夜居家写实摄影文案拍摄主体为25-30岁中国女性，人物形象需严格贴合参考图，脸型、五官比例、眉眼结构、鼻唇形态高度还原，杜绝陌生面孔、网红脸及偏离参考图的造型。追求自然耐看的原生质感，五官并非绝对完美，自带生活记忆点，以高度写实状态呈现——允许存在细微真实瑕疵，如轻微肤色不均、淡微小痘印、细密毛孔等原生皮肤特质，摒弃"零瑕疵完美感"，整体宛若真实存在的普通人，而非人工感强烈的模特。发型为自然披散的长发，呈现未经刻意打理的微乱感，整体不对称，几缕发丝随性垂落于脸侧、锁骨处，贴合深夜居家的松弛状态，不刻意规整，尽显不造作的女性魅力。表情放松无刻意管理，带着淡淡的情绪张力，眼神自然灵动，不直视镜头到底，偶有游离闪躲，如同真实聊天中的自然神态，氛围感松弛不僵硬。妆容趋近素颜，仅极淡修饰或无修饰，无明显妆感痕迹。皮肤质感真实可触，毛孔清晰可见，不磨皮、不美颜、无任何滤镜，还原皮肤原生状态，拒绝过度美化。穿搭为贴身居家睡衣或上衣，剪裁简约自然，材质柔软亲肤，贴合身体原生线条，风格保守不暴露，以真实感与疏离感诠释含蓄性感，而非依靠刻意造型营造氛围。构图与场景：采用正面构图（frontal shot），镜头位于人物正前方与眼睛平齐高度，非自拍角度，画面中无相机、手机等拍摄工具出现。人物与镜头距离极近，脸部在画面中占比偏大，强化亲密交流感与轻微压迫感，构图聚焦胸部以上或近景范围，脸部细节清晰可辨，背景呈轻微虚化效果，兼具层次感与真实呼吸感..."

                【videoPrompt范例】
                "夜晚真实女生卧室场景，采用写实摄影风格与纪录感生活影像基调，人物、场景、气质与前置分镜设定完全统一。聚焦25-30岁中国女性，外貌严格参照参考图，五官比例贴合真实人体结构，非网红化脸型，允许皮肤轻微瑕疵存在，全程无磨皮、无美颜、无滤镜，还原原生肤质状态。人物端坐于沙发之上，沙发右侧优先贴近墙面，以横向姿态贯穿画面或占据中部核心区域，人物落座于沙发中部偏右位置，身体正对镜头，上半身自然前倾，与镜头距离极近，脸部在画面中占比突出，形成近景构图。背景保持场景一致性，沙发呈现自然居家状态，无需刻意整理，可保留轻微褶皱，其上放置一个体积显著偏大的毛绒玩偶，玩偶摆放不对称、不刻意，兼具陪伴感与长期摆放的生活痕迹，成为空间的自然组成部分。镜头位于人物正前方，与眼部保持同一高度，采用正面构图，非自拍视角，画面中无相机出镜。画面开篇：人物保持自然静坐姿态，无多余动作，神情松弛，似正准备开口与他人闲聊。后续连贯动作：人物一边说话，一边自然抬手拿起手机，手机从画面下方入镜..."

                ═══════════════════════════════════════════════════════════════
                【核心原则】
                ═══════════════════════════════════════════════════════════════

                1. **简洁精准**：每个提示词必须简洁精准，总长度严格限制在800字符以内
                2. **拒绝模糊**：每个细节都要具体、可描述、可执行
                3. **拒绝模板化**：每个提示词都要根据具体内容定制
                4. **拒绝完美主义**：保留真实的不完美感（皮肤瑕疵、环境痕迹）
                5. **追求真实感**：原生写实主义（raw realism）为核心

                【必须包含的维度】

                ▶ 人物维度（必须全部覆盖）:
                - 年龄精确区间
                - 脸型五官详细描述
                - 皮肤状态（毛孔、瑕疵、质感）
                - 发型（长度、颜色、造型、凌乱度）
                - 表情神态（眼神、微笑、情绪）
                - 妆容状态
                - 穿搭描述
                - 肢体语言

                ▶ 场景维度（必须全部覆盖）:
                - 空间布局
                - 光线条件
                - 色调氛围
                - 环境细节
                - 道具陈设

                ▶ 镜头维度（必须全部覆盖）:
                - 构图方式
                - 景别
                - 机位高度
                - 运镜方式
                - 镜头距离

                ▶ 动作维度（videoPrompt必须覆盖）:
                - 肢体动作
                - 表情变化
                - 动作节奏
                - 口播内容

                【输出要求】
                - imagePrompt: 简洁精准的图片提示词，限制800字符以内
                - videoPrompt: 简洁精准的视频提示词，限制800字符以内
                - 必须是中文输出
                - 必须符合原生写实主义风格
                """;
    }

    private AgentResponse adaptForPlatform(AgentRequest request) {
        log.info("平台适配: {}", request.getMessage());

        Map<String, Object> context = request.getContext() != null ? request.getContext() : new HashMap<>();
        String targetPlatform = (String) context.getOrDefault("targetPlatform", "douyin");
        String originalContent = context.containsKey("originalContent") ?
                context.get("originalContent").toString() : request.getMessage();

        String platformRules = getPlatformRules(targetPlatform);

        String systemPrompt = String.format("""
                你是电商平台规则专家,精通各平台的文案规范。

                目标平台: %s

                平台规则:
                %s

                请将原始内容适配到目标平台,确保:
                1. 符合平台字数限制
                2. 符合平台风格调性
                3. 使用平台热门话题标签
                4. 避免平台违禁词
                5. 优化排版格式
                """, targetPlatform, platformRules);

        String userPrompt = String.format("""
                原始内容:
                %s

                请进行平台适配优化。
                """, originalContent);

        String adaptedContent = gptChatService.chat(systemPrompt, userPrompt);

        return AgentResponse.success(
                String.format("已完成%s平台适配:\n\n%s", targetPlatform, adaptedContent),
                Map.of(
                        "platform", targetPlatform,
                        "adaptedContent", adaptedContent,
                        "originalContent", originalContent
                )
        );
    }

    private AgentResponse optimizeContent(AgentRequest request) {
        log.info("内容优化: {}", request.getMessage());

        String systemPrompt = """
                你是顶级电商内容优化师,擅长提升内容的转化率。

                优化维度:
                1. 标题吸引力 - 提升点击率
                2. 开场钩子 - 前3秒抓住注意力
                3. 痛点挖掘 - 准确击中用户需求
                4. 卖点提炼 - 突出差异化优势
                5. 信任建立 - 增加可信度元素
                6. 行动号召 - 强化转化引导
                7. 情感共鸣 - 增强感染力

                输出格式:
                - 优化后的完整内容
                - 优化要点说明(每处修改的原因)
                - 转化率预估提升
                """;

        String optimizedContent = gptChatService.chatWithThinking(systemPrompt, request.getMessage());

        return AgentResponse.success(
                "内容优化完成!以下是优化结果:\n\n" + optimizedContent,
                Map.of("optimizedContent", optimizedContent)
        );
    }

    private AgentResponse handleGeneralRequest(AgentRequest request) {
        log.info("处理一般性内容生成请求");

        String systemPrompt = """
                你是电商内容创作专家,专注于:

                1. 短视频脚本创作(抖音、快手、小红书)
                2. 直播话术设计
                3. 商品详情页文案
                4. 种草笔记撰写
                5. 广告文案创意

                创作原则:
                - 用户为中心,痛点驱动
                - 数据支撑,有理有据
                - 差异化表达,避免同质化
                - 合规安全,规避风险
                """;

        String response = gptChatService.chat(systemPrompt, request.getMessage());
        return AgentResponse.success(response);
    }

    private String buildScriptGenerationSystemPrompt(String platform) {
        return String.format("""
                你是顶级的电商带货脚本创作大师,专注于%s平台。

                创作核心理念:
                1. 黄金3秒法则 - 开场必须抓住眼球
                2. 痛点-方案-证明 - 经典说服结构
                3. 情感共鸣 - 让用户产生代入感
                4. 价值锚定 - 展示产品独特价值
                5. 紧迫感营造 - 促进立即行动

                %s平台特点:
                %s

                高转化脚本公式:
                [吸睛标题] + [痛点开场] + [产品引入] + [卖点展示] + [使用场景] + [社会证明] + [行动号召]

                注意事项:
                - 语言口语化,避免书面语
                - 控制时长(30-60秒最佳)
                - 适当使用网络热词
                - 避免极限词和夸大宣传
                """, platform, platform, getPlatformCharacteristics(platform));
    }

    private String getPlatformRules(String platform) {
        return switch (platform) {
            case "douyin" -> """
                    抖音平台规则:
                    - 标题: 55字符以内
                    - 正文: 可长可短,建议1000字以内
                    - 热门话题:#话题名 格式
                    - 避免敏感词: 最、第一、绝对等
                    - 视频时长: 15秒-3分钟为佳
                    """;
            case "kuaishou" -> """
                    快手平台规则:
                    - 标题: 50字符以内
                    - 风格更接地气,亲切自然
                    - 强调真实体验分享
                    - 互动性强,多提问引导评论
                    """;
            case "xiaohongshu" -> """
                    小红书平台规则:
                    - 标题: 20字以内,带emoji
                    - 正文: 图文并茂,分段清晰
                    - 大量使用emoji表情
                    - 标签格式: #标签 #标签
                    - 风格精致,注重美感
                    """;
            default -> "通用平台规则";
        };
    }

    private String getPlatformCharacteristics(String platform) {
        return switch (platform) {
            case "douyin" -> """
                    - 快节奏,强视觉冲击
                    - 音乐卡点很重要
                    - 适合剧情反转类内容
                    - 用户年轻化,喜欢新奇
                    """;
            case "kuaishou" -> """
                    - 老铁文化,接地气
                    - 强调真实、信任
                    - 直播带货氛围浓
                    - 私域流量价值高
                    """;
            case "xiaohongshu" -> """
                    - 种草属性强
                    - 用户消费能力强
                    - 注重品质生活
                    - 图文+短视频结合
                    """;
            default -> "通用特性";
        };
    }

    private String extractAction(AgentRequest request) {
        if (request.getParameters() != null && request.getParameters().containsKey("action")) {
            return (String) request.getParameters().get("action");
        }
        if (request.getMessage() != null) {
            String msg = request.getMessage().toLowerCase();
            if (msg.contains("脚本") || msg.contains("文案") || msg.contains("话术")) {
                return "generate_script";
            } else if (msg.contains("veo") || msg.contains("视频提示词") || msg.contains("video prompt")) {
                return "generate_veo_prompt";
            } else if (msg.contains("适配") || msg.contains("platform")) {
                return "adapt_platform";
            } else if (msg.contains("优化") || msg.contains("改进")) {
                return "optimize_content";
            }
        }
        return "general";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseScripts(String response) {
        try {
            return objectMapper.readValue(response, Map.class);
        } catch (Exception e) {
            return Map.of("raw_response", response);
        }
    }
}
