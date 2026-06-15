package com.ecommerce.workflow.controller;

import java.util.HashMap;
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
import com.ecommerce.workflow.entity.ViralPattern;
import com.ecommerce.workflow.mapper.ViralPatternMapper;

@RestController
@RequestMapping("/api/viral-patterns")
public class ViralPatternController {
    
    private static final Logger log = LoggerFactory.getLogger(ViralPatternController.class);
    
    @Autowired
    private ViralPatternMapper viralPatternMapper;
    
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String category) {
        log.info("获取爆款规律列表: page={}, size={}, category={}", page, size, category);
        
        try {
            Page<ViralPattern> pageObj = new Page<>(page, size);
            QueryWrapper<ViralPattern> wrapper = new QueryWrapper<>();
            wrapper.eq("deleted", 0);
            
            if (category != null && !category.isEmpty()) {
                wrapper.eq("category", category);
            }
            
            wrapper.orderByDesc("success_rate");
            
            Page<ViralPattern> result = viralPatternMapper.selectPage(pageObj, wrapper);
            
            Map<String, Object> response = new HashMap<>();
            response.put("list", result.getRecords());
            response.put("total", result.getTotal());
            response.put("page", page);
            response.put("size", size);
            
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("获取爆款规律列表失败", e);
            return ApiResponse.error("获取爆款规律列表失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/get/{id}")
    public ApiResponse<ViralPattern> getById(@PathVariable Long id) {
        log.info("获取爆款规律详情: id={}", id);
        ViralPattern pattern = viralPatternMapper.selectById(id);
        if (pattern == null) {
            return ApiResponse.error("爆款规律不存在");
        }
        return ApiResponse.success(pattern);
    }
    
    @PostMapping("/create")
    public ApiResponse<ViralPattern> create(@RequestBody ViralPattern pattern) {
        log.info("创建爆款规律: name={}", pattern.getName());
        try {
            viralPatternMapper.insert(pattern);
            return ApiResponse.success(pattern);
        } catch (Exception e) {
            log.error("创建爆款规律失败", e);
            return ApiResponse.error("创建爆款规律失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/update/{id}")
    public ApiResponse<ViralPattern> update(@PathVariable Long id, @RequestBody ViralPattern pattern) {
        log.info("更新爆款规律: id={}", id);
        ViralPattern existing = viralPatternMapper.selectById(id);
        if (existing == null) {
            return ApiResponse.error("爆款规律不存在");
        }
        pattern.setId(id);
        viralPatternMapper.updateById(pattern);
        return ApiResponse.success(pattern);
    }
    
    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        log.info("删除爆款规律: id={}", id);
        ViralPattern pattern = viralPatternMapper.selectById(id);
        if (pattern == null) {
            return ApiResponse.error("爆款规律不存在");
        }
        pattern.setDeleted(1);
        viralPatternMapper.updateById(pattern);
        return ApiResponse.success("删除成功");
    }
    
    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> getStatistics() {
        log.info("获取爆款规律统计数据");
        
        try {
            long totalCount = viralPatternMapper.selectCount(
                new QueryWrapper<ViralPattern>().eq("deleted", 0)
            );
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("total", totalCount);
            
            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("閼惧嘲褰囩紒鐔活吀娣団剝浼呮径杈Е", e);
            return ApiResponse.error("閼惧嘲褰囩紒鐔活吀娣団剝浼呮径杈Е: " + e.getMessage());
        }
    }
}
