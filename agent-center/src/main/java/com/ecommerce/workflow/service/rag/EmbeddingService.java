package com.ecommerce.workflow.service.rag;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.ecommerce.workflow.service.ai.AiProviderService;
import com.ecommerce.workflow.service.config.SysConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class EmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private final AiProviderService aiProviderService;
    private final ObjectMapper objectMapper;
    private final SysConfigService sysConfigService;
    private final Map<String, float[]> embeddingCache = new ConcurrentHashMap<>();
    @Value("${rag.top-k:5}")
    private int topK;
    @Value("${rag.cache.enabled:true}")
    private boolean cacheEnabled;
    public EmbeddingService(AiProviderService aiProviderService, ObjectMapper objectMapper, SysConfigService sysConfigService) {
        this.aiProviderService = aiProviderService;
        this.objectMapper = objectMapper;
        this.sysConfigService = sysConfigService;
    }
    public float[] embed(String text) {
        if (!cacheEnabled) {
            return generateSimpleEmbedding(text);
        }
        String cacheKey = "emb_" + text.hashCode();
        float[] cached = embeddingCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        float[] embedding = generateEmbedding(text);
        if (embedding != null) {
            embeddingCache.put(cacheKey, embedding);
        }
        return embedding;
    }
    private float[] generateEmbedding(String text) {
        log.debug("生成文本嵌入向量: {}", text);
        // 不再用聊天模型伪造 embedding。聊天接口延迟高、格式不稳定，会阻塞文件读取/数据库查询等确定性任务。
        // 后续接入真实 embedding provider 时，只需要在这里替换为专用 embedding API。
        return generateSimpleEmbedding(text);
    }
    private float[] generateSimpleEmbedding(String text) {
        int dimension = sysConfigService.getIntConfig("embedding_fallback_dimension", 384);
        float[] embedding = new float[dimension];
        String normalized = text.toLowerCase().trim();
        if (normalized.isEmpty()) {
            return embedding;
        }
        for (int i = 0; i < dimension; i++) {
            int charIndex = i % normalized.length();
            embedding[i] = (float) charIndex / 127.0f;
        }
        return embedding;
    }
    private List<Double> parseEmbeddingResponse(String response) {
        try {
            if (response != null && response.contains("[") && response.contains("]")) {
                int start = response.indexOf("[");
                int end = response.lastIndexOf("]") + 1;
                String jsonArray = response.substring(start, end);
                return objectMapper.readValue(jsonArray, new TypeReference<List<Double>>() {});
            }
        } catch (Exception e) {
            log.warn("解析嵌入向量响应失败", e);
        }
        return new ArrayList<>();
    }
    public void clearCache() {
        embeddingCache.clear();
        log.info("嵌入向量缓存已清空");
    }
    public int getCacheSize() {
        return embeddingCache.size();
    }
}
