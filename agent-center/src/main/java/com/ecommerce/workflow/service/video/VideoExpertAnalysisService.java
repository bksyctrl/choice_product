package com.ecommerce.workflow.service.video;

import com.ecommerce.workflow.entity.AiVideoConfig;
import com.ecommerce.workflow.entity.ExpertRoleConfig;
import com.ecommerce.workflow.service.ai.AiProviderService;
import com.ecommerce.workflow.service.ai.AiRateLimiter;
import com.ecommerce.workflow.service.ai.PromptBuilderService;
import com.ecommerce.workflow.service.cache.ImageAnalysisCache;
import com.ecommerce.workflow.service.learning.ExpertRoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class VideoExpertAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(VideoExpertAnalysisService.class);

    @Value("${app.upload.path:./uploads}")
    private String uploadPath;

    @Autowired
    private ExpertRoleService expertRoleService;

    @Autowired
    private AiProviderService aiProviderService;

    @Autowired
    private PromptBuilderService promptBuilderService;

    @Autowired
    private ImageAnalysisCache imageAnalysisCache;

    @Autowired
    private AiRateLimiter aiRateLimiter;

    @Autowired
    @Qualifier("expertAnalysisExecutor")
    private Executor expertAnalysisExecutor;

    public Map<String, Object> analyzeImageWithExperts(String imageUrl, boolean isBase64) {
        Map<String, Object> result = new HashMap<>();

        try {
            String cacheKey = null;
            if (isBase64 && imageUrl != null && imageUrl.length() > 100) {
                cacheKey = ImageAnalysisCache.computeHash(imageUrl.substring(0, Math.min(5000, imageUrl.length())));
                Map<String, Object> cached = imageAnalysisCache.get(cacheKey);
                if (cached != null) {
                    return cached;
                }
            }

            String combinedPrompt = "请对这张图片进行详细分析，重点关注产品细节以防止视频生成时出现穿模或变形。请按以下结构输出：\n\n" +
                    "【产品识别与分类】\n" +
                    "- 产品类型：客观描述这是什么产品（不要预设类型，根据实际图像识别）\n" +
                    "- 产品数量：图片中有几个产品/几个主体（精确计数）\n" +
                    "- 产品布局：每个产品在画面中的具体位置和排列方式\n\n" +
                    "【产品详细描述 - 像描述人物一样详细】\n" +
                    "请像描述一个人的外貌特征一样，详细描述这个产品的视觉特征：\n\n" +
                    "- 整体轮廓：形状、长宽比例、三维结构、边缘弧度\n" +
                    "- 尺寸感知：在画面中的大小、厚薄、立体感、层次感\n" +
                    "- 颜色系统：主色调、配色方案、渐变/纯色、明暗分布\n" +
                    "- 材质表现：表面光滑/磨砂/透明/金属/织物/皮革等质感\n" +
                    "- 纹理细节：是否有图案、印花、文字、Logo、颗粒感、光泽度\n" +
                    "- 结构特征：分层设计、拼接缝隙、边缘处理、转角形状\n\n" +
                    "【多视角特征描述 - 防止两面一样的问题】\n" +
                    "根据当前画面视角，描述：\n" +
                    "- 当前可见面：这个面上有什么（图案、文字、按钮、开孔等）\n" +
                    "- 边缘/侧面：厚度、边缘形状、侧面是否有细节\n" +
                    "- 立体特征：凸起部分、凹陷部分、曲面/平面分布\n" +
                    "- 可推断的背面：基于当前视图，背面可能是什么样子（不要编造）\n\n" +
                    "【防穿模/防变形关键约束】\n" +
                    "- 对称性：产品是否为对称设计，左右/上下是否一致\n" +
                    "- 透明/镂空：是否有透明部分、镂空设计、半透明材质\n" +
                    "- 堆叠关系：多个产品之间是否有遮挡、堆叠、重叠\n" +
                    "- 边界清晰度：产品边缘与背景的分界是否明显\n" +
                    "- 正反面差异：产品正面和背面是否有明显区别（防止生成两面一样）\n\n" +
                    "【视觉环境】\n" +
                    "- 光线：光源方向、阴影位置、反光区域\n" +
                    "- 背景：背景类型、与产品的对比度\n\n" +
                    "请尽可能详细客观描述，不要预设产品类型，完全基于图像实际内容。详细的产品描述是防止视频生成出现变形、穿模、两面一样的关键。";

            // 处理图片URL：如果是本地URL，转换为base64
            String imageUrlForAnalysis = imageUrl;
            boolean isBase64ForAnalysis = isBase64;
            
            // 检查是否是本地URL（不以http开头，或者是localhost/127.0.0.1）
            boolean isLocalUrl = !isBase64 && imageUrl != null && 
                    (!imageUrl.startsWith("http") || 
                     imageUrl.contains("localhost") || 
                     imageUrl.contains("127.0.0.1"));
            
            if (isLocalUrl) {
                log.info("检测到本地图片路径，转换为base64格式以便AI分析: {}", imageUrl);
                try {
                    String localPath = resolveImagePath(imageUrl);
                    File imageFile = new File(localPath);
                    if (imageFile.exists()) {
                        byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
                        String base64 = Base64.getEncoder().encodeToString(imageBytes);
                        String mimeType = getMimeType(imageFile.getName());
                        imageUrlForAnalysis = "data:" + mimeType + ";base64," + base64;
                        isBase64ForAnalysis = true;
                        log.info("本地图片转换为base64成功: {} bytes", imageBytes.length);
                    } else {
                        log.warn("本地图片文件不存在: {}", localPath);
                    }
                } catch (Exception e) {
                    log.error("本地图片转换为base64失败: {}", e.getMessage());
                }
            }
            
            String combinedAnalysis = aiProviderService.analyzeImageWithFallback(
                    imageUrlForAnalysis, combinedPrompt, isBase64ForAnalysis);
            
            result.put("basicAnalysis", combinedAnalysis);
            result.put("combinedExpertAdvice", combinedAnalysis);
            result.put("visualDesignerAdvice", combinedAnalysis);
            result.put("imageAnalystAdvice", combinedAnalysis);
            result.put("productManagerAdvice", combinedAnalysis);

            if (cacheKey != null) {
                imageAnalysisCache.put(cacheKey, new HashMap<>(result));
            }

            log.info("图片分析完成(合并调用): 长度={}", combinedAnalysis != null ? combinedAnalysis.length() : 0);

        } catch (Exception e) {
            log.error("图片分析失败: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
        }

        return result;
    }

    public String getExpertAdviceForVideo(AiVideoConfig config) {
        StringBuilder advice = new StringBuilder();

        try {
            List<String> experts = matchExpertsForVideo(config);
            String context = buildExpertContext(config);

            List<CompletableFuture<Map.Entry<String, String>>> futures = new ArrayList<>();
            
            for (String expertCode : experts) {
                CompletableFuture<Map.Entry<String, String>> future = CompletableFuture.supplyAsync(() -> {
                    ExpertRoleConfig role = expertRoleService.getExpertRole(expertCode);
                    String expertName = role != null ? role.getRoleName() : expertCode;
                    String expertAdvice = analyzeWithExpertRole(expertCode, context);
                    return new AbstractMap.SimpleEntry<>(expertName, expertAdvice);
                }, expertAnalysisExecutor);
                futures.add(future);
            }

            for (CompletableFuture<Map.Entry<String, String>> future : futures) {
                try {
                    Map.Entry<String, String> entry = future.join();
                    if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                        advice.append("【").append(entry.getKey()).append("】专业建议:\n");
                        advice.append(entry.getValue()).append("\n\n");
                    }
                } catch (Exception e) {
                    log.warn("专家分析异常: {}", e.getMessage());
                }
            }

            log.info("视频专家建议生成完成: 参与专家数={}, 建议长度={}", experts.size(), advice.length());

        } catch (Exception e) {
            log.error("视频专家建议生成失败: {}", e.getMessage(), e);
        }

        return advice.toString();
    }

    private String analyzeWithExpertRole(String expertCode, String context) {
        try {
            ExpertRoleConfig role = expertRoleService.getExpertRole(expertCode);
            if (role == null || !"ACTIVE".equals(role.getStatus())) {
                log.warn("专家角色不存在或未激活: {}", expertCode);
                return null;
            }

            String analysisPrompt = buildExpertAnalysisPrompt(role, context);

            String aiResponse = aiProviderService.chatWithHistory(
                    "你是世界顶级的" + role.getRoleName() + "，请根据以下上下文进行专业分析。",
                    analysisPrompt,
                    null,
                    0.7,
                    2000);

            return aiResponse;

        } catch (Exception e) {
            log.error("专家分析失败: role={}, error={}", expertCode, e.getMessage(), e);
            return null;
        }
    }

    private String buildExpertAnalysisPrompt(ExpertRoleConfig role, String context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("请从").append(role.getRoleName()).append("的专业角度分析以下内容:\n\n");
        prompt.append("分析上下文:\n").append(context).append("\n\n");

        prompt.append("请从以下维度进行分析:\n");
        List<String> dimensions = expertRoleService.getExpertCoreDimensions(role.getRoleCode());
        if (dimensions != null && !dimensions.isEmpty()) {
            for (String dimension : dimensions) {
                prompt.append("- ").append(dimension).append("\n");
            }
        }

        prompt.append("\n请给出具体、可操作的专业建议。");

        return prompt.toString();
    }

    private List<String> matchExpertsForVideo(AiVideoConfig config) {
        List<String> experts = new ArrayList<>();

        experts.add("visual_designer");
        experts.add("video_editor");

        String topic = config.getTopic() != null ? config.getTopic().toLowerCase() : "";
        String scene = config.getScene() != null ? config.getScene().toLowerCase() : "";

        if (topic.contains("产品") || topic.contains("商品") || scene.contains("展示")) {
            experts.add("product_manager");
            experts.add("copywriter");
        }

        if (topic.contains("情感") || topic.contains("故事") || topic.contains("剧情")) {
            experts.add("director");
            experts.add("copywriter");
            experts.add("marketing_planner");
        }

        if (topic.contains("热点") || topic.contains("趋势") || topic.contains("运营")) {
            experts.add("operations_expert");
            experts.add("data_analyst");
            experts.add("marketing_planner");
        }

        if (topic.contains("测评") || topic.contains("开箱") || topic.contains("评测")) {
            experts.add("product_manager");
            experts.add("director");
            experts.add("visual_designer");
        }

        if (topic.contains("避坑") || topic.contains("指南") || topic.contains("教程")) {
            experts.add("operations_expert");
            experts.add("data_analyst");
            experts.add("copywriter");
        }

        if (experts.size() < 3) {
            if (!experts.contains("director")) experts.add("director");
            if (!experts.contains("copywriter")) experts.add("copywriter");
        }

        return experts.stream().distinct().collect(Collectors.toList());
    }

    private String buildExpertContext(AiVideoConfig config) {
        StringBuilder context = new StringBuilder();

        context.append("视频配置信息:\n");
        context.append("- 主题: ").append(config.getTopic()).append("\n");
        context.append("- 人物: ").append(config.getRace()).append(" ").append(config.getRole()).append("\n");
        context.append("- 场景: ").append(config.getSceneType()).append(" ").append(config.getScene()).append("\n");
        context.append("- 运镜: ").append(config.getFrameType()).append("\n");
        context.append("- 时长: ").append(config.getDuration()).append("秒\n");
        context.append("- 画面比例: ").append(config.getAspectRatio()).append("\n");
        context.append("- 分辨率: ").append(config.getResolution()).append("\n");
        context.append("- 风格强度: ").append(config.getStyleIntensity()).append("%\n");
        context.append("- 创意度: ").append(config.getCreativity()).append("%\n");

        return context.toString();
    }

    private String combineExpertAdvice(String visual, String image, String product) {
        StringBuilder combined = new StringBuilder();

        combined.append("=== 专家联合分析结果 ===\n\n");

        if (visual != null && !visual.isEmpty()) {
            combined.append("【视觉设计师】\n").append(visual).append("\n\n");
        }
        if (image != null && !image.isEmpty()) {
            combined.append("【图片分析师】\n").append(image).append("\n\n");
        }
        if (product != null && !product.isEmpty()) {
            combined.append("【产品经理】\n").append(product).append("\n\n");
        }

        combined.append("=== 分析结束 ===");

        return combined.toString();
    }

    /**
     * 解析图片URL为本地文件路径
     */
    private String resolveImagePath(String imageUrl) {
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            return imageUrl;
        }

        String path = imageUrl;
        if (path.startsWith("/uploads/")) {
            path = uploadPath + path.substring(8);
        } else if (path.startsWith("/api/files/")) {
            path = uploadPath + "/" + path.substring(11);
        } else if (!new File(path).isAbsolute()) {
            path = uploadPath + "/" + path;
        }

        return path;
    }

    /**
     * 根据文件名获取MIME类型
     */
    private String getMimeType(String fileName) {
        String ext = fileName.toLowerCase();
        if (ext.endsWith(".png")) return "image/png";
        if (ext.endsWith(".gif")) return "image/gif";
        if (ext.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
