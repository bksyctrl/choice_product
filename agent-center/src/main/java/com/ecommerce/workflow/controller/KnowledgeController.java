package com.ecommerce.workflow.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.workflow.entity.Knowledge;
import com.ecommerce.workflow.mapper.KnowledgeMapper;
import com.ecommerce.workflow.service.knowledge.KnowledgeService;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    
    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);
    
    @Autowired
    private KnowledgeService knowledgeService;
    
    @Autowired
    private KnowledgeMapper knowledgeMapper;
    
    @PostMapping("/store")
    public ApiResponse<Knowledge> storeKnowledge(@RequestBody Knowledge knowledge) {
        log.info("存储知识: title={}, type={}", knowledge.getTitle(), knowledge.getType());
        try {
            Knowledge stored = knowledgeService.storeKnowledge(knowledge);
            return ApiResponse.success(stored);
        } catch (Exception e) {
            log.error("存储知识失败", e);
            return ApiResponse.error("存储知识失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/get/{knowledgeId}")
    public ApiResponse<Knowledge> getKnowledge(@PathVariable String knowledgeId) {
        log.info("获取知识: knowledgeId={}", knowledgeId);
        Knowledge knowledge = null;
        
        try {
            knowledge = knowledgeService.getKnowledge(knowledgeId);
            if (knowledge == null) {
                Long id = Long.parseLong(knowledgeId);
                knowledge = knowledgeMapper.selectById(id);
            }
        } catch (NumberFormatException e) {
            // knowledgeId可能是字符串格式，尝试使用knowledgeService查询
        }
        
        if (knowledge == null || knowledge.getDeleted() == 1) {
            return ApiResponse.error("知识不存在");
        }
        return ApiResponse.success(knowledge);
    }
    
    @GetMapping("/search")
    public ApiResponse<List<Knowledge>> searchKnowledge(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        log.info("搜索知识: query={}, topK={}", query, topK);
        try {
            List<Knowledge> results = knowledgeService.searchSimilar(query, topK);
            return ApiResponse.success(results);
        } catch (Exception e) {
            log.error("搜索知识失败", e);
            return ApiResponse.error("搜索知识失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/search")
    public ApiResponse<List<Knowledge>> searchKnowledgePost(@RequestBody SearchRequest request) {
        log.info("搜索知识(Post): query={}, topK={}", request.getQuery(), request.getTopK());
        try {
            int topK = request.getTopK() > 0 ? request.getTopK() : 5;
            List<Knowledge> results = knowledgeService.searchSimilar(request.getQuery(), topK);
            return ApiResponse.success(results);
        } catch (Exception e) {
            log.error("搜索知识失败", e);
            return ApiResponse.error("搜索知识失败: " + e.getMessage());
        }
    }
    
    public static class SearchRequest {
        private String query;
        private int topK = 5;
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
    }
    
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> listKnowledge(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String keyword) {
        
        int safeSize = Math.min(Math.max(size, 1), 20);
        int safePage = Math.max(page, 1);
        
        log.info("获取知识列表: page={}, size={}, type={}, source={}, keyword={}", safePage, safeSize, type, source, keyword);
        
        try {
            Page<Knowledge> pageObj = new Page<>(safePage, safeSize);
            QueryWrapper<Knowledge> wrapper = new QueryWrapper<>();
            wrapper.eq("deleted", 0);
            
            if (type != null && !type.isEmpty()) {
                wrapper.eq("type", type);
            }
            if (source != null && !source.isEmpty()) {
                wrapper.eq("source", source);
            }
            if (keyword != null && !keyword.isEmpty()) {
                wrapper.and(w -> w.like("title", keyword).or().like("content", keyword));
            }
            
            wrapper.orderByDesc("created_at");
            
            Page<Knowledge> result = knowledgeMapper.selectPage(pageObj, wrapper);
            
            Map<String, Object> response = new HashMap<>();
            response.put("list", result.getRecords());
            response.put("total", result.getTotal());
            response.put("page", safePage);
            response.put("size", safeSize);
            
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("获取知识列表失败", e);
            return ApiResponse.error("获取知识列表失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/by-type/{type}")
    public ApiResponse<List<Knowledge>> getByType(@PathVariable String type) {
        log.info("按类型查询知识: type={}", type);
        List<Knowledge> knowledge = knowledgeService.getByType(type);
        return ApiResponse.success(knowledge);
    }
    
    @GetMapping("/by-source/{source}")
    public ApiResponse<List<Knowledge>> getBySource(@PathVariable String source) {
        log.info("按来源查询知识: source={}", source);
        List<Knowledge> knowledge = knowledgeService.getBySource(source);
        return ApiResponse.success(knowledge);
    }
    
    @PostMapping("/record-application")
    public ApiResponse<String> recordApplication(
            @RequestParam String knowledgeId,
            @RequestParam boolean success) {
        log.info("记录知识应用: knowledgeId={}, success={}", knowledgeId, success);
        knowledgeService.recordApplication(knowledgeId, success);
        return ApiResponse.success("记录应用成功");
    }
    
    @DeleteMapping("/delete/{knowledgeId}")
    public ApiResponse<String> deleteKnowledge(@PathVariable String knowledgeId) {
        log.info("删除知识: knowledgeId={}", knowledgeId);
        Knowledge knowledge = knowledgeService.getKnowledge(knowledgeId);
        if (knowledge == null) {
            return ApiResponse.error("知识点不存在");
        }
        knowledge.setDeleted(1);
        knowledgeMapper.updateById(knowledge);
        return ApiResponse.success("删除成功");
    }
    
    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> getStatistics() {
        log.info("获取知识统计信息");
        
        try {
            long totalCount = knowledgeMapper.selectCount(
                new QueryWrapper<Knowledge>().eq("deleted", 0)
            );
            
            long promptCount = knowledgeMapper.selectCount(
                new QueryWrapper<Knowledge>()
                    .eq("deleted", 0)
                    .eq("type", "PROMPT_TEMPLATE")
            );
            
            long knowledgeCount = knowledgeMapper.selectCount(
                new QueryWrapper<Knowledge>()
                    .eq("deleted", 0)
                    .eq("type", "GENERAL")
            );
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("total", totalCount);
            stats.put("promptTemplates", promptCount);
            stats.put("knowledge", knowledgeCount);
            
            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("閼惧嘲褰囩紒鐔活吀娣団剝浼呮径杈Е", e);
            return ApiResponse.error("閼惧嘲褰囩紒鐔活吀娣団剝浼呮径杈Е: " + e.getMessage());
        }
    }
}
