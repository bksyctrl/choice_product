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
import com.ecommerce.workflow.entity.CaseMemory;
import com.ecommerce.workflow.mapper.CaseMemoryMapper;

@RestController
@RequestMapping("/api/case-memory")
public class CaseMemoryController {
    
    private static final Logger log = LoggerFactory.getLogger(CaseMemoryController.class);
    
    @Autowired
    private CaseMemoryMapper caseMemoryMapper;
    
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String productName) {
        log.info("获取案例记忆列表: page={}, size={}, caseType={}, productName={}", page, size, caseType, productName);
        
        try {
            Page<CaseMemory> pageObj = new Page<>(page, size);
            QueryWrapper<CaseMemory> wrapper = new QueryWrapper<>();
            wrapper.eq("deleted", 0);
            
            if (caseType != null && !caseType.isEmpty()) {
                wrapper.eq("case_type", caseType);
            }
            if (productName != null && !productName.isEmpty()) {
                wrapper.like("product_name", productName);
            }
            
            wrapper.orderByDesc("created_at");
            
            Page<CaseMemory> result = caseMemoryMapper.selectPage(pageObj, wrapper);
            
            Map<String, Object> response = new HashMap<>();
            response.put("list", result.getRecords());
            response.put("total", result.getTotal());
            response.put("page", page);
            response.put("size", size);
            
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("获取案例记忆列表失败", e);
            return ApiResponse.error("获取案例记忆列表失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/get/{id}")
    public ApiResponse<CaseMemory> getById(@PathVariable Long id) {
        log.info("获取案例记忆详情: id={}", id);
        CaseMemory caseMemory = caseMemoryMapper.selectById(id);
        if (caseMemory == null) {
            return ApiResponse.error("案例记忆不存在");
        }
        return ApiResponse.success(caseMemory);
    }
    
    @GetMapping("/detail/{id}")
    public ApiResponse<CaseMemory> getDetail(@PathVariable Long id) {
        return getById(id);
    }
    
    @PostMapping("/create")
    public ApiResponse<CaseMemory> create(@RequestBody CaseMemory caseMemory) {
        log.info("创建案例记忆: productName={}", caseMemory.getProductName());
        try {
            caseMemoryMapper.insert(caseMemory);
            return ApiResponse.success(caseMemory);
        } catch (Exception e) {
            log.error("创建案例记忆失败", e);
            return ApiResponse.error("创建案例记忆失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/update/{id}")
    public ApiResponse<CaseMemory> update(@PathVariable Long id, @RequestBody CaseMemory caseMemory) {
        log.info("更新案例记忆: id={}", id);
        CaseMemory existing = caseMemoryMapper.selectById(id);
        if (existing == null) {
            return ApiResponse.error("案例不存在");
        }
        caseMemory.setId(id);
        caseMemoryMapper.updateById(caseMemory);
        return ApiResponse.success(caseMemory);
    }
    
    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        log.info("删除案例记忆: id={}", id);
        CaseMemory caseMemory = caseMemoryMapper.selectById(id);
        if (caseMemory == null) {
            return ApiResponse.error("案例记忆不存在");
        }
        caseMemory.setDeleted(1);
        caseMemoryMapper.updateById(caseMemory);
        return ApiResponse.success("删除成功");
    }
    
    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> getStatistics() {
        log.info("获取案例记忆统计数据");
        
        try {
            long totalCount = caseMemoryMapper.selectCount(
                new QueryWrapper<CaseMemory>().eq("deleted", 0)
            );
            
            long successCount = caseMemoryMapper.selectCount(
                new QueryWrapper<CaseMemory>()
                    .eq("deleted", 0)
                    .eq("case_type", "success")
            );
            
            long failCount = caseMemoryMapper.selectCount(
                new QueryWrapper<CaseMemory>()
                    .eq("deleted", 0)
                    .eq("case_type", "fail")
            );
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("total", totalCount);
            stats.put("success", successCount);
            stats.put("fail", failCount);
            
            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("获取案例记忆统计数据失败", e);
            return ApiResponse.error("获取案例记忆统计数据失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/search")
    public ApiResponse<List<CaseMemory>> search(@RequestParam String keyword) {
        log.info("搜索案例记忆: keyword={}", keyword);
        
        try {
            List<CaseMemory> results = caseMemoryMapper.selectList(
                new QueryWrapper<CaseMemory>()
                    .eq("deleted", 0)
                    .and(wrapper -> wrapper
                        .like("product_name", keyword)
                        .or()
                        .like("lesson_learned", keyword)
                        .or()
                        .like("failure_reason", keyword)
                    )
                    .orderByDesc("created_at")
                    .last("LIMIT 20")
            );
            
            return ApiResponse.success(results);
        } catch (Exception e) {
            log.error("搜索案例记忆失败", e);
            return ApiResponse.error("搜索案例记忆失败: " + e.getMessage());
        }
    }
}
