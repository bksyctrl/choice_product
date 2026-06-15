package com.ecommerce.workflow.service.ai;

import org.springframework.stereotype.Service;

@Service
public class PromptBuilderService {

    public String buildImageAnalysisPrompt() {
        return "请分析这张图片，严格按照以下JSON格式返回结果。不要输出任何其他文字，只输出JSON:\n\n" +
                "{\n" +
                "  \"language\": \"zh-cn\",\n" +
                "  \"race\": \"east-asian\",\n" +
                "  \"cognition\": [\"hotspot\"],\n" +
                "  \"interest\": [\"hobby\"],\n" +
                "  \"benefit\": [\"solve\"],\n" +
                "  \"emotion\": [\"resonance\"],\n" +
                "  \"basic\": [\"copywriting\"],\n" +
                "  \"scene\": {\"type\": \"scene-function\", \"value\": \"场景描述\"},\n" +
                "  \"character\": {\"type\": \"content-role\", \"value\": \"角色描述\"},\n" +
                "  \"product\": {\"type\": \"product-basic\", \"value\": \"产品描述\"},\n" +
                "  \"framework\": \"tech-unbox\",\n" +
                "  \"description\": \"简短描述图片内容\"\n" +
                "}\n\n" +
                "字段说明:\n" +
                "- language: 语言代码(zh-cn/en/ja/ko等)\n" +
                "- race: 人种(east-asian/southeast-asian/caucasian/african/latino/mixed等)\n" +
                "- cognition: 认知维度数组[hotspot/trend/insight等]\n" +
                "- interest: 兴趣维度数组[hobby/tech/fashion/food等]\n" +
                "- benefit: 利益维度数组[solve/save/improve等]\n" +
                "- emotion: 情感维度数组[resonance/joy/surprise等]\n" +
                "- basic: 基础维度数组[copywriting/story/visual等]\n" +
                "- scene: 场景对象{type, value}\n" +
                "- character: 人物对象{type, value}\n" +
                "- product: 产品对象{type, value}\n" +
                "- framework: 框架类型(hand-shake/mirror/desk/tech-unbox/bestie/family等)\n" +
                "- description: 图片内容的简短描述\n\n" +
                "重要: 必须只返回JSON，不要有任何其他文字或解释！";
    }

    public String buildImageAnalysisRetryPrompt(String basicAnalysis) {
        return "请根据以下图片分析内容，严格按照要求的JSON格式返回结果。只输出JSON，不要有任何其他文字:\n\n" +
                "图片分析内容:\n" + basicAnalysis + "\n\n" +
                "JSON格式要求:\n" +
                "{\n" +
                "  \"language\": \"zh-cn\",\n" +
                "  \"race\": \"east-asian\",\n" +
                "  \"cognition\": [\"hotspot\"],\n" +
                "  \"interest\": [\"tech\"],\n" +
                "  \"benefit\": [\"solve\"],\n" +
                "  \"emotion\": [\"resonance\"],\n" +
                "  \"basic\": [\"copywriting\"],\n" +
                "  \"scene\": {\"type\": \"scene-function\", \"value\": \"场景描述\"},\n" +
                "  \"character\": {\"type\": \"content-role\", \"value\": \"角色描述\"},\n" +
                "  \"product\": {\"type\": \"product-basic\", \"value\": \"产品描述\"},\n" +
                "  \"framework\": \"tech-unbox\",\n" +
                "  \"description\": \"图片内容简短描述\"\n" +
                "}\n\n" +
                "重要: 必须只返回JSON，不要有任何其他文字或解释！";
    }

    public String buildBasicImageAnalysisPrompt() {
        return "详细分析这张图片的内容、视觉元素、色彩、构图、人物、场景、产品等所有细节";
    }
}
