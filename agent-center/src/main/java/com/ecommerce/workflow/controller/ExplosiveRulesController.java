package com.ecommerce.workflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.workflow.entity.ExplosiveRule;
import com.ecommerce.workflow.mapper.ExplosiveRuleMapper;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/explosive-rules")
public class ExplosiveRulesController {
    
    private final ExplosiveRuleMapper explosiveRuleMapper;
    
    public ExplosiveRulesController(ExplosiveRuleMapper explosiveRuleMapper) {
        this.explosiveRuleMapper = explosiveRuleMapper;
    }
    
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getExplosiveRules(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
        int offset = (safePage - 1) * safeSize;
        
        QueryWrapper<ExplosiveRule> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0)
               .eq("status", "ACTIVE");
        
        if (category != null && !category.isEmpty()) {
            wrapper.eq("category", category);
        }
        
        if (type != null && !type.isEmpty()) {
            wrapper.eq("category", type);
        }
        
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like("rule_name", keyword).or().like("rule_content", keyword).or().like("description", keyword));
        }
        
        wrapper.orderByDesc("effectiveness")
               .last("LIMIT " + safeSize + " OFFSET " + offset);
        
        List<ExplosiveRule> rules = explosiveRuleMapper.selectList(wrapper);
        
        long total = explosiveRuleMapper.selectCount(
            new QueryWrapper<ExplosiveRule>()
                .eq("deleted", 0)
                .eq("status", "ACTIVE")
        );
        
        List<Map<String, Object>> result = rules.stream().map(rule -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", rule.getRuleCode());
            map.put("ruleName", rule.getRuleName());
            map.put("ruleContent", rule.getRuleContent());
            map.put("description", rule.getDescription());
            map.put("effectiveness", rule.getEffectiveness() != null ? 
                rule.getEffectiveness().doubleValue() : 0.0);
            map.put("applications", rule.getApplications() != null ? rule.getApplications() : 0);
            map.put("successRate", rule.getSuccessRate() != null ? 
                rule.getSuccessRate().doubleValue() : 0.0);
            map.put("category", rule.getCategory());
            map.put("ruleType", rule.getCategory());
            map.put("confidence", rule.getEffectiveness() != null ? 
                rule.getEffectiveness().doubleValue() : 0.0);
            map.put("applyCount", rule.getApplications() != null ? rule.getApplications() : 0);
            map.put("successCount", rule.getSuccessRate() != null ? 
                (int)(rule.getApplications() * rule.getSuccessRate().doubleValue()) : 0);
            map.put("avgCvrLift", 0);
            
            if (rule.getTags() != null && !rule.getTags().isEmpty()) {
                map.put("features", Arrays.asList(rule.getTags().split(",")));
            } else {
                map.put("features", new ArrayList<>());
            }
            
            map.put("applicableScenarios", rule.getApplicableScenarios());
            map.put("priority", rule.getPriority());
            
            return map;
        }).collect(Collectors.toList());
        
        return ApiResponse.success(Map.of(
                "list", result,
                "total", total
        ));
    }
    
    @GetMapping("/{ruleCode}")
    public ApiResponse<Map<String, Object>> getRuleDetail(@PathVariable String ruleCode) {
        QueryWrapper<ExplosiveRule> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0)
               .eq("rule_code", ruleCode);
        
        ExplosiveRule rule = explosiveRuleMapper.selectOne(wrapper);
        
        if (rule == null) {
            return ApiResponse.error("规则不存在");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", rule.getRuleCode());
        result.put("title", rule.getRuleName());
        result.put("description", rule.getDescription());
        result.put("effectiveness", rule.getEffectiveness());
        result.put("applications", rule.getApplications());
        result.put("successRate", rule.getSuccessRate());
        result.put("category", rule.getCategory());
        result.put("ruleContent", rule.getRuleContent());
        result.put("applicableScenarios", rule.getApplicableScenarios());
        result.put("priority", rule.getPriority());
        
        if (rule.getTags() != null && !rule.getTags().isEmpty()) {
            result.put("tags", Arrays.asList(rule.getTags().split(",")));
        } else {
            result.put("tags", new ArrayList<>());
        }
        
        return ApiResponse.success(result);
    }
    
    @GetMapping("/categories")
    public ApiResponse<List<String>> getCategories() {
        QueryWrapper<ExplosiveRule> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0)
               .eq("status", "ACTIVE")
               .select("DISTINCT category");
        
        List<ExplosiveRule> rules = explosiveRuleMapper.selectList(wrapper);
        
        List<String> categories = rules.stream()
            .map(ExplosiveRule::getCategory)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        
        return ApiResponse.success(categories);
    }
}
