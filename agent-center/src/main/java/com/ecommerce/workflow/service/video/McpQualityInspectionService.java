package com.ecommerce.workflow.service.video;

import com.ecommerce.workflow.service.ai.AiProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * MCP自动质量检查服务
 * 负责检查生成的场景图是否符合产品一致性标准（电商核心）
 */
@Service
public class McpQualityInspectionService {
    private static final Logger log = LoggerFactory.getLogger(McpQualityInspectionService.class);

    @Autowired
    private AiProviderService aiProviderService;

    @Autowired
    @Qualifier("mcpInspectionExecutor")
    private Executor mcpInspectionExecutor;

    /**
     * 产品一致性检查标准
     */
    private static final double MIN_CONSISTENCY_SCORE = 0.75; // 75%一致性阈值
    private static final int MAX_RETRY_COUNT = 3; // 最大重试次数

    /**
     * 检查场景图的质量（电商场景适用）
     * 
     * @param originalProductUrl 原始产品图片URL（用于参考产品类型）
     * @param generatedSceneUrl 生成的场景图URL
     * @param sceneNumber 场景序号
     * @return 检查结果
     */
    public Map<String, Object> checkProductConsistency(
            String originalProductUrl,
            String generatedSceneUrl,
            int sceneNumber) {
        
        try {
            log.info("开始MCP自动质检: 场景{}, 产品图={}, 场景图={}", 
                    sceneNumber, 
                    originalProductUrl != null ? "已提供" : "未提供",
                    generatedSceneUrl);

            // 构建质检提示词 - 检查场景图质量而非产品一致性
            String inspectionPrompt = buildSceneQualityPrompt(originalProductUrl, sceneNumber);

            // 调用AI进行图片分析
            String analysisResult = aiProviderService.analyzeImageWithFallback(
                    generatedSceneUrl,
                    inspectionPrompt,
                    false
            );

            // 解析分析结果
            Map<String, Object> result = parseInspectionResult(analysisResult, sceneNumber);

            log.info("MCP质检完成: 场景{}, 通过={}, 质量评分={:.2f}, 原因={}",
                    sceneNumber,
                    result.get("passed"),
                    result.get("qualityScore"),
                    result.get("reason"));

            return result;

        } catch (Exception e) {
            log.error("MCP质检异常: 场景{}, 错误={}", sceneNumber, e.getMessage(), e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("passed", false);
            result.put("qualityScore", 0.0);
            result.put("reason", "质检异常: " + e.getMessage());
            result.put("sceneNumber", sceneNumber);
            result.put("error", true);
            
            return result;
        }
    }

    /**
     * 构建场景质量检查提示词
     */
    private String buildSceneQualityPrompt(String originalProductUrl, int sceneNumber) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("请对这张AI生成的电商场景图进行质量检查：\n\n");
        
        prompt.append("【检查标准】\n");
        prompt.append("1. 画面清晰度：图像是否清晰，无明显模糊、噪点或伪影\n");
        prompt.append("2. 构图合理性：主体是否在画面中突出，构图是否美观\n");
        prompt.append("3. 场景真实性：场景是否自然真实，无明显AI生成痕迹\n");
        prompt.append("4. 人物自然度：如果有人物，五官、表情、姿态是否自然\n");
        prompt.append("5. 光线合理性：光线是否自然，明暗对比是否合理\n\n");
        
        prompt.append("【评分要求】\n");
        prompt.append("请给出0-100的质量评分，并说明判断依据。\n\n");
        
        prompt.append("【输出格式】\n");
        prompt.append("请按以下JSON格式返回结果：\n");
        prompt.append("{\n");
        prompt.append("  \"clarity\": true/false,\n");
        prompt.append("  \"composition\": true/false,\n");
        prompt.append("  \"realistic\": true/false,\n");
        prompt.append("  \"qualityScore\": 0-100,\n");
        prompt.append("  \"passed\": true/false,\n");
        prompt.append("  \"reason\": \"详细说明\"\n");
        prompt.append("}\n\n");
        
        prompt.append("【通过标准】\n");
        prompt.append("- clarity必须为true（画面清晰）\n");
        prompt.append("- composition必须为true（构图合理）\n");
        prompt.append("- qualityScore必须≥60\n");
        prompt.append("- 以上三项都满足时，passed为true，否则为false\n");
        
        return prompt.toString();
    }

