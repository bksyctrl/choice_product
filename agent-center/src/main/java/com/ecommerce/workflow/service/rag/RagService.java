package com.ecommerce.workflow.service.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.ecommerce.workflow.entity.Knowledge;
import com.ecommerce.workflow.service.knowledge.KnowledgeService;

@Service
public class RagService {
    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private final KnowledgeService knowledgeService;

    public RagService(KnowledgeService knowledgeService, EmbeddingService embeddingService) {
        this.knowledgeService = knowledgeService;
    }

    public String enhanceQuery(String query, Map<String, Object> context) {
        log.info("RAG查询增强处理: {}", query);
        List<Knowledge> relevantKnowledge = retrieveRelevantKnowledge(query);
        String enhancedPrompt = buildEnhancedPrompt(query, relevantKnowledge);
        String contextStr = buildContextString(context);
        return buildPrompt(enhancedPrompt, contextStr);
    }

    private List<Knowledge> retrieveRelevantKnowledge(String query) {
        List<Knowledge> allKnowledge = new ArrayList<>();
        allKnowledge.addAll(knowledgeService.getByType("SUCCESS_EXPERIENCE"));
        allKnowledge.addAll(knowledgeService.getByType("FAILURE_LESSON"));
        allKnowledge.addAll(knowledgeService.getByType("PATTERN"));
        allKnowledge.addAll(knowledgeService.getByType("RULE"));
        allKnowledge.addAll(knowledgeService.getByType("INSIGHT"));
        allKnowledge.addAll(knowledgeService.getByType("BEST_PRACTICE"));
        List<Knowledge> relevant = new ArrayList<>();
        for (Knowledge k : allKnowledge) {
            double score = calculateRelevanceScore(query, k);
            if (score > 0.3) {
                relevant.add(k);
            }
        }
        relevant.sort((a, b) -> Double.compare(
                b.getConfidence() != null ? b.getConfidence() : 0.0,
                a.getConfidence() != null ? a.getConfidence() : 0.0));
        return relevant;
    }

    private double calculateRelevanceScore(String query, Knowledge knowledge) {
        String combined = (knowledge.getTitle() + " " + knowledge.getContent()).toLowerCase();
        String[] queryWords = query.toLowerCase().split("\\s+");
        int matchCount = 0;
        for (String queryWord : queryWords) {
            if (combined.contains(queryWord)) {
                matchCount++;
            }
        }
        return (double) matchCount / (double) queryWords.length;
    }

    private String buildEnhancedPrompt(String query, List<Knowledge> knowledge) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("用户查询: ").append(query).append("\n");
        if (!knowledge.isEmpty()) {
            prompt.append("相关知识库:\n");
            for (Knowledge k : knowledge) {
                prompt.append("- ").append(k.getTitle()).append(": ").append(k.getContent()).append("\n");
            }
        } else {
            prompt.append("基于以下相关知识，请提供详细的分析和建议:");
        }
        return prompt.toString();
    }

    private String buildContextString(Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("上下文信息:\n");
        if (context != null && !context.isEmpty()) {
            context.forEach((key, value) -> sb.append("- ").append(key).append(": ").append(value).append("\n"));
        }
        return sb.toString();
    }

    private String buildPrompt(String enhancedPrompt, String contextStr) {
        return enhancedPrompt + "\n\n" + contextStr;
    }

    public Map<String, Object> getKnowledgeContext(List<String> knowledgeIds) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> knowledgeList = new ArrayList<>();
        for (String id : knowledgeIds) {
            Knowledge k = knowledgeService.getKnowledge(id);
            if (k != null) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", k.getKnowledgeId());
                item.put("title", k.getTitle());
                item.put("type", k.getType());
                item.put("confidence", k.getConfidence());
                knowledgeList.add(item);
            }
        }
        result.put("knowledge", knowledgeList);
        return result;
    }
}
