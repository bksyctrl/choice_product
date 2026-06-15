package com.ecommerce.workflow.node;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ecommerce.workflow.engine.ExecutionContext;
import com.ecommerce.workflow.engine.NodeExecutor;
import com.ecommerce.workflow.engine.NodeResult;
import com.ecommerce.workflow.entity.Knowledge;
import com.ecommerce.workflow.service.ai.GptChatService;
import com.ecommerce.workflow.service.knowledge.KnowledgeService;
import com.ecommerce.workflow.service.rag.RagService;

@Component
public class ScriptGenerateNode implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(ScriptGenerateNode.class);
    private final GptChatService gptChatService;
    private final KnowledgeService knowledgeService;
    private final RagService ragService;

    public ScriptGenerateNode(GptChatService gptChatService, 
                             KnowledgeService knowledgeService,
                             RagService ragService) {
        this.gptChatService = gptChatService;
        this.knowledgeService = knowledgeService;
        this.ragService = ragService;
    }

    @Override
    public String getNodeCode() {
        return "script_generate";
    }

    @Override
    public NodeResult execute(ExecutionContext context) throws Exception {
        log.info("开始执行脚本生成节点");

        Map<String, Object> similarProducts = context.getNodeOutput("similar_search");
        String causalFactors = context.getVariable("causalFactors") != null ?
                context.getVariable("causalFactors").toString() : "";
        String platform = context.getVariable("platform") != null ?
                context.getVariable("platform").toString() : "douyin";

        String productInfo = context.getVariable("productInfo") != null ?
                context.getVariable("productInfo").toString() :
                (similarProducts != null ? similarProducts.toString() : "");

        log.info("脚本生成参数: platform={}, 有因果因素={}", platform, !causalFactors.isEmpty());

        String professionalKnowledge = retrieveProfessionalKnowledge(productInfo, platform);
        log.info("专业知识检索结果: {}", professionalKnowledge.length() > 0 ? "有" : "无");

        String systemPrompt = buildKnowledgeEnhancedSystemPrompt(platform, professionalKnowledge);

        String userPrompt = String.format("""
                请根据以下信息生成电商视频脚本：

                产品信息：
                %s

                因果分析结果：
                %s

                专业知识参考：
                %s

                脚本要求：
                - 每个脚本包含完整的知识钩子和转化引导
                - 结合专业知识优化脚本结构和内容表现力
                - 生成至少3个不同风格的版本
                - 包含：标题、开场白、产品介绍、结尾
                - 适合平台：使用适合该平台的内容风格和节奏
                - 视频时长：30-60秒
                - 字幕：包含关键卖点字幕
                - 互动：设计引导用户互动的元素和话题
                """,
                productInfo,
                causalFactors.isEmpty() ? "无因果分析数据，请根据产品信息自行分析" : causalFactors,
                professionalKnowledge.isEmpty() ? "无专业知识参考，请基于通用知识生成脚本" : professionalKnowledge
        );

        String scripts = gptChatService.chatWithThinking(systemPrompt, userPrompt);

        Map<String, Object> result = new HashMap<>();
        result.put("scripts", scripts);
        result.put("platform", platform);
        result.put("versionCount", 3);
        result.put("content", scripts);
        result.put("knowledgeUsed", professionalKnowledge.length() > 0);

        context.setNodeOutput(getNodeCode(), result);

        log.info("脚本生成完成: 3个版本, 使用专业知识={}", professionalKnowledge.length() > 0);
        return NodeResult.success(result);
    }

    private String buildScriptSystemPrompt(String platform) {
        return String.format("""
                你是专业的电商视频脚本创作专家，擅长%s平台内容
                脚本结构要求：
                [标题] [开场钩子] [产品展示] [用户痛点] [解决方案] [行动号召] [结尾]

                创作原则：
                1. 开场3秒必须抓住注意力，设置悬念
                2. 钩子要自然过渡到产品，避免生硬转折
                3. 产品展示要突出差异化卖点
                4. 解决方案要具体可信，有数据支撑
                5. 行动号召要紧迫有力，明确下一步
                特别注意：
                - 钩子类型： 悬念/冲突/惊喜开场优先
                - 解决方案： 必须包含具体使用场景
                - 行动号召： 引导评论或关注
                禁止：
                - 使用虚假夸大的宣传用语
                - 抄袭他人脚本内容
                - 违反平台广告规范
                """, platform
        );
    }
    
    private String retrieveProfessionalKnowledge(String productInfo, String platform) {
        try {
            log.info("开始检索专业知识库: productInfo长度={}, platform={}", productInfo.length(), platform);
            
            List<Knowledge> professionalKnowledge = knowledgeService.searchSimilar(
                "视频脚本创作 视频制作 互动 行动号召 场景", 
                10
            );
            
            if (professionalKnowledge == null || professionalKnowledge.isEmpty()) {
                log.warn("未找到专业视频脚本知识，尝试获取通用知识");
                professionalKnowledge = knowledgeService.getByType("GENERAL");
            }
            
            if (professionalKnowledge == null || professionalKnowledge.isEmpty()) {
                log.warn("知识库中没有任何可用知识");
                return "";
            }
            
            StringBuilder knowledgeBuilder = new StringBuilder();
            knowledgeBuilder.append("═══════════════════════════════════════\n");
            knowledgeBuilder.append("专业知识库参考内容：\n");
            knowledgeBuilder.append("═══════════════════════════════════════\n\n");
            
            for (Knowledge knowledge : professionalKnowledge) {
                knowledgeBuilder.append("▪ ").append(knowledge.getTitle()).append("\n");
                knowledgeBuilder.append(knowledge.getContent()).append("\n\n");
            }
            
            knowledgeBuilder.append("═══════════════════════════════════════\n");
            knowledgeBuilder.append("知识应用指南：\n");
            knowledgeBuilder.append("1. 每个脚本必须融入专业知识中的视频制作技巧\n");
            knowledgeBuilder.append("2. 结合知识库中的成功案例优化脚本结构\n");
            knowledgeBuilder.append("3. 避免知识库中标记的常见错误和失败模式\n");
            knowledgeBuilder.append("4. 参考知识库中的互动策略提升脚本互动性\n");
            knowledgeBuilder.append("═══════════════════════════════════════\n");
            
            return knowledgeBuilder.toString();
            
        } catch (Exception e) {
            log.error("专业知识检索处理失败: {}", e.getMessage(), e);
            return "";
        }
    }
    
    private String buildKnowledgeEnhancedSystemPrompt(String platform, String professionalKnowledge) {
        StringBuilder systemPrompt = new StringBuilder();
        
        systemPrompt.append(String.format("""
                你是专业视频脚本创作专家，结合专业知识库创作%s平台脚本
                
                你的创作必须严格遵循以下专业标准和知识库指导：
                - 开场钩子必须基于知识库中的成功模式，确保3秒内抓住用户注意力
                - 产品展示要融入知识库中的差异化策略，突出独特卖点避免同质化
                - 用户痛点描述要参考知识库中的用户画像数据，精准触达目标用户
                - 解决方案必须引用知识库中的验证策略，确保可信度和说服力
                - 行动号召要结合知识库中的转化策略，使用紧迫感和明确指引
                - 互动设计要参考知识库中的互动策略，设计引导用户参与的话题
                - 脚本结构要遵循知识库中的成功案例模板，确保逻辑流畅
                - 语言风格要符合平台特色和目标用户习惯，自然亲切
                - 结尾要留下深刻印象，强化品牌记忆点
                
                """, platform));
        
        if (!professionalKnowledge.isEmpty()) {
            systemPrompt.append("\n专业知识库内容：\n");
            systemPrompt.append(professionalKnowledge);
            systemPrompt.append("\n");
        }
        
        systemPrompt.append("""
                
                脚本创作要求：
                1. 每个脚本必须包含完整的知识钩子和转化引导元素
                2. 脚本结构必须遵循知识库中的成功模式
                3. 产品展示要突出差异化卖点，避免与竞品同质化
                4. 提示词生成要基于知识库中的成功案例和最佳实践
                5. 结尾要包含明确的行动号召和品牌强化
                
                脚本格式要求：
                每个脚本必须包含：
                - 标题：吸引眼球的主题
                - 钩子：3秒内抓住注意力
                - 产品展示：突出差异化卖点，避免同质化
                - 行动号召：引导用户下单或关注
                - 解决方案：引用验证策略增强说服力
                - 互动设计：设计引导用户参与的话题
                - 语言风格：符合平台特色和用户习惯
                - 结尾：强化品牌记忆点
                
                禁止：
                - 使用虚假夸大的宣传用语
                - 抄袭他人脚本内容
                - 违反平台广告规范
                - 使用低俗或误导性内容
                """);
        
        return systemPrompt.toString();
    }
}
