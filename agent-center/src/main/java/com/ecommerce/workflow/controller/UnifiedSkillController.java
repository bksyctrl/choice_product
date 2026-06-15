package com.ecommerce.workflow.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import com.ecommerce.workflow.entity.SkillConfig;
import com.ecommerce.workflow.service.evolution.SkillConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/skills")
public class UnifiedSkillController {
    private static final Logger log = LoggerFactory.getLogger(UnifiedSkillController.class);
    
    private final SkillConfigService skillConfigService;
    private final ObjectMapper objectMapper;
    
    public UnifiedSkillController(SkillConfigService skillConfigService, ObjectMapper objectMapper) {
        this.skillConfigService = skillConfigService;
        this.objectMapper = objectMapper;
    }
    
    @GetMapping
    public ApiResponse<List<SkillConfig>> listAllSkills(
            @RequestParam(required = false) String category) {
        try {
            List<SkillConfig> skills;
            if (category != null && !category.isEmpty()) {
                skills = skillConfigService.getSkillsByCategory(category);
            } else {
                skills = skillConfigService.getAllActiveSkills();
            }
            return ApiResponse.success(skills);
        } catch (Exception e) {
            log.error("获取Skill列表失败", e);
            return ApiResponse.error("获取Skill列表失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/list")
    public ApiResponse<List<SkillConfig>> listSkills(
            @RequestParam(required = false) String category) {
        return listAllSkills(category);
    }
    
    @GetMapping("/categories")
    public ApiResponse<List<String>> listCategories() {
        try {
            List<SkillConfig> allSkills = skillConfigService.getAllActiveSkills();
            List<String> categories = allSkills.stream()
                    .map(SkillConfig::getSkillCategory)
                    .distinct()
                    .toList();
            return ApiResponse.success(categories);
        } catch (Exception e) {
            log.error("获取Skill分类失败", e);
            return ApiResponse.error("获取Skill分类失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/{skillCode}")
    public ApiResponse<SkillConfig> getSkill(@PathVariable String skillCode) {
        try {
            SkillConfig skill = skillConfigService.getActiveSkill(skillCode);
            if (skill == null) {
                return ApiResponse.error("Skill不存在: " + skillCode);
            }
            return ApiResponse.success(skill);
        } catch (Exception e) {
            log.error("获取Skill失败: {}", skillCode, e);
            return ApiResponse.error("获取Skill失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/{skillCode}/params")
    public ApiResponse<Map<String, Object>> getSkillParams(@PathVariable String skillCode) {
        try {
            Map<String, Object> params = skillConfigService.getSkillParams(skillCode);
            return ApiResponse.success(params);
        } catch (Exception e) {
            log.error("获取Skill参数失败: {}", skillCode, e);
            return ApiResponse.error("获取Skill参数失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/{skillCode}/workflow")
    public ApiResponse<Map<String, Object>> getSkillWorkflow(@PathVariable String skillCode) {
        try {
            SkillConfig skill = skillConfigService.getActiveSkill(skillCode);
            if (skill == null) {
                return ApiResponse.error("Skill不存在: " + skillCode);
            }
            
            Map<String, Object> workflow = new HashMap<>();
            workflow.put("skillCode", skillCode);
            workflow.put("skillName", skill.getSkillName());
            workflow.put("triggerType", skill.getTriggerType());
            workflow.put("triggerPattern", skill.getTriggerPattern());
            workflow.put("executorType", skill.getExecutorType());
            workflow.put("executorDefinition", skill.getExecutorDefinition());
            workflow.put("steps", new HashMap<String, Object>());
            
            return ApiResponse.success(workflow);
        } catch (Exception e) {
            log.error("获取Skill工作流定义失败: {}", skillCode, e);
            return ApiResponse.error("获取Skill工作流定义失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/search")
    public ApiResponse<List<SkillConfig>> searchSkills(@RequestParam String keyword) {
        try {
            List<SkillConfig> allSkills = skillConfigService.getAllActiveSkills();
            List<SkillConfig> filtered = allSkills.stream()
                    .filter(s -> s.getSkillName().toLowerCase().contains(keyword.toLowerCase()) ||
                                 s.getSkillCode().toLowerCase().contains(keyword.toLowerCase()))
                    .toList();
            return ApiResponse.success(filtered);
        } catch (Exception e) {
            log.error("搜索Skill失败: {}", keyword, e);
            return ApiResponse.error("搜索失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/custom")
    public ApiResponse<SkillConfig> createCustomSkill(@RequestBody CreateCustomSkillRequest request) {
        try {
            Map<String, Object> params = new HashMap<>();
            if (request.getConfigParams() != null && !request.getConfigParams().isEmpty()) {
                params = objectMapper.readValue(request.getConfigParams(), new TypeReference<Map<String, Object>>() {});
            }
            
            String skillCode = request.getName().toLowerCase().replace(" ", "_").replace("-", "_");
            
            SkillConfig skill = skillConfigService.createOrUpdateSkill(
                    skillCode, 
                    request.getName(), 
                    request.getCategory() != null ? request.getCategory() : "custom",
                    params, 
                    "用户自定义"
            );
            
            return ApiResponse.success(skill);
        } catch (Exception e) {
            log.error("创建自定义Skill失败", e);
            return ApiResponse.error("创建失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/{skillCode}")
    public ApiResponse<SkillConfig> updateSkill(@PathVariable String skillCode,
                                                   @Valid @RequestBody UpdateSkillRequest request) {
        try {
            SkillConfig existing = skillConfigService.getActiveSkill(skillCode);
            if (existing == null) {
                return ApiResponse.error("Skill不存在: " + skillCode);
            }
            
            Map<String, Object> currentParams = skillConfigService.getSkillParams(skillCode);
            
            if (request.getConfigParams() != null && !request.getConfigParams().isEmpty()) {
                Map<String, Object> newParams = objectMapper.readValue(request.getConfigParams(), new TypeReference<Map<String, Object>>() {});
                currentParams.putAll(newParams);
            }
            
            String newName = request.getName() != null ? request.getName() : existing.getSkillName();
            
            SkillConfig updated = skillConfigService.createOrUpdateSkill(
                    skillCode, 
                    newName, 
                    existing.getSkillCategory(),
                    currentParams, 
                    request.getReason() != null ? request.getReason() : "用户更新"
            );
            
            return ApiResponse.success(updated);
        } catch (Exception e) {
            log.error("更新Skill失败: {}", skillCode, e);
            return ApiResponse.error("更新失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/{skillCode}")
    public ApiResponse<Void> deleteSkill(@PathVariable String skillCode) {
        try {
            SkillConfig skill = skillConfigService.getActiveSkill(skillCode);
            if (skill == null) {
                return ApiResponse.error("Skill不存在: " + skillCode);
            }
            
            skill.setStatus("DELETED");
            skillConfigService.createOrUpdateSkill(
                    skillCode, 
                    skill.getSkillName(), 
                    skill.getSkillCategory(),
                    skillConfigService.getSkillParams(skillCode), 
                    "用户删除"
            );
            
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("删除Skill失败: {}", skillCode, e);
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/{skillCode}/record-usage")
    public ApiResponse<Void> recordUsage(@PathVariable String skillCode,
                                          @RequestParam boolean success,
                                          @RequestParam(required = false) Double cvr,
                                          @RequestParam(required = false) java.math.BigDecimal gmv) {
        try {
            skillConfigService.recordUsage(skillCode, success, cvr, gmv);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("记录Skill使用情况失败: {}", skillCode, e);
            return ApiResponse.error("记录失败: " + e.getMessage());
        }
    }
    
    public static class CreateCustomSkillRequest {
        private String name;
        private String description;
        private String category;
        private String triggerType;
        private String triggerPattern;
        private String executorType;
        private String executorDefinition;
        private String configParams;
        private Long userId;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getTriggerType() { return triggerType; }
        public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
        public String getTriggerPattern() { return triggerPattern; }
        public void setTriggerPattern(String triggerPattern) { this.triggerPattern = triggerPattern; }
        public String getExecutorType() { return executorType; }
        public void setExecutorType(String executorType) { this.executorType = executorType; }
        public String getExecutorDefinition() { return executorDefinition; }
        public void setExecutorDefinition(String executorDefinition) { this.executorDefinition = executorDefinition; }
        public String getConfigParams() { return configParams; }
        public void setConfigParams(String configParams) { this.configParams = configParams; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
    }
    
    public static class UpdateSkillRequest {
        private String name;
        private String description;
        private String configParams;
        private String reason;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getConfigParams() { return configParams; }
        public void setConfigParams(String configParams) { this.configParams = configParams; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
