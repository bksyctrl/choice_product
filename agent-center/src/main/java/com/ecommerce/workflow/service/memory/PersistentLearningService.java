package com.ecommerce.workflow.service.memory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PersistentLearningService {
    private static final Logger log = LoggerFactory.getLogger(PersistentLearningService.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final WhiteBoxMemoryService whiteBoxMemory;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final Map<String, EffectiveStrategy> effectiveStrategies = new HashMap<>();
    private final Map<String, List<EvolutionMemory>> evolutionMemories = new HashMap<>();
    private final List<FailedPattern> failedPatterns = new ArrayList<>();
    
    public static class EffectiveStrategy {
        private String id;
        private String skillCode;
        private String strategyType;
        private double avgImprovement;
        private int successCount;
        private int failCount;
        private String successfulParams;
        private LocalDateTime lastUpdated;
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getSkillCode() { return skillCode; }
        public void setSkillCode(String skillCode) { this.skillCode = skillCode; }
        public String getStrategyType() { return strategyType; }
        public void setStrategyType(String strategyType) { this.strategyType = strategyType; }
        public double getAvgImprovement() { return avgImprovement; }
        public void setAvgImprovement(double avgImprovement) { this.avgImprovement = avgImprovement; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public int getFailCount() { return failCount; }
        public void setFailCount(int failCount) { this.failCount = failCount; }
        public String getSuccessfulParams() { return successfulParams; }
        public void setSuccessfulParams(String successfulParams) { this.successfulParams = successfulParams; }
        public LocalDateTime getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
    }
    
    public static class EvolutionMemory {
        private String id;
        private String skillCode;
        private String strategyType;
        private String params;
        private double beforeMetric;
        private double afterMetric;
        private boolean effective;
        private LocalDateTime timestamp;
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getSkillCode() { return skillCode; }
        public void setSkillCode(String skillCode) { this.skillCode = skillCode; }
        public String getStrategyType() { return strategyType; }
        public void setStrategyType(String strategyType) { this.strategyType = strategyType; }
        public String getParams() { return params; }
        public void setParams(String params) { this.params = params; }
        public double getBeforeMetric() { return beforeMetric; }
        public void setBeforeMetric(double beforeMetric) { this.beforeMetric = beforeMetric; }
        public double getAfterMetric() { return afterMetric; }
        public void setAfterMetric(double afterMetric) { this.afterMetric = afterMetric; }
        public boolean isEffective() { return effective; }
        public void setEffective(boolean effective) { this.effective = effective; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
    
    public static class FailedPattern {
        private String id;
        private String issueType;
        private String description;
        private String failureReason;
        private int occurrenceCount;
        private LocalDateTime firstOccurrence;
        private LocalDateTime lastOccurrence;
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getIssueType() { return issueType; }
        public void setIssueType(String issueType) { this.issueType = issueType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
        public int getOccurrenceCount() { return occurrenceCount; }
        public void setOccurrenceCount(int occurrenceCount) { this.occurrenceCount = occurrenceCount; }
        public LocalDateTime getFirstOccurrence() { return firstOccurrence; }
        public void setFirstOccurrence(LocalDateTime firstOccurrence) { this.firstOccurrence = firstOccurrence; }
        public LocalDateTime getLastOccurrence() { return lastOccurrence; }
        public void setLastOccurrence(LocalDateTime lastOccurrence) { this.lastOccurrence = lastOccurrence; }
    }

    public PersistentLearningService(JdbcTemplate jdbcTemplate, WhiteBoxMemoryService whiteBoxMemory) {
        this.jdbcTemplate = jdbcTemplate;
        this.whiteBoxMemory = whiteBoxMemory;
        initTables();
        loadFromDatabase();
    }
    
    private void initTables() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_effective_strategy (
                    id VARCHAR(50) PRIMARY KEY,
                    skill_code VARCHAR(100),
                    strategy_type VARCHAR(50),
                    avg_improvement DOUBLE,
                    success_count INT DEFAULT 0,
                    fail_count INT DEFAULT 0,
                    successful_params TEXT,
                    last_updated TIMESTAMP
                )
                """);
            try {
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_skill_code ON sys_effective_strategy(skill_code)");
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.debug("sys_effective_strategy table may already exist");
        }
        
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_evolution_memory (
                    id VARCHAR(50) PRIMARY KEY,
                    skill_code VARCHAR(100),
                    strategy_type VARCHAR(50),
                    params TEXT,
                    before_metric DOUBLE,
                    after_metric DOUBLE,
                    effective BOOLEAN,
                    timestamp TIMESTAMP
                )
                """);
            try {
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_evolution_skill_code ON sys_evolution_memory(skill_code)");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_evolution_timestamp ON sys_evolution_memory(timestamp)");
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.debug("sys_evolution_memory table may already exist");
        }
        
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_failed_pattern (
                    id VARCHAR(50) PRIMARY KEY,
                    issue_type VARCHAR(50),
                    description TEXT,
                    failure_reason TEXT,
                    occurrence_count INT DEFAULT 1,
                    first_occurrence TIMESTAMP,
                    last_occurrence TIMESTAMP
                )
                """);
            try {
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_failed_issue_type ON sys_failed_pattern(issue_type)");
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.debug("sys_failed_pattern table may already exist");
        }
        
        log.info("持久化学习系统初始化完成");
    }
    
    private String safeGetString(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null) return null;
        return val.toString();
    }

    private double safeGetDouble(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0.0; }
    }

    private int safeGetInt(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return 0; }
    }

    private boolean safeGetBoolean(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null) return false;
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof Number) return ((Number) val).intValue() != 0;
        return Boolean.parseBoolean(val.toString());
    }

    private LocalDateTime safeGetLocalDateTime(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null) return null;
        if (val instanceof LocalDateTime) return (LocalDateTime) val;
        if (val instanceof java.sql.Timestamp) return ((java.sql.Timestamp) val).toLocalDateTime();
        try { return LocalDateTime.parse(val.toString()); } catch (Exception e) { return null; }
    }

    private void loadFromDatabase() {
        try {
            List<Map<String, Object>> strategies = jdbcTemplate.queryForList(
                    "SELECT * FROM sys_effective_strategy");
            for (Map<String, Object> row : strategies) {
                EffectiveStrategy strategy = new EffectiveStrategy();
                strategy.setId(safeGetString(row, "id"));
                strategy.setSkillCode(safeGetString(row, "skill_code"));
                strategy.setStrategyType(safeGetString(row, "strategy_type"));
                strategy.setAvgImprovement(safeGetDouble(row, "avg_improvement"));
                strategy.setSuccessCount(safeGetInt(row, "success_count"));
                strategy.setFailCount(safeGetInt(row, "fail_count"));
                strategy.setSuccessfulParams(safeGetString(row, "successful_params"));
                strategy.setLastUpdated(safeGetLocalDateTime(row, "last_updated"));
                if (strategy.getSkillCode() != null) {
                    effectiveStrategies.put(strategy.getSkillCode() + ":" + strategy.getStrategyType(), strategy);
                }
            }
            
            List<Map<String, Object>> memories = jdbcTemplate.queryForList(
                    "SELECT * FROM sys_evolution_memory ORDER BY timestamp DESC LIMIT 1000");
            for (Map<String, Object> row : memories) {
                EvolutionMemory memory = new EvolutionMemory();
                memory.setId(safeGetString(row, "id"));
                memory.setSkillCode(safeGetString(row, "skill_code"));
                memory.setStrategyType(safeGetString(row, "strategy_type"));
                memory.setParams(safeGetString(row, "params"));
                memory.setBeforeMetric(safeGetDouble(row, "before_metric"));
                memory.setAfterMetric(safeGetDouble(row, "after_metric"));
                memory.setEffective(safeGetBoolean(row, "effective"));
                memory.setTimestamp(safeGetLocalDateTime(row, "timestamp"));
                if (memory.getSkillCode() != null) {
                    evolutionMemories.computeIfAbsent(memory.getSkillCode(), k -> new ArrayList<>()).add(memory);
                }
            }
            
            List<Map<String, Object>> patterns = jdbcTemplate.queryForList(
                    "SELECT * FROM sys_failed_pattern");
            for (Map<String, Object> row : patterns) {
                FailedPattern pattern = new FailedPattern();
                pattern.setId(safeGetString(row, "id"));
                pattern.setIssueType(safeGetString(row, "issue_type"));
                pattern.setDescription(safeGetString(row, "description"));
                pattern.setFailureReason(safeGetString(row, "failure_reason"));
                pattern.setOccurrenceCount(safeGetInt(row, "occurrence_count"));
                pattern.setFirstOccurrence(safeGetLocalDateTime(row, "first_occurrence"));
                pattern.setLastOccurrence(safeGetLocalDateTime(row, "last_occurrence"));
                failedPatterns.add(pattern);
            }
            
            log.info("持久化学习数据加载完成: 有效策略={}个, 进化记忆={}个, 失败模式={}个", 
                    effectiveStrategies.size(), 
                    evolutionMemories.values().stream().mapToInt(List::size).sum(),
                    failedPatterns.size());
        } catch (Exception e) {
            log.error("持久化学习数据加载失败", e);
        }
    }
    
    @Transactional
    public void saveEffectiveStrategy(String skillCode, String strategyType, 
                                       double improvement, boolean success,
                                       Map<String, Object> params) {
        String key = skillCode + ":" + strategyType;
        EffectiveStrategy strategy = effectiveStrategies.get(key);
        
        if (strategy == null) {
            strategy = new EffectiveStrategy();
            strategy.setId("STR_" + System.currentTimeMillis());
            strategy.setSkillCode(skillCode);
            strategy.setStrategyType(strategyType);
            strategy.setSuccessCount(0);
            strategy.setFailCount(0);
            effectiveStrategies.put(key, strategy);
        }
        
        if (success) {
            strategy.setSuccessCount(strategy.getSuccessCount() + 1);
            if (params != null) {
                try {
                    strategy.setSuccessfulParams(objectMapper.writeValueAsString(params));
                } catch (JsonProcessingException e) {
                    log.warn("参数序列化失败", e);
                }
            }
        } else {
            strategy.setFailCount(strategy.getFailCount() + 1);
        }
        
        int total = strategy.getSuccessCount() + strategy.getFailCount();
        strategy.setAvgImprovement(
            (strategy.getAvgImprovement() * (total - 1) + improvement) / total
        );
        strategy.setLastUpdated(LocalDateTime.now());
        
        jdbcTemplate.update("""
            INSERT INTO sys_effective_strategy 
            (id, skill_code, strategy_type, avg_improvement, success_count, fail_count, successful_params, last_updated)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
            avg_improvement = ?, success_count = ?, fail_count = ?, successful_params = ?, last_updated = ?
            """,
            strategy.getId(), strategy.getSkillCode(), strategy.getStrategyType(),
            strategy.getAvgImprovement(), strategy.getSuccessCount(), strategy.getFailCount(),
            strategy.getSuccessfulParams(), strategy.getLastUpdated(),
            strategy.getAvgImprovement(), strategy.getSuccessCount(), strategy.getFailCount(),
            strategy.getSuccessfulParams(), strategy.getLastUpdated()
        );
        
        log.debug("保存有效策略: {} -> 成功={}/失败={}", key, strategy.getSuccessCount(), strategy.getFailCount());
    }
    
    @Transactional
    public void saveEvolutionMemory(String skillCode, String strategyType,
                                     Map<String, Object> params, double beforeMetric,
                                     double afterMetric, boolean effective) {
        EvolutionMemory memory = new EvolutionMemory();
        memory.setId("MEM_" + System.currentTimeMillis());
        memory.setSkillCode(skillCode);
        memory.setStrategyType(strategyType);
        try {
            memory.setParams(params != null ? objectMapper.writeValueAsString(params) : "{}");
        } catch (JsonProcessingException e) {
            memory.setParams("{}");
        }
        memory.setBeforeMetric(beforeMetric);
        memory.setAfterMetric(afterMetric);
        memory.setEffective(effective);
        memory.setTimestamp(LocalDateTime.now());
        
        evolutionMemories.computeIfAbsent(skillCode, k -> new ArrayList<>()).add(memory);
        
        jdbcTemplate.update("""
            INSERT INTO sys_evolution_memory
            (id, skill_code, strategy_type, params, before_metric, after_metric, effective, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            memory.getId(), memory.getSkillCode(), memory.getStrategyType(),
            memory.getParams(), memory.getBeforeMetric(), memory.getAfterMetric(),
            memory.isEffective(), memory.getTimestamp()
        );
        
        saveEffectiveStrategy(skillCode, strategyType, afterMetric - beforeMetric, effective, params);
        
        String category = effective ? "SUCCESS" : "FAILURE";
        whiteBoxMemory.recordLearning(category, skillCode + "优化策略",
                "优化前: " + String.format("%.2f", beforeMetric) + " -> 优化后: " + String.format("%.2f", afterMetric),
                strategyType,
                effective ? "提升: " + String.format("%.2f", afterMetric - beforeMetric) : "下降: " + String.format("%.2f", beforeMetric - afterMetric),
                effective ? memory.getParams() : "");
        
        log.debug("保存进化记忆: {} -> {} (有效? {})", skillCode, strategyType, effective);
    }
    
    @Transactional
    public void recordFailedPattern(String issueType, String description, String failureReason) {
        for (FailedPattern pattern : failedPatterns) {
            if (issueType != null && issueType.equals(pattern.getIssueType()) && 
                description != null && description.equals(pattern.getDescription())) {
                pattern.setOccurrenceCount(pattern.getOccurrenceCount() + 1);
                pattern.setLastOccurrence(LocalDateTime.now());
                
                jdbcTemplate.update("""
                    UPDATE sys_failed_pattern 
                    SET occurrence_count = ?, last_occurrence = ?
                    WHERE id = ?
                    """,
                    pattern.getOccurrenceCount(), pattern.getLastOccurrence(), pattern.getId()
                );
                return;
            }
        }
        
        FailedPattern pattern = new FailedPattern();
        pattern.setId("FAIL_" + System.currentTimeMillis());
        pattern.setIssueType(issueType);
        pattern.setDescription(description);
        pattern.setFailureReason(failureReason);
        pattern.setOccurrenceCount(1);
        pattern.setFirstOccurrence(LocalDateTime.now());
        pattern.setLastOccurrence(LocalDateTime.now());
        failedPatterns.add(pattern);
        
        jdbcTemplate.update("""
            INSERT INTO sys_failed_pattern
            (id, issue_type, description, failure_reason, occurrence_count, first_occurrence, last_occurrence)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            pattern.getId(), pattern.getIssueType(), pattern.getDescription(),
            pattern.getFailureReason(), pattern.getOccurrenceCount(),
            pattern.getFirstOccurrence(), pattern.getLastOccurrence()
        );
        
        whiteBoxMemory.recordLearning("FAILURE", issueType, description, failureReason, "系统自动记录", "");
        
        log.info("记录失败模式: {} - {}", issueType, description);
    }
    
    public boolean isKnownFailure(String issueType, String description) {
        return failedPatterns.stream()
                .anyMatch(p -> issueType != null && issueType.equals(p.getIssueType()) && 
                              description != null && description.equals(p.getDescription()) &&
                              p.getOccurrenceCount() >= 3);
    }
    
    public EffectiveStrategy getBestStrategy(String skillCode) {
        return effectiveStrategies.entrySet().stream()
                .filter(e -> e.getKey().startsWith(skillCode + ":"))
                .filter(e -> e.getValue().getSuccessCount() > e.getValue().getFailCount())
                .max((e1, e2) -> Double.compare(
                        e1.getValue().getAvgImprovement(), 
                        e2.getValue().getAvgImprovement()))
                .map(Map.Entry::getValue)
                .orElse(null);
    }
    
    public List<EvolutionMemory> getMemories(String skillCode) {
        return evolutionMemories.getOrDefault(skillCode, new ArrayList<>());
    }
    
    public Map<String, EffectiveStrategy> getAllStrategies() {
        return new HashMap<>(effectiveStrategies);
    }
    
    public List<FailedPattern> getFailedPatterns() {
        return new ArrayList<>(failedPatterns);
    }
    
    public Map<String, Object> getLearningStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalStrategies", effectiveStrategies.size());
        stats.put("totalMemories", evolutionMemories.values().stream().mapToInt(List::size).sum());
        stats.put("totalFailedPatterns", failedPatterns.size());
        stats.put("successRate", calculateOverallSuccessRate());
        return stats;
    }
    
    private double calculateOverallSuccessRate() {
        int totalSuccess = effectiveStrategies.values().stream()
                .mapToInt(EffectiveStrategy::getSuccessCount).sum();
        int totalFail = effectiveStrategies.values().stream()
                .mapToInt(EffectiveStrategy::getFailCount).sum();
        int total = totalSuccess + totalFail;
        return total > 0 ? (double) totalSuccess / total * 100 : 0;
    }
    
    @Scheduled(cron = "0 0 4 * * ?")
    public void cleanupOldMemories() {
        try {
            jdbcTemplate.update("""
                DELETE FROM sys_evolution_memory 
                WHERE timestamp < DATE_SUB(NOW(), INTERVAL 30 DAY)
                AND effective = true
                """);
            
            jdbcTemplate.update("""
                DELETE FROM sys_evolution_memory 
                WHERE timestamp < DATE_SUB(NOW(), INTERVAL 7 DAY)
                AND effective = false
                """);
            
            log.info("清理过期记忆完成");
        } catch (Exception e) {
            log.error("清理过期记忆失败", e);
        }
    }
}