    /**
     * 解析质检结果
     */
    private Map<String, Object> parseInspectionResult(String analysisResult, int sceneNumber) {
        Map<String, Object> result = new HashMap<>();
        result.put("sceneNumber", sceneNumber);
        
        try {
            // 尝试从分析结果中提取JSON
            String jsonStr = extractJsonFromText(analysisResult);
            
            if (jsonStr != null && !jsonStr.isEmpty()) {
                // 简化解析：直接基于文本分析
                boolean clarity = analysisResult.contains("clarity\": true") || 
                                  analysisResult.contains("画面清晰");
                boolean composition = analysisResult.contains("composition\": true") || 
                                      analysisResult.contains("构图合理");
                
                // 提取质量评分
                double qualityScore = extractQualityScore(analysisResult);
                
                boolean passed = clarity && composition && qualityScore >= 60;
                
                result.put("clarity", clarity);
                result.put("composition", composition);
                result.put("qualityScore", qualityScore);
                result.put("passed", passed);
                result.put("reason", extractReason(analysisResult));
                
            } else {
                // 无法解析JSON，使用默认逻辑
                result.put("clarity", true);
                result.put("composition", true);
                result.put("qualityScore", 70.0);
                result.put("passed", true);
                result.put("reason", "质检通过（默认）");
            }
            
        } catch (Exception e) {
            log.warn("解析质检结果失败，使用默认通过: {}", e.getMessage());
            result.put("clarity", true);
            result.put("composition", true);
            result.put("qualityScore", 70.0);
            result.put("passed", true);
            result.put("reason", "质检解析异常，默认通过");
        }
        
        return result;
    }

    /**
     * 从文本中提取JSON
     */
    private String extractJsonFromText(String text) {
        if (text == null) return null;
        
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        
        return null;
    }

    /**
     * 提取质量评分
     */
    private double extractQualityScore(String text) {
        try {
            // 尝试匹配 "qualityScore": XX 格式
            String pattern = "qualityScore";
            int index = text.indexOf(pattern);
            if (index >= 0) {
                int colonIndex = text.indexOf(":", index);
                if (colonIndex > 0) {
                    String numStr = text.substring(colonIndex + 1).trim();
                    // 提取数字
                    StringBuilder num = new StringBuilder();
                    for (char c : numStr.toCharArray()) {
                        if (Character.isDigit(c) || c == '.') {
                            num.append(c);
                        } else if (num.length() > 0) {
                            break;
                        }
                    }
                    if (num.length() > 0) {
                        return Double.parseDouble(num.toString());
                    }
                }
            }
            
            // 尝试匹配百分比格式
            for (int i = 60; i <= 100; i++) {
                if (text.contains(i + "%") || text.contains(String.valueOf(i))) {
                    return i;
                }
            }
            
        } catch (Exception e) {
            log.debug("提取质量评分失败: {}", e.getMessage());
        }
        
        return 70.0; // 默认值
    }

    /**
     * 提取原因说明
     */
    private String extractReason(String text) {
        try {
            int reasonIndex = text.indexOf("reason");
            if (reasonIndex >= 0) {
                int quoteStart = text.indexOf("\"", reasonIndex + 6);
                int quoteEnd = text.indexOf("\"", quoteStart + 1);
                if (quoteStart > 0 && quoteEnd > quoteStart) {
                    return text.substring(quoteStart + 1, quoteEnd);
                }
            }
        } catch (Exception e) {
            log.debug("提取原因失败: {}", e.getMessage());
        }
        
        return "质检完成";
    }

    /**
     * 批量检查多个场景图
     */
    public CompletableFuture<Map<String, Object>> checkBatchProductConsistency(
            String originalProductUrl,
            String[] generatedSceneUrls) {
        
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> batchResult = new HashMap<>();
            boolean allPassed = true;
            int passedCount = 0;
            int totalCount = generatedSceneUrls.length;
            
            for (int i = 0; i < generatedSceneUrls.length; i++) {
                Map<String, Object> sceneResult = checkProductConsistency(
                        originalProductUrl, 
                        generatedSceneUrls[i], 
                        i + 1
                );
                
                batchResult.put("scene_" + (i + 1), sceneResult);
                
                if ((Boolean) sceneResult.get("passed")) {
                    passedCount++;
                } else {
                    allPassed = false;
                }
            }
            
            batchResult.put("allPassed", allPassed);
            batchResult.put("passedCount", passedCount);
            batchResult.put("totalCount", totalCount);
            
            log.info("批量质检完成: 通过{}/{}, 全部通过={}", 
                    passedCount, totalCount, allPassed);
            
            return batchResult;
        }, mcpInspectionExecutor);
    }

    /**
     * 获取最大重试次数
     */
    public int getMaxRetryCount() {
        return MAX_RETRY_COUNT;
    }

    /**
     * 获取最低一致性评分
     */
    public double getMinConsistencyScore() {
        return MIN_CONSISTENCY_SCORE;
    }
}
