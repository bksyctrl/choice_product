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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.workflow.entity.AvoidanceRule;
import com.ecommerce.workflow.mapper.AvoidanceRuleMapper;

@RestController
@RequestMapping("/api/avoidance-rules")
public class AvoidanceRuleController {
    
    private static final Logger log = LoggerFactory.getLogger(AvoidanceRuleController.class);
    
    @Autowired
    private AvoidanceRuleMapper avoidanceRuleMapper;
    
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String category) {
        log.info("查询避坑规则列表: page={}, size={}, category={}", page, size, category);
        
        try {
            Page<AvoidanceRule> pageObj = new Page<>(page, size);
            QueryWrapper<AvoidanceRule> wrapper = new QueryWrapper<>();
            wrapper.eq("deleted", 0);
            
            if (category != null && !category.isEmpty()) {
                wrapper.eq("category", category);
            }
            
            wrapper.orderByDesc("created_at");
            
            Page<AvoidanceRule> result = avoidanceRuleMapper.selectPage(pageObj, wrapper);
            
            Map<String, Object> response = new HashMap<>();
            response.put("list", result.getRecords());
            response.put("total", result.getTotal());
            response.put("page", page);
            response.put("size", size);
            
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("查询避坑规则列表失败", e);
            return ApiResponse.error("查询避坑规则列表失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/get/{id}")
    public ApiResponse<AvoidanceRule> getById(@PathVariable Long id) {
        log.info("查询避坑规则: id={}", id);
        AvoidanceRule rule = avoidanceRuleMapper.selectById(id);
        if (rule == null) {
            return ApiResponse.error("规则不存在");
        }
        return ApiResponse.success(rule);
    }
    
    @PostMapping("/create")
    public ApiResponse<AvoidanceRule> create(@RequestBody AvoidanceRule rule) {
        log.info("创建避坑规则: title={}", rule.getTitle());
        try {
            avoidanceRuleMapper.insert(rule);
            return ApiResponse.success(rule);
        } catch (Exception e) {
            log.error("创建避坑规则失败", e);
            return ApiResponse.error("创建避坑规则失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/update/{id}")
    public ApiResponse<AvoidanceRule> update(@PathVariable Long id, @RequestBody AvoidanceRule rule) {
        log.info("更新避坑规则: id={}", id);
        AvoidanceRule existing = avoidanceRuleMapper.selectById(id);
        if (existing == null) {
            return ApiResponse.error("规则不存在");
        }
        rule.setId(id);
        avoidanceRuleMapper.updateById(rule);
        return ApiResponse.success(rule);
    }
    
    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        log.info("删除避坑规则: id={}", id);
        AvoidanceRule rule = avoidanceRuleMapper.selectById(id);
        if (rule == null) {
            return ApiResponse.error("规则不存在");
        }
        rule.setDeleted(1);
        avoidanceRuleMapper.updateById(rule);
        return ApiResponse.success("删除成功");
    }
    
    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> getStatistics() {
        log.info("获取避坑规则统计数据");
        
        try {
            long totalCount = avoidanceRuleMapper.selectCount(
                new QueryWrapper<AvoidanceRule>().eq("deleted", 0)
            );
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("total", totalCount);
            
            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("获取统计数据失败", e);
            return ApiResponse.error("获取统计数据失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/search")
    public ApiResponse<List<AvoidanceRule>> search(@RequestParam String keyword) {
        log.info("搜索避坑规则: keyword={}", keyword);
        
        try {
            List<AvoidanceRule> results = avoidanceRuleMapper.selectList(
                new QueryWrapper<AvoidanceRule>()
                    .eq("deleted", 0)
                    .and(wrapper -> wrapper
                        .like("title", keyword)
                        .or()
                        .like("description", keyword)
                        .or()
                        .like("solution", keyword)
                    )
                    .orderByDesc("created_at")
                    .last("LIMIT 20")
            );
            
            return ApiResponse.success(results);
        } catch (Exception e) {
            log.error("搜索避坑规则失败", e);
            return ApiResponse.error("搜索避坑规则失败: " + e.getMessage());
        }
    }
}
