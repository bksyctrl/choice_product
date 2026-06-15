package com.ecommerce.workflow.service.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.workflow.entity.MemoryRelation;
import com.ecommerce.workflow.mapper.MemoryRelationMapper;
import com.ecommerce.workflow.service.rag.EmbeddingService;
import com.ecommerce.workflow.service.vector.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class MemoryRelationService {
    
    private static final Logger log = LoggerFactory.getLogger(MemoryRelationService.class);
    
    @Autowired
    private MemoryRelationMapper memoryRelationMapper;
    
    @Autowired(required = false)
    private EmbeddingService embeddingService;
    
    @Autowired(required = false)
    private VectorSearchService vectorSearchService;
    
    @Autowired
    private WhiteBoxMemoryService whiteBoxMemoryService;
    
    @Value("${memory.relation.auto-discover:true}")
    private boolean autoDiscover;
    
    @Value("${memory.relation.similarity-threshold:0.75}")
    private double similarityThreshold;
    
    @Value("${memory.relation.max-relations:10}")
    private int maxRelations;
    
    private final Map<Long, Set<Long>> relationCache = new ConcurrentHashMap<>();
    
    public void createRelation(Long sourceMemoryId, Long targetMemoryId, String relationType, 
                               Double strength, String source) {
        if (sourceMemoryId.equals(targetMemoryId)) {
            return;
        }
        
        LambdaQueryWrapper<MemoryRelation> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w
            .eq(MemoryRelation::getSourceMemoryId, sourceMemoryId)
            .eq(MemoryRelation::getTargetMemoryId, targetMemoryId)
        ).or(w -> w
            .eq(MemoryRelation::getSourceMemoryId, targetMemoryId)
            .eq(MemoryRelation::getTargetMemoryId, sourceMemoryId)
        );
        
        MemoryRelation existing = memoryRelationMapper.selectOne(wrapper);
        
        if (existing != null) {
            existing.setStrength(Math.max(existing.getStrength(), strength));
            existing.setAccessCount(existing.getAccessCount() + 1);
            existing.setLastAccessedAt(LocalDateTime.now());
            existing.setUpdatedAt(LocalDateTime.now());
            memoryRelationMapper.updateById(existing);
            log.debug("更新记忆关联: {} <-> {}, strength={}", sourceMemoryId, targetMemoryId, strength);
        } else {
            MemoryRelation relation = new MemoryRelation();
            relation.setSourceMemoryId(sourceMemoryId);
            relation.setTargetMemoryId(targetMemoryId);
            relation.setRelationType(relationType);
            relation.setStrength(strength);
            relation.setSource(source);
            relation.setAccessCount(1);
            relation.setLastAccessedAt(LocalDateTime.now());
            relation.setCreatedAt(LocalDateTime.now());
            relation.setUpdatedAt(LocalDateTime.now());
            memoryRelationMapper.insert(relation);
            log.debug("创建记忆关联: {} <-> {}, type={}, strength={}", 
                    sourceMemoryId, targetMemoryId, relationType, strength);
        }
        
        invalidateCache(sourceMemoryId);
        invalidateCache(targetMemoryId);
        
        whiteBoxMemoryService.addMemoryRelation(sourceMemoryId, targetMemoryId, strength);
    }
    
    public List<MemoryRelation> getRelatedMemories(Long memoryId) {
        return getRelatedMemories(memoryId, maxRelations);
    }
    
    public List<MemoryRelation> getRelatedMemories(Long memoryId, int limit) {
        Set<Long> cached = relationCache.get(memoryId);
        if (cached != null && cached.size() >= limit) {
            return memoryRelationMapper.findByMemoryId(memoryId)
                    .stream()
                    .limit(limit)
                    .collect(Collectors.toList());
        }
        
        List<MemoryRelation> relations = memoryRelationMapper.findByMemoryId(memoryId)
                .stream()
                .sorted((a, b) -> Double.compare(b.getStrength(), a.getStrength()))
                .limit(limit)
                .collect(Collectors.toList());
        
        Set<Long> relatedIds = relations.stream()
                .flatMap(r -> Arrays.asList(r.getSourceMemoryId(), r.getTargetMemoryId()).stream())
                .filter(id -> !id.equals(memoryId))
                .collect(Collectors.toSet());
        relationCache.put(memoryId, relatedIds);
        
        return relations;
    }
    
    public List<Long> getRelatedMemoryIds(Long memoryId, int limit) {
        return getRelatedMemories(memoryId, limit).stream()
                .flatMap(r -> Arrays.asList(r.getSourceMemoryId(), r.getTargetMemoryId()).stream())
                .filter(id -> !id.equals(memoryId))
                .distinct()
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    @Async
    public void discoverRelations(Long memoryId, String content) {
        if (!autoDiscover || embeddingService == null || vectorSearchService == null) {
            return;
        }
        
        try {
            float[] embedding = embeddingService.embed(content);
            if (embedding == null || embedding.length == 0) {
                return;
            }
            
            List<Float> queryVector = new ArrayList<>();
            for (float f : embedding) {
                queryVector.add(f);
            }
            
            List<VectorSearchService.MemorySearchResult> similar = 
                    vectorSearchService.searchByVector(queryVector, maxRelations + 1);
            
            for (VectorSearchService.MemorySearchResult result : similar) {
                if (result.getMemoryId().equals(memoryId)) {
                    continue;
                }
                
                double similarity = 1.0 / (1.0 + result.getScore());
                if (similarity >= similarityThreshold) {
                    createRelation(memoryId, result.getMemoryId(), "SEMANTIC_SIMILAR", 
                            similarity, "AUTO_DISCOVER");
                }
            }
            
            log.info("获取相似记忆完成: memoryId={}, 相似数量={}", memoryId, similar.size() - 1);
            
        } catch (Exception e) {
            log.error("自动发现记忆关联失败: memoryId={}", memoryId, e);
        }
    }
    
    public void recordAccess(Long memoryId) {
        List<MemoryRelation> relations = memoryRelationMapper.findByMemoryId(memoryId);
        for (MemoryRelation relation : relations) {
            relation.setAccessCount(relation.getAccessCount() + 1);
            relation.setLastAccessedAt(LocalDateTime.now());
            relation.setUpdatedAt(LocalDateTime.now());
            memoryRelationMapper.updateById(relation);
        }
    }
    
    public void strengthenRelation(Long sourceMemoryId, Long targetMemoryId, double increment) {
        LambdaQueryWrapper<MemoryRelation> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w
            .eq(MemoryRelation::getSourceMemoryId, sourceMemoryId)
            .eq(MemoryRelation::getTargetMemoryId, targetMemoryId)
        ).or(w -> w
            .eq(MemoryRelation::getSourceMemoryId, targetMemoryId)
            .eq(MemoryRelation::getTargetMemoryId, sourceMemoryId)
        );
        
        MemoryRelation relation = memoryRelationMapper.selectOne(wrapper);
        if (relation != null) {
            double newStrength = Math.min(1.0, relation.getStrength() + increment);
            relation.setStrength(newStrength);
            relation.setUpdatedAt(LocalDateTime.now());
            memoryRelationMapper.updateById(relation);
            
            whiteBoxMemoryService.addMemoryRelation(sourceMemoryId, targetMemoryId, newStrength);
        }
    }
    
    public Map<String, Object> getRelationStats(Long memoryId) {
        Map<String, Object> stats = new HashMap<>();
        
        List<MemoryRelation> relations = memoryRelationMapper.findByMemoryId(memoryId);
        
        stats.put("totalRelations", relations.size());
        
        Map<String, Long> byType = relations.stream()
                .collect(Collectors.groupingBy(MemoryRelation::getRelationType, Collectors.counting()));
        stats.put("relationsByType", byType);
        
        double avgStrength = relations.stream()
                .mapToDouble(MemoryRelation::getStrength)
                .average()
                .orElse(0.0);
        stats.put("averageStrength", avgStrength);
        
        int totalAccess = relations.stream()
                .mapToInt(r -> r.getAccessCount() != null ? r.getAccessCount() : 0)
                .sum();
        stats.put("totalAccessCount", totalAccess);
        
        return stats;
    }
    
    public void deleteRelation(Long sourceMemoryId, Long targetMemoryId) {
        LambdaQueryWrapper<MemoryRelation> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w
            .eq(MemoryRelation::getSourceMemoryId, sourceMemoryId)
            .eq(MemoryRelation::getTargetMemoryId, targetMemoryId)
        ).or(w -> w
            .eq(MemoryRelation::getSourceMemoryId, targetMemoryId)
            .eq(MemoryRelation::getTargetMemoryId, sourceMemoryId)
        );
        
        memoryRelationMapper.delete(wrapper);
        
        invalidateCache(sourceMemoryId);
        invalidateCache(targetMemoryId);
        
        log.info("删除记忆关联: {} <-> {}", sourceMemoryId, targetMemoryId);
    }
    
    public void deleteAllRelationsForMemory(Long memoryId) {
        LambdaQueryWrapper<MemoryRelation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MemoryRelation::getSourceMemoryId, memoryId)
               .or()
               .eq(MemoryRelation::getTargetMemoryId, memoryId);
        
        memoryRelationMapper.delete(wrapper);
        invalidateCache(memoryId);
        
        log.info("删除记忆关联: memoryId={}", memoryId);
    }
    
    private void invalidateCache(Long memoryId) {
        relationCache.remove(memoryId);
    }
    
    public void clearCache() {
        relationCache.clear();
    }
    
    public List<MemoryRelation> findStrongestRelations(int limit) {
        LambdaQueryWrapper<MemoryRelation> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(MemoryRelation::getStrength)
               .last("LIMIT " + limit);
        return memoryRelationMapper.selectList(wrapper);
    }
    
    public List<MemoryRelation> findMostAccessedRelations(int limit) {
        LambdaQueryWrapper<MemoryRelation> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(MemoryRelation::getAccessCount)
               .last("LIMIT " + limit);
        return memoryRelationMapper.selectList(wrapper);
    }
}
