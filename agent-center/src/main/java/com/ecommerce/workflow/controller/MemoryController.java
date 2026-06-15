package com.ecommerce.workflow.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.workflow.service.memory.HeartbeatReflectionService;
import com.ecommerce.workflow.service.memory.PersistentLearningService;
import com.ecommerce.workflow.service.memory.PersistentLearningService.EffectiveStrategy;
import com.ecommerce.workflow.service.memory.PersistentLearningService.EvolutionMemory;
import com.ecommerce.workflow.service.memory.PersistentLearningService.FailedPattern;
import com.ecommerce.workflow.service.memory.WhiteBoxMemoryService;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {
    private static final Logger log = LoggerFactory.getLogger(MemoryController.class);
    
    private final WhiteBoxMemoryService whiteBoxMemory;
    private final PersistentLearningService persistentLearning;
    private final HeartbeatReflectionService heartbeatReflection;
    
    public MemoryController(WhiteBoxMemoryService whiteBoxMemory,
                             PersistentLearningService persistentLearning,
                             HeartbeatReflectionService heartbeatReflection) {
        this.whiteBoxMemory = whiteBoxMemory;
        this.persistentLearning = persistentLearning;
        this.heartbeatReflection = heartbeatReflection;
    }
    
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getStatus() {
        return ApiResponse.success(Map.of(
                "whiteBoxEnabled", whiteBoxMemory.isEnabled(),
                "memoryPath", whiteBoxMemory.getMemoryPath(),
                "memoryFiles", whiteBoxMemory.listMemoryFiles(),
                "learningStats", persistentLearning.getLearningStats(),
                "heartbeatEnabled", heartbeatReflection.isEnabled(),
                "reflectionCount", heartbeatReflection.getReflectionCount(),
                "lastReflection", heartbeatReflection.getLastReflectionTime() != null ? 
                        heartbeatReflection.getLastReflectionTime().toString() : "never"
        ));
    }
    
    @GetMapping("/files")
    public ApiResponse<List<String>> listMemoryFiles() {
        return ApiResponse.success(whiteBoxMemory.listMemoryFiles());
    }
    
    @GetMapping("/files/{fileName}")
    public ApiResponse<String> readMemory(@PathVariable String fileName) {
        String content = whiteBoxMemory.readMemory(fileName);
        if (content.isEmpty()) {
            return ApiResponse.error("记忆文件不存在或读取失败");
        }
        return ApiResponse.success(content);
    }
    
    @GetMapping("/export")
    public ApiResponse<String> exportAllMemories() {
        return ApiResponse.success(whiteBoxMemory.exportAllMemories());
    }
    
    @PostMapping("/import")
    public ApiResponse<Void> importMemories(@RequestBody String content) {
        whiteBoxMemory.importMemories(content);
        return ApiResponse.success(null);
    }
    
    @PostMapping("/learn/success")
    public ApiResponse<Void> recordSuccess(
            @RequestParam String skillCode,
            @RequestParam String scenario,
            @RequestParam String solution,
            @RequestParam String effect) {
        heartbeatReflection.recordSuccess(skillCode, scenario, solution, effect);
        return ApiResponse.success(null);
    }
    
    @PostMapping("/learn/failure")
    public ApiResponse<Void> recordFailure(
            @RequestParam String skillCode,
            @RequestParam String scenario,
            @RequestParam String attempt,
            @RequestParam String reason) {
        heartbeatReflection.recordFailure(skillCode, scenario, attempt, reason);
        return ApiResponse.success(null);
    }
    
    @PostMapping("/learn/correction")
    public ApiResponse<Void> recordCorrection(
            @RequestParam String scenario,
            @RequestParam String originalOutput,
            @RequestParam String userCorrection,
            @RequestParam String learningPoint) {
        heartbeatReflection.recordUserCorrection(scenario, originalOutput, userCorrection, learningPoint);
        return ApiResponse.success(null);
    }
    
    @GetMapping("/strategies")
    public ApiResponse<Map<String, EffectiveStrategy>> getAllStrategies() {
        return ApiResponse.success(persistentLearning.getAllStrategies());
    }
    
    @GetMapping("/strategies/{skillCode}")
    public ApiResponse<EffectiveStrategy> getBestStrategy(@PathVariable String skillCode) {
        EffectiveStrategy strategy = persistentLearning.getBestStrategy(skillCode);
        if (strategy == null) {
            return ApiResponse.error("该技能暂无有效策略记录");
        }
        return ApiResponse.success(strategy);
    }
    
    @GetMapping("/memories/{skillCode}")
    public ApiResponse<List<EvolutionMemory>> getMemories(@PathVariable String skillCode) {
        return ApiResponse.success(persistentLearning.getMemories(skillCode));
    }
    
    @GetMapping("/failures")
    public ApiResponse<List<FailedPattern>> getFailedPatterns() {
        return ApiResponse.success(persistentLearning.getFailedPatterns());
    }
    
    @GetMapping("/reflection")
    public ApiResponse<Map<String, Object>> getReflectionStatus() {
        return ApiResponse.success(Map.of(
                "enabled", heartbeatReflection.isEnabled(),
                "count", heartbeatReflection.getReflectionCount(),
                "lastTime", heartbeatReflection.getLastReflectionTime(),
                "lastResult", heartbeatReflection.getLastReflectionResult()
        ));
    }
    
    @PostMapping("/reflection/trigger")
    public ApiResponse<Void> triggerReflection() {
        heartbeatReflection.performHeartbeatReflection();
        return ApiResponse.success(null);
    }
    
    @PostMapping("/profile/update")
    public ApiResponse<Void> updateProfile(@RequestParam String key, @RequestParam String value) {
        whiteBoxMemory.updateUserProfile(key, value);
        return ApiResponse.success(null);
    }
    
    @PostMapping("/skill/usage")
    public ApiResponse<Void> recordSkillUsage(
            @RequestParam String skillName,
            @RequestParam boolean success) {
        whiteBoxMemory.updateSkillUsage(skillName, success);
        return ApiResponse.success(null);
    }
}
