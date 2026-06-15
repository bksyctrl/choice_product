package com.ecommerce.workflow.node;

import com.ecommerce.workflow.engine.*;
import com.ecommerce.workflow.service.ai.GptChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ProductIdentifyNode implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProductIdentifyNode.class);
    private final GptChatService gptChatService;

    public ProductIdentifyNode(GptChatService gptChatService) {
        this.gptChatService = gptChatService;
    }

    @Override
    public String getNodeCode() {
        return "product_identify";
    }

    @Override
    public NodeResult execute(ExecutionContext context) throws Exception {
        log.info("执行产品识别节点");

        String imageUrl = context.getVariable("productImage") != null ?
                context.getVariable("productImage").toString() : "";
        String productName = context.getVariable("productName") != null ?
                context.getVariable("productName").toString() : "";

        if (imageUrl.isEmpty()) {
            return NodeResult.failure("缺少产品图片URL");
        }

        log.info("识别产品图片: {}", imageUrl.substring(0, Math.min(imageUrl.length(), 50)));

        String identifyPrompt = """
                请根据产品图片进行详细分析，识别以下信息:

                1. 产品名称和分类
                2. 产品特点和卖点
                3. 目标用户群体
                4. 适用场景和用途
                5. 价格区间估算
                6. 竞争优势(如有)
                7. 营销建议和推荐策略
                请以JSON格式返回结果,包含以上所有字段的信息。
                """;

        String identificationResult = gptChatService.analyzeImage(imageUrl, identifyPrompt);

        Map<String, Object> result = new HashMap<>();
        result.put("imageUrl", imageUrl);
        result.put("identificationResult", identificationResult);
        result.put("productName", productName.isEmpty() ? "AI识别产品" : productName);

        context.setVariable("productInfo", identificationResult);
        context.setNodeOutput(getNodeCode(), result);

        log.info("产品识别完成");
        return NodeResult.success(result);
    }
}
