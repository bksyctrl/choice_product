package com.ecommerce.workflow.node;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.workflow.engine.*;
import com.ecommerce.workflow.entity.CaseMemory;
import com.ecommerce.workflow.entity.Knowledge;
import com.ecommerce.workflow.mapper.CaseMemoryMapper;
import com.ecommerce.workflow.mapper.KnowledgeMapper;
import com.ecommerce.workflow.service.rag.EmbeddingService;
import com.ecommerce.workflow.service.vector.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SimilarSearchNode implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(SimilarSearchNode.class);
    
    private final CaseMemoryMapper caseMemoryMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    
    public SimilarSearchNode(CaseMemoryMapper caseMemoryMapper,
                            KnowledgeMapper knowledgeMapper,
                            EmbeddingService embeddingService,
                            VectorSearchService vectorSearchService) {
        this.caseMemoryMapper = caseMemoryMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.embeddingService = embeddingService;
        this.vectorSearchService = vectorSearchService;
    }

    @Override
    public String getNodeCode() {
        return "similar_search";
    }

    @Override
    public NodeResult execute(ExecutionContext context) throws Exception {
        log.info("执行相似产品搜索节点");

        String productInfo = context.getVariable("productInfo") != null ?
                context.getVariable("productInfo").toString() : "";
        String category = context.getVariable("category") != null ?
                context.getVariable("category").toString() : "";
        int topK = context.getVariable("topK") != null ?
                (Integer) context.getVariable("topK") : 10;

        log.info("搜索参数: productInfo={}, category={}, topK={}", productInfo, category, topK);

        List<Map<String, Object>> similarProducts = searchSimilarProducts(productInfo, category, topK);

        Map<String, Object> result = new HashMap<>();
        result.put("similarProducts", similarProducts);
        result.put("totalCount", similarProducts.size());
        result.put("searchQuery", productInfo);
        context.setNodeOutput(getNodeCode(), result);

        log.info("相似产品搜索完成: 找到{}个相似产品", similarProducts.size());
        return NodeResult.success(result);
    }

    private List<Map<String, Object>> searchSimilarProducts(String productInfo, String category, int topK) {
        List<Map<String, Object>> products = new ArrayList<>();
        
        try {
            float[] queryEmbedding = embeddingService.embed(productInfo);
            
            if (queryEmbedding != null && queryEmbedding.length > 0) {
                List<Float> queryVector = new ArrayList<>();
                for (float f : queryEmbedding) {
                    queryVector.add(f);
                }
                
                List<VectorSearchService.MemorySearchResult> vectorResults = 
                    vectorSearchService.searchByVector(queryVector, topK);
                
                for (VectorSearchService.MemorySearchResult vr : vectorResults) {
                    Map<String, Object> product = new HashMap<>();
                    product.put("id", vr.getMemoryId());
                    product.put("content", vr.getContent());
                    product.put("similarity", 1.0 - vr.getScore());
                    product.put("source", "vector_search");
                    products.add(product);
                }
            }
        } catch (Exception e) {
            log.warn("向量搜索失败，回退到数据库搜索: {}", e.getMessage());
        }
        
        if (products.isEmpty()) {
            products.addAll(searchFromCaseMemory(productInfo, category, topK));
        }
        
        if (products.size() < topK) {
            products.addAll(searchFromKnowledge(productInfo, category, topK - products.size()));
        }
        
        products.sort((a, b) -> Double.compare(
            (Double) b.get("similarity"),
            (Double) a.get("similarity")
        ));
        
        if (products.size() > topK) {
            products = products.subList(0, topK);
        }
        
        return products;
    }
    
    private List<Map<String, Object>> searchFromCaseMemory(String productInfo, String category, int limit) {
        List<Map<String, Object>> products = new ArrayList<>();
        
        QueryWrapper<CaseMemory> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0)
               .eq("quality_tag", "SUCCESS")
               .orderByDesc("cvr")
               .last("LIMIT " + limit);
        
        if (category != null && !category.isEmpty()) {
            wrapper.like("product_name", category);
        }
        
        if (productInfo != null && !productInfo.isEmpty()) {
            wrapper.and(w -> w.like("product_name", productInfo)
                             .or().like("lesson_learned", productInfo));
        }
        
        List<CaseMemory> cases = caseMemoryMapper.selectList(wrapper);
        
        for (CaseMemory caseMemory : cases) {
            Map<String, Object> product = new HashMap<>();
            product.put("id", caseMemory.getId());
            product.put("name", caseMemory.getProductName());
            product.put("caseNo", caseMemory.getCaseNo());
            product.put("cvr", caseMemory.getCvr() != null ? caseMemory.getCvr() : 0.0);
            product.put("gmv", caseMemory.getGmv());
            product.put("similarity", calculateTextSimilarity(productInfo, caseMemory.getProductName()));
            product.put("source", "case_memory");
            product.put("lessonLearned", caseMemory.getLessonLearned());
            products.add(product);
        }
        
        return products;
    }
    
    private List<Map<String, Object>> searchFromKnowledge(String productInfo, String category, int limit) {
        List<Map<String, Object>> products = new ArrayList<>();
        
        QueryWrapper<Knowledge> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0)
               .eq("status", "ACTIVE")
               .orderByDesc("confidence")
               .last("LIMIT " + limit);
        
        if (productInfo != null && !productInfo.isEmpty()) {
            wrapper.and(w -> w.like("title", productInfo)
                             .or().like("content", productInfo));
        }
        
        List<Knowledge> knowledgeList = knowledgeMapper.selectList(wrapper);
        
        for (Knowledge knowledge : knowledgeList) {
            Map<String, Object> product = new HashMap<>();
            product.put("id", knowledge.getId());
            product.put("knowledgeId", knowledge.getKnowledgeId());
            product.put("name", knowledge.getTitle());
            product.put("content", knowledge.getContent());
            product.put("confidence", knowledge.getConfidence());
            product.put("similarity", calculateTextSimilarity(productInfo, knowledge.getTitle()));
            product.put("source", "knowledge_base");
            product.put("type", knowledge.getType());
            products.add(product);
        }
        
        return products;
    }
    
    private double calculateTextSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) return 0.0;
        if (text1.isEmpty() || text2.isEmpty()) return 0.0;
        
        Set<String> words1 = new HashSet<>(Arrays.asList(text1.toLowerCase().split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(text2.toLowerCase().split("\\s+")));
        
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);
        
        if (union.isEmpty()) return 0.0;
        
        return (double) intersection.size() / union.size();
    }
}
